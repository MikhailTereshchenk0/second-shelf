package com.secondshelf.userservice.internal.controller;

import com.secondshelf.userservice.dto.CreateUserProfileRequest;
import com.secondshelf.userservice.dto.UserProfileResponse;
import com.secondshelf.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users")  // TODO
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfileResponse createProfile(@Valid @RequestBody CreateUserProfileRequest request) {
        return userService.createProfile(request);
    }
}
