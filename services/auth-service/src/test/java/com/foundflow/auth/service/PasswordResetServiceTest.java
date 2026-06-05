package com.foundflow.auth.service;

import com.foundflow.auth.domain.PasswordResetToken;
import com.foundflow.auth.domain.RefreshToken;
import com.foundflow.auth.domain.Role;
import com.foundflow.auth.domain.User;
import com.foundflow.auth.dto.PasswordResetConfirmRequest;
import com.foundflow.auth.dto.PasswordResetRequest;
import com.foundflow.auth.messaging.PasswordResetEventPublisher;
import com.foundflow.auth.repository.PasswordResetTokenRepository;
import com.foundflow.auth.repository.RefreshTokenRepository;
import com.foundflow.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordResetEventPublisher passwordResetEventPublisher;

    @Test
    void requestPasswordReset_shouldCreateTokenAndSendMailWhenUserExists() {
        PasswordResetService service = new PasswordResetService(
                userRepository,
                passwordResetTokenRepository,
                refreshTokenRepository,
                passwordEncoder,
                passwordResetEventPublisher,
                30
        );
        User user = new User("staff@example.com", Role.STAFF, "hash", UUID.randomUUID());

        when(userRepository.findByEmailIgnoreCase("staff@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findByUserAndUsedAtIsNull(user))
                .thenReturn(List.of());

        service.requestPasswordReset(new PasswordResetRequest(" Staff@Example.COM "));

        ArgumentCaptor<PasswordResetToken> tokenCaptor =
                ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        verify(passwordResetEventPublisher).publishPasswordResetRequested(
                any(),
                any(),
                anyString(),
                anyString()
        );

        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertTrue(savedToken.getTokenHash().matches("[0-9a-f]{64}"));
        assertNotNull(savedToken.getExpiresAt());
    }

    @Test
    void requestPasswordReset_shouldNotRevealMissingUsers() {
        PasswordResetService service = new PasswordResetService(
                userRepository,
                passwordResetTokenRepository,
                refreshTokenRepository,
                passwordEncoder,
                passwordResetEventPublisher,
                30
        );

        when(userRepository.findByEmailIgnoreCase("missing@example.com"))
                .thenReturn(Optional.empty());

        service.requestPasswordReset(new PasswordResetRequest("missing@example.com"));

        verifyNoInteractions(passwordResetTokenRepository);
        verifyNoInteractions(passwordResetEventPublisher);
    }

    @Test
    void confirmPasswordReset_shouldUpdatePasswordConsumeTokenAndRevokeRefreshTokens() {
        PasswordResetService service = new PasswordResetService(
                userRepository,
                passwordResetTokenRepository,
                refreshTokenRepository,
                passwordEncoder,
                passwordResetEventPublisher,
                30
        );
        User user = new User("staff@example.com", Role.STAFF, "old-hash", UUID.randomUUID());
        PasswordResetToken resetToken = new PasswordResetToken(
                user,
                "token-hash",
                Instant.now().plusSeconds(300),
                Instant.now()
        );
        RefreshToken refreshToken = new RefreshToken(
                user,
                "refresh-hash",
                Instant.now().plusSeconds(300),
                Instant.now()
        );

        when(passwordResetTokenRepository.findByTokenHashAndUsedAtIsNull(anyString()))
                .thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("new-password"))
                .thenReturn("new-hash");
        when(refreshTokenRepository.findByUserAndRevokedAtIsNull(user))
                .thenReturn(List.of(refreshToken));

        service.confirmPasswordReset(
                new PasswordResetConfirmRequest("reset-token", "new-password")
        );

        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(resetToken);
        assertNotNull(resetToken.getUsedAt());
        assertNotNull(refreshToken.getRevokedAt());
    }

    @Test
    void confirmPasswordReset_shouldRejectExpiredTokenAndMarkItUsed() {
        PasswordResetService service = new PasswordResetService(
                userRepository,
                passwordResetTokenRepository,
                refreshTokenRepository,
                passwordEncoder,
                passwordResetEventPublisher,
                30
        );
        User user = new User("staff@example.com", Role.STAFF, "old-hash", UUID.randomUUID());
        PasswordResetToken resetToken = new PasswordResetToken(
                user,
                "token-hash",
                Instant.now().minusSeconds(1),
                Instant.now().minusSeconds(60)
        );

        when(passwordResetTokenRepository.findByTokenHashAndUsedAtIsNull(anyString()))
                .thenReturn(Optional.of(resetToken));

        assertThrows(
                BadCredentialsException.class,
                () -> service.confirmPasswordReset(
                        new PasswordResetConfirmRequest("reset-token", "new-password")
                )
        );

        verify(passwordResetTokenRepository).save(resetToken);
        assertNotNull(resetToken.getUsedAt());
        verify(passwordEncoder, never()).encode(anyString());
    }
}
