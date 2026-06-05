package com.foundflow.auth.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.PasswordResetRequestedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
public class PasswordResetEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String publicBaseUrl;

    public PasswordResetEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${foundflow.public.base-url}") String publicBaseUrl
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.publicBaseUrl = publicBaseUrl;
    }

    public void publishPasswordResetRequested(UUID userId, UUID venueId, String recipient, String token) {
        rabbitTemplate.convertAndSend(
                FoundFlowEventRouting.EXCHANGE,
                FoundFlowEventRouting.PASSWORD_RESET_REQUESTED,
                new PasswordResetRequestedEvent(
                        UUID.randomUUID(),
                        Instant.now(),
                        userId,
                        venueId,
                        recipient,
                        resetUrl(token)
                )
        );
    }

    private String resetUrl(String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        return publicBaseUrl + "/reset-password?token=" + encodedToken;
    }
}
