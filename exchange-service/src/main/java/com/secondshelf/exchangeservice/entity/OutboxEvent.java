package com.secondshelf.exchangeservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_outbox_events_event_id", columnNames = "event_id")
        },
        indexes = {
                @Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
                @Index(name = "idx_outbox_status_next_attempt_created", columnList = "status, next_attempt_at, created_at"),
                @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "first_failed_at")
    private LocalDateTime firstFailedAt;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "attempts_count", nullable = false)
    private int attemptsCount;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @PrePersist
    void prePersist() {
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = OutboxEventStatus.PENDING;
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = createdAt;
        }
    }

    public void incrementAttempts() {
        attemptsCount++;
    }

    public void recordPublishFailure(String errorMessage, String failureCode, int maxAttempts, LocalDateTime nextAttemptAt) {
        incrementAttempts();
        lastError = normalizeErrorMessage(errorMessage);
        errorCode = normalizeErrorCode(failureCode);
        if (firstFailedAt == null) {
            firstFailedAt = LocalDateTime.now();
        }

        if (attemptsCount >= Math.max(1, maxAttempts)) {
            status = OutboxEventStatus.TERMINAL_FAILED;
            failedAt = LocalDateTime.now();
            return;
        }

        status = OutboxEventStatus.RETRYABLE_FAILED;
        this.nextAttemptAt = nextAttemptAt != null ? nextAttemptAt : LocalDateTime.now();
    }

    public void markPublished() {
        status = OutboxEventStatus.PUBLISHED;
        publishedAt = LocalDateTime.now();
        failedAt = null;
        lastError = null;
        errorCode = null;
    }

    private String normalizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return null;
        }
        return errorMessage.length() <= 2000 ? errorMessage : errorMessage.substring(0, 2000);
    }

    private String normalizeErrorCode(String failureCode) {
        if (failureCode == null || failureCode.isBlank()) {
            return null;
        }
        return failureCode.length() <= 100 ? failureCode : failureCode.substring(0, 100);
    }
}
