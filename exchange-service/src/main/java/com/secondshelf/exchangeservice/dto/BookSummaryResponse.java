package com.secondshelf.exchangeservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookSummaryResponse {
    private Long id;
    private Long ownerId;
    private String title;
    private String author;
    private String visibility;
    private String status;
}
