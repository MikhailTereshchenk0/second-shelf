package com.secondshelf.userservice.security;

import lombok.Getter;

import java.security.Principal;

@Getter
public class AuthenticatedUser implements Principal {
    private final Long userId;
    private final String username;

    public AuthenticatedUser(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    @Override
    public String getName() {
        return username;
    }

}
