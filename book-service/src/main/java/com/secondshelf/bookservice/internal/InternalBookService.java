package com.secondshelf.bookservice.internal;

import com.secondshelf.bookservice.entity.Book;
import com.secondshelf.bookservice.exception.BookNotFoundException;
import com.secondshelf.bookservice.repository.BookRepository;
import com.secondshelf.bookservice.service.BookLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InternalBookService {

    private final BookRepository bookRepository;
    private final BookLifecyclePolicy bookLifecyclePolicy;

    public Book get(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
    }

    public Book reserve(Long bookId) {
        Book b = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        bookLifecyclePolicy.assertCanReserve(b);

        b.setStatus(com.secondshelf.bookservice.entity.BookStatus.RESERVED);
        return b;
    }

    public Book makeAvailable(Long bookId) {
        Book b = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        bookLifecyclePolicy.assertCanMakeAvailable(b);

        b.setStatus(com.secondshelf.bookservice.entity.BookStatus.AVAILABLE);
        return b;
    }

    public Book markExchanged(Long bookId) {
        Book b = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        bookLifecyclePolicy.assertCanMarkExchangedInternally(b);

        b.setStatus(com.secondshelf.bookservice.entity.BookStatus.EXCHANGED);
        b.setVisibility(com.secondshelf.bookservice.entity.BookVisibility.PRIVATE);
        return b;
    }
}
