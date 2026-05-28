package com.secondshelf.exchangeservice.dto;

import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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
    private String ownerPhoneNumber;
    private String requesterPhoneNumber;
    private List<BookSummaryResponse> requesterAvailableBooks;
    private ExchangeStatus status;
    private String message;
    private LocalDateTime ownerCompletionConfirmedAt;
    private LocalDateTime requesterCompletionConfirmedAt;
    private String repairReason;
    private LocalDateTime repairRequiredAt;
    private Integer repairAttempts;
    private LocalDateTime lastRepairAttemptAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
