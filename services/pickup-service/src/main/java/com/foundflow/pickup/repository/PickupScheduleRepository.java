package com.foundflow.pickup.repository;

import com.foundflow.pickup.domain.PickupSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PickupScheduleRepository extends JpaRepository<PickupSchedule, UUID> {

    List<PickupSchedule> findByVenueIdOrderByStartDateAscStartTimeAsc(UUID venueId);
}
