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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceImplTest {

    private static final UserPrincipal OWNER = new UserPrincipal(42L, "alice");
    private static final UserPrincipal OTHER_USER = new UserPrincipal(99L, "bob");

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookServiceImpl bookService;

    @Test
    void createShouldAssignOwnerAndDefaultPrivateVisibilityWhenVisibilityIsNotProvided() {
        CreateBookRequest request = new CreateBookRequest();
        request.setTitle("Clean Code");
        request.setAuthor("Robert C. Martin");
        request.setDescription("A classic book about software craftsmanship.");

        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> {
            Book book = invocation.getArgument(0);
            book.setId(100L);
            return book;
        });

        ArgumentCaptor<Book> bookCaptor = ArgumentCaptor.forClass(Book.class);

        BookResponse response = bookService.create(request, OWNER);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(42L, response.getOwnerId());
        assertEquals("Clean Code", response.getTitle());
        assertEquals("Robert C. Martin", response.getAuthor());
        assertEquals(BookVisibility.PRIVATE, response.getVisibility());
        assertEquals(BookStatus.AVAILABLE, response.getStatus());

        verify(bookRepository).save(bookCaptor.capture());

        Book savedBook = bookCaptor.getValue();
        assertEquals(42L, savedBook.getOwnerId());
        assertEquals("Clean Code", savedBook.getTitle());
        assertEquals("Robert C. Martin", savedBook.getAuthor());
        assertEquals(BookVisibility.PRIVATE, savedBook.getVisibility());
        assertEquals(BookStatus.AVAILABLE, savedBook.getStatus());
    }

    @Test
    void getByIdShouldReturnPrivateBookForOwner() {
        Book book = ownedBook(1L, BookVisibility.PRIVATE, BookStatus.AVAILABLE);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        BookResponse response = bookService.getById(1L, OWNER);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(42L, response.getOwnerId());
        assertEquals(BookVisibility.PRIVATE, response.getVisibility());
    }

    @Test
    void getByIdShouldHidePrivateBookFromAnotherUserAsNotFound() {
        Book book = ownedBook(2L, BookVisibility.PRIVATE, BookStatus.AVAILABLE);
        when(bookRepository.findById(2L)).thenReturn(Optional.of(book));

        BookNotFoundException exception = assertThrows(
                BookNotFoundException.class,
                () -> bookService.getById(2L, OTHER_USER)
        );

        assertEquals("Book with id = 2 not found.", exception.getMessage());
    }

    @Test
    void getByIdShouldReturnPublicAvailableBookForAnotherUser() {
        Book book = ownedBook(3L, BookVisibility.PUBLIC, BookStatus.AVAILABLE);
        when(bookRepository.findById(3L)).thenReturn(Optional.of(book));

        BookResponse response = bookService.getById(3L, OTHER_USER);

        assertNotNull(response);
        assertEquals(3L, response.getId());
        assertEquals(BookVisibility.PUBLIC, response.getVisibility());
        assertEquals(BookStatus.AVAILABLE, response.getStatus());
    }

    @Test
    void getByIdShouldHideReservedPublicBookFromAnotherUserAsNotFound() {
        Book book = ownedBook(4L, BookVisibility.PUBLIC, BookStatus.RESERVED);
        when(bookRepository.findById(4L)).thenReturn(Optional.of(book));

        assertThrows(BookNotFoundException.class, () -> bookService.getById(4L, OTHER_USER));
    }

    @Test
    void getByIdShouldHideExchangedBookFromAnotherUserAsNotFound() {
        Book book = ownedBook(5L, BookVisibility.PUBLIC, BookStatus.EXCHANGED);
        when(bookRepository.findById(5L)).thenReturn(Optional.of(book));

        assertThrows(BookNotFoundException.class, () -> bookService.getById(5L, OTHER_USER));
    }

    @Test
    void updateShouldThrowAccessDeniedWhenBookBelongsToAnotherUser() {
        Book book = ownedBook(6L, BookVisibility.PUBLIC, BookStatus.AVAILABLE);
        UpdateBookRequest request = new UpdateBookRequest();
        request.setTitle("Updated Title");

        when(bookRepository.findById(6L)).thenReturn(Optional.of(book));

        BookAccessDeniedException exception = assertThrows(
                BookAccessDeniedException.class,
                () -> bookService.update(6L, request, OTHER_USER)
        );

        assertEquals("Access denied for book id = 6", exception.getMessage());
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void reservedBookShouldNotBeUpdatedByOwner() {
        Book book = ownedBook(7L, BookVisibility.PUBLIC, BookStatus.RESERVED);
        UpdateBookRequest request = new UpdateBookRequest();
        request.setTitle("Updated");

        when(bookRepository.findById(7L)).thenReturn(Optional.of(book));

        BookStateConflictException exception = assertThrows(
                BookStateConflictException.class,
                () -> bookService.update(7L, request, OWNER)
        );

        assertEquals(
                "Book must be AVAILABLE for owner-side update/delete/publish/hide operations.",
                exception.getMessage()
        );
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void reservedBookShouldNotBeDeletedByOwner() {
        Book book = ownedBook(8L, BookVisibility.PUBLIC, BookStatus.RESERVED);
        when(bookRepository.findById(8L)).thenReturn(Optional.of(book));

        assertThrows(BookStateConflictException.class, () -> bookService.delete(8L, OWNER));

        verify(bookRepository, never()).delete(any(Book.class));
    }

    @Test
    void reservedBookShouldNotBePublishedByOwner() {
        Book book = ownedBook(9L, BookVisibility.PRIVATE, BookStatus.RESERVED);
        when(bookRepository.findById(9L)).thenReturn(Optional.of(book));

        assertThrows(BookStateConflictException.class, () -> bookService.publish(9L, OWNER));

        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void reservedBookShouldNotBeHiddenByOwner() {
        Book book = ownedBook(10L, BookVisibility.PUBLIC, BookStatus.RESERVED);
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));

        assertThrows(BookStateConflictException.class, () -> bookService.hide(10L, OWNER));

        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void exchangedBookShouldNotBeUpdatedByOwner() {
        Book book = ownedBook(11L, BookVisibility.PRIVATE, BookStatus.EXCHANGED);
        UpdateBookRequest request = new UpdateBookRequest();
        request.setTitle("Updated");

        when(bookRepository.findById(11L)).thenReturn(Optional.of(book));

        assertThrows(BookStateConflictException.class, () -> bookService.update(11L, request, OWNER));

        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void exchangedBookShouldNotBeDeletedByOwner() {
        Book book = ownedBook(12L, BookVisibility.PRIVATE, BookStatus.EXCHANGED);
        when(bookRepository.findById(12L)).thenReturn(Optional.of(book));

        assertThrows(BookStateConflictException.class, () -> bookService.delete(12L, OWNER));

        verify(bookRepository, never()).delete(any(Book.class));
    }

    @Test
    void exchangedBookShouldNotBePublishedByOwner() {
        Book book = ownedBook(13L, BookVisibility.PRIVATE, BookStatus.EXCHANGED);
        when(bookRepository.findById(13L)).thenReturn(Optional.of(book));

        assertThrows(BookStateConflictException.class, () -> bookService.publish(13L, OWNER));

        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void exchangedBookShouldNotBeHiddenByOwner() {
        Book book = ownedBook(14L, BookVisibility.PRIVATE, BookStatus.EXCHANGED);
        when(bookRepository.findById(14L)).thenReturn(Optional.of(book));

        assertThrows(BookStateConflictException.class, () -> bookService.hide(14L, OWNER));

        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void deleteShouldRemoveOwnedAvailableBook() {
        Book book = ownedBook(15L, BookVisibility.PUBLIC, BookStatus.AVAILABLE);
        when(bookRepository.findById(15L)).thenReturn(Optional.of(book));

        bookService.delete(15L, OWNER);

        verify(bookRepository).delete(book);
    }

    @Test
    void publicCatalogShouldQueryOnlyPublicAvailableBooks() {
        Book availableBook = ownedBook(16L, BookVisibility.PUBLIC, BookStatus.AVAILABLE);

        when(bookRepository.findAllByVisibilityAndStatusIn(
                eq(BookVisibility.PUBLIC),
                eq(List.of(BookStatus.AVAILABLE)),
                any()
        )).thenReturn(new PageImpl<>(List.of(availableBook), PageRequest.of(0, 20), 1));

        var response = bookService.getPublicCatalog(PageRequest.of(0, 20));

        assertEquals(1, response.getTotalElements());
        assertEquals(BookStatus.AVAILABLE, response.getContent().get(0).getStatus());
        verify(bookRepository).findAllByVisibilityAndStatusIn(
                BookVisibility.PUBLIC,
                List.of(BookStatus.AVAILABLE),
                PageRequest.of(0, 20)
        );
    }

    @Test
    void ownerMarkExchangedShouldAlsoHideBook() {
        Book book = ownedBook(17L, BookVisibility.PUBLIC, BookStatus.RESERVED);
        when(bookRepository.findById(17L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookResponse response = bookService.markExchanged(17L, OWNER);

        assertEquals(BookStatus.EXCHANGED, response.getStatus());
        assertEquals(BookVisibility.PRIVATE, response.getVisibility());
    }

    private Book ownedBook(Long id, BookVisibility visibility, BookStatus status) {
        return Book.builder()
                .id(id)
                .ownerId(OWNER.userId())
                .title("Book " + id)
                .author("Author")
                .description("Description")
                .visibility(visibility)
                .status(status)
                .build();
    }
}
