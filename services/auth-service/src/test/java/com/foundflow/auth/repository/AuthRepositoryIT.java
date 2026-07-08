package com.foundflow.auth.repository;

import com.foundflow.auth.domain.RefreshToken;
import com.foundflow.auth.domain.Role;
import com.foundflow.auth.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuthRepositoryIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void userRepository_findsByEmailAndVenue() {
        UUID venueId = UUID.randomUUID();
        User user = userRepository.save(new User(
                "staff@example.com",
                Role.STAFF,
                "hash-1",
                venueId
        ));
        userRepository.save(new User(
                "admin@example.com",
                Role.ADMIN,
                "hash-2",
                UUID.randomUUID()
        ));

        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findByEmail("staff@example.com"))
                .get()
                .extracting(User::getId)
                .isEqualTo(user.getId());
        assertThat(userRepository.findByVenueId(venueId))
                .extracting(User::getEmail)
                .containsExactly("staff@example.com");
    }

    @Test
    void refreshTokenRepository_returnsOnlyActiveTokenByHash() {
        User user = userRepository.save(new User(
                "staff@example.com",
                Role.STAFF,
                "hash-1",
                UUID.randomUUID()
        ));

        RefreshToken active = refreshTokenRepository.save(new RefreshToken(
                user,
                "active-token-hash",
                Instant.parse("2026-05-28T10:15:30Z"),
                Instant.parse("2026-05-27T10:15:30Z")
        ));
        RefreshToken revoked = refreshTokenRepository.save(new RefreshToken(
                user,
                "revoked-token-hash",
                Instant.parse("2026-05-28T10:15:30Z"),
                Instant.parse("2026-05-27T10:15:30Z")
        ));
        revoked.revoke(Instant.parse("2026-05-27T11:15:30Z"));

        entityManager.flush();
        entityManager.clear();

        assertThat(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull("active-token-hash"))
                .get()
                .extracting(RefreshToken::getTokenHash)
                .isEqualTo(active.getTokenHash());
        assertThat(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull("revoked-token-hash"))
                .isEmpty();
    }
}
