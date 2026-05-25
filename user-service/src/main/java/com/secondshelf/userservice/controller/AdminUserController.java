package com.secondshelf.userservice.controller;

import com.secondshelf.userservice.dto.PrivateUserProfileResponse;
import com.secondshelf.userservice.dto.UpdateRolesRequest;
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
            description = "Updates the role set for a specific user and returns the user's private profile. Intended for administrators only"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Private profile with updated roles returned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Administrator role required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{id}/roles")
    public PrivateUserProfileResponse updateRoles(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateRolesRequest request) {
        return adminUserService.updateRoles(id, request.getRoles());
    }

    @Operation(
            summary = "Block user",
            description = "Blocks a user account and returns the user's private profile. Intended for administrators only"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Private profile with blocked status returned successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Administrator role required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{id}/block")
    public PrivateUserProfileResponse block(@PathVariable Long id) {
        return adminUserService.block(id);
    }

    @Operation(
            summary = "Unblock user",
            description = "Removes blocking from a user account and returns the user's private profile. Intended for administrators only"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Private profile with unblocked status returned successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Administrator role required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{id}/unblock")
    public PrivateUserProfileResponse unblock(@PathVariable Long id) {
        return adminUserService.unblock(id);
    }
}
