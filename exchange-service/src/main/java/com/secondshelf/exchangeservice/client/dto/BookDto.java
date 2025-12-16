package com.secondshelf.exchangeservice.client.dto;

import lombok.Data;

@Data
public class BookDto {
    private Long id;
    private Long ownerId;
    private String visibility; // PUBLIC/PRIVATE
    private String status;     // AVAILABLE/RESERVED/EXCHANGED
}
