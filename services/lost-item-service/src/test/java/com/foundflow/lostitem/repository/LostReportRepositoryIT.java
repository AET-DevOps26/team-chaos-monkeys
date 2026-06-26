package com.foundflow.lostitem.repository;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.lostitem.domain.LostReport;
import com.foundflow.lostitem.domain.ReportStatus;
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
class LostReportRepositoryIT {

    @Autowired
    private LostReportRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findDailyBuckets_filtersByVenueAndStatus_andOrdersByDay() {
        UUID venueId = UUID.randomUUID();
        UUID otherVenueId = UUID.randomUUID();

        repository.saveAll(List.of(
                lostReport(
                        venueId,
                        ReportStatus.OPEN,
                        LocalDateTime.of(2026, 5, 19, 9, 15),
                        List.of("red tag")
                ),
                lostReport(
                        venueId,
                        ReportStatus.OPEN,
                        LocalDateTime.of(2026, 5, 19, 18, 45),
                        List.of("front pocket")
                ),
                lostReport(
                        venueId,
                        ReportStatus.CLOSED,
                        LocalDateTime.of(2026, 5, 20, 10, 0),
                        List.of("ignored status")
                ),
                lostReport(
                        otherVenueId,
                        ReportStatus.OPEN,
                        LocalDateTime.of(2026, 5, 18, 12, 0),
                        List.of("ignored venue")
                ),
                lostReport(
                        venueId,
                        ReportStatus.OPEN,
                        LocalDateTime.of(2026, 5, 20, 8, 30),
                        List.of("water bottle")
                )
        ));

        entityManager.flush();
        entityManager.clear();

        List<BucketCountView> buckets = repository.findDailyBuckets(venueId, ReportStatus.OPEN.name());

        assertThat(buckets)
                .extracting(BucketCountView::getBucketStart, BucketCountView::getCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 5, 19), 2L),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 5, 20), 1L)
                );
    }

    @Test
    void saveAndLoad_preservesEmbeddedAttributesAndMarks() {
        UUID venueId = UUID.randomUUID();
        LostReport saved = repository.save(lostReport(
                venueId,
                ReportStatus.OPEN,
                LocalDateTime.of(2026, 5, 21, 14, 5),
                List.of("silver zipper", "festival sticker")
        ));

        entityManager.flush();
        entityManager.clear();

        LostReport reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getVenueId()).isEqualTo(venueId);
        assertThat(reloaded.getAttributes()).isNotNull();
        assertThat(reloaded.getAttributes().getCategory()).isEqualTo("Bag");
        assertThat(reloaded.getAttributes().getBrand()).isEqualTo("Nike");
        assertThat(reloaded.getAttributes().getColor()).isEqualTo("Black");
        assertThat(reloaded.getAttributes().getMarks())
                .containsExactly("silver zipper", "festival sticker");
    }

    private LostReport lostReport(
            UUID venueId,
            ReportStatus status,
            LocalDateTime lostAt,
            List<String> marks
    ) {
        return new LostReport(
                "photos/" + UUID.randomUUID() + ".jpg",
                "Black backpack",
                lostAt,
                "North entrance",
                status,
                venueId,
                "owner@example.com",
                new ItemAttributes("Bag", null, "Nike", "Black", marks)
        );
    }
}
