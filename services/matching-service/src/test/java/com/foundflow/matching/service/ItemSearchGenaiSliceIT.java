package com.foundflow.matching.service;

import com.foundflow.genai.client.GenaiClient;
import com.foundflow.matching.domain.ItemType;
import com.foundflow.matching.domain.SearchScope;
import com.foundflow.matching.dto.ItemSearchRequest;
import com.foundflow.matching.dto.ItemSearchResponse;
import com.foundflow.matching.repository.ItemEmbeddingRepository;
import com.foundflow.matching.repository.ScopedSimilarItem;
import com.foundflow.matching.security.VenueAccessService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Spring-free slice wiring a real {@link GenaiClient} (over WireMock) into {@link ItemSearchService}
 * with a mocked repository. Validates the actual embed+answer HTTP round-trip and, crucially, the
 * degrade-to-raw-results path when genai {@code /answer} fails.
 */
class ItemSearchGenaiSliceIT {

    WireMockServer wm;
    ItemEmbeddingRepository repository;
    ItemSearchService service;

    UUID venueId = UUID.randomUUID();
    UUID hitId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();

        // Mirror GenaiClientAutoConfiguration: SimpleClientHttpRequestFactory (HTTP/1.1) — the JDK
        // HttpClient default negotiates HTTP/2, which is flaky against WireMock (RST_STREAM).
        GenaiClient genaiClient = new GenaiClient(RestClient.builder()
                .baseUrl(wm.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build());
        repository = mock(ItemEmbeddingRepository.class);
        when(repository.findTopKForSearch(any(), eq(venueId), any(), anyInt())).thenReturn(List.of(
                new ScopedSimilarItem(hitId, ItemType.FOUND, "Wallet", "black leather wallet near lobby", 0.12f)
        ));

        service = new ItemSearchService(
                genaiClient, repository, new VenueAccessService(), new SimpleMeterRegistry(), true, 5, 32);
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    void search_happyPath_callsEmbedThenAnswer_andReturnsGroundedResult() {
        stubEmbedOk();
        wm.stubFor(post(urlEqualTo("/answer"))
                .withRequestBody(matchingJsonPath("$.query", equalTo("black leather wallet")))
                .withRequestBody(matchingJsonPath("$.snippets[0].id", equalTo(hitId.toString())))
                .withRequestBody(matchingJsonPath("$.snippets[0].itemType", equalTo("found_item")))
                .willReturn(okJson("""
                        {
                          "answer": "The match is a black leather wallet near the lobby [1].",
                          "citations": ["%s"],
                          "grounded": true,
                          "modelInfo": { "provider": "openai", "model": "gpt-4o-mini" }
                        }
                        """.formatted(hitId))));

        ItemSearchResponse response = service.search(
                new ItemSearchRequest("black leather wallet", SearchScope.BOTH, 5), jwtWithVenue());

        assertThat(response.answer()).contains("black leather wallet");
        assertThat(response.grounded()).isTrue();
        assertThat(response.citations()).containsExactly(hitId.toString());
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).id()).isEqualTo(hitId.toString());
    }

    @Test
    void search_answerReturns504_degradesToRawResults() {
        stubEmbedOk();
        wm.stubFor(post(urlEqualTo("/answer")).willReturn(aResponse().withStatus(504)));

        ItemSearchResponse response = service.search(
                new ItemSearchRequest("black leather wallet", SearchScope.BOTH, 5), jwtWithVenue());

        assertThat(response.answer()).isNull();
        assertThat(response.grounded()).isFalse();
        assertThat(response.citations()).isEmpty();
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).id()).isEqualTo(hitId.toString());
    }

    @Test
    void search_embedReturns504_throwsSearchUnavailable() {
        wm.stubFor(post(urlEqualTo("/embed")).willReturn(aResponse().withStatus(504)));

        assertThatThrownBy(() -> service.search(
                new ItemSearchRequest("black leather wallet", SearchScope.BOTH, 5), jwtWithVenue()))
                .isInstanceOf(SearchUnavailableException.class);
    }

    private void stubEmbedOk() {
        wm.stubFor(post(urlEqualTo("/embed")).willReturn(okJson("""
                {
                  "embeddings": [[0.1, 0.2]],
                  "dimensions": 2,
                  "modelInfo": { "provider": "openai", "model": "text-embedding-3-small" }
                }
                """)));
    }

    private Jwt jwtWithVenue() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("roles", List.of("STAFF"))
                .claim("venue_id", venueId.toString())
                .build();
    }
}
