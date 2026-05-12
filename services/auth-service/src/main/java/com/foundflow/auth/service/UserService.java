package com.foundflow.auth.service;

import com.foundflow.auth.domain.User;
import com.foundflow.auth.dto.CreateUserRequest;
import com.foundflow.auth.dto.UpdateUserRequest;
import com.foundflow.auth.dto.UserResponse;
import com.foundflow.auth.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse createUser(CreateUserRequest request) {
        User user = new User(
                request.email(),
                request.role()
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

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
    }
}