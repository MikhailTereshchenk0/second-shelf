package com.secondshelf.userservice.config;

import com.secondshelf.userservice.entity.Role;
import com.secondshelf.userservice.entity.User;
import com.secondshelf.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${seed.admin.username:admin}")
    private String adminUsername;

    @Value("${seed.admin.password:admin12345}")
    private String adminPassword;

    @Value("${seed.admin.email:admin@secondshelf.local}")
    private String adminEmail;

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername(adminUsername).isPresent()) return;

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
}
