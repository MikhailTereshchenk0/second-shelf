package com.secondshelf.exchangeservice.service;

import com.secondshelf.exchangeservice.client.BookServiceClient;
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

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ExchangeService {

    private final ExchangeRepository exchangeRepository;
    private final BookServiceClient bookServiceClient;

    public ExchangeResponse create(CreateExchangeRequest req, UserPrincipal principal) {
        Long requesterId = requireUserId(principal);

        var book = bookServiceClient.getBook(req.getRequestedBookId());

        if (book.getOwnerId().equals(requesterId)) {
            throw new IllegalArgumentException("You cannot request exchange for your own book.");
        }
        if (!"PUBLIC".equals(book.getVisibility())) {
            throw new IllegalArgumentException("Book is not public.");
        }
        if (!"AVAILABLE".equals(book.getStatus())) {
            throw new IllegalArgumentException("Book is not available.");
        }

        ExchangeRequest saved = exchangeRepository.save(
                ExchangeRequest.builder()
                        .requestedBookId(req.getRequestedBookId())
                        .offeredBookId(req.getOfferedBookId())
                        .ownerId(book.getOwnerId())
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

        // Лочим все pending/accepted по этой книге, чтобы не приняли дважды одновременно
        exchangeRepository.lockAllByRequestedBookIdAndStatuses(
                req.getRequestedBookId(),
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED)
        );

        if (exchangeRepository.existsByRequestedBookIdAndStatus(req.getRequestedBookId(), ExchangeStatus.ACCEPTED)) {
            throw new IllegalArgumentException("This book already has an accepted exchange request.");
        }

        // reserve книгу в book-service (внутренним токеном)
        bookServiceClient.reserve(req.getRequestedBookId());

        req.setStatus(ExchangeStatus.ACCEPTED);

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
        if (req.getStatus() != ExchangeStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING request can be cancelled.");
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

        bookServiceClient.markExchanged(req.getRequestedBookId());
        req.setStatus(ExchangeStatus.COMPLETED);

        return toResponse(exchangeRepository.save(req));
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
