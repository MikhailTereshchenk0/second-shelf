package com.secondshelf.exchangeservice.repository;

import com.secondshelf.exchangeservice.entity.ExchangeRequest;
import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExchangeRepository extends JpaRepository<ExchangeRequest, Long> {

    Page<ExchangeRequest> findAllByRequesterId(Long requesterId, Pageable pageable);
    Page<ExchangeRequest> findAllByOwnerId(Long ownerId, Pageable pageable);

    boolean existsByRequestedBookIdAndStatus(Long requestedBookId, ExchangeStatus status);

    boolean existsByRequesterIdAndRequestedBookIdAndOfferedBookIdAndStatusIn(
            Long requesterId,
            Long requestedBookId,
            Long offeredBookId,
            Collection<ExchangeStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ExchangeRequest r where r.id = :id")
    Optional<ExchangeRequest> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ExchangeRequest r where r.requestedBookId = :bookId and r.status in :statuses")
    List<ExchangeRequest> lockAllByRequestedBookIdAndStatuses(@Param("bookId") Long bookId,
                                                              @Param("statuses") Collection<ExchangeStatus> statuses);
}
