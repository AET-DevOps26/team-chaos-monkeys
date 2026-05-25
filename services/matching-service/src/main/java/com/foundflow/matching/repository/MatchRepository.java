package com.foundflow.matching.repository;

import com.foundflow.matching.domain.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    List<Match> findByVenueId(UUID venueId);

    Optional<Match> findFirstByLostReportIdAndFoundItemId(UUID lostReportId, UUID foundItemId);

    @Query(
            value = """
                    SELECT *
                    FROM matches
                    WHERE (CAST(:venueId AS uuid) IS NULL OR venue_id = CAST(:venueId AS uuid))
                      AND (CAST(:foundItemId AS uuid) IS NULL OR found_item_id = CAST(:foundItemId AS uuid))
                      AND (CAST(:lostReportId AS uuid) IS NULL OR lost_report_id = CAST(:lostReportId AS uuid))
                      AND (:status IS NULL OR status = :status)
                    """,
            nativeQuery = true
    )
    List<Match> findFiltered(
            @Param("venueId") UUID venueId,
            @Param("foundItemId") UUID foundItemId,
            @Param("lostReportId") UUID lostReportId,
            @Param("status") String status
    );

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM matches
                    WHERE (CAST(:venueId AS uuid) IS NULL OR venue_id = CAST(:venueId AS uuid))
                      AND (CAST(:foundItemId AS uuid) IS NULL OR found_item_id = CAST(:foundItemId AS uuid))
                      AND (CAST(:lostReportId AS uuid) IS NULL OR lost_report_id = CAST(:lostReportId AS uuid))
                      AND (:status IS NULL OR status = :status)
                    """,
            nativeQuery = true
    )
    long countFiltered(
            @Param("venueId") UUID venueId,
            @Param("foundItemId") UUID foundItemId,
            @Param("lostReportId") UUID lostReportId,
            @Param("status") String status
    );

    @Query(
            value = """
                    SELECT CAST(created_at AS DATE) AS bucketStart, COUNT(*) AS count
                    FROM matches
                    WHERE (CAST(:venueId AS uuid) IS NULL OR venue_id = CAST(:venueId AS uuid))
                      AND (CAST(:foundItemId AS uuid) IS NULL OR found_item_id = CAST(:foundItemId AS uuid))
                      AND (CAST(:lostReportId AS uuid) IS NULL OR lost_report_id = CAST(:lostReportId AS uuid))
                      AND (:status IS NULL OR status = :status)
                    GROUP BY CAST(created_at AS DATE)
                    ORDER BY CAST(created_at AS DATE)
                    """,
            nativeQuery = true
    )
    List<BucketCountView> findDailyBuckets(
            @Param("venueId") UUID venueId,
            @Param("foundItemId") UUID foundItemId,
            @Param("lostReportId") UUID lostReportId,
            @Param("status") String status
    );
}
