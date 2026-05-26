package com.secondshelf.bookservice.internal;

import com.secondshelf.bookservice.entity.Book;
import com.secondshelf.bookservice.entity.BookStatus;
import com.secondshelf.bookservice.entity.BookVisibility;
import com.secondshelf.bookservice.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalBookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private InternalBookService internalBookService;

    @Test
    void markExchangedShouldHideReservedBook() {
        Book book = Book.builder()
                .id(101L)
                .ownerId(42L)
                .title("Reserved Book")
                .author("Author")
                .visibility(BookVisibility.PUBLIC)
                .status(BookStatus.RESERVED)
                .build();

        when(bookRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(book));

        Book updated = internalBookService.markExchanged(101L);

        assertEquals(BookStatus.EXCHANGED, updated.getStatus());
        assertEquals(BookVisibility.PRIVATE, updated.getVisibility());
    }
}
