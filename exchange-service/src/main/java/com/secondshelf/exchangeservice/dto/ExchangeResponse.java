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
    private String requestedBookTitle;
    private String requestedBookAuthor;
    private Long offeredBookId;
    private String offeredBookTitle;
    private String offeredBookAuthor;
    private Long ownerId;
    private Long requesterId;
    private String ownerUsernameSnapshot;
    private String requesterUsernameSnapshot;
    private ExchangeStatus status;
    private String message;
    private LocalDateTime ownerCompletionConfirmedAt;
    private LocalDateTime requesterCompletionConfirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
