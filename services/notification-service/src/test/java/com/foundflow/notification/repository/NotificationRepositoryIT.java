package com.foundflow.notification.repository;

import com.foundflow.notification.domain.Notification;
import com.foundflow.notification.dto.MatchContactStatusResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class NotificationRepositoryIT {

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void repositoryFilters_byVenueRecipientAndCombinedLookup() {
        UUID venueId = UUID.randomUUID();
        UUID otherVenueId = UUID.randomUUID();

        repository.save(notification(venueId, "owner@example.com"));
        repository.save(notification(venueId, "other@example.com"));
        repository.save(notification(otherVenueId, "owner@example.com"));

        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByVenueId(venueId)).hasSize(2);
        assertThat(repository.findByRecipientAddress("owner@example.com")).hasSize(2);
        assertThat(repository.findByVenueIdAndRecipientAddress(venueId, "owner@example.com"))
                .singleElement()
                .extracting(Notification::getVenueId)
                .isEqualTo(venueId);
    }

    @Test
    void matchContactStatuses_reduceToLatestSendPerMatchAndScopeByVenue() {
        UUID venueId = UUID.randomUUID();
        UUID otherVenueId = UUID.randomUUID();

        UUID contactedMatchId = UUID.randomUUID();
        UUID queuedMatchId = UUID.randomUUID();
        UUID otherVenueMatchId = UUID.randomUUID();

        LocalDateTime earlier = LocalDateTime.of(2026, 6, 30, 9, 0);
        LocalDateTime later = LocalDateTime.of(2026, 6, 30, 11, 30);

        // Same match, two notifications: one earlier-sent, one still queued (null).
        // MAX(sentAt) must collapse them to the single latest non-null timestamp.
        repository.save(matchNotification(venueId, contactedMatchId, earlier));
        repository.save(matchNotification(venueId, contactedMatchId, null));
        // A match whose only notification is still queued -> reported with null sentAt.
        repository.save(matchNotification(venueId, queuedMatchId, null));
        // A notification with no match (e.g. password reset) -> excluded entirely.
        repository.save(matchNotification(venueId, null, later));
        // Another venue -> excluded from the venue-scoped query.
        repository.save(matchNotification(otherVenueId, otherVenueMatchId, later));

        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findMatchContactStatusesByVenueId(venueId))
                .hasSize(2)
                .anySatisfy(s -> {
                    assertThat(s.matchId()).isEqualTo(contactedMatchId);
                    assertThat(s.sentAt()).isEqualTo(earlier);
                })
                .anySatisfy(s -> {
                    assertThat(s.matchId()).isEqualTo(queuedMatchId);
                    assertThat(s.sentAt()).isNull();
                });

        assertThat(repository.findAllMatchContactStatuses())
                .hasSize(3)
                .extracting(MatchContactStatusResponse::matchId)
                .containsExactlyInAnyOrder(contactedMatchId, queuedMatchId, otherVenueMatchId);
    }

    private Notification matchNotification(UUID venueId, UUID matchId, LocalDateTime sentAt) {
        return new Notification(
                matchId,
                venueId,
                "guest@example.com",
                "de",
                "Gute Nachrichten",
                "Wir haben moeglicherweise Ihr verlorenes Objekt gefunden",
                "Bitte wenden Sie sich an das Fundbuero.",
                sentAt
        );
    }

    private Notification notification(UUID venueId, String recipientAddress) {
        return new Notification(
                UUID.randomUUID(),
                venueId,
                recipientAddress,
                "de",
                "Gute Nachrichten",
                "Wir haben moeglicherweise Ihr verlorenes Objekt gefunden",
                "Bitte wenden Sie sich an das Fundbuero.",
                LocalDateTime.of(2026, 5, 27, 11, 0)
        );
    }
}
