package com.secondshelf.bookservice.dto;

import com.secondshelf.bookservice.entity.BookVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateBookRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(max = 200)
    private String author;

    @Size(max = 2000)
    private String description;

    private BookVisibility visibility;
}
