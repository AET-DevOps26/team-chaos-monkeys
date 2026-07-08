package com.foundflow.auth.service;

import com.foundflow.auth.domain.RefreshToken;
import com.foundflow.auth.domain.User;
import com.foundflow.auth.dto.LoginRequest;
import com.foundflow.auth.dto.RefreshTokenRequest;
import com.foundflow.auth.dto.TokenResponse;
import com.foundflow.auth.repository.RefreshTokenRepository;
import com.foundflow.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final String issuerUri;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            @Value("${foundflow.auth.issuer-uri:http://localhost:8081}") String issuerUri,
            @Value("${foundflow.auth.access-token-minutes:30}") long accessTokenMinutes,
            @Value("${foundflow.auth.refresh-token-days:7}") long refreshTokenDays
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.issuerUri = issuerUri;
        this.accessTokenTtl = Duration.ofMinutes(accessTokenMinutes);
        this.refreshTokenTtl = Duration.ofDays(refreshTokenDays);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Login failed: unknown email");
                    return new BadCredentialsException("Invalid credentials");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed: wrong password for user={} venue={}", user.getId(), user.getVenueId());
            throw new BadCredentialsException("Invalid credentials");
        }

        log.info("User logged in user={} venue={}", user.getId(), user.getVenueId());
        return issueTokenPair(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        Instant now = Instant.now();
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHashAndRevokedAtIsNull(hashToken(request.refreshToken()))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (refreshToken.isExpired(now)) {
            refreshToken.revoke(now);
            throw new BadCredentialsException("Invalid refresh token");
        }

        refreshToken.revoke(now);
        return issueTokenPair(refreshToken.getUser());
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        Instant now = Instant.now();
        refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(hashToken(request.refreshToken()))
                .ifPresent(refreshToken -> refreshToken.revoke(now));
    }

    private TokenResponse issueTokenPair(User user) {
        Instant now = Instant.now();
        Instant accessTokenExpiresAt = now.plus(accessTokenTtl);
        String accessToken = createAccessToken(user, now, accessTokenExpiresAt);
        String refreshToken = createRefreshToken(user, now);

        return new TokenResponse(
                accessToken,
                refreshToken,
                TOKEN_TYPE,
                accessTokenTtl.toSeconds()
        );
    }

    private String createAccessToken(User user, Instant issuedAt, Instant expiresAt) {
        String userId = requireUserId(user);

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuerUri)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("user_id", userId)
                .claim("roles", List.of(user.getRole().name()));

        if (user.getVenueId() != null) {
            claims.claim("venue_id", user.getVenueId().toString());
        }

        return jwtEncoder.encode(JwtEncoderParameters.from(claims.build()))
                .getTokenValue();
    }

    private String requireUserId(User user) {
        if (user.getId() == null) {
            throw new IllegalStateException("Authenticated user has no id.");
        }

        return user.getId().toString();
    }

    private String createRefreshToken(User user, Instant now) {
        byte[] randomBytes = new byte[48];
        secureRandom.nextBytes(randomBytes);

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        refreshTokenRepository.save(new RefreshToken(
                user,
                hashToken(token),
                now.plus(refreshTokenTtl),
                now
        ));

        return token;
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
