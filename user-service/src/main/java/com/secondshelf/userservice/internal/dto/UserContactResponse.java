package com.secondshelf.userservice.internal.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserContactResponse {
    private Long id;
    private String username;
    private String phoneNumber;
}
