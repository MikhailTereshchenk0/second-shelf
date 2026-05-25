package com.secondshelf.userservice.controller;

import com.secondshelf.userservice.dto.PrivateUserProfileResponse;
import com.secondshelf.userservice.dto.PublicUserProfileResponse;
import com.secondshelf.userservice.dto.UpdateUserProfileRequest;
import com.secondshelf.userservice.security.AuthenticatedUser;
import com.secondshelf.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User API", description = "User profile endpoints")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "Get user profile by id",
            description = "Returns a public profile projection for a user by internal id without revealing email, first name, or last name"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profile returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}")
    public PublicUserProfileResponse getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @Operation(
            summary = "Get user profile by username",
            description = "Returns a public profile projection for a user by username without revealing email, first name, or last name"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profile returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/by-username")
    public PublicUserProfileResponse getByUsername(@RequestParam String username) {
        return userService.getByUsername(username);
    }

    @Operation(
            summary = "Get current authenticated user private profile",
            description = "Returns the authenticated user's private profile, including email, first name, last name, roles, enabled flag, and createdAt"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Private profile returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/me")
    public PrivateUserProfileResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return userService.getCurrentUser(principal.getUserId());
    }

    @Operation(
            summary = "Update own profile",
            description = "Updates profile data for the authenticated user and returns the owner's private profile. Access is allowed only to the profile owner"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Private profile updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied for another user's profile"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{id}")
    @PreAuthorize("#id == principal.userId")
    public PrivateUserProfileResponse updateProfile(@PathVariable Long id,
                                                    @Valid @RequestBody UpdateUserProfileRequest request) {
        return userService.updateProfile(id, request);
    }
}
