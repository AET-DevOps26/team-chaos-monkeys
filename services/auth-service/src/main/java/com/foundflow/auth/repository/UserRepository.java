package com.foundflow.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.foundflow.auth.domain.Role;
import com.foundflow.auth.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByVenueId(UUID venueId);

    List<User> findByRole(Role role);

    List<User> findByVenueIdAndRole(UUID venueId, Role role);
}
