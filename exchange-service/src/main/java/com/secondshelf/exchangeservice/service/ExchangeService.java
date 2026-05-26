package com.secondshelf.exchangeservice.service;

import com.secondshelf.exchangeservice.client.BookServiceClient;
import com.secondshelf.exchangeservice.client.dto.BookDto;
import com.secondshelf.exchangeservice.dto.CreateExchangeRequest;
import com.secondshelf.exchangeservice.dto.ExchangeResponse;
import com.secondshelf.exchangeservice.entity.ExchangeRequest;
import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import com.secondshelf.exchangeservice.exception.ExchangeBadRequestException;
import com.secondshelf.exchangeservice.exception.ExchangeConflictException;
import com.secondshelf.exchangeservice.exception.ExchangeException;
import com.secondshelf.exchangeservice.exception.ExchangeForbiddenException;
import com.secondshelf.exchangeservice.exception.ExchangeNotFoundException;
import com.secondshelf.exchangeservice.outbox.ExchangeEventContext;
import com.secondshelf.exchangeservice.outbox.ExchangeEventType;
import com.secondshelf.exchangeservice.outbox.ExchangeOutboxService;
import com.secondshelf.exchangeservice.repository.ExchangeRepository;
import com.secondshelf.exchangeservice.security.UserPrincipal;
import com.secondshelf.observability.AuditEvent;
import com.secondshelf.observability.AuditLogger;
import com.secondshelf.observability.AuditOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ExchangeService {

    private static final AuditLogger AUDIT_LOGGER = AuditLogger.forClass(ExchangeService.class);

    private static final List<ExchangeStatus> ACTIVE_EXCHANGE_STATUSES = List.of(
            ExchangeStatus.PENDING,
            ExchangeStatus.ACCEPTED,
            ExchangeStatus.COMPLETION_PENDING
    );

    private static final List<ExchangeStatus> COMPLETION_ELIGIBLE_STATUSES = List.of(
            ExchangeStatus.ACCEPTED,
            ExchangeStatus.COMPLETION_PENDING
    );

    private final ExchangeRepository exchangeRepository;
    private final BookServiceClient bookServiceClient;
    private final ExchangeOutboxService exchangeOutboxService;

    public ExchangeResponse create(CreateExchangeRequest req, UserPrincipal principal) {
        Long requesterId = requireUserId(principal);

        try {
            if (req.getRequestedBookId().equals(req.getOfferedBookId())) {
                throw new ExchangeBadRequestException(
                        "INVALID_EXCHANGE_BOOK_SELECTION",
                        "Requested book and offered book must be different."
                );
            }

            BookDto requestedBook = getRequestedBook(req.getRequestedBookId());
            BookDto offeredBook = getOfferedBook(req.getOfferedBookId());

            validateRequestedBook(requestedBook, requesterId);
            validateOfferedBook(offeredBook, requesterId);

            if (exchangeRepository.existsByRequesterIdAndRequestedBookIdAndOfferedBookIdAndStatusIn(
                    requesterId,
                    req.getRequestedBookId(),
                    req.getOfferedBookId(),
                    ACTIVE_EXCHANGE_STATUSES
            )) {
                throw new ExchangeConflictException(
                        "DUPLICATE_ACTIVE_EXCHANGE_REQUEST",
                        "Duplicate active exchange request already exists."
                );
            }

            ExchangeRequest saved = exchangeRepository.save(
                    ExchangeRequest.builder()
                            .requestedBookId(req.getRequestedBookId())
                            .requestedBookTitle(requestedBook.getTitle())
                            .requestedBookAuthor(requestedBook.getAuthor())
                            .offeredBookId(req.getOfferedBookId())
                            .offeredBookTitle(offeredBook.getTitle())
                            .offeredBookAuthor(offeredBook.getAuthor())
                            .ownerId(requestedBook.getOwnerId())
                            .requesterId(requesterId)
                            .status(ExchangeStatus.PENDING)
                            .message(req.getMessage())
                            .build()
            );

            exchangeOutboxService.recordExchangeEvent(
                    ExchangeEventType.EXCHANGE_REQUEST_CREATED,
                    saved,
                    eventContext(principal)
            );

            ExchangeResponse response = toResponse(saved);
            AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_CREATE", AuditOutcome.SUCCESS)
                    .actorUserId(requesterId)
                    .targetUserId(response.getOwnerId())
                    .entityId(response.getId())
                    .attribute("status", response.getStatus())
                    .build());
            return response;
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_CREATE", AuditOutcome.FAILURE)
                    .actorUserId(requesterId)
                    .entityId(null)
                    .reason(ex.getMessage())
                    .errorCode(resolveErrorCode(ex))
                    .build());
            throw ex;
        }
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
        Long me = requireUserId(principal);

        try {
            ExchangeRequest req = findExchangeRequestForUpdate(exchangeId);

            if (!me.equals(req.getOwnerId())) {
                throw new ExchangeForbiddenException("ONLY_OWNER_CAN_ACCEPT", "Only owner can accept.");
            }
            if (req.getStatus() != ExchangeStatus.PENDING) {
                throw new ExchangeConflictException(
                        "INVALID_EXCHANGE_STATUS_TRANSITION",
                        "Only PENDING request can be accepted."
                );
            }

            List<Long> bookIds = List.of(req.getRequestedBookId(), req.getOfferedBookId());

            List<ExchangeRequest> activeRequests = exchangeRepository.lockAllActiveByBookIds(
                    bookIds,
                    ACTIVE_EXCHANGE_STATUSES
            );

            if (exchangeRepository.existsAnotherByStatusesAndBookIds(
                    req.getId(),
                    bookIds,
                    List.of(ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING)
            )) {
                throw new ExchangeConflictException(
                        "BOOK_ALREADY_IN_ACCEPTED_EXCHANGE",
                        "One of the books already participates in another accepted exchange."
                );
            }

            reserveBothBooks(req);

            req.setStatus(ExchangeStatus.ACCEPTED);
            List<ExchangeRequest> declinedRequests = declineConflictingPendingRequests(req.getId(), activeRequests);
            ExchangeRequest saved = exchangeRepository.save(req);

            ExchangeEventContext eventContext = eventContext(principal);
            exchangeOutboxService.recordExchangeEvent(ExchangeEventType.EXCHANGE_REQUEST_ACCEPTED, saved, eventContext);
            declinedRequests.forEach(declinedRequest ->
                    exchangeOutboxService.recordExchangeEvent(ExchangeEventType.EXCHANGE_REQUEST_DECLINED, declinedRequest, eventContext)
            );

            ExchangeResponse response = toResponse(saved);
            AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_ACCEPT", AuditOutcome.SUCCESS)
                    .actorUserId(me)
                    .targetUserId(response.getRequesterId())
                    .entityId(exchangeId)
                    .attribute("status", response.getStatus())
                    .build());
            return response;
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_ACCEPT", AuditOutcome.FAILURE)
                    .actorUserId(me)
                    .entityId(exchangeId)
                    .reason(ex.getMessage())
                    .errorCode(resolveErrorCode(ex))
                    .build());
            throw ex;
        }
    }

    public ExchangeResponse decline(Long exchangeId, UserPrincipal principal) {
        Long me = requireUserId(principal);

        try {
            ExchangeRequest req = findExchangeRequestForUpdate(exchangeId);

            if (!me.equals(req.getOwnerId())) {
                throw new ExchangeForbiddenException("ONLY_OWNER_CAN_DECLINE", "Only owner can decline.");
            }
            if (req.getStatus() != ExchangeStatus.PENDING) {
                throw new ExchangeConflictException(
                        "INVALID_EXCHANGE_STATUS_TRANSITION",
                        "Only PENDING request can be declined."
                );
            }

            req.setStatus(ExchangeStatus.DECLINED);
            ExchangeRequest saved = exchangeRepository.save(req);
            exchangeOutboxService.recordExchangeEvent(
                    ExchangeEventType.EXCHANGE_REQUEST_DECLINED,
                    saved,
                    eventContext(principal)
            );

            ExchangeResponse response = toResponse(saved);
            AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_DECLINE", AuditOutcome.SUCCESS)
                    .actorUserId(me)
                    .targetUserId(response.getRequesterId())
                    .entityId(exchangeId)
                    .attribute("status", response.getStatus())
                    .build());
            return response;
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_DECLINE", AuditOutcome.FAILURE)
                    .actorUserId(me)
                    .entityId(exchangeId)
                    .reason(ex.getMessage())
                    .errorCode(resolveErrorCode(ex))
                    .build());
            throw ex;
        }
    }

    public ExchangeResponse cancel(Long exchangeId, UserPrincipal principal) {
        Long me = requireUserId(principal);

        try {
            ExchangeRequest req = findExchangeRequestForUpdate(exchangeId);

            if (!me.equals(req.getRequesterId())) {
                throw new ExchangeForbiddenException("ONLY_REQUESTER_CAN_CANCEL", "Only requester can cancel.");
            }
            if ((req.getStatus() != ExchangeStatus.PENDING && req.getStatus() != ExchangeStatus.ACCEPTED)
                    || req.hasAnyCompletionConfirmation()) {
                throw new ExchangeConflictException(
                        "INVALID_EXCHANGE_STATUS_TRANSITION",
                        "Only PENDING or ACCEPTED request without completion confirmation can be canceled."
                );
            }

            if (req.getStatus() == ExchangeStatus.ACCEPTED) {
                releaseBothBooks(req);
            }

            req.setStatus(ExchangeStatus.CANCELLED);
            ExchangeRequest saved = exchangeRepository.save(req);
            exchangeOutboxService.recordExchangeEvent(
                    ExchangeEventType.EXCHANGE_REQUEST_CANCELLED,
                    saved,
                    eventContext(principal)
            );

            ExchangeResponse response = toResponse(saved);
            AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_CANCEL", AuditOutcome.SUCCESS)
                    .actorUserId(me)
                    .targetUserId(response.getOwnerId())
                    .entityId(exchangeId)
                    .attribute("status", response.getStatus())
                    .build());
            return response;
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_CANCEL", AuditOutcome.FAILURE)
                    .actorUserId(me)
                    .entityId(exchangeId)
                    .reason(ex.getMessage())
                    .errorCode(resolveErrorCode(ex))
                    .build());
            throw ex;
        }
    }

    public ExchangeResponse complete(Long exchangeId, UserPrincipal principal) {
        Long me = requireUserId(principal);

        try {
            ExchangeRequest req = findExchangeRequestForUpdate(exchangeId);

            if (!req.isParticipant(me)) {
                throw new ExchangeForbiddenException(
                        "ONLY_EXCHANGE_PARTICIPANT_CAN_COMPLETE",
                        "Only exchange participants can confirm completion."
                );
            }
            if (!COMPLETION_ELIGIBLE_STATUSES.contains(req.getStatus())) {
                throw new ExchangeConflictException(
                        "INVALID_EXCHANGE_STATUS_TRANSITION",
                        "Only ACCEPTED or COMPLETION_PENDING request can be completed."
                );
            }
            if (req.hasCompletionConfirmationFrom(me)) {
                ExchangeResponse response = toResponse(req);
                AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_COMPLETE", AuditOutcome.SUCCESS)
                        .actorUserId(me)
                        .targetUserId(resolveCounterpartyUserId(req, me))
                        .entityId(exchangeId)
                        .reason("ALREADY_CONFIRMED")
                        .attribute("status", response.getStatus())
                        .build());
                return response;
            }

            LocalDateTime confirmedAt = LocalDateTime.now();
            if (!req.hasAnyCompletionConfirmation()) {
                req.confirmCompletion(me, confirmedAt);
                req.setStatus(ExchangeStatus.COMPLETION_PENDING);
                ExchangeRequest saved = exchangeRepository.save(req);
                exchangeOutboxService.recordExchangeEvent(
                        ExchangeEventType.EXCHANGE_REQUEST_COMPLETION_CONFIRMED,
                        saved,
                        eventContext(principal, me)
                );

                ExchangeResponse response = toResponse(saved);
                AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_COMPLETE", AuditOutcome.SUCCESS)
                        .actorUserId(me)
                        .targetUserId(resolveCounterpartyUserId(saved, me))
                        .entityId(exchangeId)
                        .attribute("status", response.getStatus())
                        .build());
                return response;
            }

            completeBothBooks(req);
            req.confirmCompletion(me, confirmedAt);
            req.setStatus(ExchangeStatus.COMPLETED);
            ExchangeRequest saved = exchangeRepository.save(req);
            exchangeOutboxService.recordExchangeEvent(
                    ExchangeEventType.EXCHANGE_REQUEST_COMPLETED,
                    saved,
                    eventContext(principal, me)
            );

            ExchangeResponse response = toResponse(saved);
            AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_COMPLETE", AuditOutcome.SUCCESS)
                    .actorUserId(me)
                    .targetUserId(resolveCounterpartyUserId(saved, me))
                    .entityId(exchangeId)
                    .attribute("status", response.getStatus())
                    .build());
            return response;
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_COMPLETE", AuditOutcome.FAILURE)
                    .actorUserId(me)
                    .entityId(exchangeId)
                    .reason(ex.getMessage())
                    .errorCode(resolveErrorCode(ex))
                    .build());
            throw ex;
        }
    }

    private void reserveBothBooks(ExchangeRequest req) {
        List<Long> reservedBookIds = new ArrayList<>();

        try {
            reserveBook(req.getRequestedBookId(), "REQUESTED_BOOK_RESERVATION_CONFLICT", "Requested book cannot be reserved.");
            reservedBookIds.add(req.getRequestedBookId());

            reserveBook(req.getOfferedBookId(), "OFFERED_BOOK_RESERVATION_CONFLICT", "Offered book cannot be reserved.");
            reservedBookIds.add(req.getOfferedBookId());
        } catch (RuntimeException e) {
            rollbackReservedBooks(reservedBookIds);
            throw e;
        }
    }

    private void rollbackReservedBooks(List<Long> reservedBookIds) {
        for (int i = reservedBookIds.size() - 1; i >= 0; i--) {
            Long bookId = reservedBookIds.get(i);
            try {
                bookServiceClient.makeAvailable(bookId);
                AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_REPAIR", AuditOutcome.SUCCESS)
                        .entityId(bookId)
                        .reason("ROLLBACK_RESERVED_BOOK")
                        .build());
            } catch (RuntimeException rollbackException) {
                AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_REPAIR", AuditOutcome.FAILURE)
                        .entityId(bookId)
                        .reason("ROLLBACK_RESERVED_BOOK_FAILED")
                        .build());
                // best-effort compensation:
                // exchange request is not accepted, but manual investigation may be required
            }
        }
    }

    private List<ExchangeRequest> declineConflictingPendingRequests(Long acceptedExchangeId, List<ExchangeRequest> activeRequests) {
        List<ExchangeRequest> conflictingPendingRequests = activeRequests.stream()
                .filter(r -> !r.getId().equals(acceptedExchangeId))
                .filter(r -> r.getStatus() == ExchangeStatus.PENDING)
                .toList();

        if (conflictingPendingRequests.isEmpty()) {
            return List.of();
        }

        conflictingPendingRequests.forEach(r -> r.setStatus(ExchangeStatus.DECLINED));
        return exchangeRepository.saveAll(conflictingPendingRequests);
    }

    private void completeBothBooks(ExchangeRequest req) {
        markBookExchanged(
                req.getRequestedBookId(),
                "REQUESTED_BOOK_COMPLETION_CONFLICT",
                "Requested book cannot be completed."
        );
        markBookExchanged(
                req.getOfferedBookId(),
                "OFFERED_BOOK_COMPLETION_CONFLICT",
                "Offered book cannot be completed."
        );
    }

    private void releaseBothBooks(ExchangeRequest req) {
        List<Long> releasedBookIds = new ArrayList<>();

        try {
            makeBookAvailable(
                    req.getRequestedBookId(),
                    "REQUESTED_BOOK_RELEASE_CONFLICT",
                    "Requested book cannot be released."
            );
            releasedBookIds.add(req.getRequestedBookId());

            makeBookAvailable(
                    req.getOfferedBookId(),
                    "OFFERED_BOOK_RELEASE_CONFLICT",
                    "Offered book cannot be released."
            );
            releasedBookIds.add(req.getOfferedBookId());
        } catch (RuntimeException e) {
            rollbackReleasedBooks(releasedBookIds);
            throw e;
        }
    }

    private void rollbackReleasedBooks(List<Long> releasedBookIds) {
        for (int i = releasedBookIds.size() - 1; i >= 0; i--) {
            Long bookId = releasedBookIds.get(i);
            try {
                bookServiceClient.reserve(bookId);
                AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_REPAIR", AuditOutcome.SUCCESS)
                        .entityId(bookId)
                        .reason("ROLLBACK_RELEASED_BOOK")
                        .build());
            } catch (RuntimeException rollbackException) {
                AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_REPAIR", AuditOutcome.FAILURE)
                        .entityId(bookId)
                        .reason("ROLLBACK_RELEASED_BOOK_FAILED")
                        .build());
                // best-effort compensation:
                // exchange request is not canceled, but manual investigation may be required
            }
        }
    }

    private void validateRequestedBook(BookDto requestedBook, Long requesterId) {
        if (requestedBook.getOwnerId().equals(requesterId)) {
            throw new ExchangeBadRequestException(
                    "OWN_BOOK_EXCHANGE_NOT_ALLOWED",
                    "You cannot request exchange for your own book."
            );
        }
        if (!"PUBLIC".equals(requestedBook.getVisibility())) {
            throw new ExchangeConflictException("REQUESTED_BOOK_NOT_PUBLIC", "Requested book must be public.");
        }
        if (!"AVAILABLE".equals(requestedBook.getStatus())) {
            throw new ExchangeConflictException("REQUESTED_BOOK_NOT_AVAILABLE", "Requested book must be available.");
        }
    }

    private void validateOfferedBook(BookDto offeredBook, Long requesterId) {
        if (!requesterId.equals(offeredBook.getOwnerId())) {
            throw new ExchangeForbiddenException(
                    "OFFERED_BOOK_NOT_OWNED_BY_REQUESTER",
                    "Offered book must belong to requester."
            );
        }
        if (!"PUBLIC".equals(offeredBook.getVisibility())) {
            throw new ExchangeConflictException("OFFERED_BOOK_NOT_PUBLIC", "Offered book must be public.");
        }
        if (!"AVAILABLE".equals(offeredBook.getStatus())) {
            throw new ExchangeConflictException("OFFERED_BOOK_NOT_AVAILABLE", "Offered book must be available.");
        }
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ExchangeForbiddenException(
                    "AUTHENTICATED_USER_REQUIRED",
                    "Authenticated user is required."
            );
        }
        return principal.userId();
    }

    private ExchangeEventContext eventContext(UserPrincipal principal) {
        return eventContext(principal, null);
    }

    private ExchangeEventContext eventContext(UserPrincipal principal, Long completedByUserId) {
        return ExchangeEventContext.builder()
                .initiatorUserId(principal != null ? principal.userId() : null)
                .initiatorUsername(principal != null ? principal.username() : null)
                .completedByUserId(completedByUserId)
                .build();
    }

    private ExchangeRequest findExchangeRequestForUpdate(Long exchangeId) {
        return exchangeRepository.findByIdForUpdate(exchangeId)
                .orElseThrow(() -> new ExchangeNotFoundException(
                        "EXCHANGE_REQUEST_NOT_FOUND",
                        "Exchange request not found."
                ));
    }

    private BookDto getRequestedBook(Long bookId) {
        try {
            return bookServiceClient.getBook(bookId);
        } catch (HttpClientErrorException ex) {
            if (HttpStatus.NOT_FOUND.equals(ex.getStatusCode())) {
                throw new ExchangeNotFoundException("REQUESTED_BOOK_NOT_FOUND", "Requested book not found.");
            }
            throw ex;
        }
    }

    private BookDto getOfferedBook(Long bookId) {
        try {
            return bookServiceClient.getBook(bookId);
        } catch (HttpClientErrorException ex) {
            if (HttpStatus.NOT_FOUND.equals(ex.getStatusCode())) {
                throw new ExchangeNotFoundException("OFFERED_BOOK_NOT_FOUND", "Offered book not found.");
            }
            throw ex;
        }
    }

    private void reserveBook(Long bookId, String conflictCode, String conflictMessage) {
        try {
            bookServiceClient.reserve(bookId);
        } catch (HttpClientErrorException ex) {
            throw mapBookOperationException(ex, conflictCode, conflictMessage);
        }
    }

    private void makeBookAvailable(Long bookId, String conflictCode, String conflictMessage) {
        try {
            bookServiceClient.makeAvailable(bookId);
        } catch (HttpClientErrorException ex) {
            throw mapBookOperationException(ex, conflictCode, conflictMessage);
        }
    }

    private void markBookExchanged(Long bookId, String conflictCode, String conflictMessage) {
        try {
            bookServiceClient.markExchanged(bookId);
        } catch (HttpClientErrorException ex) {
            throw mapBookOperationException(ex, conflictCode, conflictMessage);
        }
    }

    private RuntimeException mapBookOperationException(HttpClientErrorException ex,
                                                       String conflictCode,
                                                       String conflictMessage) {
        if (HttpStatus.NOT_FOUND.equals(ex.getStatusCode())) {
            return new ExchangeNotFoundException("EXCHANGE_BOOK_NOT_FOUND", "Book referenced by exchange was not found.");
        }
        if (HttpStatus.FORBIDDEN.equals(ex.getStatusCode()) || HttpStatus.CONFLICT.equals(ex.getStatusCode())) {
            return new ExchangeConflictException(conflictCode, conflictMessage);
        }
        return ex;
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
                .ownerCompletionConfirmedAt(r.getOwnerCompletionConfirmedAt())
                .requesterCompletionConfirmedAt(r.getRequesterCompletionConfirmedAt())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private Long resolveCounterpartyUserId(ExchangeRequest exchangeRequest, Long actorUserId) {
        if (exchangeRequest.getOwnerId().equals(actorUserId)) {
            return exchangeRequest.getRequesterId();
        }
        return exchangeRequest.getOwnerId();
    }

    private String resolveErrorCode(RuntimeException ex) {
        if (ex instanceof ExchangeException exchangeException) {
            return exchangeException.getCode();
        }
        return null;
    }
}
