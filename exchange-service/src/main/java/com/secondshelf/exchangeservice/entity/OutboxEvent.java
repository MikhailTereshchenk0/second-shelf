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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

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
    }

    public void incrementAttempts() {
        attemptsCount++;
    }

    public void recordPublishFailure(String errorMessage, int maxAttempts) {
        incrementAttempts();
        lastError = normalizeErrorMessage(errorMessage);

        if (attemptsCount >= Math.max(1, maxAttempts)) {
            status = OutboxEventStatus.TERMINAL_FAILED;
            failedAt = LocalDateTime.now();
        }
    }

    public void markPublished() {
        status = OutboxEventStatus.PUBLISHED;
        publishedAt = LocalDateTime.now();
        failedAt = null;
        lastError = null;
    }

    private String normalizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return null;
        }
        return errorMessage.length() <= 2000 ? errorMessage : errorMessage.substring(0, 2000);
    }
}
