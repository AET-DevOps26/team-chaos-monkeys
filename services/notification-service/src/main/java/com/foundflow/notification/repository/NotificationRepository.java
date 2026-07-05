package com.foundflow.notification.repository;

import com.foundflow.notification.domain.Notification;
import com.foundflow.notification.dto.MatchContactStatusResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByVenueId(UUID venueId);

    List<Notification> findByRecipientAddress(String recipientAddress);

    List<Notification> findByVenueIdAndRecipientAddress(
            UUID venueId,
            String recipientAddress
    );

    // One row per match, carrying the latest send time. MAX(sentAt) ignores nulls,
    // so a match with any sent notification reports that timestamp; a match whose
    // notifications are all still queued reports null.
    @Query("""
            SELECT new com.foundflow.notification.dto.MatchContactStatusResponse(n.matchId, MAX(n.sentAt))
            FROM Notification n
            WHERE n.matchId IS NOT NULL AND n.venueId = :venueId
            GROUP BY n.matchId
            """)
    List<MatchContactStatusResponse> findMatchContactStatusesByVenueId(UUID venueId);

    @Query("""
            SELECT new com.foundflow.notification.dto.MatchContactStatusResponse(n.matchId, MAX(n.sentAt))
            FROM Notification n
            WHERE n.matchId IS NOT NULL
            GROUP BY n.matchId
            """)
    List<MatchContactStatusResponse> findAllMatchContactStatuses();
}
