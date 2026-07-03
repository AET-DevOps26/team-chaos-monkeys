package com.foundflow.notification.repository;

import com.foundflow.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByVenueId(UUID venueId);

    List<Notification> findByRecipientAddress(String recipientAddress);

    List<Notification> findByVenueIdAndRecipientAddress(
            UUID venueId,
            String recipientAddress
    );

    List<Notification> findByVenueIdAndMatchIdNotNull(UUID venueId);

    List<Notification> findByMatchIdNotNull();
}
