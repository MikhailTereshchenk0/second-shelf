package com.secondshelf.exchangeservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateExchangeRequest {
    @NotNull
    private Long requestedBookId;

    @Schema(description = "Deprecated for create requests; requester no longer selects their own book at creation time.", deprecated = true)
    private Long offeredBookId;

    @Size(max = 1000)
    private String message;
}
