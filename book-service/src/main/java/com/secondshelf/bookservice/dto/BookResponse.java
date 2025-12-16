package com.secondshelf.bookservice.dto;

import com.secondshelf.bookservice.entity.BookStatus;
import com.secondshelf.bookservice.entity.BookVisibility;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BookResponse {
    private Long id;
    private Long ownerId;
    private String title;
    private String author;
    private String description;
    private BookVisibility visibility;
    private BookStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
