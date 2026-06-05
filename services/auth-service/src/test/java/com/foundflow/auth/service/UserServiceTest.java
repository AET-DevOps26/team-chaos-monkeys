package com.foundflow.auth.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import com.foundflow.auth.domain.Role;
import com.foundflow.auth.domain.User;
import com.foundflow.auth.dto.CreateUserRequest;
import com.foundflow.auth.dto.UpdateUserRequest;
import com.foundflow.auth.dto.UserResponse;
import com.foundflow.auth.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createUser_shouldHashPasswordSaveAndReturnUser() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        CreateUserRequest request = new CreateUserRequest(
                "staff@example.com",
                Role.STAFF,
                "password123",
                UUID.randomUUID()
        );

        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.createUser(request, adminJwt());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User savedUser = captor.getValue();

        assertEquals("staff@example.com", savedUser.getEmail());
        assertEquals(Role.STAFF, savedUser.getRole());
        assertEquals("hashed-password", savedUser.getPasswordHash());

        assertEquals("staff@example.com", response.email());
        assertEquals(Role.STAFF, response.role());
        assertEquals(request.venueId(), response.venueId());

        verify(passwordEncoder).encode("password123");
    }

    @Test
    void createUser_shouldUseOwnVenueForOpsManager() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        UUID ownVenueId = UUID.randomUUID();
        UUID requestedVenueId = UUID.randomUUID();
        CreateUserRequest request = new CreateUserRequest(
                "staff@example.com",
                Role.STAFF,
                "password123",
                requestedVenueId
        );

        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.createUser(
                request,
                opsManagerJwt(ownVenueId, "manager@example.com")
        );

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertEquals(ownVenueId, captor.getValue().getVenueId());
        assertEquals(ownVenueId, response.venueId());
    }

    @Test
    void createUser_shouldRejectAdminCreationForOpsManager() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        CreateUserRequest request = new CreateUserRequest(
                "admin@example.com",
                Role.ADMIN,
                "password123",
                null
        );

        assertThrows(
                AccessDeniedException.class,
                () -> userService.createUser(
                        request,
                        opsManagerJwt(UUID.randomUUID(), "manager@example.com")
                )
        );

        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(userRepository);
    }

    @Test
    void createUser_shouldRejectStaffWithoutVenueForAdmin() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        CreateUserRequest request = new CreateUserRequest(
                "staff@example.com",
                Role.STAFF,
                "password123",
                null
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.createUser(request, adminJwt())
        );

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(userRepository);
    }

    @Test
    void createUser_shouldRejectAdminWithVenueForAdmin() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        CreateUserRequest request = new CreateUserRequest(
                "admin@example.com",
                Role.ADMIN,
                "password123",
                UUID.randomUUID()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.createUser(request, adminJwt())
        );

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(userRepository);
    }

    @Test
    void getAllUsers_shouldReturnMappedResponses() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        User user1 = new User("staff@example.com", Role.STAFF, "hash-1", UUID.randomUUID());
        User user2 = new User("admin@example.com", Role.ADMIN, "hash-2", null);

        when(userRepository.findAll()).thenReturn(List.of(user1, user2));

        List<UserResponse> responses = userService.getAllUsers(adminJwt());

        assertEquals(2, responses.size());
        assertEquals("staff@example.com", responses.get(0).email());
        assertEquals(Role.STAFF, responses.get(0).role());
        assertEquals("admin@example.com", responses.get(1).email());
        assertEquals(Role.ADMIN, responses.get(1).role());

        verify(userRepository).findAll();
    }

    @Test
    void getAllUsers_shouldReturnOwnVenueUsersForOpsManager() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        UUID venueId = UUID.randomUUID();
        User user = new User("staff@example.com", Role.STAFF, "hash-1", venueId);

        when(userRepository.findByVenueId(venueId)).thenReturn(List.of(user));

        List<UserResponse> responses =
                userService.getAllUsers(opsManagerJwt(venueId, "manager@example.com"));

        assertEquals(1, responses.size());
        assertEquals(venueId, responses.get(0).venueId());
        verify(userRepository).findByVenueId(venueId);
    }

    @Test
    void getUserById_shouldReturnResponseWhenUserExists() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        UUID id = UUID.randomUUID();
        User user = new User("manager@example.com", Role.OPS_MANAGER, "hash", UUID.randomUUID());

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        Optional<UserResponse> response = userService.getUserById(id, adminJwt());

        assertTrue(response.isPresent());
        assertEquals("manager@example.com", response.get().email());
        assertEquals(Role.OPS_MANAGER, response.get().role());

        verify(userRepository).findById(id);
    }

    @Test
    void getUserById_shouldReturnOwnUserForStaff() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        User user = new User("staff@example.com", Role.STAFF, "hash", venueId);

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        Optional<UserResponse> response =
                userService.getUserById(id, staffJwt(venueId, "staff@example.com"));

        assertTrue(response.isPresent());
        assertEquals("staff@example.com", response.get().email());
        verify(userRepository).findById(id);
    }

    @Test
    void getUserById_shouldHideVenuePeerForStaff() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        User user = new User("other-staff@example.com", Role.STAFF, "hash", venueId);

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        Optional<UserResponse> response =
                userService.getUserById(id, staffJwt(venueId, "staff@example.com"));

        assertTrue(response.isEmpty());
        verify(userRepository).findById(id);
    }

    @Test
    void getUserById_shouldReturnEmptyWhenUserDoesNotExist() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        UUID id = UUID.randomUUID();

        when(userRepository.findById(id)).thenReturn(Optional.empty());

        Optional<UserResponse> response = userService.getUserById(id, adminJwt());

        assertTrue(response.isEmpty());
        verify(userRepository).findById(id);
    }

    @Test
    void getUserByEmail_shouldReturnResponseWhenUserExists() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        User user = new User("admin@example.com", Role.ADMIN, "hash", null);

        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(user));

        Optional<UserResponse> response =
                userService.getUserByEmail("admin@example.com", adminJwt());

        assertTrue(response.isPresent());
        assertEquals("admin@example.com", response.get().email());
        assertEquals(Role.ADMIN, response.get().role());

        verify(userRepository).findByEmail("admin@example.com");
    }

    @Test
    void updateUser_shouldUpdateExistingUserWithoutChangingPasswordHash() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        UUID id = UUID.randomUUID();

        User existingUser = new User(
                "old@example.com",
                Role.STAFF,
                "existing-password-hash",
                UUID.randomUUID()
        );

        UpdateUserRequest request = new UpdateUserRequest(
                "updated@example.com",
                Role.ADMIN
        );

        when(userRepository.findById(id)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<UserResponse> response = userService.updateUser(id, request, adminJwt());

        assertTrue(response.isPresent());
        assertEquals("updated@example.com", response.get().email());
        assertEquals(Role.ADMIN, response.get().role());
        assertEquals("existing-password-hash", existingUser.getPasswordHash());

        verify(userRepository).findById(id);
        verify(userRepository).save(existingUser);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateUser_shouldAllowStaffToUpdateItselfWithoutChangingRole() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        User existingUser = new User(
                "staff@example.com",
                Role.STAFF,
                "existing-password-hash",
                venueId
        );

        UpdateUserRequest request = new UpdateUserRequest(
                "updated-staff@example.com",
                Role.STAFF
        );

        when(userRepository.findById(id)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<UserResponse> response =
                userService.updateUser(id, request, staffJwt(venueId, "staff@example.com"));

        assertTrue(response.isPresent());
        assertEquals("updated-staff@example.com", response.get().email());
        assertEquals(Role.STAFF, response.get().role());
        assertEquals("existing-password-hash", existingUser.getPasswordHash());
        verify(userRepository).save(existingUser);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateUser_shouldRejectStaffSelfRoleChange() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        User existingUser = new User("staff@example.com", Role.STAFF, "hash", venueId);
        UpdateUserRequest request = new UpdateUserRequest(
                "staff@example.com",
                Role.OPS_MANAGER
        );

        when(userRepository.findById(id)).thenReturn(Optional.of(existingUser));

        assertThrows(
                AccessDeniedException.class,
                () -> userService.updateUser(id, request, staffJwt(venueId, "staff@example.com"))
        );
    }

    @Test
    void updateUser_shouldRejectStaffUpdatingVenuePeer() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        User existingUser = new User("other-staff@example.com", Role.STAFF, "hash", venueId);
        UpdateUserRequest request = new UpdateUserRequest(
                "updated@example.com",
                Role.STAFF
        );

        when(userRepository.findById(id)).thenReturn(Optional.of(existingUser));

        assertThrows(
                AccessDeniedException.class,
                () -> userService.updateUser(id, request, staffJwt(venueId, "staff@example.com"))
        );
    }

    @Test
    void deleteUser_shouldDeleteExistingUser() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        UUID id = UUID.randomUUID();

        User user = new User("staff@example.com", Role.STAFF, "hash", UUID.randomUUID());
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        boolean result = userService.deleteUser(id, adminJwt());

        assertTrue(result);
        verify(userRepository).findById(id);
        verify(userRepository).delete(user);
    }

    @Test
    void deleteUser_shouldAllowSelfDeletionForOpsManager() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        User user = new User("manager@example.com", Role.OPS_MANAGER, "hash", venueId);

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        boolean result = userService.deleteUser(id, opsManagerJwt(venueId, "manager@example.com"));

        assertTrue(result);
        verify(userRepository).findById(id);
        verify(userRepository).delete(user);
    }

    @Test
    void deleteUser_shouldAllowSelfDeletionForStaff() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        User user = new User("staff@example.com", Role.STAFF, "hash", venueId);

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        boolean result = userService.deleteUser(id, staffJwt(venueId, "staff@example.com"));

        assertTrue(result);
        verify(userRepository).findById(id);
        verify(userRepository).delete(user);
    }

    @Test
    void deleteUser_shouldRejectStaffDeletingVenuePeer() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        User user = new User("other-staff@example.com", Role.STAFF, "hash", venueId);

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThrows(
                AccessDeniedException.class,
                () -> userService.deleteUser(id, staffJwt(venueId, "staff@example.com"))
        );

        verify(userRepository).findById(id);
    }

    private Jwt adminJwt() {
        return Jwt.withTokenValue("token")
                .subject("admin@example.com")
                .header("alg", "none")
                .claim("roles", List.of("ADMIN"))
                .build();
    }

    private Jwt opsManagerJwt(UUID venueId, String email) {
        return Jwt.withTokenValue("token")
                .subject(email)
                .header("alg", "none")
                .claim("roles", List.of("OPS_MANAGER"))
                .claim("venue_id", venueId.toString())
                .build();
    }

    private Jwt staffJwt(UUID venueId, String email) {
        return Jwt.withTokenValue("token")
                .subject(email)
                .header("alg", "none")
                .claim("roles", List.of("STAFF"))
                .claim("venue_id", venueId.toString())
                .build();
    }
}
