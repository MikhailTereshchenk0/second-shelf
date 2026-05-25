package com.secondshelf.authservice.dto;

import com.secondshelf.authservice.validation.password.ValidPasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@ValidPasswordPolicy
public class RegisterRequest {

    @NotBlank @Size(min = 3, max = 50)
    private String username;

    @NotBlank @Email @Size(max = 100)
    private String email;

    @NotBlank @Size(min = 2, max = 50)
    private String firstName;

    @NotBlank @Size(min = 2, max = 50)
    private String lastName;

    @Size(max = 50)
    private String city;

    @Size(max = 1000)
    private String about;

    @NotBlank
    private String password;
}
