package com.foundflow.matching.service;

import com.foundflow.genai.client.GenaiClient;
import com.foundflow.genai.client.model.AnswerRequest;
import com.foundflow.genai.client.model.AnswerResponse;
import com.foundflow.genai.client.model.EmbedRequest;
import com.foundflow.genai.client.model.SearchSnippet;
import com.foundflow.matching.domain.ItemType;
import com.foundflow.matching.domain.SearchScope;
import com.foundflow.matching.dto.ItemSearchRequest;
import com.foundflow.matching.dto.ItemSearchResponse;
import com.foundflow.matching.dto.SearchResultItem;
import com.foundflow.matching.repository.ItemEmbeddingRepository;
import com.foundflow.matching.repository.ScopedSimilarItem;
import com.foundflow.matching.security.VenueAccessService;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates staff free-text item search (RAG over the pgvector index): embed the query,
 * retrieve the most similar venue-scoped items, then ask genai-service to synthesise a grounded
 * answer over them. Best-effort: if answer synthesis is unavailable or disabled, the endpoint
 * degrades to the raw retrieval results.
 */
@Service
public class ItemSearchService {

    private static final Logger log = LoggerFactory.getLogger(ItemSearchService.class);

    /** genai /answer accepts at most 32 snippets (see api/openapi.yaml AnswerRequest). */
    private static final int MAX_SNIPPETS = 32;
    /** genai SearchSnippet.text max length (see api/openapi.yaml SearchSnippet). */
    private static final int MAX_SNIPPET_TEXT = 8000;

    private final GenaiClient genaiClient;
    private final ItemEmbeddingRepository embeddingRepository;
    private final VenueAccessService venueAccessService;
    private final MeterRegistry meters;
    private final boolean searchEnabled;
    private final int defaultK;
    private final int maxK;

    public ItemSearchService(
            GenaiClient genaiClient,
            ItemEmbeddingRepository embeddingRepository,
            VenueAccessService venueAccessService,
            MeterRegistry meters,
            @Value("${genai.search.enabled:true}") boolean searchEnabled,
            @Value("${genai.search.default-k:5}") int defaultK,
            @Value("${genai.search.max-k:32}") int maxK
    ) {
        this.genaiClient = genaiClient;
        this.embeddingRepository = embeddingRepository;
        this.venueAccessService = venueAccessService;
        this.meters = meters;
        this.searchEnabled = searchEnabled;
        this.defaultK = defaultK;
        this.maxK = maxK;
    }

    public ItemSearchResponse search(ItemSearchRequest request, Jwt jwt) {
        Timer.Sample overall = Timer.start(meters);
        String result = "success";
        try {
            UUID venueId = venueAccessService.getVenueId(jwt);
            SearchScope scope = request.scope() != null ? request.scope() : SearchScope.BOTH;
            int k = clampK(request.k());
            String query = request.query().strip();

            float[] embedding = embedQuery(query);

            List<ScopedSimilarItem> hits = Timer.builder("matching.search.retrieval.duration")
                    .description("Time to run the pgvector similarity search for a staff query")
                    .register(meters)
                    .record(() -> embeddingRepository.findTopKForSearch(scope.itemTypeOrNull(), venueId, embedding, k));
            DistributionSummary.builder("matching.search.retrieval.count")
                    .description("Number of items retrieved per staff query")
                    .register(meters)
                    .record(hits.size());

            List<SearchResultItem> results = hits.stream().map(ItemSearchService::toCard).toList();

            if (results.isEmpty()) {
                result = "empty";
                return degraded(results);   // nothing to ground
            }

            if (!searchEnabled) {
                meters.counter("matching.search.answer_total", "result", "disabled").increment();
                return synthesizedDisabled(results);
            }

            return synthesize(query, hits, results);
        } catch (AccessDeniedException e) {
            result = "forbidden";
            throw e;
        } catch (SearchUnavailableException e) {
            result = "embed_unavailable";
            throw e;
        } finally {
            meters.counter("matching.search.requests_total", "result", result).increment();
            overall.stop(meters.timer("matching.search.requests.duration"));
        }
    }

    private float[] embedQuery(String query) {
        try {
            return Timer.builder("matching.search.embed.duration")
                    .description("Time to embed a staff query via genai-service")
                    .register(meters)
                    .record(() -> GenaiClientSupport.toFloatArray(genaiClient.embed(
                            new EmbedRequest()
                                    .texts(List.of(query))
                                    .purpose(EmbedRequest.PurposeEnum.SEARCH_QUERY))));
        } catch (RestClientException e) {
            String reason = GenaiClientSupport.classify(e);
            meters.counter("matching.search.failures_total", "stage", "embed", "reason", reason).increment();
            log.warn("search embed failed ({}): {}", reason, e.getMessage());
            throw new SearchUnavailableException("Query embedding is unavailable.");
        }
    }

    private ItemSearchResponse synthesize(String query, List<ScopedSimilarItem> hits, List<SearchResultItem> results) {
        List<SearchSnippet> snippets = hits.stream().limit(MAX_SNIPPETS).map(ItemSearchService::toSnippet).toList();
        try {
            AnswerResponse answer = Timer.builder("matching.search.answer.duration")
                    .description("Time to synthesise a grounded answer via genai-service")
                    .register(meters)
                    .record(() -> genaiClient.answer(
                            new AnswerRequest().query(query).snippets(snippets).language("en")));

            boolean grounded = Boolean.TRUE.equals(answer.getGrounded());
            List<String> citations = resolveCitations(answer.getCitations(), results);
            meters.counter("matching.search.answer_total", "result", grounded ? "grounded" : "ungrounded").increment();
            return new ItemSearchResponse(answer.getAnswer(), grounded, citations, results);
        } catch (RestClientException e) {
            String reason = GenaiClientSupport.classify(e);
            meters.counter("matching.search.failures_total", "stage", "answer", "reason", reason).increment();
            meters.counter("matching.search.answer_total", "result", "error").increment();
            log.warn("search answer failed ({}); degrading to raw results: {}", reason, e.getMessage());
            return degraded(results);
        }
    }

    private ItemSearchResponse synthesizedDisabled(List<SearchResultItem> results) {
        return degraded(results);
    }

    private int clampK(Integer requested) {
        int k = requested != null ? requested : defaultK;
        return Math.max(1, Math.min(k, maxK));
    }

    private static ItemSearchResponse degraded(List<SearchResultItem> results) {
        return new ItemSearchResponse(null, false, List.of(), results);
    }

    private static List<String> resolveCitations(List<String> citations, List<SearchResultItem> results) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }
        Set<String> retrievedIds = results.stream().map(SearchResultItem::id).collect(Collectors.toSet());
        return citations.stream().filter(retrievedIds::contains).distinct().toList();
    }

    private static SearchResultItem toCard(ScopedSimilarItem hit) {
        return new SearchResultItem(
                hit.itemId().toString(),
                hit.itemType(),
                hit.category(),
                hit.textSource(),
                hit.cosineDistance()
        );
    }

    private static SearchSnippet toSnippet(ScopedSimilarItem hit) {
        return new SearchSnippet()
                .id(hit.itemId().toString())
                .itemType(hit.itemType() == ItemType.LOST
                        ? SearchSnippet.ItemTypeEnum.LOST_REPORT
                        : SearchSnippet.ItemTypeEnum.FOUND_ITEM)
                .category(hit.category())
                .text(truncate(hit.textSource(), MAX_SNIPPET_TEXT))
                .distance(BigDecimal.valueOf(hit.cosineDistance()));
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
