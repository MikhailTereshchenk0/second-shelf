package com.secondshelf.exchangeservice.entity;

public enum OutboxEventStatus {
    PENDING,
    RETRYABLE_FAILED,
    PUBLISHED,
    TERMINAL_FAILED
}
