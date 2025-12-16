package com.secondshelf.bookservice.controller;

import com.secondshelf.bookservice.dto.BookResponse;
import com.secondshelf.bookservice.dto.CreateBookRequest;
import com.secondshelf.bookservice.dto.UpdateBookRequest;
import com.secondshelf.bookservice.security.UserPrincipal;
import com.secondshelf.bookservice.service.BookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static org.springframework.data.domain.Sort.Direction.DESC;

@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    // каталог “объявлений” (PUBLIC + AVAILABLE/RESERVED)
    @GetMapping("/public")
    public Page<BookResponse> publicCatalog(
            @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable
    ) {
        return bookService.getPublicCatalog(pageable);
    }

    // мои книги
    @GetMapping("/my")
    public Page<BookResponse> myBooks(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable
    ) {
        return bookService.getMyBooks(principal, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse create(
            @Valid @RequestBody CreateBookRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return bookService.create(request, principal);
    }

    @PatchMapping("/{id}")
    public BookResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBookRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return bookService.update(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        bookService.delete(id, principal);
    }

    @GetMapping("/{id}")
    public BookResponse getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return bookService.getById(id, principal);
    }

    // --- отдельные действия с видимостью ---
    @PutMapping("/{id}/publish")
    public BookResponse publish(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return bookService.publish(id, principal);
    }

    @PutMapping("/{id}/hide")
    public BookResponse hide(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return bookService.hide(id, principal);
    }

    // --- статус (пока вручную, позже привяжем к exchange flow) ---
    @PutMapping("/{id}/mark-exchanged")
    public BookResponse markExchanged(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return bookService.markExchanged(id, principal);
    }
}
