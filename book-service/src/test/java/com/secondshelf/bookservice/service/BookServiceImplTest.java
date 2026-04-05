package com.secondshelf.bookservice.service;

import com.secondshelf.bookservice.dto.BookResponse;
import com.secondshelf.bookservice.dto.CreateBookRequest;
import com.secondshelf.bookservice.dto.UpdateBookRequest;
import com.secondshelf.bookservice.entity.Book;
import com.secondshelf.bookservice.entity.BookStatus;
import com.secondshelf.bookservice.entity.BookVisibility;
import com.secondshelf.bookservice.exception.BookAccessDeniedException;
import com.secondshelf.bookservice.exception.BookNotFoundException;
import com.secondshelf.bookservice.repository.BookRepository;
import com.secondshelf.bookservice.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceImplTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookServiceImpl bookService;

    @Test
    void createShouldAssignOwnerAndDefaultPrivateVisibilityWhenVisibilityIsNotProvided() {
        // arrange
        CreateBookRequest request = new CreateBookRequest();
        request.setTitle("Clean Code");
        request.setAuthor("Robert C. Martin");
        request.setDescription("A classic book about software craftsmanship.");

        UserPrincipal principal = new UserPrincipal(42L, "alice");

        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> {
            Book book = invocation.getArgument(0);
            book.setId(100L);
            return book;
        });

        ArgumentCaptor<Book> bookCaptor = ArgumentCaptor.forClass(Book.class);

        // act
        BookResponse response = bookService.create(request, principal);

        // assert
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
        // arrange
        Book book = Book.builder()
                .id(1L)
                .ownerId(42L)
                .title("Private Book")
                .author("Author")
                .visibility(BookVisibility.PRIVATE)
                .status(BookStatus.AVAILABLE)
                .build();

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        // act
        BookResponse response = bookService.getById(1L, new UserPrincipal(42L, "alice"));

        // assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(42L, response.getOwnerId());
        assertEquals(BookVisibility.PRIVATE, response.getVisibility());
    }

    @Test
    void getByIdShouldHidePrivateBookFromAnotherUserAsNotFound() {
        // arrange
        Book book = Book.builder()
                .id(2L)
                .ownerId(42L)
                .title("Private Book")
                .author("Author")
                .visibility(BookVisibility.PRIVATE)
                .status(BookStatus.AVAILABLE)
                .build();

        when(bookRepository.findById(2L)).thenReturn(Optional.of(book));

        // act
        BookNotFoundException exception = assertThrows(
                BookNotFoundException.class,
                () -> bookService.getById(2L, new UserPrincipal(99L, "bob"))
        );

        // assert
        assertEquals("Book with id = 2 not found.", exception.getMessage());
    }

    @Test
    void getByIdShouldReturnPublicBookForAnotherUser() {
        // arrange
        Book book = Book.builder()
                .id(3L)
                .ownerId(42L)
                .title("Public Book")
                .author("Author")
                .visibility(BookVisibility.PUBLIC)
                .status(BookStatus.AVAILABLE)
                .build();

        when(bookRepository.findById(3L)).thenReturn(Optional.of(book));

        // act
        BookResponse response = bookService.getById(3L, new UserPrincipal(99L, "bob"));

        // assert
        assertNotNull(response);
        assertEquals(3L, response.getId());
        assertEquals(BookVisibility.PUBLIC, response.getVisibility());
    }

    @Test
    void updateShouldThrowAccessDeniedWhenBookBelongsToAnotherUser() {
        // arrange
        Book book = Book.builder()
                .id(4L)
                .ownerId(42L)
                .title("Original Title")
                .author("Author")
                .visibility(BookVisibility.PUBLIC)
                .status(BookStatus.AVAILABLE)
                .build();

        UpdateBookRequest request = new UpdateBookRequest();
        request.setTitle("Updated Title");

        when(bookRepository.findById(4L)).thenReturn(Optional.of(book));

        // act
        BookAccessDeniedException exception = assertThrows(
                BookAccessDeniedException.class,
                () -> bookService.update(4L, request, new UserPrincipal(99L, "bob"))
        );

        // assert
        assertEquals("Access denied for book id = 4", exception.getMessage());
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void deleteShouldRemoveOwnedBook() {
        // arrange
        Book book = Book.builder()
                .id(5L)
                .ownerId(42L)
                .title("Owned Book")
                .author("Author")
                .visibility(BookVisibility.PUBLIC)
                .status(BookStatus.AVAILABLE)
                .build();

        when(bookRepository.findById(5L)).thenReturn(Optional.of(book));

        // act
        bookService.delete(5L, new UserPrincipal(42L, "alice"));

        // assert
        verify(bookRepository).delete(book);
    }
}