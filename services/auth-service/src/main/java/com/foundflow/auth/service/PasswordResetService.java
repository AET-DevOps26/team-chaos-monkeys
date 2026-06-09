package com.foundflow.auth.service;

import com.foundflow.auth.domain.PasswordResetToken;
import com.foundflow.auth.domain.User;
import com.foundflow.auth.dto.PasswordResetConfirmRequest;
import com.foundflow.auth.dto.PasswordResetRequest;
import com.foundflow.auth.messaging.PasswordResetEventPublisher;
import com.foundflow.auth.repository.PasswordResetTokenRepository;
import com.foundflow.auth.repository.RefreshTokenRepository;
import com.foundflow.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetEventPublisher passwordResetEventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration passwordResetTokenTtl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            PasswordResetEventPublisher passwordResetEventPublisher,
            @Value("${foundflow.auth.password-reset-token-minutes:30}") long passwordResetTokenMinutes
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetEventPublisher = passwordResetEventPublisher;
        this.passwordResetTokenTtl = Duration.ofMinutes(passwordResetTokenMinutes);
    }

    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .ifPresent(this::createAndSendPasswordReset);
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        Instant now = Instant.now();
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNull(hashToken(request.token()))
                .orElseThrow(() -> new BadCredentialsException("Invalid password reset token"));

        if (resetToken.isExpired(now)) {
            resetToken.markUsed(now);
            passwordResetTokenRepository.save(resetToken);
            throw new BadCredentialsException("Invalid password reset token");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        resetToken.markUsed(now);

        refreshTokenRepository.findByUserAndRevokedAtIsNull(user)
                .forEach(refreshToken -> refreshToken.revoke(now));

        userRepository.save(user);
        passwordResetTokenRepository.save(resetToken);
    }

    private void createAndSendPasswordReset(User user) {
        Instant now = Instant.now();
        passwordResetTokenRepository.findByUserAndUsedAtIsNull(user)
                .forEach(token -> token.markUsed(now));

        String token = createRawToken();
        PasswordResetToken resetToken = new PasswordResetToken(
                user,
                hashToken(token),
                now.plus(passwordResetTokenTtl),
                now
        );

        passwordResetTokenRepository.save(resetToken);
        publishPasswordResetRequestedAfterCommit(
                user.getId(),
                user.getVenueId(),
                user.getEmail(),
                token
        );
    }

    private String createRawToken() {
        byte[] randomBytes = new byte[48];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    private void publishPasswordResetRequestedAfterCommit(
            UUID userId,
            UUID venueId,
            String email,
            String token
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishPasswordResetRequested(userId, venueId, email, token);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishPasswordResetRequested(userId, venueId, email, token);
            }
        });
    }

    private void publishPasswordResetRequested(
            UUID userId,
            UUID venueId,
            String email,
            String token
    ) {
        passwordResetEventPublisher.publishPasswordResetRequested(
                userId,
                venueId,
                email,
                token
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
