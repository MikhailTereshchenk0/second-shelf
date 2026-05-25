package com.secondshelf.userservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigurationValidatorTest {

    private static final String DEMO_JWT_SECRET =
            "change_this_secret_to_something_long_and_random_1234567890_change_me";
    private static final String STRONG_JWT_SECRET =
            "R9fK7mQ2Lp8Vx4NzH1cD6sY0Wa3Jr5Tu9Bg2Mn7Qk4Hx8Cv6Pd1Ls3Xe7Kb5Qw2M";
    private static final String STRONG_INTERNAL_TOKEN = "uK9pL4sD8mF2qR7tV1xN6cH3yB5jZ0wQ";

    @Test
    void localProfileShouldAllowDemoValues() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        SecurityConfigurationValidator validator = new SecurityConfigurationValidator(
                environment,
                DEMO_JWT_SECRET,
                "internal-secret-123",
                "secret",
                "admin",
                "admin12345",
                "admin@secondshelf.local"
        );

        assertDoesNotThrow(validator::validateConfiguration);
    }

    @Test
    void prodProfileShouldRejectDemoValues() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        SecurityConfigurationValidator validator = new SecurityConfigurationValidator(
                environment,
                DEMO_JWT_SECRET,
                "internal-secret-123",
                "secret",
                "admin",
                "admin12345",
                "admin@secondshelf.local"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                validator::validateConfiguration
        );

        assertTrue(exception.getMessage().contains("JWT_SECRET"));
        assertTrue(exception.getMessage().contains("INTERNAL_TOKEN"));
        assertTrue(exception.getMessage().contains("DB_PASSWORD"));
        assertTrue(exception.getMessage().contains("SEED_ADMIN_PASSWORD"));
    }

    @Test
    void prodProfileShouldAcceptStrongValues() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        SecurityConfigurationValidator validator = new SecurityConfigurationValidator(
                environment,
                STRONG_JWT_SECRET,
                STRONG_INTERNAL_TOKEN,
                "Db!Passw0rd-For-Prod",
                "platform-admin",
                "Str0ng!AdminSeedPwd",
                "platform-admin@example.com"
        );

        assertDoesNotThrow(validator::validateConfiguration);
    }
}
