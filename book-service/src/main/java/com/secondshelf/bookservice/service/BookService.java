package com.secondshelf.bookservice.service;

import com.secondshelf.bookservice.dto.BookResponse;
import com.secondshelf.bookservice.dto.CreateBookRequest;
import com.secondshelf.bookservice.dto.UpdateBookRequest;
import com.secondshelf.bookservice.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookService {
    BookResponse create(CreateBookRequest request, UserPrincipal principal);
    BookResponse update(Long bookId, UpdateBookRequest request, UserPrincipal principal);
    void delete(Long bookId, UserPrincipal principal);

    BookResponse getById(Long bookId, UserPrincipal principal);

    Page<BookResponse> getMyBooks(UserPrincipal principal, Pageable pageable);
    Page<BookResponse> getPublicCatalog(Pageable pageable);

    BookResponse publish(Long bookId, UserPrincipal principal);
    BookResponse hide(Long bookId, UserPrincipal principal);

    BookResponse markExchanged(Long bookId, UserPrincipal principal);
}
