package com.secondshelf.bookservice.security;

import java.util.List;

public record CurrentUser(Long userId, String username, List<String> roles) {
    public boolean isAdmin() {
        return roles != null && roles.contains("ROLE_ADMIN");
    }
}
