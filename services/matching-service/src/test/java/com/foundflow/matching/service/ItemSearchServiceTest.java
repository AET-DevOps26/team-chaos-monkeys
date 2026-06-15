package com.foundflow.matching.service;

import com.foundflow.genai.client.GenaiClient;
import com.foundflow.genai.client.model.AnswerRequest;
import com.foundflow.genai.client.model.AnswerResponse;
import com.foundflow.genai.client.model.EmbedRequest;
import com.foundflow.genai.client.model.EmbedResponse;
import com.foundflow.matching.domain.ItemType;
import com.foundflow.matching.domain.SearchScope;
import com.foundflow.matching.dto.ItemSearchRequest;
import com.foundflow.matching.dto.ItemSearchResponse;
import com.foundflow.matching.repository.ItemEmbeddingRepository;
import com.foundflow.matching.repository.ScopedSimilarItem;
import com.foundflow.matching.security.VenueAccessService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemSearchServiceTest {

    private final GenaiClient genaiClient = org.mockito.Mockito.mock(GenaiClient.class);
    private final ItemEmbeddingRepository repository = org.mockito.Mockito.mock(ItemEmbeddingRepository.class);
    private final VenueAccessService venueAccessService = org.mockito.Mockito.mock(VenueAccessService.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private ItemSearchService service(boolean searchEnabled) {
        return new ItemSearchService(genaiClient, repository, venueAccessService, meters, searchEnabled, 5, 32);
    }

    @Test
    void search_happyPath_returnsGroundedAnswerWithResolvedCitations() {
        UUID venueId = UUID.randomUUID();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(venueAccessService.getVenueId(any())).thenReturn(venueId);
        when(genaiClient.embed(any())).thenReturn(embedResponse(0.1f, 0.2f));
        when(repository.findTopKForSearch(any(), eq(venueId), any(), anyInt())).thenReturn(List.of(
                new ScopedSimilarItem(id1, ItemType.FOUND, "Bag", "black bag near lobby", 0.10f),
                new ScopedSimilarItem(id2, ItemType.LOST, "Wallet", "brown wallet", 0.30f)
        ));
        when(genaiClient.answer(any())).thenReturn(new AnswerResponse()
                .answer("Top match is the black bag near the lobby [1].")
                .citations(List.of(id1.toString()))
                .grounded(true));

        ItemSearchResponse response = service(true)
                .search(new ItemSearchRequest("black bag near lobby", SearchScope.BOTH, 5), jwt());

        assertThat(response.answer()).contains("black bag");
        assertThat(response.grounded()).isTrue();
        assertThat(response.citations()).containsExactly(id1.toString());
        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).id()).isEqualTo(id1.toString());
        assertThat(response.results().get(0).itemType()).isEqualTo(ItemType.FOUND);
        assertThat(response.results().get(0).distance()).isEqualTo(0.10f);

        ArgumentCaptor<EmbedRequest> embedCaptor = ArgumentCaptor.forClass(EmbedRequest.class);
        verify(genaiClient).embed(embedCaptor.capture());
        assertThat(embedCaptor.getValue().getPurpose()).isEqualTo(EmbedRequest.PurposeEnum.SEARCH_QUERY);
        assertThat(embedCaptor.getValue().getTexts()).containsExactly("black bag near lobby");
    }

    @Test
    void search_foundScope_filtersRetrievalToFoundItems() {
        UUID venueId = UUID.randomUUID();
        when(venueAccessService.getVenueId(any())).thenReturn(venueId);
        when(genaiClient.embed(any())).thenReturn(embedResponse(0.1f));
        when(repository.findTopKForSearch(any(), any(), any(), anyInt())).thenReturn(List.of());

        service(true).search(new ItemSearchRequest("wallet", SearchScope.FOUND, 5), jwt());

        ArgumentCaptor<ItemType> filterCaptor = ArgumentCaptor.forClass(ItemType.class);
        verify(repository).findTopKForSearch(filterCaptor.capture(), eq(venueId), any(), eq(5));
        assertThat(filterCaptor.getValue()).isEqualTo(ItemType.FOUND);
    }

    @Test
    void search_bothScope_doesNotFilterByItemType() {
        UUID venueId = UUID.randomUUID();
        when(venueAccessService.getVenueId(any())).thenReturn(venueId);
        when(genaiClient.embed(any())).thenReturn(embedResponse(0.1f));
        when(repository.findTopKForSearch(any(), any(), any(), anyInt())).thenReturn(List.of());

        service(true).search(new ItemSearchRequest("wallet", SearchScope.BOTH, 5), jwt());

        ArgumentCaptor<ItemType> filterCaptor = ArgumentCaptor.forClass(ItemType.class);
        verify(repository).findTopKForSearch(filterCaptor.capture(), eq(venueId), any(), anyInt());
        assertThat(filterCaptor.getValue()).isNull();
    }

    @Test
    void search_nullScope_defaultsToBoth() {
        UUID venueId = UUID.randomUUID();
        when(venueAccessService.getVenueId(any())).thenReturn(venueId);
        when(genaiClient.embed(any())).thenReturn(embedResponse(0.1f));
        when(repository.findTopKForSearch(any(), any(), any(), anyInt())).thenReturn(List.of());

        service(true).search(new ItemSearchRequest("wallet", null, 5), jwt());

        ArgumentCaptor<ItemType> filterCaptor = ArgumentCaptor.forClass(ItemType.class);
        verify(repository).findTopKForSearch(filterCaptor.capture(), eq(venueId), any(), anyInt());
        assertThat(filterCaptor.getValue()).isNull();
    }

    @Test
    void search_nullK_usesConfiguredDefault() {
        UUID venueId = UUID.randomUUID();
        when(venueAccessService.getVenueId(any())).thenReturn(venueId);
        when(genaiClient.embed(any())).thenReturn(embedResponse(0.1f));
        when(repository.findTopKForSearch(any(), any(), any(), anyInt())).thenReturn(List.of());

        service(true).search(new ItemSearchRequest("wallet", SearchScope.BOTH, null), jwt());

        verify(repository).findTopKForSearch(any(), eq(venueId), any(), eq(5));
    }

    @Test
    void search_emptyResults_skipsAnswerCallAndReturnsUngrounded() {
        UUID venueId = UUID.randomUUID();
        when(venueAccessService.getVenueId(any())).thenReturn(venueId);
        when(genaiClient.embed(any())).thenReturn(embedResponse(0.1f));
        when(repository.findTopKForSearch(any(), any(), any(), anyInt())).thenReturn(List.of());

        ItemSearchResponse response = service(true)
                .search(new ItemSearchRequest("nothing matches", SearchScope.BOTH, 5), jwt());

        assertThat(response.answer()).isNull();
        assertThat(response.grounded()).isFalse();
        assertThat(response.citations()).isEmpty();
        assertThat(response.results()).isEmpty();
        verify(genaiClient, never()).answer(any());
    }

    @Test
    void search_killSwitchDisabled_skipsAnswerCallAndDegradesToRawResults() {
        UUID venueId = UUID.randomUUID();
        UUID id1 = UUID.randomUUID();
        when(venueAccessService.getVenueId(any())).thenReturn(venueId);
        when(genaiClient.embed(any())).thenReturn(embedResponse(0.1f));
        when(repository.findTopKForSearch(any(), any(), any(), anyInt())).thenReturn(List.of(
                new ScopedSimilarItem(id1, ItemType.FOUND, "Bag", "black bag", 0.10f)
        ));

        ItemSearchResponse response = service(false)
                .search(new ItemSearchRequest("black bag", SearchScope.BOTH, 5), jwt());

        assertThat(response.answer()).isNull();
        assertThat(response.grounded()).isFalse();
        assertThat(response.results()).hasSize(1);
        verify(genaiClient, never()).answer(any());
    }

    @Test
    void search_answerCallFails_degradesToRawResults() {
        UUID venueId = UUID.randomUUID();
        UUID id1 = UUID.randomUUID();
        when(venueAccessService.getVenueId(any())).thenReturn(venueId);
        when(genaiClient.embed(any())).thenReturn(embedResponse(0.1f));
        when(repository.findTopKForSearch(any(), any(), any(), anyInt())).thenReturn(List.of(
                new ScopedSimilarItem(id1, ItemType.FOUND, "Bag", "black bag", 0.10f)
        ));
        when(genaiClient.answer(any())).thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        ItemSearchResponse response = service(true)
                .search(new ItemSearchRequest("black bag", SearchScope.BOTH, 5), jwt());

        assertThat(response.answer()).isNull();
        assertThat(response.grounded()).isFalse();
        assertThat(response.citations()).isEmpty();
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).id()).isEqualTo(id1.toString());
    }

    @Test
    void search_embedCallFails_throwsSearchUnavailable_andDoesNotRetrieve() {
        when(venueAccessService.getVenueId(any())).thenReturn(UUID.randomUUID());
        when(genaiClient.embed(any())).thenThrow(new ResourceAccessException("connect timeout"));

        assertThatThrownBy(() -> service(true)
                .search(new ItemSearchRequest("anything", SearchScope.BOTH, 5), jwt()))
                .isInstanceOf(SearchUnavailableException.class);

        verify(repository, never()).findTopKForSearch(any(), any(), any(), anyInt());
        verify(genaiClient, never()).answer(any());
    }

    @Test
    void search_missingVenueClaim_propagatesAccessDenied_andDoesNotEmbed() {
        when(venueAccessService.getVenueId(any()))
                .thenThrow(new AccessDeniedException("Missing venue_id claim."));

        assertThatThrownBy(() -> service(true)
                .search(new ItemSearchRequest("anything", SearchScope.BOTH, 5), jwt()))
                .isInstanceOf(AccessDeniedException.class);

        verify(genaiClient, never()).embed(any());
    }

    @Test
    void search_dropsCitationsThatAreNotAmongRetrievedItems() {
        UUID venueId = UUID.randomUUID();
        UUID id1 = UUID.randomUUID();
        when(venueAccessService.getVenueId(any())).thenReturn(venueId);
        when(genaiClient.embed(any())).thenReturn(embedResponse(0.1f));
        when(repository.findTopKForSearch(any(), any(), any(), anyInt())).thenReturn(List.of(
                new ScopedSimilarItem(id1, ItemType.FOUND, "Bag", "black bag", 0.10f)
        ));
        when(genaiClient.answer(any())).thenReturn(new AnswerResponse()
                .answer("answer")
                .citations(List.of(id1.toString(), "not-a-retrieved-id"))
                .grounded(true));

        ItemSearchResponse response = service(true)
                .search(new ItemSearchRequest("black bag", SearchScope.BOTH, 5), jwt());

        assertThat(response.citations()).containsExactly(id1.toString());
    }

    @Test
    void search_buildsSnippetsWithMappedItemTypeAndQuery() {
        UUID venueId = UUID.randomUUID();
        UUID id1 = UUID.randomUUID();
        when(venueAccessService.getVenueId(any())).thenReturn(venueId);
        when(genaiClient.embed(any())).thenReturn(embedResponse(0.1f));
        when(repository.findTopKForSearch(any(), any(), any(), anyInt())).thenReturn(List.of(
                new ScopedSimilarItem(id1, ItemType.LOST, "Wallet", "brown wallet", 0.20f)
        ));
        when(genaiClient.answer(any())).thenReturn(new AnswerResponse()
                .answer("a").citations(List.of()).grounded(false));

        service(true).search(new ItemSearchRequest("brown wallet", SearchScope.BOTH, 5), jwt());

        ArgumentCaptor<AnswerRequest> answerCaptor = ArgumentCaptor.forClass(AnswerRequest.class);
        verify(genaiClient).answer(answerCaptor.capture());
        AnswerRequest sent = answerCaptor.getValue();
        assertThat(sent.getQuery()).isEqualTo("brown wallet");
        assertThat(sent.getSnippets()).hasSize(1);
        assertThat(sent.getSnippets().get(0).getId()).isEqualTo(id1.toString());
        assertThat(sent.getSnippets().get(0).getItemType().getValue()).isEqualTo("lost_report");
        assertThat(sent.getSnippets().get(0).getText()).isEqualTo("brown wallet");
    }

    private static EmbedResponse embedResponse(float... values) {
        List<Float> vector = new java.util.ArrayList<>();
        for (float v : values) {
            vector.add(v);
        }
        EmbedResponse response = new EmbedResponse();
        response.setEmbeddings(List.of(vector));
        response.setDimensions(values.length);
        return response;
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "staff-user")
                .build();
    }
}
