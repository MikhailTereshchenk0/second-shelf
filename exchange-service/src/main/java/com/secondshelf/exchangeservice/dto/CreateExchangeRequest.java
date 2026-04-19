package com.secondshelf.exchangeservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateExchangeRequest {
    @NotNull
    private Long requestedBookId;

    @NotNull
    private Long offeredBookId;

    @Size(max = 1000)
    private String message;
}
