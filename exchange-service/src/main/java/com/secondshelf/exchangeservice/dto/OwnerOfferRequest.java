package com.secondshelf.exchangeservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OwnerOfferRequest {
    @NotNull
    private Long offeredBookId;
}
