package com.secondshelf.userservice.dto;

import com.secondshelf.userservice.entity.Role;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class UpdateRolesRequest {
    @NotEmpty
    private Set<Role> roles;
}
