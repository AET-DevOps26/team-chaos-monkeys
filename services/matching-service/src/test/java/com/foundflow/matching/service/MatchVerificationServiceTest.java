package com.foundflow.matching.service;

import com.foundflow.genai.client.GenaiClient;
import com.foundflow.genai.client.model.ModelInfo;
import com.foundflow.genai.client.model.VerifyMatchResponse;
import com.foundflow.matching.config.GenaiVerifyProperties;
import com.foundflow.matching.domain.MatchVerification;
import com.foundflow.matching.repository.MatchRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MatchVerificationServiceTest {

    GenaiClient client;
    MatchRepository repo;
    MeterRegistry meters;
    GenaiVerifyProperties props;
    MatchVerificationService svc;
    UUID matchId;

    @BeforeEach
    void setUp() {
        client = mock(GenaiClient.class);
        repo = mock(MatchRepository.class);
        meters = new SimpleMeterRegistry();
        props = new GenaiVerifyProperties();
        svc = new MatchVerificationService(client, repo, meters, props);
        matchId = UUID.randomUUID();
    }

    private VerifyMatchResponse okResponse(String verdict) {
        VerifyMatchResponse r = new VerifyMatchResponse();
        r.setVerdict(VerifyMatchResponse.VerdictEnum.fromValue(verdict));
        r.setConfidence(0.9f);
        r.setRationale("ok");
        ModelInfo mi = new ModelInfo();
        mi.setProvider(ModelInfo.ProviderEnum.fromValue("openai"));
        mi.setModel("gpt-4o-mini");
        r.setModelInfo(mi);
        return r;
    }

    @Test
    void happyPath_appliesVerificationAndIncrementsCounters() {
        when(client.verifyMatch(any())).thenReturn(okResponse("match"));

        svc.verifyAsync(matchId, "lost text", "found text");

        ArgumentCaptor<MatchVerification> v = ArgumentCaptor.forClass(MatchVerification.class);
        verify(repo).applyVerification(eq(matchId), v.capture());
        assertThat(v.getValue().verdict()).isEqualTo("match");
        assertThat(v.getValue().modelProvider()).isEqualTo("openai");

        assertThat(meters.counter("matching.verify.requests_total", "result", "success").count())
                .isEqualTo(1.0);
        assertThat(meters.counter("matching.verify.verdict_total", "verdict", "match").count())
                .isEqualTo(1.0);
    }

    @Test
    void noMatchVerdict_dropsUninvitedPendingMatch() {
        when(client.verifyMatch(any())).thenReturn(okResponse("no_match"));
        when(repo.deleteIfPendingAndUninvited(matchId)).thenReturn(1);

        svc.verifyAsync(matchId, "purple tshirt", "purple puffer jacket");

        verify(repo).applyVerification(eq(matchId), any());
        verify(repo).deleteIfPendingAndUninvited(matchId);
        assertThat(meters.counter("matching.verify.dropped_total").count()).isEqualTo(1.0);
    }

    @Test
    void noMatchVerdict_onInvitedMatch_leavesItForTheGuest() {
        when(client.verifyMatch(any())).thenReturn(okResponse("no_match"));
        // The guarded delete matches no rows because the match already has a public link.
        when(repo.deleteIfPendingAndUninvited(matchId)).thenReturn(0);

        svc.verifyAsync(matchId, "l", "f");

        verify(repo).deleteIfPendingAndUninvited(matchId);
        assertThat(meters.counter("matching.verify.dropped_total").count()).isEqualTo(0.0);
    }

    @Test
    void matchVerdict_doesNotDrop() {
        when(client.verifyMatch(any())).thenReturn(okResponse("match"));

        svc.verifyAsync(matchId, "l", "f");

        verify(repo).applyVerification(eq(matchId), any());
        verify(repo, never()).deleteIfPendingAndUninvited(any());
    }

    @Test
    void uncertainVerdict_doesNotDrop() {
        when(client.verifyMatch(any())).thenReturn(okResponse("uncertain"));

        svc.verifyAsync(matchId, "l", "f");

        verify(repo, never()).deleteIfPendingAndUninvited(any());
    }

    @Test
    void lowConfidenceNoMatch_doesNotDrop() {
        VerifyMatchResponse r = okResponse("no_match");
        r.setConfidence(0.5f); // below the drop confidence floor
        when(client.verifyMatch(any())).thenReturn(r);

        svc.verifyAsync(matchId, "l", "f");

        verify(repo).applyVerification(eq(matchId), any());
        verify(repo, never()).deleteIfPendingAndUninvited(any());
    }

    @Test
    void dropDisabled_keepsNoMatchPending() {
        props.setDropOnNoMatch(false);
        when(client.verifyMatch(any())).thenReturn(okResponse("no_match"));

        svc.verifyAsync(matchId, "l", "f");

        verify(repo).applyVerification(eq(matchId), any());
        verify(repo, never()).deleteIfPendingAndUninvited(any());
    }

    @Test
    void disabledFlag_shortCircuitsWithoutCallingClient() {
        props.setEnabled(false);

        svc.verifyAsync(matchId, "lost", "found");

        verifyNoInteractions(client, repo);
        assertThat(meters.counter("matching.verify.requests_total", "result", "disabled").count())
                .isEqualTo(1.0);
    }

    @Test
    void timeout504_classifiedAsTimeoutAndRowStaysNull() {
        when(client.verifyMatch(any())).thenThrow(HttpServerErrorException.GatewayTimeout.create(
                org.springframework.http.HttpStatus.GATEWAY_TIMEOUT, "504", null, null, null));

        svc.verifyAsync(matchId, "l", "f");

        verifyNoInteractions(repo);
        assertThat(meters.counter("matching.verify.requests_total",
                "result", "error", "reason", "timeout").count()).isEqualTo(1.0);
    }

    @Test
    void upstream5xx_classifiedAsUpstream5xx() {
        when(client.verifyMatch(any())).thenThrow(HttpServerErrorException.create(
                org.springframework.http.HttpStatus.BAD_GATEWAY, "502", null, null, null));

        svc.verifyAsync(matchId, "l", "f");

        assertThat(meters.counter("matching.verify.requests_total",
                "result", "error", "reason", "upstream_5xx").count()).isEqualTo(1.0);
    }

    @Test
    void throttled429_classifiedAsThrottled() {
        when(client.verifyMatch(any())).thenThrow(HttpClientErrorException.TooManyRequests.create(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "429", null, null, null));

        svc.verifyAsync(matchId, "l", "f");

        assertThat(meters.counter("matching.verify.requests_total",
                "result", "error", "reason", "throttled").count()).isEqualTo(1.0);
    }

    @Test
    void contract4xx_classifiedAsContractErrorAtErrorLevel() {
        when(client.verifyMatch(any())).thenThrow(HttpClientErrorException.create(
                org.springframework.http.HttpStatus.BAD_REQUEST, "400", null, null, null));

        svc.verifyAsync(matchId, "l", "f");

        assertThat(meters.counter("matching.verify.requests_total",
                "result", "error", "reason", "contract_error").count()).isEqualTo(1.0);
    }

    @Test
    void executorRejection_classifiedAsExecutorFull() {
        when(client.verifyMatch(any())).thenThrow(new RejectedExecutionException("queue full"));

        svc.verifyAsync(matchId, "l", "f");

        assertThat(meters.counter("matching.verify.requests_total",
                "result", "error", "reason", "executor_full").count()).isEqualTo(1.0);
    }

    @Test
    void unexpected_classifiedAsUnexpectedAtErrorLevel() {
        when(client.verifyMatch(any())).thenThrow(new IllegalStateException("boom"));

        svc.verifyAsync(matchId, "l", "f");

        assertThat(meters.counter("matching.verify.requests_total",
                "result", "error", "reason", "unexpected").count()).isEqualTo(1.0);
    }
}
