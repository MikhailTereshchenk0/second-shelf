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
import com.secondshelf.observability.AuditEvent;
import com.secondshelf.observability.AuditLogger;
import com.secondshelf.observability.AuditOutcome;
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

    private static final AuditLogger AUDIT_LOGGER = AuditLogger.forClass(BookServiceImpl.class);

    private final BookRepository bookRepository;
    private final BookLifecyclePolicy bookLifecyclePolicy;

    @Override
    public BookResponse create(CreateBookRequest request, UserPrincipal principal) {
        Long actorUserId = principal != null ? principal.userId() : null;

        try {
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

            BookResponse response = toResponse(bookRepository.save(book));
            AUDIT_LOGGER.log(AuditEvent.builder("BOOK_CREATE", AuditOutcome.SUCCESS)
                    .actorUserId(actorUserId)
                    .targetUserId(response.getOwnerId())
                    .entityId(response.getId())
                    .build());
            return response;
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("BOOK_CREATE", AuditOutcome.FAILURE)
                    .actorUserId(actorUserId)
                    .targetUserId(actorUserId)
                    .reason(ex.getMessage())
                    .build());
            throw ex;
        }
    }

    @Override
    public BookResponse update(Long bookId, UpdateBookRequest request, UserPrincipal principal) {
        Long actorUserId = principal != null ? principal.userId() : null;

        try {
            Book book = getOwnedBook(bookId, principal);
            bookLifecyclePolicy.assertCanModifyByOwner(book);

            if (request.getTitle() != null) book.setTitle(request.getTitle());
            if (request.getAuthor() != null) book.setAuthor(request.getAuthor());
            if (request.getDescription() != null) book.setDescription(request.getDescription());

            BookResponse response = toResponse(bookRepository.save(book));
            AUDIT_LOGGER.log(AuditEvent.builder("BOOK_UPDATE", AuditOutcome.SUCCESS)
                    .actorUserId(actorUserId)
                    .targetUserId(response.getOwnerId())
                    .entityId(bookId)
                    .build());
            return response;
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("BOOK_UPDATE", AuditOutcome.FAILURE)
                    .actorUserId(actorUserId)
                    .targetUserId(actorUserId)
                    .entityId(bookId)
                    .reason(ex.getMessage())
                    .build());
            throw ex;
        }
    }

    @Override
    public void delete(Long bookId, UserPrincipal principal) {
        Long actorUserId = principal != null ? principal.userId() : null;

        try {
            Book book = getOwnedBook(bookId, principal);
            bookLifecyclePolicy.assertCanDelete(book);
            bookRepository.delete(book);

            AUDIT_LOGGER.log(AuditEvent.builder("BOOK_DELETE", AuditOutcome.SUCCESS)
                    .actorUserId(actorUserId)
                    .targetUserId(book.getOwnerId())
                    .entityId(bookId)
                    .build());
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("BOOK_DELETE", AuditOutcome.FAILURE)
                    .actorUserId(actorUserId)
                    .targetUserId(actorUserId)
                    .entityId(bookId)
                    .reason(ex.getMessage())
                    .build());
            throw ex;
        }
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

        bookLifecyclePolicy.assertCanBeVisibleInPublicView(book);
        return toResponse(book);
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
                        List.of(BookStatus.AVAILABLE),
                        pageable
                )
                .map(this::toResponse);
    }

    @Override
    public BookResponse publish(Long bookId, UserPrincipal principal) {
        Long actorUserId = principal != null ? principal.userId() : null;

        try {
            Book book = getOwnedBook(bookId, principal);
            bookLifecyclePolicy.assertCanPublish(book);

            book.setVisibility(BookVisibility.PUBLIC);
            BookResponse response = toResponse(bookRepository.save(book));
            AUDIT_LOGGER.log(AuditEvent.builder("BOOK_PUBLISH", AuditOutcome.SUCCESS)
                    .actorUserId(actorUserId)
                    .targetUserId(response.getOwnerId())
                    .entityId(bookId)
                    .build());
            return response;
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("BOOK_PUBLISH", AuditOutcome.FAILURE)
                    .actorUserId(actorUserId)
                    .targetUserId(actorUserId)
                    .entityId(bookId)
                    .reason(ex.getMessage())
                    .build());
            throw ex;
        }
    }

    @Override
    public BookResponse hide(Long bookId, UserPrincipal principal) {
        Long actorUserId = principal != null ? principal.userId() : null;

        try {
            Book book = getOwnedBook(bookId, principal);
            bookLifecyclePolicy.assertCanHide(book);

            book.setVisibility(BookVisibility.PRIVATE);
            BookResponse response = toResponse(bookRepository.save(book));
            AUDIT_LOGGER.log(AuditEvent.builder("BOOK_HIDE", AuditOutcome.SUCCESS)
                    .actorUserId(actorUserId)
                    .targetUserId(response.getOwnerId())
                    .entityId(bookId)
                    .build());
            return response;
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("BOOK_HIDE", AuditOutcome.FAILURE)
                    .actorUserId(actorUserId)
                    .targetUserId(actorUserId)
                    .entityId(bookId)
                    .reason(ex.getMessage())
                    .build());
            throw ex;
        }
    }

    @Override
    public BookResponse markExchanged(Long bookId, UserPrincipal principal) {
        Long actorUserId = principal != null ? principal.userId() : null;

        try {
            Book book = getOwnedBook(bookId, principal);

            bookLifecyclePolicy.assertCanMarkExchangedForOwner(book);

            book.setStatus(BookStatus.EXCHANGED);
            book.setVisibility(BookVisibility.PRIVATE);
            BookResponse response = toResponse(bookRepository.save(book));
            AUDIT_LOGGER.log(AuditEvent.builder("BOOK_MARK_EXCHANGED", AuditOutcome.SUCCESS)
                    .actorUserId(actorUserId)
                    .targetUserId(response.getOwnerId())
                    .entityId(bookId)
                    .build());
            return response;
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("BOOK_MARK_EXCHANGED", AuditOutcome.FAILURE)
                    .actorUserId(actorUserId)
                    .targetUserId(actorUserId)
                    .entityId(bookId)
                    .reason(ex.getMessage())
                    .build());
            throw ex;
        }
    }

    private Book getOwnedBook(Long bookId, UserPrincipal principal) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        if (!requireUserId(principal).equals(book.getOwnerId())) {
            throw new BookAccessDeniedException(bookId);
        }
        return book;
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
