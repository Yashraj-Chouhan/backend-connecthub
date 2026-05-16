package com.connecthub.authservice.config;

import com.connecthub.authservice.entity.User;
import com.connecthub.authservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Configuration
public class AdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
    private static final String ADMIN_EMAIL = "placy.support@gmail.com";

    @Value("${app.admin.bootstrap-password:}")
    private String configuredAdminPassword;

    @Bean
    CommandLineRunner initAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            String adminPassword = resolveAdminBootstrapPassword();
            java.util.Optional<User> existingUserOpt = userRepository.findByEmail(ADMIN_EMAIL);
            
            if (existingUserOpt.isEmpty()) {
                User admin = User.builder()
                        .email(ADMIN_EMAIL)
                        .username("admin")
                        .password(passwordEncoder.encode(adminPassword))
                        .role("ADMIN")
                        .isBlocked(false)
                        .build();
                userRepository.save(admin);
                log.info("Successfully seeded admin user with email: {}", ADMIN_EMAIL);
            } else {
                User existingUser = existingUserOpt.get();
                existingUser.setRole("ADMIN");
                existingUser.setPassword(passwordEncoder.encode(adminPassword));
                userRepository.save(existingUser);
                log.info("Successfully updated existing user {} to ADMIN role with refreshed password.", ADMIN_EMAIL);
            }
        };
    }

    private String resolveAdminBootstrapPassword() {
        if (StringUtils.hasText(configuredAdminPassword)) {
            return configuredAdminPassword.trim();
        }

        String generatedPassword = UUID.randomUUID().toString();
        log.warn("No app.admin.bootstrap-password configured. Generated a one-time bootstrap password for {}.", ADMIN_EMAIL);
        return generatedPassword;
    }
}
