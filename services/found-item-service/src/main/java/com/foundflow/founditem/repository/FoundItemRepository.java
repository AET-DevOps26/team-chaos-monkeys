package com.foundflow.founditem.repository;

import com.foundflow.founditem.domain.FoundItem;
import com.foundflow.founditem.domain.ItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FoundItemRepository extends JpaRepository<FoundItem, UUID> {

    List<FoundItem> findByVenueId(UUID venueId);

    List<FoundItem> findByStatus(ItemStatus status);

    List<FoundItem> findByVenueIdAndStatus(UUID venueId, ItemStatus status);

    long countByStatus(ItemStatus status);

    long countByVenueId(UUID venueId);

    long countByVenueIdAndStatus(UUID venueId, ItemStatus status);

    @Query(
            value = """
                    SELECT CAST(found_at AS DATE) AS bucketStart, COUNT(*) AS count
                    FROM found_items
                    WHERE (:venueId IS NULL OR venue_id = :venueId)
                      AND (:status IS NULL OR status = :status)
                    GROUP BY CAST(found_at AS DATE)
                    ORDER BY CAST(found_at AS DATE)
                    """,
            nativeQuery = true
    )
    List<BucketCountView> findDailyBuckets(
            @Param("venueId") UUID venueId,
            @Param("status") String status
    );
}
