package com.secondshelf.exchangeservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "exchange_requests",
        indexes = {
                @Index(name = "idx_ex_req_requested_book", columnList = "requested_book_id"),
                @Index(name = "idx_ex_req_offered_book", columnList = "offered_book_id"),
                @Index(name = "idx_ex_req_owner", columnList = "owner_id"),
                @Index(name = "idx_ex_req_requester", columnList = "requester_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requested_book_id", nullable = false)
    private Long requestedBookId;

    @Column(name = "requested_book_title", length = 200)
    private String requestedBookTitle;

    @Column(name = "requested_book_author", length = 200)
    private String requestedBookAuthor;

    @Column(name = "offered_book_id")
    private Long offeredBookId;

    @Column(name = "offered_book_title", length = 200)
    private String offeredBookTitle;

    @Column(name = "offered_book_author", length = 200)
    private String offeredBookAuthor;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId; // владелец запрашиваемой книги

    @Column(name = "requester_id", nullable = false)
    private Long requesterId; // кто инициировал обмен

    @Column(name = "requester_username_snapshot", length = 100)
    private String requesterUsernameSnapshot;

    @Column(name = "owner_username_snapshot", length = 100)
    private String ownerUsernameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExchangeStatus status;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "owner_completion_confirmed_at")
    private LocalDateTime ownerCompletionConfirmedAt;

    @Column(name = "requester_completion_confirmed_at")
    private LocalDateTime requesterCompletionConfirmedAt;

    @Column(name = "repair_reason", columnDefinition = "TEXT")
    private String repairReason;

    @Column(name = "repair_required_at")
    private LocalDateTime repairRequiredAt;

    @Builder.Default
    @Column(name = "repair_attempts", nullable = false)
    private Integer repairAttempts = 0;

    @Column(name = "last_repair_attempt_at")
    private LocalDateTime lastRepairAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        var now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (repairAttempts == null) repairAttempts = 0;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isOwnerParticipant(Long userId) {
        return Objects.equals(ownerId, userId);
    }

    public boolean isRequesterParticipant(Long userId) {
        return Objects.equals(requesterId, userId);
    }

    public boolean isParticipant(Long userId) {
        return isOwnerParticipant(userId) || isRequesterParticipant(userId);
    }

    public boolean hasCompletionConfirmationFrom(Long userId) {
        if (isOwnerParticipant(userId)) {
            return ownerCompletionConfirmedAt != null;
        }
        if (isRequesterParticipant(userId)) {
            return requesterCompletionConfirmedAt != null;
        }
        return false;
    }

    public boolean hasAnyCompletionConfirmation() {
        return ownerCompletionConfirmedAt != null || requesterCompletionConfirmedAt != null;
    }

    public boolean isCompletionConfirmedByBothParticipants() {
        return ownerCompletionConfirmedAt != null && requesterCompletionConfirmedAt != null;
    }

    public void confirmCompletion(Long userId, LocalDateTime confirmedAt) {
        if (isOwnerParticipant(userId)) {
            ownerCompletionConfirmedAt = confirmedAt;
            return;
        }
        if (isRequesterParticipant(userId)) {
            requesterCompletionConfirmedAt = confirmedAt;
            return;
        }
        throw new IllegalArgumentException("User is not a participant of this exchange request.");
    }
}
