package com.foundflow.matching.repository;

import com.foundflow.matching.domain.Match;
import com.foundflow.matching.domain.MatchStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MatchRepositoryIT {

    @Autowired
    private MatchRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findFilteredAndCountFiltered_applyVenueItemAndStatusFilters() {
        UUID venueId = UUID.randomUUID();
        UUID otherVenueId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID otherFoundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        UUID otherLostReportId = UUID.randomUUID();

        Match matching = repository.save(match(
                foundItemId,
                lostReportId,
                venueId,
                MatchStatus.PENDING,
                LocalDateTime.of(2026, 5, 19, 10, 0)
        ));
        repository.save(match(
                foundItemId,
                otherLostReportId,
                venueId,
                MatchStatus.CONFIRMED,
                LocalDateTime.of(2026, 5, 19, 11, 0)
        ));
        repository.save(match(
                otherFoundItemId,
                lostReportId,
                venueId,
                MatchStatus.PENDING,
                LocalDateTime.of(2026, 5, 20, 9, 0)
        ));
        repository.save(match(
                foundItemId,
                lostReportId,
                otherVenueId,
                MatchStatus.PENDING,
                LocalDateTime.of(2026, 5, 21, 9, 0)
        ));

        entityManager.flush();
        entityManager.clear();

        List<Match> filtered = repository.findFiltered(
                venueId,
                foundItemId,
                lostReportId,
                MatchStatus.PENDING.name()
        );

        assertThat(filtered)
                .extracting(Match::getId)
                .containsExactly(matching.getId());
        assertThat(repository.countFiltered(
                venueId,
                foundItemId,
                null,
                MatchStatus.PENDING.name()
        )).isEqualTo(1L);
    }

    @Test
    void findDailyBuckets_groupsAndOrdersFilteredMatchesByDay() {
        UUID venueId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();

        repository.save(match(
                foundItemId,
                lostReportId,
                venueId,
                MatchStatus.PENDING,
                LocalDateTime.of(2026, 5, 19, 10, 0)
        ));
        repository.save(match(
                foundItemId,
                lostReportId,
                venueId,
                MatchStatus.PENDING,
                LocalDateTime.of(2026, 5, 19, 18, 30)
        ));
        repository.save(match(
                foundItemId,
                lostReportId,
                venueId,
                MatchStatus.CONFIRMED,
                LocalDateTime.of(2026, 5, 20, 8, 45)
        ));

        entityManager.flush();
        entityManager.clear();

        List<BucketCountView> buckets = repository.findDailyBuckets(
                venueId,
                foundItemId,
                lostReportId,
                MatchStatus.PENDING.name()
        );

        assertThat(buckets)
                .extracting(BucketCountView::getBucketStart, BucketCountView::getCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 5, 19), 2L)
                );
    }

    @Test
    void deleteByFoundItemId_removesOnlyMatchesForDeletedFoundItem() {
        UUID venueId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID otherFoundItemId = UUID.randomUUID();

        repository.save(match(
                foundItemId,
                UUID.randomUUID(),
                venueId,
                MatchStatus.PENDING,
                LocalDateTime.of(2026, 5, 19, 10, 0)
        ));
        Match retained = repository.save(match(
                otherFoundItemId,
                UUID.randomUUID(),
                venueId,
                MatchStatus.PENDING,
                LocalDateTime.of(2026, 5, 19, 11, 0)
        ));

        int deleted = repository.deleteByFoundItemId(foundItemId);
        entityManager.flush();
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findAll())
                .extracting(Match::getId)
                .containsExactly(retained.getId());
    }

    private Match match(
            UUID foundItemId,
            UUID lostReportId,
            UUID venueId,
            MatchStatus status,
            LocalDateTime createdAt
    ) {
        return new Match(
                foundItemId,
                lostReportId,
                venueId,
                status,
                0.9f,
                0.8f,
                0.72f,
                createdAt
        );
    }
}
