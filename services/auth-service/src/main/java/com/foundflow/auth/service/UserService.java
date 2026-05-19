package com.foundflow.auth.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    public UserResponse createUser(CreateUserRequest request) {
        if ((request.role() == Role.STAFF || request.role() == Role.OPS_MANAGER) && request.venueId() == null) {
            throw new IllegalArgumentException("STAFF and OPS_MANAGER require a venueId.");
        }

        if (request.role() == Role.ADMIN && request.venueId() != null) {
            throw new IllegalArgumentException("ADMIN must not have a venueId.");
        }
        
        User user = new User(
            request.email(),
            request.role(),
            passwordEncoder.encode(request.password()),
            request.venueId()
        );

        User savedUser = userRepository.save(user);
        return toResponse(savedUser);
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<UserResponse> getUserById(UUID id) {
        return userRepository.findById(id)
                .map(this::toResponse);
    }

    public Optional<UserResponse> getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(this::toResponse);
    }

    public Optional<UserResponse> updateUser(
            UUID id,
            UpdateUserRequest request
    ) {
        return userRepository.findById(id)
                .map(user -> {
                    user.setEmail(request.email());
                    user.setRole(request.role());

                    User updatedUser = userRepository.save(user);
                    return toResponse(updatedUser);
                });
    }

    public boolean deleteUser(UUID id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        } else {
            return false;
        }
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
    }
}