package com.secondshelf.bookservice.controller;

import com.secondshelf.bookservice.dto.BookResponse;
import com.secondshelf.bookservice.dto.CreateBookRequest;
import com.secondshelf.bookservice.dto.UpdateBookRequest;
import com.secondshelf.bookservice.security.UserPrincipal;
import com.secondshelf.bookservice.service.BookService;
import com.secondshelf.bookservice.web.PageableSanitizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

import static org.springframework.data.domain.Sort.Direction.DESC;

@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
@Tag(name = "Book API", description = "Book catalog and owner book management endpoints")
public class BookController {

    private static final Set<String> BOOK_SORT_FIELDS = Set.of("createdAt", "updatedAt", "title", "author");

    private final BookService bookService;

    @Operation(
            summary = "Get public catalog",
            description = "Returns paginated public catalog of books that are PUBLIC and AVAILABLE for exchange"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Public catalog returned")
    })
    @GetMapping("/public")
    public Page<BookResponse> publicCatalog(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable
    ) {
        return bookService.getPublicCatalog(PageableSanitizer.sanitize(pageable, BOOK_SORT_FIELDS));
    }

    @Operation(
            summary = "Get my books",
            description = "Returns paginated list of books owned by the authenticated user, including AVAILABLE, RESERVED, and EXCHANGED states"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owned books returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @GetMapping("/my")
    public Page<BookResponse> myBooks(
            @AuthenticationPrincipal UserPrincipal principal,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable
    ) {
        return bookService.getMyBooks(principal, PageableSanitizer.sanitize(pageable, BOOK_SORT_FIELDS));
    }

    @Operation(
            summary = "Create book",
            description = "Creates a new book entry for the authenticated user"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Book created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse create(
            @Valid @RequestBody CreateBookRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return bookService.create(request, principal);
    }

    @Operation(
            summary = "Update book",
            description = "Updates book data for an AVAILABLE book owned by the authenticated user"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @PatchMapping("/{id}")
    public BookResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBookRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return bookService.update(id, request, principal);
    }

    @Operation(
            summary = "Delete book",
            description = "Deletes an AVAILABLE book owned by the authenticated user"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Book deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        bookService.delete(id, principal);
    }

    @Operation(
            summary = "Get book by id",
            description = "Returns a book by id. Owners can access their own books in any state, while non-owners can access only PUBLIC and AVAILABLE books; all other states are hidden as 404."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @GetMapping("/{id}")
    public BookResponse getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return bookService.getById(id, principal);
    }

    @Operation(
            summary = "Publish book",
            description = "Makes an owned AVAILABLE book visible in the public catalog"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book published successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Book not found"),
            @ApiResponse(responseCode = "409", description = "Book state conflict")
    })
    @PutMapping("/{id}/publish")
    public BookResponse publish(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return bookService.publish(id, principal);
    }

    @Operation(
            summary = "Hide book",
            description = "Removes an owned AVAILABLE book from the public catalog"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book hidden successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Book not found"),
            @ApiResponse(responseCode = "409", description = "Book state conflict")
    })
    @PutMapping("/{id}/hide")
    public BookResponse hide(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return bookService.hide(id, principal);
    }

    @Operation(
            summary = "Mark book as exchanged",
            description = "Marks the book as exchanged manually"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book marked as exchanged"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Book not found"),
            @ApiResponse(responseCode = "409", description = "Book state conflict")
    })
    @PutMapping("/{id}/mark-exchanged")
    public BookResponse markExchanged(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return bookService.markExchanged(id, principal);
    }
}
