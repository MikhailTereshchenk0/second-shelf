package com.secondshelf.userservice.controller;

import com.secondshelf.userservice.dto.UpdateRolesRequest;
import com.secondshelf.userservice.dto.UserProfileResponse;
import com.secondshelf.userservice.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PutMapping("/{id}/roles")
    public UserProfileResponse updateRoles(@PathVariable Long id,
                                           @Valid @RequestBody UpdateRolesRequest request) {
        return adminUserService.updateRoles(id, request.getRoles());
    }

    @PutMapping("/{id}/block")
    public UserProfileResponse block(@PathVariable Long id) {
        return adminUserService.block(id);
    }

    @PutMapping("/{id}/unblock")
    public UserProfileResponse unblock(@PathVariable Long id) {
        return adminUserService.unblock(id);
    }
}
