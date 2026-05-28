package com.secondshelf.bookservice.repository;

import com.secondshelf.bookservice.entity.Book;
import com.secondshelf.bookservice.entity.BookStatus;
import com.secondshelf.bookservice.entity.BookVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;


import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    Page<Book> findAllByOwnerId(Long ownerId, Pageable pageable);

    List<Book> findAllByOwnerIdAndVisibilityAndStatusOrderByCreatedAtDesc(
            Long ownerId,
            BookVisibility visibility,
            BookStatus status
    );

    Page<Book> findAllByVisibilityAndStatusIn(
            BookVisibility visibility,
            Collection<BookStatus> statuses,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id = :id")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);
}
