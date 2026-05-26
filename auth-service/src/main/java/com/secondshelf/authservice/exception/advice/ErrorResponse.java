package com.secondshelf.authservice.exception.advice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private LocalDateTime timestamp;
    private String path;
    private int status;
    private String errorCode;
    private String message;
    private Map<String, Object> details;
}
