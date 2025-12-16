package com.secondshelf.bookservice.service;

import com.secondshelf.bookservice.dto.BookResponse;
import com.secondshelf.bookservice.dto.CreateBookRequest;
import com.secondshelf.bookservice.dto.UpdateBookRequest;
import com.secondshelf.bookservice.entity.Book;
import com.secondshelf.bookservice.entity.BookStatus;
import com.secondshelf.bookservice.entity.BookVisibility;
import com.secondshelf.bookservice.exception.BookAccessDeniedException;
import com.secondshelf.bookservice.exception.BookNotFoundException;
import com.secondshelf.bookservice.exception.BookStateConflictException;
import com.secondshelf.bookservice.repository.BookRepository;
import com.secondshelf.bookservice.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;

    @Override
    public BookResponse create(CreateBookRequest request, UserPrincipal principal) {
        BookVisibility visibility = request.getVisibility() == null
                ? BookVisibility.PRIVATE
                : request.getVisibility();

        Book book = Book.builder()
                .ownerId(requireUserId(principal))
                .title(request.getTitle())
                .author(request.getAuthor())
                .description(request.getDescription())
                .visibility(visibility)
                .status(BookStatus.AVAILABLE)
                .build();

        return toResponse(bookRepository.save(book));
    }

    @Override
    public BookResponse update(Long bookId, UpdateBookRequest request, UserPrincipal principal) {
        Book book = getOwnedBook(bookId, principal);
        assertNotExchanged(book);

        if (request.getTitle() != null) book.setTitle(request.getTitle());
        if (request.getAuthor() != null) book.setAuthor(request.getAuthor());
        if (request.getDescription() != null) book.setDescription(request.getDescription());

        return toResponse(bookRepository.save(book));
    }

    @Override
    public void delete(Long bookId, UserPrincipal principal) {
        Book book = getOwnedBook(bookId, principal);
        assertNotExchanged(book);
        bookRepository.delete(book);
    }

    @Override
    @Transactional(readOnly = true)
    public BookResponse getById(Long bookId, UserPrincipal principal) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        Long userId = requireUserId(principal);

        // владелец видит всегда
        if (userId.equals(book.getOwnerId())) {
            return toResponse(book);
        }

        // остальные видят только PUBLIC
        if (book.getVisibility() == BookVisibility.PUBLIC) {
            return toResponse(book);
        }

        // приватное скрываем как 404
        throw new BookNotFoundException(bookId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookResponse> getMyBooks(UserPrincipal principal, Pageable pageable) {
        return bookRepository.findAllByOwnerId(requireUserId(principal), pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookResponse> getPublicCatalog(Pageable pageable) {
        return bookRepository.findAllByVisibilityAndStatusIn(
                        BookVisibility.PUBLIC,
                        List.of(BookStatus.AVAILABLE, BookStatus.RESERVED),
                        pageable
                )
                .map(this::toResponse);
    }

    @Override
    public BookResponse publish(Long bookId, UserPrincipal principal) {
        Book book = getOwnedBook(bookId, principal);
        assertNotExchanged(book);

        book.setVisibility(BookVisibility.PUBLIC);
        return toResponse(bookRepository.save(book));
    }

    @Override
    public BookResponse hide(Long bookId, UserPrincipal principal) {
        Book book = getOwnedBook(bookId, principal);
        assertNotExchanged(book);

        book.setVisibility(BookVisibility.PRIVATE);
        return toResponse(bookRepository.save(book));
    }

    @Override
    public BookResponse markExchanged(Long bookId, UserPrincipal principal) {
        Book book = getOwnedBook(bookId, principal);

        if (book.getStatus() != BookStatus.RESERVED) {
            throw new BookStateConflictException("Book must be RESERVED to mark as EXCHANGED.");
        }

        book.setStatus(BookStatus.EXCHANGED);
        return toResponse(bookRepository.save(book));
    }

    private Book getOwnedBook(Long bookId, UserPrincipal principal) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        if (!requireUserId(principal).equals(book.getOwnerId())) {
            throw new BookAccessDeniedException(bookId);
        }
        return book;
    }

    private void assertNotExchanged(Book book) {
        if (book.getStatus() == BookStatus.EXCHANGED) {
            throw new BookStateConflictException("Book is already EXCHANGED and cannot be modified.");
        }
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new IllegalStateException("JWT has no userId or JWT filter is not applied.");
        }
        return principal.userId();
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
