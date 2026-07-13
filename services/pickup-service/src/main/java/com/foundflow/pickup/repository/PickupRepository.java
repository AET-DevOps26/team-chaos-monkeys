package com.foundflow.pickup.repository;

import com.foundflow.pickup.domain.Pickup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PickupRepository extends JpaRepository<Pickup, UUID> {

    List<Pickup> findByVenueId(UUID venueId);

    @Modifying
    @Transactional
    long deleteByMatchId(UUID matchId);

    Optional<Pickup> findFirstByMatchId(UUID matchId);

    List<Pickup> findByVenueIdAndPickupAt(UUID venueId, LocalDateTime pickupAt);

    List<Pickup> findByVenueIdAndPickupAtBetween(
            UUID venueId,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive
    );
}
