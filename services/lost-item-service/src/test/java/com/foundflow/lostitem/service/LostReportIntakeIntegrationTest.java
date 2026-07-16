package com.foundflow.lostitem.service;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.LostReportCreatedEvent;
import com.foundflow.genai.client.AttributeExtractionService;
import com.foundflow.genai.client.ExtractionResult;
import com.foundflow.lostitem.dto.CreateLostReportRequest;
import com.foundflow.lostitem.dto.ItemAttributesDto;
import com.foundflow.lostitem.dto.LostReportResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the intake wiring end-to-end in a real Spring context (H2), covering the
 * two behaviours the plain-Mockito service tests can't:
 * <ul>
 *   <li>the guest response returns <em>before</em> the (blocked) GenAI call finishes —
 *       the whole point of the change;</li>
 *   <li>the extracted category actually rides the {@code lost-report.created.v1}
 *       event that the async listener publishes after commit.</li>
 * </ul>
 */
@SpringBootTest
class LostReportIntakeIntegrationTest {

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private AttributeExtractionService attributeExtractionService;

    @Autowired
    private LostReportService lostReportService;

    @Test
    void createLostReport_shouldReturnBeforeExtractionFinishes_thenPublishEnrichedEvent() throws Exception {
        UUID venueId = UUID.randomUUID();
        CountDownLatch extractionStarted = new CountDownLatch(1);
        CountDownLatch releaseExtraction = new CountDownLatch(1);
        // Mutable marks list, exactly as AttributeExtractionService.toDomain produces —
        // Hibernate mutates the @ElementCollection in place on the enrichment save.
        ItemAttributes extracted =
                new ItemAttributes("CLOTHING", "purple cotton shirt", null, "purple", new ArrayList<>());

        // The async enrichment blocks inside extraction until we release it. If the
        // create path awaited the LLM, createLostReport itself would block here.
        when(attributeExtractionService.extractWithLocation(any(), any())).thenAnswer(invocation -> {
            extractionStarted.countDown();
            assertTrue(releaseExtraction.await(5, TimeUnit.SECONDS), "extraction gate never released");
            return Optional.of(new ExtractionResult(extracted, null));
        });

        LostReportResponse response =
                lostReportService.createLostReport(request(venueId), staffJwt(venueId));

        // Guest already has a response while the GenAI call is still blocked.
        assertNotNull(response);
        assertEquals(venueId, response.venueId());
        assertTrue(extractionStarted.await(3, TimeUnit.SECONDS), "async enrichment never started");
        // Extraction is gated, so the created event cannot have been published yet.
        verify(rabbitTemplate, never()).convertAndSend(
                eq(FoundFlowEventRouting.EXCHANGE), eq(FoundFlowEventRouting.LOST_REPORT_CREATED), any(Object.class));

        releaseExtraction.countDown();

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, timeout(5000)).convertAndSend(
                eq(FoundFlowEventRouting.EXCHANGE),
                eq(FoundFlowEventRouting.LOST_REPORT_CREATED),
                event.capture());

        LostReportCreatedEvent published = (LostReportCreatedEvent) event.getValue();
        // The category extracted asynchronously rides the event matching consumes.
        assertEquals("CLOTHING", published.attributes().category());
    }

    @Test
    void createLostReport_shouldPublishCreatedEvenWhenExtractionAddsNothing() {
        UUID venueId = UUID.randomUUID();
        when(attributeExtractionService.extractWithLocation(any(), any())).thenReturn(Optional.empty());

        lostReportService.createLostReport(request(venueId), staffJwt(venueId));

        // Best-effort: nothing extracted, but matching must still run.
        verify(rabbitTemplate, timeout(5000)).convertAndSend(
                eq(FoundFlowEventRouting.EXCHANGE),
                eq(FoundFlowEventRouting.LOST_REPORT_CREATED),
                any(Object.class));
    }

    private CreateLostReportRequest request(UUID venueId) {
        return new CreateLostReportRequest(
                "purple wallet left at the bar",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "the bar",
                venueId,
                "person@example.com",
                new ItemAttributesDto(null, null, null, null, List.of())
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
