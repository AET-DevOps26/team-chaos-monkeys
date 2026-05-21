package com.foundflow.matching.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class VenueAccessService {

    public boolean isAdmin(Jwt jwt) {
        return hasRole(jwt, "ADMIN");
    }

    public UUID getVenueId(Jwt jwt) {
        String venueId = jwt.getClaimAsString("venue_id");

        if (venueId == null || venueId.isBlank()) {
            throw new AccessDeniedException("Missing venue_id claim.");
        }

        return UUID.fromString(venueId);
    }

    public boolean canAccessVenue(Jwt jwt, UUID resourceVenueId) {
        if (isAdmin(jwt)) {
            return true;
        }

        if (resourceVenueId == null) {
            return false;
        }

        return getVenueId(jwt).equals(resourceVenueId);
    }

    private boolean hasRole(Jwt jwt, String role) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains(role);
    }
}
