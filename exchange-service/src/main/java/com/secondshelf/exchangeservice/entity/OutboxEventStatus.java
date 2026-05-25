package com.secondshelf.exchangeservice.entity;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    TERMINAL_FAILED
}
