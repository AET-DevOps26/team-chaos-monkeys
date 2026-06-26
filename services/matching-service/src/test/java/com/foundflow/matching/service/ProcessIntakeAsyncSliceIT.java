package com.foundflow.matching.service;

import com.foundflow.genai.client.GenaiClient;
import com.foundflow.genai.client.model.EmbedResponse;
import com.foundflow.matching.domain.ItemType;
import com.foundflow.matching.domain.Match;
import com.foundflow.matching.messaging.MatchCandidateEventPublisher;
import com.foundflow.matching.repository.ItemEmbeddingRepository;
import com.foundflow.matching.repository.MatchRepository;
import com.foundflow.matching.repository.SimilarItemEmbedding;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Slice test that verifies processIntake does NOT block on verifyAsync.
 *
 * Rather than a full @SpringBootTest (which requires live DB + broker + env vars),
 * this test constructs a real ThreadPoolTaskExecutor and wires it into a
 * MatchVerificationService spy that dispatches to the pool — proving the
 * timing contract without the full Spring context.
 */
class ProcessIntakeAsyncSliceIT {

    private static final int TOP_K = 20;
    private static final float THRESHOLD = 0.55f;
    private static final int EMBEDDING_DIM = 2;
    private static final long SLOW_VERIFY_MS = 3_000L;

    private ItemEmbeddingRepository itemEmbeddingRepository;
    private MatchRepository matchRepository;
    private GenaiClient genaiClient;
    private MatchCandidateEventPublisher eventPublisher;
    private MatchVerificationService verificationService;
    private CandidateMatchingService matchingService;
    private ThreadPoolTaskExecutor executor;

    /** Latch released when verifyAsync finally "completes". */
    private CountDownLatch verifyDone;
    /** Whether the slow async task has completed (simulates the async write-back). */
    private AtomicBoolean verifySettled;

    @AfterEach
    void tearDown() {
        executor.destroy();
    }

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("test-verify-");
        executor.initialize();

        itemEmbeddingRepository = mock(ItemEmbeddingRepository.class);
        matchRepository = mock(MatchRepository.class);
        genaiClient = mock(GenaiClient.class);
        eventPublisher = mock(MatchCandidateEventPublisher.class);

        verifyDone = new CountDownLatch(1);
        verifySettled = new AtomicBoolean(false);

        // MatchVerificationService mock: verifyAsync submits a slow task to the real executor
        // (simulating @Async dispatching to genaiVerifyExecutor).
        // isNull() matcher required because JPA @GeneratedValue is not applied without a real DB.
        verificationService = mock(MatchVerificationService.class);
        doAnswer((Answer<Void>) invocation -> {
            executor.execute(() -> {
                try {
                    Thread.sleep(SLOW_VERIFY_MS);
                    verifySettled.set(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    verifyDone.countDown();
                }
            });
            return null;
        }).when(verificationService).verifyAsync(isNull(), any(String.class), any(String.class));

        matchingService = new CandidateMatchingService(
                itemEmbeddingRepository,
                matchRepository,
                genaiClient,
                eventPublisher,
                verificationService,
                new SimpleMeterRegistry(),
                TOP_K,
                THRESHOLD,
                0.85f,
                0.01f,
                EMBEDDING_DIM
        );
    }

    @Test
    void slowVerify_doesNotBlockProcessIntakeReturning() throws Exception {
        // Arrange
        UUID lostItemId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();

        when(itemEmbeddingRepository.findTextSource(ItemType.LOST, lostItemId))
                .thenReturn(Optional.empty());
        when(genaiClient.embed(any())).thenReturn(embedResponse(1.0f, 0.0f));
        // Use null category on the candidate so categoryGate(null, null)=1.0; combined=1.0*0.9=0.9 > 0.55
        when(itemEmbeddingRepository.findTopKSimilar(eq(ItemType.FOUND), eq(venueId), any(), eq(TOP_K)))
                .thenReturn(List.of(new SimilarItemEmbedding(foundItemId, null, null, "blue jacket found side", 0.1f)));
        when(matchRepository.findFirstByLostReportIdAndFoundItemId(lostItemId, foundItemId))
                .thenReturn(Optional.empty());
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act: measure how long processIntake takes to return
        long t0 = System.nanoTime();
        matchingService.processIntake(
                ItemType.LOST,
                lostItemId,
                venueId,
                "blue jacket lobby",
                null
        );
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        // Assert: processIntake must return well before the slow verify completes
        assertThat(elapsedMs)
                .as("processIntake must return before the %dms verify sleep (returned in %dms)",
                        SLOW_VERIFY_MS, elapsedMs)
                .isLessThan(SLOW_VERIFY_MS / 2);

        // Assert: verifyAsync was called (matchId is null in unit test since JPA doesn't assign it)
        verify(verificationService).verifyAsync(isNull(), anyString(), anyString());

        // Assert: the async verify does eventually complete (within a reasonable window)
        boolean completed = verifyDone.await(SLOW_VERIFY_MS * 2, TimeUnit.MILLISECONDS);
        assertThat(completed).as("verify task should have completed within timeout").isTrue();
        assertThat(verifySettled.get()).isTrue();
    }

