package com.foundflow.pickup.repository;

import com.foundflow.pickup.domain.Pickup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PickupRepository extends JpaRepository<Pickup, UUID> {

    List<Pickup> findByVenueId(UUID venueId);

    Optional<Pickup> findFirstByMatchId(UUID matchId);

    List<Pickup> findByVenueIdAndPickupAt(UUID venueId, LocalDateTime pickupAt);

    List<Pickup> findByVenueIdAndPickupAtBetween(
            UUID venueId,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive
    );
}
