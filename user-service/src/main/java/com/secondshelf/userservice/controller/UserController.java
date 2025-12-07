package com.secondshelf.userservice.controller;

import com.secondshelf.userservice.dto.UpdateUserProfileRequest;
import com.secondshelf.userservice.dto.UserProfileResponse;
import com.secondshelf.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public UserProfileResponse getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @GetMapping("/by-username")
    public UserProfileResponse getByUsername(@RequestParam String username) {
        return userService.getByUsername(username);
    }

    @PutMapping("/{id}")
    public UserProfileResponse updateProfile(@PathVariable Long id,
                                             @Valid @RequestBody UpdateUserProfileRequest request) {
        // TODO: позже здесь добавим проверку, что id == id текущего авторизованного пользователя (из JWT)
        return userService.updateProfile(id, request);
    }
}
