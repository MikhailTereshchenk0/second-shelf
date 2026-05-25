package com.secondshelf.userservice.internal.controller;

import com.secondshelf.userservice.dto.CreateUserProfileRequest;
import com.secondshelf.userservice.dto.PrivateUserProfileResponse;
import com.secondshelf.userservice.exception.UserNotFoundException;
import com.secondshelf.userservice.internal.dto.UserClaimsResponse;
import com.secondshelf.userservice.repository.UserRepository;
import com.secondshelf.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Hidden
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;
    private final UserRepository userRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrivateUserProfileResponse createProfile(@Valid @RequestBody CreateUserProfileRequest request) {
        return userService.createProfile(request);
    }

    @GetMapping("/{id}/claims")
    public UserClaimsResponse claims(@PathVariable Long id) {
        var user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        return new UserClaimsResponse(
                user.getId(),
                user.getUsername(),
                user.getRoles().stream().map(Enum::name).toList(),
                user.isEnabled()
        );
    }

}
