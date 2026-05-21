package com.foundflow.auth.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.foundflow.auth.domain.Role;
import com.foundflow.auth.domain.User;
import com.foundflow.auth.repository.UserRepository;

@Configuration
@Profile("dev")
public class DevUserInitializer {

    @Bean
    public CommandLineRunner createDevAdminUser(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            String email = System.getenv("DEV_ADMIN_EMAIL");
            String rawPassword = System.getenv("DEV_ADMIN_PASSWORD");

            if (email == null || email.isBlank()
                    || rawPassword == null || rawPassword.isBlank()) {
                return;
            }

            if (userRepository.findByEmail(email).isEmpty()) {
                User admin = new User(
                        email,
                        Role.ADMIN,
                        passwordEncoder.encode(rawPassword),
                        null
                );

                userRepository.save(admin);
            }
        };
    }
}