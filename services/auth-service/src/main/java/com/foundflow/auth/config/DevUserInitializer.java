package com.foundflow.auth.config;

import com.foundflow.auth.domain.Role;
import com.foundflow.auth.domain.User;
import com.foundflow.auth.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DevUserInitializer {

    @Bean
    public CommandLineRunner createDevAdminUser(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            String email = "admin@foundflow.local";
            String rawPassword = "admin12345";

            if (userRepository.findByEmail(email).isEmpty()) {
                User admin = new User(
                        email,
                        Role.ADMIN,
                        passwordEncoder.encode(rawPassword)
                );

                userRepository.save(admin);
            }
        };
    }
}