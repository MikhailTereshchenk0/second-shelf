package com.secondshelf.userservice.config;

import com.secondshelf.userservice.entity.Role;
import com.secondshelf.userservice.entity.User;
import com.secondshelf.userservice.repository.UserRepository;
import com.secondshelf.userservice.validation.password.PasswordPolicyRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${seed.admin.username:admin}")
    private String adminUsername;

    @Value("${seed.admin.password:admin12345}")
    private String adminPassword;

    @Value("${seed.admin.email:admin@secondshelf.local}")
    private String adminEmail;

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername(adminUsername).isPresent()) return;
        if (shouldEnforceStrictPasswordPolicy()) {
            PasswordPolicyRules.validate(adminPassword, adminUsername, adminEmail)
                    .ifPresent(message -> {
                        throw new IllegalStateException(
                                "Seed admin password does not satisfy the password policy for non-local profiles: " + message
                        );
                    });
        }

        User admin = User.builder()
                .username(adminUsername)
                .email(adminEmail)
                .firstName("Admin")
                .lastName("User")
                .password(passwordEncoder.encode(adminPassword))
                .build();

        admin.getRoles().add(Role.ROLE_ADMIN);
        admin.getRoles().add(Role.ROLE_USER);

        userRepository.save(admin);
    }

    boolean shouldEnforceStrictPasswordPolicy() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return false;
        }
        return Arrays.stream(activeProfiles)
                .noneMatch(profile -> "local".equalsIgnoreCase(profile));
    }
}
