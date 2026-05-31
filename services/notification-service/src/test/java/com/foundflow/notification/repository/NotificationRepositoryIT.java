package com.foundflow.notification.repository;

import com.foundflow.notification.domain.Notification;
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
