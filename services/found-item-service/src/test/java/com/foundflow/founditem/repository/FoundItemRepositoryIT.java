package com.foundflow.founditem.repository;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.founditem.domain.FoundItem;
import com.foundflow.founditem.domain.ItemStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class FoundItemRepositoryIT {

    @Autowired
    private FoundItemRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findDailyBuckets_filtersByVenueAndStatus_andOrdersByDay() {
        UUID venueId = UUID.randomUUID();
        UUID otherVenueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID otherReporterId = UUID.randomUUID();

        repository.saveAll(List.of(
                foundItem(
                        venueId,
                        reporterId,
                        ItemStatus.STORED,
                        LocalDateTime.of(2026, 5, 19, 9, 15),
                        List.of("red tag")
                ),
                foundItem(
                        venueId,
                        reporterId,
                        ItemStatus.STORED,
                        LocalDateTime.of(2026, 5, 19, 18, 45),
                        List.of("front pocket")
                ),
                foundItem(
                        venueId,
                        reporterId,
                        ItemStatus.RETURNED,
                        LocalDateTime.of(2026, 5, 20, 10, 0),
                        List.of("ignored status")
                ),
                foundItem(
                        venueId,
                        otherReporterId,
                        ItemStatus.STORED,
                        LocalDateTime.of(2026, 5, 20, 11, 0),
                        List.of("ignored reporter")
                ),
                foundItem(
                        otherVenueId,
                        reporterId,
                        ItemStatus.STORED,
                        LocalDateTime.of(2026, 5, 18, 12, 0),
                        List.of("ignored venue")
                ),
                foundItem(
                        venueId,
                        reporterId,
                        ItemStatus.STORED,
                        LocalDateTime.of(2026, 5, 20, 8, 30),
                        List.of("water bottle")
                )
        ));

        entityManager.flush();
        entityManager.clear();

        List<BucketCountView> buckets =
                repository.findDailyBuckets(venueId, ItemStatus.STORED.name(), reporterId);

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
        FoundItem saved = repository.save(foundItem(
                venueId,
                UUID.randomUUID(),
                ItemStatus.STORED,
                LocalDateTime.of(2026, 5, 21, 14, 5),
                List.of("silver zipper", "festival sticker")
        ));

        entityManager.flush();
        entityManager.clear();

        FoundItem reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getVenueId()).isEqualTo(venueId);
        assertThat(reloaded.getAttributes()).isNotNull();
        assertThat(reloaded.getAttributes().getCategory()).isEqualTo("Bag");
        assertThat(reloaded.getAttributes().getBrand()).isEqualTo("Nike");
        assertThat(reloaded.getAttributes().getColor()).isEqualTo("Black");
        assertThat(reloaded.getAttributes().getMarks())
                .containsExactly("silver zipper", "festival sticker");
    }

    private FoundItem foundItem(
            UUID venueId,
            UUID reporterId,
            ItemStatus status,
            LocalDateTime foundAt,
            List<String> marks
    ) {
        return new FoundItem(
                "photos/" + UUID.randomUUID() + ".jpg",
                "Black backpack",
                foundAt,
                "North entrance",
                status,
                venueId,
                reporterId,
                new ItemAttributes("Bag", null, "Nike", "Black", marks)
        );
    }
}
