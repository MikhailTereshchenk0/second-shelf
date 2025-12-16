package com.secondshelf.exchangeservice.dto;

import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ExchangeResponse {
    private Long id;
    private Long requestedBookId;
    private Long ownerId;
    private Long requesterId;
    private ExchangeStatus status;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
