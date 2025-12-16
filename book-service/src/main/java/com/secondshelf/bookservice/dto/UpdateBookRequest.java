package com.secondshelf.bookservice.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateBookRequest {

    @Size(max = 200)
    private String title;

    @Size(max = 200)
    private String author;

    @Size(max = 2000)
    private String description;
}
