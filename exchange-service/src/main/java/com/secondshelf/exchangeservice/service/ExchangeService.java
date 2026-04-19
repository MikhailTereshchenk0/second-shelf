package com.secondshelf.exchangeservice.service;

import com.secondshelf.exchangeservice.client.BookServiceClient;
import com.secondshelf.exchangeservice.client.dto.BookDto;
import com.secondshelf.exchangeservice.dto.CreateExchangeRequest;
import com.secondshelf.exchangeservice.dto.ExchangeResponse;
import com.secondshelf.exchangeservice.entity.ExchangeRequest;
import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import com.secondshelf.exchangeservice.repository.ExchangeRepository;
import com.secondshelf.exchangeservice.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ExchangeService {

    private final ExchangeRepository exchangeRepository;
    private final BookServiceClient bookServiceClient;

    public ExchangeResponse create(CreateExchangeRequest req, UserPrincipal principal) {
        Long requesterId = requireUserId(principal);

        if (req.getRequestedBookId().equals(req.getOfferedBookId())) {
            throw new IllegalArgumentException("Requested book and offered book must be different.");
        }

        BookDto requestedBook = bookServiceClient.getBook(req.getRequestedBookId());
        BookDto offeredBook = bookServiceClient.getBook(req.getOfferedBookId());

        validateRequestedBook(requestedBook, requesterId);
        validateOfferedBook(offeredBook, requesterId);

        if (exchangeRepository.existsByRequesterIdAndRequestedBookIdAndOfferedBookIdAndStatusIn(
                requesterId,
                req.getRequestedBookId(),
                req.getOfferedBookId(),
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED)
        )) {
            throw new IllegalArgumentException("Duplicate active exchange request already exists.");
        }

        ExchangeRequest saved = exchangeRepository.save(
                ExchangeRequest.builder()
                        .requestedBookId(req.getRequestedBookId())
                        .offeredBookId(req.getOfferedBookId())
                        .ownerId(requestedBook.getOwnerId())
                        .requesterId(requesterId)
                        .status(ExchangeStatus.PENDING)
                        .message(req.getMessage())
                        .build()
        );

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ExchangeResponse> myOutgoing(UserPrincipal principal, Pageable pageable) {
        return exchangeRepository.findAllByRequesterId(requireUserId(principal), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ExchangeResponse> myIncoming(UserPrincipal principal, Pageable pageable) {
        return exchangeRepository.findAllByOwnerId(requireUserId(principal), pageable)
                .map(this::toResponse);
    }

    public ExchangeResponse accept(Long exchangeId, UserPrincipal principal) {
        ExchangeRequest req = exchangeRepository.findByIdForUpdate(exchangeId)
                .orElseThrow(() -> new IllegalArgumentException("Exchange request not found."));

        Long me = requireUserId(principal);
        if (!me.equals(req.getOwnerId())) {
            throw new IllegalArgumentException("Only owner can accept.");
        }
        if (req.getStatus() != ExchangeStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING request can be accepted.");
        }

        List<Long> bookIds = List.of(req.getRequestedBookId(), req.getOfferedBookId());

        List<ExchangeRequest> activeRequests = exchangeRepository.lockAllActiveByBookIds(
                bookIds,
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED)
        );

        if (exchangeRepository.existsAnotherByStatusAndBookIds(
                req.getId(),
                bookIds,
                ExchangeStatus.ACCEPTED
        )) {
            throw new IllegalArgumentException("One of the books already participates in another accepted exchange.");
        }

        reserveBothBooks(req);

        req.setStatus(ExchangeStatus.ACCEPTED);
        declineConflictingPendingRequests(req.getId(), activeRequests);

        return toResponse(exchangeRepository.save(req));
    }

    public ExchangeResponse decline(Long exchangeId, UserPrincipal principal) {
        ExchangeRequest req = exchangeRepository.findByIdForUpdate(exchangeId)
                .orElseThrow(() -> new IllegalArgumentException("Exchange request not found."));

        Long me = requireUserId(principal);
        if (!me.equals(req.getOwnerId())) {
            throw new IllegalArgumentException("Only owner can decline.");
        }
        if (req.getStatus() != ExchangeStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING request can be declined.");
        }

        req.setStatus(ExchangeStatus.DECLINED);
        return toResponse(exchangeRepository.save(req));
    }

    public ExchangeResponse cancel(Long exchangeId, UserPrincipal principal) {
        ExchangeRequest req = exchangeRepository.findByIdForUpdate(exchangeId)
                .orElseThrow(() -> new IllegalArgumentException("Exchange request not found."));

        Long me = requireUserId(principal);
        if (!me.equals(req.getRequesterId())) {
            throw new IllegalArgumentException("Only requester can cancel.");
        }
        if (req.getStatus() != ExchangeStatus.PENDING && req.getStatus() != ExchangeStatus.ACCEPTED) {
            throw new IllegalArgumentException("Only PENDING or ACCEPTED request can be canceled.");
        }

        if (req.getStatus() == ExchangeStatus.ACCEPTED) {
            releaseBothBooks(req);
        }

        req.setStatus(ExchangeStatus.CANCELLED);

        return toResponse(exchangeRepository.save(req));
    }

    public ExchangeResponse complete(Long exchangeId, UserPrincipal principal) {
        ExchangeRequest req = exchangeRepository.findByIdForUpdate(exchangeId)
                .orElseThrow(() -> new IllegalArgumentException("Exchange request not found."));

        Long me = requireUserId(principal);
        // Для MVP: завершает владелец книги (можно расширить “оба подтверждают” позже)
        if (!me.equals(req.getOwnerId())) {
            throw new IllegalArgumentException("Only owner can complete.");
        }
        if (req.getStatus() != ExchangeStatus.ACCEPTED) {
            throw new IllegalArgumentException("Only ACCEPTED request can be completed.");
        }

        completeBothBooks(req);
        req.setStatus(ExchangeStatus.COMPLETED);

        return toResponse(exchangeRepository.save(req));
    }

    private void reserveBothBooks(ExchangeRequest req) {
        List<Long> reservedBookIds = new ArrayList<>();

        try {
            bookServiceClient.reserve(req.getRequestedBookId());
            reservedBookIds.add(req.getRequestedBookId());

            bookServiceClient.reserve(req.getOfferedBookId());
            reservedBookIds.add(req.getOfferedBookId());
        } catch (RuntimeException e) {
            rollbackReservedBooks(reservedBookIds);
            throw e;
        }
    }

    private void rollbackReservedBooks(List<Long> reservedBookIds) {
        for (int i = reservedBookIds.size() - 1; i >= 0; i--) {
            try {
                bookServiceClient.makeAvailable(reservedBookIds.get(i));
            } catch (RuntimeException rollbackException) {
                // best-effort compensation:
                // exchange request is not accepted, but manual investigation may be required
            }
        }
    }

    private void declineConflictingPendingRequests(Long acceptedExchangeId, List<ExchangeRequest> activeRequests) {
        List<ExchangeRequest> conflictingPendingRequests = activeRequests.stream()
                .filter(r -> !r.getId().equals(acceptedExchangeId))
                .filter(r -> r.getStatus() == ExchangeStatus.PENDING)
                .toList();

        if (conflictingPendingRequests.isEmpty()) {
            return;
        }

        conflictingPendingRequests.forEach(r -> r.setStatus(ExchangeStatus.DECLINED));
        exchangeRepository.saveAll(conflictingPendingRequests);
    }

    private void completeBothBooks(ExchangeRequest req) {
        bookServiceClient.markExchanged(req.getRequestedBookId());
        bookServiceClient.markExchanged(req.getOfferedBookId());
    }

    private void releaseBothBooks(ExchangeRequest req) {
        List<Long> releasedBookIds = new ArrayList<>();

        try {
            bookServiceClient.makeAvailable(req.getRequestedBookId());
            releasedBookIds.add(req.getRequestedBookId());

            bookServiceClient.makeAvailable(req.getOfferedBookId());
            releasedBookIds.add(req.getOfferedBookId());
        } catch (RuntimeException e) {
            rollbackReleasedBooks(releasedBookIds);
            throw e;
        }
    }

    private void rollbackReleasedBooks(List<Long> releasedBookIds) {
        for (int i = releasedBookIds.size() - 1; i >= 0; i--) {
            try {
                bookServiceClient.reserve(releasedBookIds.get(i));
            } catch (RuntimeException rollbackException) {
                // best-effort compensation:
                // exchange request is not canceled, but manual investigation may be required
            }
        }
    }

    private void validateRequestedBook(BookDto requestedBook, Long requesterId) {
        if (requestedBook.getOwnerId().equals(requesterId)) {
            throw new IllegalArgumentException("You cannot request exchange for your own book.");
        }
        if (!"PUBLIC".equals(requestedBook.getVisibility())) {
            throw new IllegalArgumentException("Requested book must be public.");
        }
        if (!"AVAILABLE".equals(requestedBook.getStatus())) {
            throw new IllegalArgumentException("Requested book must be available.");
        }
    }

    private void validateOfferedBook(BookDto offeredBook, Long requesterId) {
        if (!requesterId.equals(offeredBook.getOwnerId())) {
            throw new IllegalArgumentException("Offered book must belong to requester.");
        }
        if (!"PUBLIC".equals(offeredBook.getVisibility())) {
            throw new IllegalArgumentException("Offered book must be public.");
        }
        if (!"AVAILABLE".equals(offeredBook.getStatus())) {
            throw new IllegalArgumentException("Offered book must be available.");
        }
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new IllegalStateException("JWT has no userId or filter is not applied.");
        }
        return principal.userId();
    }

    private ExchangeResponse toResponse(ExchangeRequest r) {
        return ExchangeResponse.builder()
                .id(r.getId())
                .requestedBookId(r.getRequestedBookId())
                .offeredBookId(r.getOfferedBookId())
                .ownerId(r.getOwnerId())
                .requesterId(r.getRequesterId())
                .status(r.getStatus())
                .message(r.getMessage())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
