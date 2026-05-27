package com.foundflow.matching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class MagicLinkService {

    public static final String TYPE_MATCH_VIEW = "match_view";

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final byte[] secret;
    private final long ttlSeconds;

    @Autowired
    public MagicLinkService(
            @Value("${foundflow.magic-link.secret}") String secret,
            @Value("${foundflow.magic-link.ttl-days:7}") long ttlDays
    ) {
        this.objectMapper = JsonMapper.builder().build();
        this.clock = Clock.systemUTC();
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlDays * 24 * 60 * 60;
    }

    public String createMatchViewToken(UUID matchId, UUID venueId, String email) {
        return createToken(new MagicLinkClaims(
                TYPE_MATCH_VIEW,
                matchId,
                null,
                venueId,
                email == null ? null : email.trim().toLowerCase(),
                Instant.now(clock).plusSeconds(ttlSeconds).getEpochSecond()
        ));
    }

    public MagicLinkClaims verifyMatchViewToken(String token) {
        MagicLinkClaims claims = verify(token);
        if (!TYPE_MATCH_VIEW.equals(claims.type())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Magic link cannot be used for this action.");
        }
        return claims;
    }

    private MagicLinkClaims verify(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw invalidToken();
        }

        byte[] expectedSignature = sign(parts[0]);
        byte[] actualSignature;
        try {
            actualSignature = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw invalidToken();
        }

        if (!timingSafeEquals(expectedSignature, actualSignature)) {
            throw invalidToken();
        }

        MagicLinkClaims claims;
        try {
            String json = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            claims = objectMapper.readValue(json, MagicLinkClaims.class);
        } catch (Exception exception) {
            throw invalidToken();
        }

        if (claims.expiresAt() < Instant.now(clock).getEpochSecond()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Magic link has expired.");
        }
        return claims;
    }

    private String createToken(MagicLinkClaims claims) {
        try {
            String payload = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(claims));
            String signature = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(sign(payload));
            return payload + "." + signature;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create magic link token.", exception);
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign magic link token.", exception);
        }
    }

    private boolean timingSafeEquals(byte[] expected, byte[] actual) {
        if (expected.length != actual.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < expected.length; i++) {
            diff |= expected[i] ^ actual[i];
        }
        return diff == 0;
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Magic link is invalid.");
    }
}
