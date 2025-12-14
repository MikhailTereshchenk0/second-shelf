package com.secondshelf.userservice.internal.service;

import com.secondshelf.userservice.internal.dto.AuthenticateResponse;
import com.secondshelf.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InternalAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticateResponse authenticate(String username, String password) {
        var user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return null;
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            return null;
        }

        List<String> roles = user.getRoles().stream().toList();

        return new AuthenticateResponse(user.getId(), user.getUsername(), roles);
    }
}
