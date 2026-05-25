package com.secondshelf.notificationservice.config;

import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class SecurityConfigurationValidator {

    private static final List<String> JWT_FORBIDDEN_FRAGMENTS = List.of(
            "change_this", "secret", "123456", "admin", "local", "test"
    );

    private final Environment environment;
    private final String jwtSecret;
    private final String databasePassword;
    private final String rabbitUsername;
    private final String rabbitPassword;

    public SecurityConfigurationValidator(
            Environment environment,
            @Value("${jwt.secret:}") String jwtSecret,
            @Value("${spring.datasource.password:}") String databasePassword,
            @Value("${spring.rabbitmq.username:}") String rabbitUsername,
            @Value("${spring.rabbitmq.password:}") String rabbitPassword
    ) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.databasePassword = databasePassword;
        this.rabbitUsername = rabbitUsername;
        this.rabbitPassword = rabbitPassword;
    }

    @PostConstruct
    void validateAtStartup() {
        validateConfiguration();
    }

    void validateConfiguration() {
        if (isLocalOnlyProfile()) {
            return;
        }

        List<String> violations = new ArrayList<>();
        validateJwtSecret(violations);
        validateDatabasePassword(violations);
        validateRabbitCredentials(violations);

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "notification-service insecure configuration for non-local profile:\n - "
                            + String.join("\n - ", violations)
            );
        }
    }

    private boolean isLocalOnlyProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            return Arrays.stream(activeProfiles)
                    .allMatch(profile -> "local".equalsIgnoreCase(profile));
        }
        return Arrays.stream(environment.getDefaultProfiles())
                .anyMatch(profile -> "local".equalsIgnoreCase(profile));
    }

    private void validateJwtSecret(List<String> violations) {
        if (!StringUtils.hasText(jwtSecret)) {
            violations.add("JWT_SECRET must be set.");
            return;
        }

        String normalized = jwtSecret.trim();
        if (normalized.length() < 64) {
            violations.add("JWT_SECRET must be at least 64 characters long.");
        }

        String lowerCased = normalized.toLowerCase(Locale.ROOT);
        if (JWT_FORBIDDEN_FRAGMENTS.stream().anyMatch(lowerCased::contains)) {
            violations.add("JWT_SECRET must not contain demo fragments such as change_this, secret, 123456, admin, local, or test.");
        }

        try {
            Keys.hmacShaKeyFor(normalized.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            violations.add("JWT_SECRET must satisfy JJWT HMAC key requirements.");
        }
    }

    private void validateDatabasePassword(List<String> violations) {
        if (!StringUtils.hasText(databasePassword)) {
            violations.add("DB_PASSWORD must be set.");
            return;
        }

        if ("secret".equalsIgnoreCase(databasePassword.trim())) {
            violations.add("DB_PASSWORD must not use the demo value 'secret'.");
        }
    }

    private void validateRabbitCredentials(List<String> violations) {
        if (!StringUtils.hasText(rabbitUsername) || !StringUtils.hasText(rabbitPassword)) {
            violations.add("RABBITMQ_USERNAME and RABBITMQ_PASSWORD must be set.");
            return;
        }

        if ("guest".equalsIgnoreCase(rabbitUsername.trim()) && "guest".equalsIgnoreCase(rabbitPassword.trim())) {
            violations.add("RabbitMQ credentials must not use the demo guest/guest pair.");
        }
    }
}
