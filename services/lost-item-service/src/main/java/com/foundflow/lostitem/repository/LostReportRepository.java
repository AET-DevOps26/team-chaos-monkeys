package com.foundflow.lostitem.repository;

import com.foundflow.lostitem.domain.LostReport;
import com.foundflow.lostitem.domain.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LostReportRepository extends JpaRepository<LostReport, UUID> {

    List<LostReport> findByVenueId(UUID venueId);

    List<LostReport> findByStatus(ReportStatus status);

    List<LostReport> findByVenueIdAndStatus(UUID venueId, ReportStatus status);

    long countByStatus(ReportStatus status);

    long countByVenueId(UUID venueId);

    long countByVenueIdAndStatus(UUID venueId, ReportStatus status);

    @Query(
            value = """
                    SELECT CAST(lost_at AS DATE) AS bucketStart, COUNT(*) AS count
                    FROM lost_reports
                    WHERE (:venueId IS NULL OR venue_id = :venueId)
                      AND (:status IS NULL OR status = :status)
                    GROUP BY CAST(lost_at AS DATE)
                    ORDER BY CAST(lost_at AS DATE)
                    """,
            nativeQuery = true
    )
    List<BucketCountView> findDailyBuckets(
            @Param("venueId") UUID venueId,
            @Param("status") String status
    );
}
