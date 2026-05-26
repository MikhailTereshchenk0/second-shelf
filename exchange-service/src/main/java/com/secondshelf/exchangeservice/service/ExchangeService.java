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
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class ExchangeService {

    private static final AuditLogger AUDIT_LOGGER = AuditLogger.forClass(ExchangeService.class);
    private static final String PARTIAL_COMPLETION_REPAIR_REASON_PREFIX = "PARTIAL_COMPLETION_FAILED";
    private static final String CANCEL_RELEASE_REPAIR_REASON_PREFIX = "CANCEL_RELEASE_COMPENSATION_FAILED";
    private static final String ACCEPT_RESERVATION_ROLLBACK_FAILED_PREFIX = "ACCEPT_RESERVATION_ROLLBACK_FAILED";
    private static final int IDEMPOTENCY_KEY_MIN_LENGTH = 16;
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 128;

    private static final List<ExchangeStatus> ACTIVE_EXCHANGE_STATUSES = List.of(
            ExchangeStatus.PENDING,
            ExchangeStatus.ACCEPTED,
            ExchangeStatus.COMPLETION_PENDING,
            ExchangeStatus.REPAIR_REQUIRED
    );

    private static final List<ExchangeStatus> COMPLETION_ELIGIBLE_STATUSES = List.of(
            ExchangeStatus.ACCEPTED,
            ExchangeStatus.COMPLETION_PENDING
    );

    private final ExchangeRepository exchangeRepository;
    private final BookServiceClient bookServiceClient;
    private final ExchangeOutboxService exchangeOutboxService;

    public ExchangeResponse create(CreateExchangeRequest req, UserPrincipal principal) {
        return create(req, principal, null);
    }

    public ExchangeResponse create(CreateExchangeRequest req, UserPrincipal principal, String idempotencyKey) {
        Long requesterId = requireUserId(principal);
        String idempotencyKeyHash = normalizeAndHashIdempotencyKey(idempotencyKey);

        try {
            if (idempotencyKeyHash != null) {
                var existing = exchangeRepository.findByRequesterIdAndIdempotencyKeyHash(requesterId, idempotencyKeyHash);
                if (existing.isPresent()) {
                    ExchangeRequest existingRequest = existing.get();
                    if (!hasSameCreatePayload(existingRequest, req)) {
                        throw new ExchangeConflictException(
                                "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST",
                                "Idempotency-Key was already used with a different exchange request payload."
                        );
                    }
                    ExchangeResponse response = toResponse(existingRequest);
                    AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_CREATE", AuditOutcome.SUCCESS)
                            .actorUserId(requesterId)
                            .targetUserId(response.getOwnerId())
                            .entityId(response.getId())
                            .reason("IDEMPOTENT_REPLAY")
                            .attribute("status", response.getStatus())
                            .build());
                    return response;
                }
            }

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
                            .requesterUsernameSnapshot(principal != null ? principal.username() : null)
                            .status(ExchangeStatus.PENDING)
                            .message(req.getMessage())
                            .idempotencyKeyHash(idempotencyKeyHash)
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
            rejectParticipantActionWhenRepairRequired(req);
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
                    List.of(ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING, ExchangeStatus.REPAIR_REQUIRED)
            )) {
                throw new ExchangeConflictException(
                        "BOOK_ALREADY_IN_ACCEPTED_EXCHANGE",
                        "One of the books already participates in another accepted exchange."
                );
            }

            populateOwnerUsernameSnapshotIfMissing(req, principal);
            try {
                reserveBothBooks(req);
            } catch (ExchangeRepairRequiredException ex) {
                return markRepairRequired(req, principal, me, ex.getMessage(), "EXCHANGE_ACCEPT");
            }

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
            rejectParticipantActionWhenRepairRequired(req);
            if (req.getStatus() != ExchangeStatus.PENDING) {
                throw new ExchangeConflictException(
                        "INVALID_EXCHANGE_STATUS_TRANSITION",
                        "Only PENDING request can be declined."
                );
            }

            populateOwnerUsernameSnapshotIfMissing(req, principal);
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
            rejectParticipantActionWhenRepairRequired(req);
            if ((req.getStatus() != ExchangeStatus.PENDING && req.getStatus() != ExchangeStatus.ACCEPTED)
                    || req.hasAnyCompletionConfirmation()) {
                throw new ExchangeConflictException(
                        "INVALID_EXCHANGE_STATUS_TRANSITION",
                        "Only PENDING or ACCEPTED request without completion confirmation can be canceled."
                );
            }

            if (req.getStatus() == ExchangeStatus.ACCEPTED) {
                try {
                    releaseBothBooks(req);
                } catch (ExchangeRepairRequiredException ex) {
                    populateRequesterUsernameSnapshotIfMissing(req, principal);
                    return markRepairRequired(req, principal, me, ex.getMessage(), "EXCHANGE_CANCEL");
                }
            }

            populateRequesterUsernameSnapshotIfMissing(req, principal);
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
            rejectParticipantActionWhenRepairRequired(req);
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
                populateParticipantUsernameSnapshotIfMissing(req, principal, me);
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

            populateParticipantUsernameSnapshotIfMissing(req, principal, me);
            req.confirmCompletion(me, confirmedAt);
            try {
                completeBothBooks(req);
            } catch (ExchangeRepairRequiredException ex) {
                return markRepairRequired(req, principal, me, ex.getMessage(), "EXCHANGE_COMPLETE");
            }
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

    @Transactional(noRollbackFor = ExchangeConflictException.class)
    public ExchangeResponse repair(Long exchangeId, UserPrincipal principal) {
        Long adminUserId = requireUserId(principal);

        try {
            ExchangeRequest req = findExchangeRequestForUpdate(exchangeId);

            if (isCompletedRepair(req) || isCancelledRepair(req)) {
                AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_ADMIN_REPAIR", AuditOutcome.SUCCESS)
                        .actorUserId(adminUserId)
                        .entityId(exchangeId)
                        .reason("ALREADY_REPAIRED")
                        .attribute("status", req.getStatus())
                        .build());
                return toResponse(req);
            }

            if (req.getStatus() != ExchangeStatus.REPAIR_REQUIRED) {
                throw new ExchangeConflictException(
                        "EXCHANGE_NOT_REPAIR_REQUIRED",
                        "Only REPAIR_REQUIRED exchange can be repaired."
                );
            }

            incrementRepairAttempt(req);
            RepairTarget target = resolveRepairTarget(req);

            try {
                if (target == RepairTarget.COMPLETED) {
                    repairCompletion(req);
                } else if (target == RepairTarget.CANCELLED) {
                    repairCancellation(req);
                } else {
                    repairReservationRollback(req);
                }
            } catch (RuntimeException ex) {
                req.setStatus(ExchangeStatus.REPAIR_REQUIRED);
                req.setRepairReason("REPAIR_ATTEMPT_FAILED: " + ex.getMessage());
                ExchangeRequest saved = exchangeRepository.save(req);
                AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_ADMIN_REPAIR", AuditOutcome.FAILURE)
                        .actorUserId(adminUserId)
                        .entityId(exchangeId)
                        .reason(saved.getRepairReason())
                        .attribute("repairAttempts", saved.getRepairAttempts())
                        .build());
                throw new ExchangeConflictException("EXCHANGE_REPAIR_FAILED", saved.getRepairReason());
            }

            ExchangeRequest saved = exchangeRepository.save(req);
            ExchangeEventContext eventContext = eventContext(principal);
            if (target == RepairTarget.COMPLETED) {
                exchangeOutboxService.recordExchangeEventIfAbsent(
                        ExchangeEventType.EXCHANGE_REQUEST_COMPLETED,
                        saved,
                        eventContext
                );
            } else if (target == RepairTarget.CANCELLED) {
                exchangeOutboxService.recordExchangeEventIfAbsent(
                        ExchangeEventType.EXCHANGE_REQUEST_CANCELLED,
                        saved,
                        eventContext
                );
            }

            AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_ADMIN_REPAIR", AuditOutcome.SUCCESS)
                    .actorUserId(adminUserId)
                    .entityId(exchangeId)
                    .attribute("status", saved.getStatus())
                    .attribute("repairAttempts", saved.getRepairAttempts())
                    .build());
            return toResponse(saved);
        } catch (RuntimeException ex) {
            if (!(ex instanceof ExchangeConflictException
                    && "EXCHANGE_REPAIR_FAILED".equals(((ExchangeConflictException) ex).getCode()))) {
                AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_ADMIN_REPAIR", AuditOutcome.FAILURE)
                        .actorUserId(adminUserId)
                        .entityId(exchangeId)
                        .reason(ex.getMessage())
                        .errorCode(resolveErrorCode(ex))
                        .build());
            }
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
            List<Long> rollbackFailedBookIds = rollbackReservedBooks(reservedBookIds);
            if (!rollbackFailedBookIds.isEmpty()) {
                throw new ExchangeRepairRequiredException(
                        ACCEPT_RESERVATION_ROLLBACK_FAILED_PREFIX
                                + ": reserved books="
                                + reservedBookIds
                                + ", rollback failed for books="
                                + rollbackFailedBookIds
                                + ", original reservation failure: "
                                + e.getMessage(),
                        e
                );
            }
            throw e;
        }
    }

    private List<Long> rollbackReservedBooks(List<Long> reservedBookIds) {
        List<Long> rollbackFailedBookIds = new ArrayList<>();
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
                rollbackFailedBookIds.add(bookId);
            }
        }
        return rollbackFailedBookIds;
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
        List<Long> exchangedBookIds = new ArrayList<>();

        try {
            markBookExchanged(
                    req.getRequestedBookId(),
                    "REQUESTED_BOOK_COMPLETION_CONFLICT",
                    "Requested book cannot be completed."
            );
            exchangedBookIds.add(req.getRequestedBookId());

            markBookExchanged(
                    req.getOfferedBookId(),
                    "OFFERED_BOOK_COMPLETION_CONFLICT",
                    "Offered book cannot be completed."
            );
            exchangedBookIds.add(req.getOfferedBookId());
        } catch (RuntimeException ex) {
            if (!exchangedBookIds.isEmpty()) {
                throw new ExchangeRepairRequiredException(
                        "PARTIAL_COMPLETION_FAILED: books marked EXCHANGED="
                                + exchangedBookIds
                                + ", failed to complete remaining book transition: "
                                + ex.getMessage(),
                        ex
                );
            }
            throw ex;
        }
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
            List<Long> rollbackFailedBookIds = rollbackReleasedBooks(releasedBookIds);
            if (!rollbackFailedBookIds.isEmpty()) {
                throw new ExchangeRepairRequiredException(
                        "CANCEL_RELEASE_COMPENSATION_FAILED: released books="
                                + releasedBookIds
                                + ", rollback failed for books="
                                + rollbackFailedBookIds
                                + ", original release failure: "
                                + e.getMessage(),
                        e
                );
            }
            throw e;
        }
    }

    private List<Long> rollbackReleasedBooks(List<Long> releasedBookIds) {
        List<Long> rollbackFailedBookIds = new ArrayList<>();
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
                rollbackFailedBookIds.add(bookId);
                // best-effort compensation:
                // exchange request is not canceled, but manual investigation may be required
            }
        }
        return rollbackFailedBookIds;
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

    private String normalizeAndHashIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() < IDEMPOTENCY_KEY_MIN_LENGTH
                || normalized.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new ExchangeBadRequestException(
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key length must be between 16 and 128 characters."
            );
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available.", ex);
        }
    }

    private boolean hasSameCreatePayload(ExchangeRequest existingRequest, CreateExchangeRequest req) {
        return Objects.equals(existingRequest.getRequestedBookId(), req.getRequestedBookId())
                && Objects.equals(existingRequest.getOfferedBookId(), req.getOfferedBookId())
                && Objects.equals(existingRequest.getMessage(), req.getMessage());
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

    private void repairCompletion(ExchangeRequest req) {
        ensureBookExchangedAndPrivate(req.getRequestedBookId());
        ensureBookExchangedAndPrivate(req.getOfferedBookId());
        req.setStatus(ExchangeStatus.COMPLETED);
    }

    private void repairCancellation(ExchangeRequest req) {
        ensureBookAvailable(req.getRequestedBookId());
        ensureBookAvailable(req.getOfferedBookId());
        req.setStatus(ExchangeStatus.CANCELLED);
    }

    private void repairReservationRollback(ExchangeRequest req) {
        ensureBookAvailable(req.getRequestedBookId());
        ensureBookAvailable(req.getOfferedBookId());
        req.setStatus(ExchangeStatus.PENDING);
    }

    private void ensureBookExchangedAndPrivate(Long bookId) {
        BookDto book = getRepairBook(bookId);
        if ("EXCHANGED".equals(book.getStatus())) {
            if (!"PRIVATE".equals(book.getVisibility())) {
                throw new ExchangeConflictException(
                        "EXCHANGE_REPAIR_UNSUPPORTED_BOOK_STATE",
                        "Book is EXCHANGED but not PRIVATE."
                );
            }
            return;
        }
        markBookExchanged(
                bookId,
                "EXCHANGE_REPAIR_BOOK_COMPLETION_CONFLICT",
                "Book cannot be marked EXCHANGED during repair."
        );
    }

    private void ensureBookAvailable(Long bookId) {
        BookDto book = getRepairBook(bookId);
        if ("AVAILABLE".equals(book.getStatus())) {
            return;
        }
        makeBookAvailable(
                bookId,
                "EXCHANGE_REPAIR_BOOK_RELEASE_CONFLICT",
                "Book cannot be made AVAILABLE during repair."
        );
    }

    private BookDto getRepairBook(Long bookId) {
        try {
            return bookServiceClient.getBook(bookId);
        } catch (HttpClientErrorException ex) {
            throw mapBookOperationException(ex, "EXCHANGE_REPAIR_BOOK_READ_CONFLICT", "Book cannot be read during repair.");
        }
    }

    private RepairTarget resolveRepairTarget(ExchangeRequest req) {
        String reason = req.getRepairReason();
        if (reason != null && reason.startsWith(ACCEPT_RESERVATION_ROLLBACK_FAILED_PREFIX)) {
            return RepairTarget.PENDING;
        }
        if (reason != null && reason.startsWith(CANCEL_RELEASE_REPAIR_REASON_PREFIX)) {
            return RepairTarget.CANCELLED;
        }
        if (reason != null && reason.startsWith(PARTIAL_COMPLETION_REPAIR_REASON_PREFIX)) {
            return RepairTarget.COMPLETED;
        }
        if (req.isCompletionConfirmedByBothParticipants()) {
            return RepairTarget.COMPLETED;
        }
        throw new ExchangeConflictException(
                "EXCHANGE_REPAIR_TARGET_UNKNOWN",
                "Exchange repair target cannot be determined from current exchange data."
        );
    }

    private void incrementRepairAttempt(ExchangeRequest req) {
        req.setRepairAttempts((req.getRepairAttempts() == null ? 0 : req.getRepairAttempts()) + 1);
        req.setLastRepairAttemptAt(LocalDateTime.now());
    }

    private boolean isCompletedRepair(ExchangeRequest req) {
        return req.getStatus() == ExchangeStatus.COMPLETED && req.getRepairRequiredAt() != null;
    }

    private boolean isCancelledRepair(ExchangeRequest req) {
        return req.getStatus() == ExchangeStatus.CANCELLED && req.getRepairRequiredAt() != null;
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

    private void rejectParticipantActionWhenRepairRequired(ExchangeRequest req) {
        if (req.getStatus() == ExchangeStatus.REPAIR_REQUIRED) {
            throw new ExchangeConflictException(
                    "EXCHANGE_REPAIR_REQUIRED",
                    "Exchange requires manual repair before participant actions can continue."
            );
        }
    }

    private ExchangeResponse markRepairRequired(ExchangeRequest req,
                                                UserPrincipal principal,
                                                Long actorUserId,
                                                String repairReason,
                                                String failedAction) {
        LocalDateTime now = LocalDateTime.now();
        req.setStatus(ExchangeStatus.REPAIR_REQUIRED);
        req.setRepairReason(repairReason);
        if (req.getRepairRequiredAt() == null) {
            req.setRepairRequiredAt(now);
        }
        if (req.getRepairAttempts() == null) {
            req.setRepairAttempts(0);
        }

        ExchangeRequest saved = exchangeRepository.save(req);
        exchangeOutboxService.recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_REPAIR_REQUIRED,
                saved,
                eventContext(principal, actorUserId)
        );

        AUDIT_LOGGER.log(AuditEvent.builder("EXCHANGE_REPAIR_REQUIRED", AuditOutcome.FAILURE)
                .actorUserId(actorUserId)
                .targetUserId(actorUserId != null ? resolveCounterpartyUserId(saved, actorUserId) : null)
                .entityId(saved.getId())
                .reason(repairReason)
                .attribute("failedAction", failedAction)
                .attribute("status", saved.getStatus())
                .build());

        return toResponse(saved);
    }

    private void populateOwnerUsernameSnapshotIfMissing(ExchangeRequest exchangeRequest, UserPrincipal principal) {
        if (exchangeRequest.getOwnerUsernameSnapshot() == null
                && principal != null
                && StringUtils.hasText(principal.username())) {
            exchangeRequest.setOwnerUsernameSnapshot(principal.username());
        }
    }

    private void populateRequesterUsernameSnapshotIfMissing(ExchangeRequest exchangeRequest, UserPrincipal principal) {
        if (exchangeRequest.getRequesterUsernameSnapshot() == null
                && principal != null
                && StringUtils.hasText(principal.username())) {
            exchangeRequest.setRequesterUsernameSnapshot(principal.username());
        }
    }

    private void populateParticipantUsernameSnapshotIfMissing(ExchangeRequest exchangeRequest,
                                                              UserPrincipal principal,
                                                              Long actorUserId) {
        if (exchangeRequest.isRequesterParticipant(actorUserId)) {
            populateRequesterUsernameSnapshotIfMissing(exchangeRequest, principal);
            return;
        }
        if (exchangeRequest.isOwnerParticipant(actorUserId)) {
            populateOwnerUsernameSnapshotIfMissing(exchangeRequest, principal);
        }
    }

    private ExchangeResponse toResponse(ExchangeRequest r) {
        return ExchangeResponse.builder()
                .id(r.getId())
                .requestedBookId(r.getRequestedBookId())
                .requestedBookTitle(r.getRequestedBookTitle())
                .requestedBookAuthor(r.getRequestedBookAuthor())
                .offeredBookId(r.getOfferedBookId())
                .offeredBookTitle(r.getOfferedBookTitle())
                .offeredBookAuthor(r.getOfferedBookAuthor())
                .ownerId(r.getOwnerId())
                .requesterId(r.getRequesterId())
                .ownerUsernameSnapshot(r.getOwnerUsernameSnapshot())
                .requesterUsernameSnapshot(r.getRequesterUsernameSnapshot())
                .status(r.getStatus())
                .message(r.getMessage())
                .ownerCompletionConfirmedAt(r.getOwnerCompletionConfirmedAt())
                .requesterCompletionConfirmedAt(r.getRequesterCompletionConfirmedAt())
                .repairReason(r.getRepairReason())
                .repairRequiredAt(r.getRepairRequiredAt())
                .repairAttempts(r.getRepairAttempts())
                .lastRepairAttemptAt(r.getLastRepairAttemptAt())
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

    private static class ExchangeRepairRequiredException extends RuntimeException {

        ExchangeRepairRequiredException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private enum RepairTarget {
        COMPLETED,
        CANCELLED,
        PENDING
    }
}
