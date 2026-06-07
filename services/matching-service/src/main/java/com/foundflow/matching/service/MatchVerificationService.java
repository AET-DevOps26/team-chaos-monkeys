package com.foundflow.matching.service;

import com.foundflow.genai.client.GenaiClient;
import com.foundflow.genai.client.model.ItemSide;
import com.foundflow.genai.client.model.VerifyMatchRequest;
import com.foundflow.genai.client.model.VerifyMatchResponse;
import com.foundflow.matching.config.GenaiVerifyProperties;
import com.foundflow.matching.domain.MatchVerification;
import com.foundflow.matching.repository.MatchRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class MatchVerificationService {

    private static final Logger log = LoggerFactory.getLogger(MatchVerificationService.class);

    private final GenaiClient client;
    private final MatchRepository repo;
    private final MeterRegistry meters;
    private final GenaiVerifyProperties props;

    public MatchVerificationService(GenaiClient client,
                                    MatchRepository repo,
                                    MeterRegistry meters,
                                    GenaiVerifyProperties props) {
        this.client = client;
        this.repo = repo;
        this.meters = meters;
        this.props = props;
    }

    @Async("genaiVerifyExecutor")
    public void verifyAsync(UUID matchId, String lostText, String foundText) {
        if (!props.isEnabled()) {
            meters.counter("matching.verify.requests_total", "result", "disabled").increment();
            return;
        }

        Timer.Sample sample = Timer.start(meters);
        try {
            VerifyMatchRequest req = new VerifyMatchRequest()
                    .lost(new ItemSide().description(lostText))
                    .found(new ItemSide().description(foundText));

            VerifyMatchResponse resp = client.verifyMatch(req);
            String verdict = resp.getVerdict().getValue();

            repo.applyVerification(matchId, new MatchVerification(
                    verdict,
                    resp.getConfidence(),
                    resp.getRationale(),
                    resp.getModelInfo() != null ? resp.getModelInfo().getProvider().getValue() : null,
                    resp.getModelInfo() != null ? resp.getModelInfo().getModel() : null,
                    OffsetDateTime.now()
            ));

            meters.counter("matching.verify.requests_total", "result", "success").increment();
            meters.counter("matching.verify.verdict_total", "verdict", verdict).increment();
            if (resp.getConfidence() != null) {
                meters.summary("matching.verify.confidence").record(resp.getConfidence());
            } else {
                log.warn("verify-match returned null confidence for match {} (provider {}); possible contract drift",
                        matchId, resp.getModelInfo() != null ? resp.getModelInfo().getProvider() : "?");
            }
        } catch (Exception e) {
            String reason = GenaiClientSupport.classify(e);
            meters.counter("matching.verify.requests_total",
                    "result", "error", "reason", reason).increment();
            if ("contract_error".equals(reason) || "unexpected".equals(reason)) {
                log.error("verify-match failed for match {} ({}): {}",
                        matchId, reason, e.getMessage(), e);
            } else {
                log.warn("verify-match failed for match {} ({}): {}",
                        matchId, reason, e.getMessage());
            }
        } finally {
            sample.stop(meters.timer("matching.verify.duration"));
        }
    }
}
