package com.secondshelf.userservice.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserProfileRequest {

    @Size(min = 2, max = 50)
    private String firstName;

    @Size(min = 2, max = 50)
    private String lastName;

    @Size(max = 50)
    private String city;

    @Size(max = 32)
    private String phoneNumber;

    @Size(max = 1000)
    private String about;
}
