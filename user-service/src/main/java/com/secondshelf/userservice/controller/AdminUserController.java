package com.secondshelf.userservice.controller;

import com.secondshelf.userservice.dto.UpdateRolesRequest;
import com.secondshelf.userservice.dto.UserProfileResponse;
import com.secondshelf.userservice.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin User API", description = "Administrative user management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(
            summary = "Update user roles",
            description = "Updates the role set for a specific user. Intended for administrators only"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roles updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Administrator role required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{id}/roles")
    public UserProfileResponse updateRoles(@PathVariable Long id,
                                           @Valid @RequestBody UpdateRolesRequest request) {
        return adminUserService.updateRoles(id, request.getRoles());
    }

    @Operation(
            summary = "Block user",
            description = "Blocks a user account. Intended for administrators only"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User blocked successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Administrator role required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{id}/block")
    public UserProfileResponse block(@PathVariable Long id) {
        return adminUserService.block(id);
    }

    @Operation(
            summary = "Unblock user",
            description = "Removes blocking from a user account. Intended for administrators only"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User unblocked successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Administrator role required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{id}/unblock")
    public UserProfileResponse unblock(@PathVariable Long id) {
        return adminUserService.unblock(id);
    }
}