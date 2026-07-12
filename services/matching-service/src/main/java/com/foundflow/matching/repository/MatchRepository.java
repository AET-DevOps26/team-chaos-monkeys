package com.foundflow.matching.repository;

import com.foundflow.matching.domain.Match;
import com.foundflow.matching.domain.MatchStatus;
import com.foundflow.matching.domain.MatchVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    List<Match> findByVenueId(UUID venueId);

    @Modifying
    @Transactional
    @Query("""
        UPDATE Match m
           SET m.verifyVerdict = :#{#v.verdict()},
               m.verifyConfidence = :#{#v.confidence()},
               m.verifyRationale = :#{#v.rationale()},
               m.verifyModelProvider = :#{#v.modelProvider()},
               m.verifyModelName = :#{#v.modelName()},
               m.verifyCompletedAt = :#{#v.completedAt()}
         WHERE m.id = :matchId
        """)
    int applyVerification(@Param("matchId") UUID matchId, @Param("v") MatchVerification v);

    /**
     * Drop a candidate when verify-match confidently rules it out (issue #374): the machine
     * verdict deletes the row so it never clutters the inbox, rather than marking it REJECTED —
     * REJECTED is reserved for an explicit human rejection. Guarded on PENDING and an un-issued
     * public link, so it never yanks a match a guest has already been invited to or acted on.
     * Returns the number of rows deleted (0 if the match had left PENDING or was already invited).
     */
    @Modifying
    @Transactional
    @Query("""
        DELETE FROM Match m
         WHERE m.id = :matchId
           AND m.status = com.foundflow.matching.domain.MatchStatus.PENDING
           AND m.publicLinkToken IS NULL
        """)
    int deleteIfPendingAndUninvited(@Param("matchId") UUID matchId);

    Optional<Match> findFirstByLostReportIdAndFoundItemId(UUID lostReportId, UUID foundItemId);

    // True while the report still has an active (non-rejected) match, so a single rejection
    // doesn't reopen a report that other candidates are still pursuing.
    boolean existsByLostReportIdAndStatusNot(UUID lostReportId, MatchStatus status);

    @Modifying
    @Transactional
    int deleteByFoundItemId(UUID foundItemId);

    @Modifying
    @Transactional
    @Query("""
        UPDATE Match m
           SET m.status = :newStatus
         WHERE m.lostReportId = :lostReportId
           AND m.foundItemId = :foundItemId
           AND m.venueId = :venueId
           AND m.status = :currentStatus
        """)
    int updateStatusForPair(
            @Param("lostReportId") UUID lostReportId,
            @Param("foundItemId") UUID foundItemId,
            @Param("venueId") UUID venueId,
            @Param("currentStatus") MatchStatus currentStatus,
            @Param("newStatus") MatchStatus newStatus
    );

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
