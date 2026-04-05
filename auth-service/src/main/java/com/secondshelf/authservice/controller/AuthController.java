package com.secondshelf.authservice.controller;

import com.secondshelf.authservice.dto.CurrentUserResponse;
import com.secondshelf.authservice.dto.LoginRequest;
import com.secondshelf.authservice.dto.RefreshRequest;
import com.secondshelf.authservice.dto.RegisterRequest;
import com.secondshelf.authservice.dto.TokenPairResponse;
import com.secondshelf.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth API", description = "Authentication and token management endpoints")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Ping auth service",
            description = "Simple public endpoint to verify that auth-service is running"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Auth service is available")
    })
    @GetMapping("/ping")
    public String ping() {
        return "auth-service is up";
    }

    @Operation(
            summary = "Login",
            description = "Authenticates a user by username and password and returns access and refresh tokens"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public TokenPairResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(
            summary = "Get current authenticated user",
            description = "Returns username and granted roles for the current authenticated user"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        String username = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return new CurrentUserResponse(username, roles);
    }

    @Operation(
            summary = "Register",
            description = "Creates a new user account and immediately returns issued access and refresh tokens"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registration successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or duplicated user data")
    })
    @PostMapping("/register")
    public TokenPairResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(
            summary = "Refresh tokens",
            description = "Issues a new access token pair using a valid refresh token"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens refreshed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Refresh token is invalid or expired")
    })
    @PostMapping("/refresh")
    public TokenPairResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @Operation(
            summary = "Logout",
            description = "Invalidates the provided refresh token"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logout successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
    }

    @Operation(
            summary = "Logout from all sessions",
            description = "Invalidates all refresh tokens associated with the provided refresh token owner"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "All sessions were logged out successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@Valid @RequestBody RefreshRequest request) {
        authService.logoutAll(request);
    }
}