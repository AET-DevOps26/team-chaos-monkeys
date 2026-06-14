package com.foundflow.auth.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.PasswordResetRequestedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PasswordResetEventPublisherTest {

    @Test
    void publishPasswordResetRequested_sendsDomainEventWithResetUrl() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        PasswordResetEventPublisher publisher =
                new PasswordResetEventPublisher(rabbitTemplate, "http://localhost:8080");
        UUID userId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        publisher.publishPasswordResetRequested(
                userId,
                venueId,
                "staff@example.com",
                "reset token"
        );

        ArgumentCaptor<PasswordResetRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(FoundFlowEventRouting.EXCHANGE),
                eq(FoundFlowEventRouting.PASSWORD_RESET_REQUESTED),
                eventCaptor.capture()
        );

        PasswordResetRequestedEvent event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.venueId()).isEqualTo(venueId);
        assertThat(event.recipient()).isEqualTo("staff@example.com");
        assertThat(event.resetUrl())
                .isEqualTo("http://localhost:8080/reset-password?token=reset+token");
    }
}
