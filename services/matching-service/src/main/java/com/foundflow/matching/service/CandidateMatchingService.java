package com.foundflow.matching.service;

import com.foundflow.events.FoundItemCreatedEvent;
import com.foundflow.events.FoundItemUpdatedEvent;
import com.foundflow.events.ItemAttributesPayload;
import com.foundflow.events.LostReportCreatedEvent;
import com.foundflow.events.LostReportUpdatedEvent;
import com.foundflow.genai.client.GenaiClient;
import com.foundflow.genai.client.model.EmbedRequest;
import com.foundflow.matching.domain.ItemEmbedding;
import com.foundflow.matching.domain.ItemType;
import com.foundflow.matching.domain.Match;
import com.foundflow.matching.domain.MatchStatus;
import com.foundflow.matching.messaging.MatchCandidateEventPublisher;
import com.foundflow.matching.repository.ItemEmbeddingRepository;
import com.foundflow.matching.repository.MatchRepository;
import com.foundflow.matching.repository.SimilarItemEmbedding;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class CandidateMatchingService {

    private static final Logger log = LoggerFactory.getLogger(CandidateMatchingService.class);

    private final ItemEmbeddingRepository itemEmbeddingRepository;
    private final MatchRepository matchRepository;
    private final GenaiClient genaiClient;
    private final MatchCandidateEventPublisher eventPublisher;
    private final MatchVerificationService verificationService;
    private final MeterRegistry meterRegistry;

    private final int topK;
    private final float threshold;
    private final float categoryMismatchFactor;
    private final float republishScoreDelta;
    private final int embeddingDim;

    public CandidateMatchingService(
            ItemEmbeddingRepository itemEmbeddingRepository,
            MatchRepository matchRepository,
            GenaiClient genaiClient,
            MatchCandidateEventPublisher eventPublisher,
            MatchVerificationService verificationService,
            MeterRegistry meterRegistry,
            @Value("${foundflow.matching.top-k:20}") int topK,
            @Value("${foundflow.matching.threshold:0.55}") float threshold,
            @Value("${foundflow.matching.category-mismatch-factor:0.85}") float categoryMismatchFactor,
            @Value("${foundflow.matching.republish-score-delta:0.01}") float republishScoreDelta,
            @Value("${foundflow.matching.embedding-dim}") int embeddingDim
    ) {
        this.itemEmbeddingRepository = itemEmbeddingRepository;
        this.matchRepository = matchRepository;
        this.genaiClient = genaiClient;
        this.eventPublisher = eventPublisher;
        this.verificationService = verificationService;
        this.meterRegistry = meterRegistry;
        this.topK = topK;
        this.threshold = threshold;
        this.categoryMismatchFactor = categoryMismatchFactor;
        this.republishScoreDelta = republishScoreDelta;
        this.embeddingDim = embeddingDim;
    }

    @Transactional
    public void findCandidatesForLostReport(LostReportCreatedEvent event) {
        processIntake(
                ItemType.LOST,
                event.lostReportId(),
                event.venueId(),
                event.contactEmail(),
                event.description(),
                event.attributes()
        );
    }

    @Transactional
    public void findCandidatesForLostReportUpdate(LostReportUpdatedEvent event) {
        processIntake(
                ItemType.LOST,
                event.lostReportId(),
                event.venueId(),
                event.contactEmail(),
                event.description(),
                event.attributes()
        );
    }

    @Transactional
    public void findCandidatesForFoundItem(FoundItemCreatedEvent event) {
        processIntake(
                ItemType.FOUND,
                event.foundItemId(),
                event.venueId(),
                null,
                event.intakeText(),
                event.attributes()
        );
    }

    @Transactional
    public void findCandidatesForFoundItemUpdate(FoundItemUpdatedEvent event) {
        // Only STORED items are available to match. Any other status (RESERVED once a
        // pickup is booked, RETURNED, DISPOSED) retires the item from the candidate pool;
        // reverting to STORED re-embeds and re-matches it. See issue #367.
        if (!"STORED".equals(event.status())) {
            int removed = itemEmbeddingRepository.deleteByItemTypeAndItemId(ItemType.FOUND, event.foundItemId());
            log.info(
                    "Found item {} is {} — removed {} embedding(s) from the matching pool.",
                    event.foundItemId(),
                    event.status(),
                    removed
            );
            return;
        }
        processIntake(
                ItemType.FOUND,
                event.foundItemId(),
                event.venueId(),
                null,
                event.intakeText(),
                event.attributes()
        );
    }

    void processIntake(
            ItemType itemType,
            UUID itemId,
            UUID venueId,
            String description,
            ItemAttributesPayload attributes
    ) {
        processIntake(itemType, itemId, venueId, null, description, attributes);
    }

    void processIntake(
            ItemType itemType,
            UUID itemId,
            UUID venueId,
            String contactEmail,
            String description,
            ItemAttributesPayload attributes
    ) {
        String embeddingText = buildEmbeddingText(description, attributes);
        if (embeddingText.isBlank()) {
            log.debug("Skipping {} {} — empty embedding text.", itemType, itemId);
            return;
        }

        Optional<String> existing = itemEmbeddingRepository.findTextSource(itemType, itemId);
        if (existing.isPresent() && existing.get().equals(embeddingText)) {
            log.debug("Skipping {} {} — text_source unchanged.", itemType, itemId);
            return;
        }

        String ownCategory = attributes != null ? attributes.category() : null;
        EmbedRequest embedRequest = new EmbedRequest()
                .texts(List.of(embeddingText))
                .purpose(itemType == ItemType.LOST
                        ? EmbedRequest.PurposeEnum.LOST_REPORT
                        : EmbedRequest.PurposeEnum.FOUND_ITEM);
        float[] embedding = Timer.builder("matching.embedding.duration")
                .description("Time to fetch an embedding from genai-service")
                .register(meterRegistry)
                .record(() -> GenaiClientSupport.toFloatArray(genaiClient.embed(embedRequest)));

        if (embedding.length != embeddingDim) {
            meterRegistry.counter("matching.embedding.dim_mismatch_total",
                    "expected", String.valueOf(embeddingDim),
                    "actual", String.valueOf(embedding.length)).increment();
            throw new EmbeddingDimensionMismatchException(
                    "Embedding from genai-service has " + embedding.length
                            + " dims, expected " + embeddingDim
                            + " for item " + itemType + " " + itemId);
        }

        itemEmbeddingRepository.upsert(new ItemEmbedding(
                UUID.randomUUID(),
                itemType,
                itemId,
                venueId,
                ownCategory,
                itemType == ItemType.LOST ? normalizeEmail(contactEmail) : null,
                embedding,
                embeddingText
        ));

        ItemType oppositeType = itemType == ItemType.LOST ? ItemType.FOUND : ItemType.LOST;
        List<SimilarItemEmbedding> candidates = Timer.builder("matching.search.duration")
                .description("Time to run pgvector similarity search")
                .register(meterRegistry)
                .record(() -> itemEmbeddingRepository.findTopKSimilar(oppositeType, venueId, embedding, topK));

        DistributionSummary scoreHistogram = DistributionSummary.builder("matching.score.combined")
                .description("Distribution of combined match scores")
                .register(meterRegistry);

        for (SimilarItemEmbedding candidate : candidates) {
            float attributeScore = categoryGate(ownCategory, candidate.category(), categoryMismatchFactor);
            float semanticScore = 1.0f - candidate.cosineDistance();
            float combinedScore = attributeScore * semanticScore;
            scoreHistogram.record(combinedScore);

            String decision = combinedScore >= threshold ? "above_threshold" : "below_threshold";
            meterRegistry.counter("matching.candidates.found_total", "decision", decision).increment();

            if (combinedScore < threshold) {
                continue;
            }

            UUID lostReportId = itemType == ItemType.LOST ? itemId : candidate.itemId();
            UUID foundItemId = itemType == ItemType.LOST ? candidate.itemId() : itemId;
            String recipientEmail = itemType == ItemType.LOST ? contactEmail : candidate.contactEmail();

            UpsertedMatch persisted = upsertMatch(
                    lostReportId,
                    foundItemId,
                    venueId,
                    recipientEmail,
                    attributeScore,
                    semanticScore,
                    combinedScore
            );

            if (persisted != null && persisted.shouldPublish()) {
                eventPublisher.publishMatchCandidateCreated(persisted.match());
                String ownText = embeddingText;
                String otherText = candidate.textSource();
                String lostText = itemType == ItemType.LOST ? ownText : otherText;
                String foundText = itemType == ItemType.LOST ? otherText : ownText;
                runAfterCommitOrNow(() -> verificationService.verifyAsync(persisted.match().getId(), lostText, foundText));
            }
        }
    }

    private UpsertedMatch upsertMatch(
            UUID lostReportId,
            UUID foundItemId,
            UUID venueId,
            String recipientEmail,
            float attributeScore,
            float semanticScore,
            float combinedScore
    ) {
        Optional<Match> existing = matchRepository.findFirstByLostReportIdAndFoundItemId(
                lostReportId,
                foundItemId
        );

        if (existing.isPresent()) {
            Match match = existing.get();
            if (match.getStatus() != MatchStatus.PENDING) {
                return null;
            }
            boolean materialScoreChange = scoreChanged(match, attributeScore, semanticScore, combinedScore);
            String normalizedEmail = normalizeEmail(recipientEmail);
            boolean contactChanged = normalizedEmail != null
                    && !Objects.equals(normalizedEmail, match.getRecipientEmail());
            if (!materialScoreChange && !contactChanged) {
                return new UpsertedMatch(match, false);
            }
            if (materialScoreChange) {
                match.setAttributeScore(attributeScore);
                match.setSemanticScore(semanticScore);
                match.setCombinedScore(combinedScore);
            }
            if (contactChanged) {
                match.setRecipientEmail(normalizedEmail);
            }
            return new UpsertedMatch(matchRepository.save(match), materialScoreChange || contactChanged);
        }

        Match fresh = new Match(
                foundItemId,
                lostReportId,
                venueId,
                normalizeEmail(recipientEmail),
                MatchStatus.PENDING,
                attributeScore,
                semanticScore,
                combinedScore,
                LocalDateTime.now()
        );
        return new UpsertedMatch(matchRepository.save(fresh), true);
    }

    private boolean scoreChanged(
            Match match,
            float attributeScore,
            float semanticScore,
            float combinedScore
    ) {
        return Math.abs(match.getAttributeScore() - attributeScore) >= republishScoreDelta
                || Math.abs(match.getSemanticScore() - semanticScore) >= republishScoreDelta
                || Math.abs(match.getCombinedScore() - combinedScore) >= republishScoreDelta;
    }

    /**
     * Category is a soft prior, not a veto. Exact agreement (case/whitespace
     * insensitive, including both-unknown) gives full weight; any other case —
     * different categories, or only one side categorised — applies a mild
     * {@code mismatchFactor} penalty rather than zeroing the score, so a strong
     * embedding match can still surface despite a category misclassification.
     * The async verify-match step backstops residual false positives.
     */
    static float categoryGate(String own, String candidate, float mismatchFactor) {
        if (Objects.equals(normalizeCategory(own), normalizeCategory(candidate))) {
            return 1.0f;
        }
        return mismatchFactor;
    }

    private static String normalizeCategory(String category) {
        return category == null ? null : category.trim().toLowerCase(Locale.ROOT);
    }

    static String buildEmbeddingText(String freeText, ItemAttributesPayload attributes) {
        List<String> parts = new ArrayList<>();
        if (freeText != null && !freeText.isBlank()) {
            parts.add(freeText.trim());
        }
        if (attributes != null) {
            if (attributes.description() != null && !attributes.description().isBlank()) {
                parts.add(attributes.description().trim());
            }
            if (attributes.category() != null) {
                parts.add("category: " + attributes.category());
            }
            if (attributes.brand() != null) {
                parts.add("brand: " + attributes.brand());
            }
            if (attributes.color() != null) {
                parts.add("color: " + attributes.color());
            }
            if (attributes.marks() != null && !attributes.marks().isEmpty()) {
                parts.add("marks: " + String.join(", ", attributes.marks()));
            }
        }
        return String.join(" | ", parts);
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }

    private void runAfterCommitOrNow(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private record UpsertedMatch(Match match, boolean shouldPublish) {
    }
}
