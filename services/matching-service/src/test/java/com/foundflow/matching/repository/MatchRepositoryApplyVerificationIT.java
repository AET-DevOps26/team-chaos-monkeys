package com.foundflow.matching.repository;

import com.foundflow.matching.domain.Match;
import com.foundflow.matching.domain.MatchStatus;
import com.foundflow.matching.domain.MatchVerification;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MatchRepositoryApplyVerificationIT {

    @Autowired
    private MatchRepository repo;

    @Autowired
    private EntityManager entityManager;

    @Test
    void applyVerification_setsAllSixColumns() {
        Match m = repo.save(new Match(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                MatchStatus.PENDING, 1.0f, 0.9f, 0.9f, LocalDateTime.now()));

        entityManager.flush();
        entityManager.clear();

        OffsetDateTime completedAt = OffsetDateTime.now();
        repo.applyVerification(m.getId(), new MatchVerification(
                "match", 0.87f, "Both describe a blue Patagonia jacket.",
                "openai", "gpt-4o-mini", completedAt));

        entityManager.flush();
        entityManager.clear();

        Match reloaded = repo.findById(m.getId()).orElseThrow();
        assertThat(reloaded.getVerifyVerdict()).isEqualTo("match");
        assertThat(reloaded.getVerifyConfidence()).isEqualTo(0.87f);
        assertThat(reloaded.getVerifyRationale()).isEqualTo("Both describe a blue Patagonia jacket.");
        assertThat(reloaded.getVerifyModelProvider()).isEqualTo("openai");
        assertThat(reloaded.getVerifyModelName()).isEqualTo("gpt-4o-mini");
        assertThat(reloaded.getVerifyCompletedAt().toInstant())
                .isCloseTo(completedAt.toInstant(), within(1, ChronoUnit.SECONDS));
    }
}
