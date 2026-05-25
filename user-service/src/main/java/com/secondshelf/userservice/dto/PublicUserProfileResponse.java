package com.secondshelf.userservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublicUserProfileResponse {
    private Long id;
    private String username;
    private String city;
    private String about;
}
