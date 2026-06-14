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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
        this.embeddingDim = embeddingDim;
    }

    public void findCandidatesForLostReport(LostReportCreatedEvent event) {
        processIntake(
                ItemType.LOST,
                event.lostReportId(),
                event.venueId(),
                event.description(),
                event.attributes()
        );
    }

    public void findCandidatesForLostReportUpdate(LostReportUpdatedEvent event) {
        processIntake(
                ItemType.LOST,
                event.lostReportId(),
                event.venueId(),
                event.description(),
                event.attributes()
        );
    }

    public void findCandidatesForFoundItem(FoundItemCreatedEvent event) {
        processIntake(
                ItemType.FOUND,
                event.foundItemId(),
                event.venueId(),
                event.intakeText(),
                event.attributes()
        );
    }

    public void findCandidatesForFoundItemUpdate(FoundItemUpdatedEvent event) {
        processIntake(
                ItemType.FOUND,
                event.foundItemId(),
                event.venueId(),
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
            float attributeScore = categoryGate(ownCategory, candidate.category());
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

            Match persisted = upsertMatch(
                    lostReportId,
                    foundItemId,
                    venueId,
                    attributeScore,
                    semanticScore,
                    combinedScore
            );

            if (persisted != null) {
                eventPublisher.publishMatchCandidateCreated(persisted);
                String ownText = embeddingText;
                String otherText = candidate.textSource();
                String lostText = itemType == ItemType.LOST ? ownText : otherText;
                String foundText = itemType == ItemType.LOST ? otherText : ownText;
                verificationService.verifyAsync(persisted.getId(), lostText, foundText);
            }
        }
    }

    private Match upsertMatch(
            UUID lostReportId,
            UUID foundItemId,
            UUID venueId,
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
            match.setAttributeScore(attributeScore);
            match.setSemanticScore(semanticScore);
            match.setCombinedScore(combinedScore);
            return matchRepository.save(match);
        }

        Match fresh = new Match(
                foundItemId,
                lostReportId,
                venueId,
                MatchStatus.PENDING,
                attributeScore,
                semanticScore,
                combinedScore,
                LocalDateTime.now()
        );
        return matchRepository.save(fresh);
    }

    static float categoryGate(String own, String candidate) {
        if (Objects.equals(own, candidate)) {
            return 1.0f;
        }
        if (own == null || candidate == null) {
            return 0.5f;
        }
        return 0.0f;
    }

    static String buildEmbeddingText(String description, ItemAttributesPayload attributes) {
        List<String> parts = new ArrayList<>();
        if (description != null && !description.isBlank()) {
            parts.add(description.trim());
        }
        if (attributes != null) {
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
}
