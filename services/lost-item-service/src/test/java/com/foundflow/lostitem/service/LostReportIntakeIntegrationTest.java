package com.foundflow.lostitem.service;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.lostitem.dto.CreateLostReportRequest;
import com.foundflow.lostitem.dto.ItemAttributesDto;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Proves the intake wiring end-to-end in a real Spring context: creating a lost
 * report returns immediately, and the {@code @Async @TransactionalEventListener}
 * publishes {@code lost-report.created.v1} <em>after</em> the transaction commits —
 * the part the plain-Mockito service tests can't exercise. Runs against H2 with
 * {@code genai.enabled=false}, so enrichment is a no-op and this specifically
 * guards the best-effort "publish even when nothing was extracted" path.
 */
@SpringBootTest
class LostReportIntakeIntegrationTest {

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private LostReportService lostReportService;

    @Test
    void createLostReport_shouldPublishCreatedEventViaAsyncListenerAfterCommit() {
        UUID venueId = UUID.randomUUID();
        CreateLostReportRequest request = new CreateLostReportRequest(
                "purple wallet left at the bar",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "the bar",
                venueId,
                "person@example.com",
                new ItemAttributesDto(null, null, null, null, List.of())
        );

        lostReportService.createLostReport(request, staffJwt(venueId));

        // The created event is published only by the async listener, after commit.
        verify(rabbitTemplate, timeout(5000)).convertAndSend(
                eq(FoundFlowEventRouting.EXCHANGE),
                eq(FoundFlowEventRouting.LOST_REPORT_CREATED),
                any(Object.class)
        );
    }

    private Jwt staffJwt(UUID venueId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("roles", List.of("STAFF"))
                .claim("venue_id", venueId.toString())
                .build();
    }
}
