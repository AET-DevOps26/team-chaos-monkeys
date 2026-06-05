package com.foundflow.auth.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.foundflow.auth.domain.Role;
import com.foundflow.auth.domain.User;
import com.foundflow.auth.dto.CreateUserRequest;
import com.foundflow.auth.dto.UpdateUserRequest;
import com.foundflow.auth.dto.UserResponse;
import com.foundflow.auth.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    public UserService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse createUser(CreateUserRequest request, Jwt jwt) {
        UUID venueId = resolveCreateVenueId(request, jwt);
        
        User user = new User(
            request.email(),
            request.role(),
            passwordEncoder.encode(request.password()),
            venueId
        );

        User savedUser = userRepository.save(user);
        return toResponse(savedUser);
    }

    public List<UserResponse> getAllUsers(Jwt jwt) {
        if (isAdmin(jwt)) {
            return userRepository.findAll()
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }

        if (!isOpsManager(jwt)) {
            throw new AccessDeniedException("Only admins and ops managers can list users.");
        }

        return userRepository.findByVenueId(getVenueId(jwt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<UserResponse> getUserById(UUID id, Jwt jwt) {
        return userRepository.findById(id)
                .filter(user -> canAccessUser(user, jwt))
                .map(this::toResponse);
    }

    public Optional<UserResponse> getUserByEmail(String email, Jwt jwt) {
        return userRepository.findByEmail(email)
                .filter(user -> canAccessUser(user, jwt))
                .map(this::toResponse);
    }

    public Optional<UserResponse> updateUser(
            UUID id,
            UpdateUserRequest request,
            Jwt jwt
    ) {
        return userRepository.findById(id)
                .map(user -> {
                    verifyUserAccess(user, jwt);
                    if (!isAdmin(jwt) && isCurrentUser(user, jwt) && request.role() != user.getRole()) {
                        throw new AccessDeniedException("Users cannot change their own role.");
                    }

                    if (!isAdmin(jwt) && request.role() == Role.ADMIN) {
                        throw new AccessDeniedException("OPS_MANAGER cannot create or promote admins.");
                    }

                    user.setEmail(request.email());
                    user.setRole(request.role());
                    if (request.role() == Role.ADMIN) {
                        user.setVenueId(null);
                    }

                    User updatedUser = userRepository.save(user);
                    return toResponse(updatedUser);
                });
    }

    public boolean deleteUser(UUID id, Jwt jwt) {
        return userRepository.findById(id)
                .map(user -> {
                    verifyUserAccess(user, jwt);
                    userRepository.delete(user);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public long deleteUsersByVenue(UUID venueId) {
        if (venueId == null) {
            return 0;
        }

        List<User> users = userRepository.findByVenueId(venueId);
        if (users.isEmpty()) {
            return 0;
        }

        userRepository.deleteAll(users);
        return users.size();
    }

    private UUID resolveCreateVenueId(CreateUserRequest request, Jwt jwt) {
        if (isAdmin(jwt)) {
            if ((request.role() == Role.STAFF || request.role() == Role.OPS_MANAGER)
                    && request.venueId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "STAFF and OPS_MANAGER require a venueId."
                );
            }

            if (request.role() == Role.ADMIN && request.venueId() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "ADMIN must not have a venueId."
                );
            }

            return request.venueId();
        }

        if (!isOpsManager(jwt)) {
            throw new AccessDeniedException("Only admins and ops managers can create users.");
        }

        if (request.role() == Role.ADMIN) {
            throw new AccessDeniedException("OPS_MANAGER cannot create admins.");
        }

        return getVenueId(jwt);
    }

    private void verifyUserAccess(User user, Jwt jwt) {
        if (isAdmin(jwt)) {
            return;
        }

        if (isCurrentUser(user, jwt)) {
            return;
        }

        if (!isOpsManager(jwt)
                || user.getVenueId() == null
                || !user.getVenueId().equals(getVenueId(jwt))) {
            throw new AccessDeniedException("No access to users outside your venue.");
        }
    }

    private boolean canAccessUser(User user, Jwt jwt) {
        if (isAdmin(jwt)) {
            return true;
        }

        if (isCurrentUser(user, jwt)) {
            return true;
        }

        return isOpsManager(jwt)
                && user.getVenueId() != null
                && user.getVenueId().equals(getVenueId(jwt));
    }

    private boolean isAdmin(Jwt jwt) {
        return hasRole(jwt, Role.ADMIN.name());
    }

    private boolean isOpsManager(Jwt jwt) {
        return hasRole(jwt, Role.OPS_MANAGER.name());
    }

    private UUID getVenueId(Jwt jwt) {
        String venueId = jwt.getClaimAsString("venue_id");

        if (venueId == null || venueId.isBlank()) {
            throw new AccessDeniedException("Missing venue_id claim.");
        }

        return UUID.fromString(venueId);
    }

    private String getCurrentUserEmail(Jwt jwt) {
        if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new AccessDeniedException("Missing subject claim.");
        }

        return jwt.getSubject();
    }

    private boolean isCurrentUser(User user, Jwt jwt) {
        return user.getEmail().equals(getCurrentUserEmail(jwt));
    }

    private boolean hasRole(Jwt jwt, String role) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains(role);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getVenueId()
        );
    }
}
