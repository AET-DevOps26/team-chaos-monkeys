package com.foundflow.founditem.security;

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

    public UUID getUserId(Jwt jwt) {
        String userId = jwt.getClaimAsString("user_id");
        if (userId == null || userId.isBlank()) {
            userId = jwt.getSubject();
        }

        if (userId == null || userId.isBlank()) {
            throw new AccessDeniedException("Missing user identity claim.");
        }

        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Invalid user identity claim.", exception);
        }
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
