package com.foundflow.magiclink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MagicLinkServiceTest {

    private static final String SECRET = "test-secret-with-enough-length";
    private static final UUID MATCH = UUID.randomUUID();
    private static final UUID VENUE = UUID.randomUUID();
    private static final UUID PICKUP = UUID.randomUUID();

    @Test
    void roundTripsMatchViewToken() {
        MagicLinkService service = new MagicLinkService(SECRET, 7);

        String token = service.createMatchViewToken(MATCH, VENUE, "Guest@Example.COM");
        MagicLinkClaims claims = service.verify(token, MagicLinkService.TYPE_MATCH_VIEW);

        assertEquals(MATCH, claims.matchId());
        assertEquals(VENUE, claims.venueId());
        assertEquals("guest@example.com", claims.email());
        assertEquals(MagicLinkService.TYPE_MATCH_VIEW, claims.type());
    }

    @Test
    void roundTripsPickupManageToken() {
        MagicLinkService service = new MagicLinkService(SECRET, 7);

        String token = service.createPickupManageToken(PICKUP, MATCH, VENUE, "guest@example.com");
        MagicLinkClaims claims = service.verify(token, MagicLinkService.TYPE_PICKUP_MANAGE);

        assertEquals(PICKUP, claims.pickupId());
        assertEquals(MATCH, claims.matchId());
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        MagicLinkService writer = new MagicLinkService("other-secret-with-some-length", 7);
        MagicLinkService verifier = new MagicLinkService(SECRET, 7);

        String token = writer.createMatchViewToken(MATCH, VENUE, "a@b.c");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> verifier.verify(token, MagicLinkService.TYPE_MATCH_VIEW)
        );
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void rejectsWrongTypeOnVerify() {
        MagicLinkService service = new MagicLinkService(SECRET, 7);

        String token = service.createMatchViewToken(MATCH, VENUE, "a@b.c");

        assertThrows(
                ResponseStatusException.class,
                () -> service.verify(token, MagicLinkService.TYPE_PICKUP_MANAGE)
        );
    }

    @Test
    void verifyForSlotsAcceptsEitherType() {
        MagicLinkService service = new MagicLinkService(SECRET, 7);

        String view = service.createMatchViewToken(MATCH, VENUE, "a@b.c");
        String manage = service.createPickupManageToken(PICKUP, MATCH, VENUE, "a@b.c");

        assertEquals(VENUE, service.verifyForSlots(view).venueId());
        assertEquals(VENUE, service.verifyForSlots(manage).venueId());
    }

    @Test
    void rejectsExpiredToken() {
        Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");
        Clock writeClock = Clock.fixed(issuedAt, ZoneOffset.UTC);
        Clock readClock = Clock.fixed(issuedAt.plusSeconds(8L * 24 * 60 * 60), ZoneOffset.UTC);
        ObjectMapper mapper = JsonMapper.builder().build();

        MagicLinkService writer = new MagicLinkService(mapper, writeClock, SECRET, 7);
        MagicLinkService reader = new MagicLinkService(mapper, readClock, SECRET, 7);

        String token = writer.createMatchViewToken(MATCH, VENUE, "a@b.c");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> reader.verify(token, MagicLinkService.TYPE_MATCH_VIEW)
        );
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void rejectsMalformedToken() {
        MagicLinkService service = new MagicLinkService(SECRET, 7);

        assertThrows(
                ResponseStatusException.class,
                () -> service.verify("not-a-real-token", MagicLinkService.TYPE_MATCH_VIEW)
        );
    }
}