    @Test
    void verifyAsync_receivesCorrectLostAndFoundTexts() throws Exception {
        // For LOST intake: lostText = embeddingText (own), foundText = candidate.textSource() (other)
        UUID lostItemId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();

        when(itemEmbeddingRepository.findTextSource(ItemType.LOST, lostItemId))
                .thenReturn(Optional.empty());
        when(genaiClient.embed(any())).thenReturn(embedResponse(1.0f, 0.0f));
        // Use null category so categoryGate(null, null)=1.0; combined=1.0*0.9=0.9 > 0.55 threshold
        when(itemEmbeddingRepository.findTopKSimilar(eq(ItemType.FOUND), eq(venueId), any(), eq(TOP_K)))
                .thenReturn(List.of(new SimilarItemEmbedding(foundItemId, null, null, "found item text source", 0.1f)));
        when(matchRepository.findFirstByLostReportIdAndFoundItemId(any(), any()))
                .thenReturn(Optional.empty());
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        matchingService.processIntake(ItemType.LOST, lostItemId, venueId, "blue jacket lobby", null);

        // The embedding text for "blue jacket lobby" with no attributes is just "blue jacket lobby"
        // Note: matchId is null in unit test (JPA @GeneratedValue not applied without real DB)
        verify(verificationService).verifyAsync(
                isNull(),
                eq("blue jacket lobby"),       // lostText = ownText (LOST side)
                eq("found item text source")   // foundText = candidate.textSource() (FOUND side)
        );
    }

    @Test
    void verifyAsync_receivesSwappedTexts_whenFoundItemIntake() throws Exception {
        // For FOUND intake: lostText = candidate.textSource() (other), foundText = embeddingText (own)
        UUID foundItemId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID lostItemId = UUID.randomUUID();

        when(itemEmbeddingRepository.findTextSource(ItemType.FOUND, foundItemId))
                .thenReturn(Optional.empty());
        when(genaiClient.embed(any())).thenReturn(embedResponse(1.0f, 0.0f));
        // Use null category so categoryGate(null, null)=1.0; combined=1.0*0.9=0.9 > 0.55 threshold
        when(itemEmbeddingRepository.findTopKSimilar(eq(ItemType.LOST), eq(venueId), any(), eq(TOP_K)))
                .thenReturn(List.of(new SimilarItemEmbedding(lostItemId, null, null, "lost report text source", 0.1f)));
        when(matchRepository.findFirstByLostReportIdAndFoundItemId(any(), any()))
                .thenReturn(Optional.empty());
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        matchingService.processIntake(ItemType.FOUND, foundItemId, venueId, "blue jacket front desk", null);

        // Note: matchId is null in unit test (JPA @GeneratedValue not applied without real DB)
        verify(verificationService).verifyAsync(
                isNull(),
                eq("lost report text source"),     // lostText = candidate.textSource() (LOST side)
                eq("blue jacket front desk")       // foundText = ownText (FOUND side)
        );
    }

    private static EmbedResponse embedResponse(float... vector) {
        java.util.List<Float> boxed = new java.util.ArrayList<>(vector.length);
        for (float v : vector) {
            boxed.add(v);
        }
        EmbedResponse response = new EmbedResponse();
        response.setEmbeddings(java.util.List.of(boxed));
        response.setDimensions(vector.length);
        return response;
    }
}
