package com.foundflow.auth.service;

import com.foundflow.auth.domain.Role;
import com.foundflow.auth.domain.User;
import com.foundflow.auth.dto.CreateUserRequest;
import com.foundflow.auth.dto.UpdateUserRequest;
import com.foundflow.auth.dto.UserResponse;
import com.foundflow.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
                "password123"
        );

        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.createUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User savedUser = captor.getValue();

        assertEquals("staff@example.com", savedUser.getEmail());
        assertEquals(Role.STAFF, savedUser.getRole());
        assertEquals("hashed-password", savedUser.getPasswordHash());

        assertEquals("staff@example.com", response.email());
        assertEquals(Role.STAFF, response.role());

        verify(passwordEncoder).encode("password123");
    }

    @Test
    void getAllUsers_shouldReturnMappedResponses() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        User user1 = new User("staff@example.com", Role.STAFF, "hash-1");
        User user2 = new User("admin@example.com", Role.ADMIN, "hash-2");

        when(userRepository.findAll()).thenReturn(List.of(user1, user2));

        List<UserResponse> responses = userService.getAllUsers();

        assertEquals(2, responses.size());
        assertEquals("staff@example.com", responses.get(0).email());
        assertEquals(Role.STAFF, responses.get(0).role());
        assertEquals("admin@example.com", responses.get(1).email());
        assertEquals(Role.ADMIN, responses.get(1).role());

        verify(userRepository).findAll();
    }

    @Test
    void getUserById_shouldReturnResponseWhenUserExists() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        UUID id = UUID.randomUUID();
        User user = new User("manager@example.com", Role.OPS_MANAGER, "hash");

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        Optional<UserResponse> response = userService.getUserById(id);

        assertTrue(response.isPresent());
        assertEquals("manager@example.com", response.get().email());
        assertEquals(Role.OPS_MANAGER, response.get().role());

        verify(userRepository).findById(id);
    }

    @Test
    void getUserById_shouldReturnEmptyWhenUserDoesNotExist() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        UUID id = UUID.randomUUID();

        when(userRepository.findById(id)).thenReturn(Optional.empty());

        Optional<UserResponse> response = userService.getUserById(id);

        assertTrue(response.isEmpty());
        verify(userRepository).findById(id);
    }

    @Test
    void getUserByEmail_shouldReturnResponseWhenUserExists() {
        UserService userService = new UserService(userRepository, passwordEncoder);

        User user = new User("admin@example.com", Role.ADMIN, "hash");

        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(user));

        Optional<UserResponse> response =
                userService.getUserByEmail("admin@example.com");

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
                "existing-password-hash"
        );

        UpdateUserRequest request = new UpdateUserRequest(
                "updated@example.com",
                Role.ADMIN
        );

        when(userRepository.findById(id)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<UserResponse> response = userService.updateUser(id, request);

        assertTrue(response.isPresent());
        assertEquals("updated@example.com", response.get().email());
        assertEquals(Role.ADMIN, response.get().role());
        assertEquals("existing-password-hash", existingUser.getPasswordHash());

        verify(userRepository).findById(id);
        verify(userRepository).save(existingUser);
        verifyNoInteractions(passwordEncoder);
    }
}