package com.foundflow.pickup.repository;

import com.foundflow.pickup.domain.PickupEmailLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PickupEmailLogRepository extends JpaRepository<PickupEmailLog, UUID> {

    List<PickupEmailLog> findByRecipientOrderBySentAtDesc(String recipient);

    List<PickupEmailLog> findByVenueIdOrderBySentAtDesc(UUID venueId);

    List<PickupEmailLog> findByVenueIdAndRecipientOrderBySentAtDesc(UUID venueId, String recipient);
}
