package com.secondshelf.exchangeservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    @Column(name = "offered_book_id")
    private Long offeredBookId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId; // владелец запрашиваемой книги

    @Column(name = "requester_id", nullable = false)
    private Long requesterId; // кто инициировал обмен

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExchangeStatus status;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        var now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
