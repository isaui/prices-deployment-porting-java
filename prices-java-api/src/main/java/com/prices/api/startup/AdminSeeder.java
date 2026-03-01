package com.prices.api.startup;

import com.prices.api.models.User;
import com.prices.api.repositories.UserRepository;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationEventListener<ServerStartupEvent> {

    private final UserRepository userRepository;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPassword;

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        validateAdminConfig();
        ensureAdminExists();
    }

    private void validateAdminConfig() {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalStateException("ADMIN_USERNAME environment variable is required");
        }
        if (adminEmail == null || adminEmail.isBlank()) {
            throw new IllegalStateException("ADMIN_EMAIL environment variable is required");
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("ADMIN_PASSWORD environment variable is required");
        }
    }

    private void ensureAdminExists() {
        // Check if admin user already exists by username or email
        if (userRepository.findByUsername(adminUsername).isPresent()) {
            log.info("Admin user '{}' already exists", adminUsername);
            return;
        }

        if (userRepository.findByEmail(adminEmail).isPresent()) {
            log.info("Admin user with email '{}' already exists", adminEmail);
            return;
        }

        // Create admin user
        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setEmail(adminEmail);
        admin.setPassword(BCrypt.hashpw(adminPassword, BCrypt.gensalt()));
        admin.setRole("admin");

        userRepository.save(admin);
        log.info("Created admin user: {} ({})", adminUsername, adminEmail);
    }
}
