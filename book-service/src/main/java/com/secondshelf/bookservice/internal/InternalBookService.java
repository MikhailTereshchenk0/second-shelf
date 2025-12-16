package com.secondshelf.bookservice.internal;

import com.secondshelf.bookservice.entity.Book;
import com.secondshelf.bookservice.entity.BookStatus;
import com.secondshelf.bookservice.exception.BookNotFoundException;
import com.secondshelf.bookservice.exception.ForbiddenOperationException;
import com.secondshelf.bookservice.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InternalBookService {

    private final BookRepository bookRepository;

    public Book get(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
    }

    public Book reserve(Long bookId) {
        Book b = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        if (b.getStatus() != BookStatus.AVAILABLE) {
            throw new ForbiddenOperationException("Book must be AVAILABLE to reserve.");
        }

        b.setStatus(BookStatus.RESERVED);
        return b;
    }

    public Book makeAvailable(Long bookId) {
        Book b = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        if (b.getStatus() != BookStatus.RESERVED) {
            throw new ForbiddenOperationException("Book must be RESERVED to set AVAILABLE.");
        }

        b.setStatus(BookStatus.AVAILABLE);
        return b;
    }

    public Book markExchanged(Long bookId) {
        Book b = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        if (b.getStatus() != BookStatus.RESERVED) {
            throw new ForbiddenOperationException("Book must be RESERVED to mark as EXCHANGED.");
        }

        b.setStatus(BookStatus.EXCHANGED);
        b.setVisibility(com.secondshelf.bookservice.entity.BookVisibility.PRIVATE);
        return b;
    }
}
