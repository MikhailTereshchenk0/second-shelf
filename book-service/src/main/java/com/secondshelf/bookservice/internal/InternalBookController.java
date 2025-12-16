package com.secondshelf.bookservice.internal;

import com.secondshelf.bookservice.dto.BookResponse;
import com.secondshelf.bookservice.entity.Book;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/books")
@RequiredArgsConstructor
public class InternalBookController {

    private final InternalBookService internalBookService;

    @GetMapping("/{id}")
    public BookResponse get(@PathVariable Long id) {
        return toResponse(internalBookService.get(id));
    }

    @PostMapping("/{id}/reserve")
    public BookResponse reserve(@PathVariable Long id) {
        return toResponse(internalBookService.reserve(id));
    }

    @PostMapping("/{id}/available")
    public BookResponse available(@PathVariable Long id) {
        return toResponse(internalBookService.makeAvailable(id));
    }

    @PostMapping("/{id}/exchanged")
    public BookResponse exchanged(@PathVariable Long id) {
        return toResponse(internalBookService.markExchanged(id));
    }

    private BookResponse toResponse(Book b) {
        return BookResponse.builder()
                .id(b.getId())
                .ownerId(b.getOwnerId())
                .title(b.getTitle())
                .author(b.getAuthor())
                .description(b.getDescription())
                .visibility(b.getVisibility())
                .status(b.getStatus())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }
}
