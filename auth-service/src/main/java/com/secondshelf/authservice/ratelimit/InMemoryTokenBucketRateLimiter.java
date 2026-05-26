package com.secondshelf.authservice.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class InMemoryTokenBucketRateLimiter {

    private static final long CLEANUP_INTERVAL = 256L;

    private final TokenBucketRule rule;
    private final Clock clock;
    private final long staleBucketThresholdMillis;
    private final ConcurrentHashMap<String, BucketState> buckets = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();

    InMemoryTokenBucketRateLimiter(TokenBucketRule rule, Clock clock) {
        this.rule = Objects.requireNonNull(rule, "rule must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        validateRule(rule);

        long refillPeriodMillis = rule.refillPeriod().toMillis();
        this.staleBucketThresholdMillis = Math.max(refillPeriodMillis * 2, Duration.ofMinutes(5).toMillis());
    }

    RateLimitDecision tryConsume(String key) {
        long now = clock.millis();
        BucketState state = buckets.computeIfAbsent(key, ignored -> new BucketState(rule.capacity(), now));
        RateLimitDecision decision = state.tryConsume(rule, now);
        cleanupIfNeeded(now);
        return decision;
    }

    private void cleanupIfNeeded(long now) {
        if (operations.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }

        buckets.entrySet().removeIf(entry -> entry.getValue().isStale(now, staleBucketThresholdMillis));
    }

    private void validateRule(TokenBucketRule rule) {
        if (rule.capacity() < 1) {
            throw new IllegalArgumentException("rate limit capacity must be positive");
        }
        if (rule.refillTokens() < 1) {
            throw new IllegalArgumentException("rate limit refillTokens must be positive");
        }
        if (rule.refillPeriod().isNegative() || rule.refillPeriod().isZero()) {
            throw new IllegalArgumentException("rate limit refillPeriod must be positive");
        }
        if (rule.refillPeriod().toMillis() < 1) {
            throw new IllegalArgumentException("rate limit refillPeriod must be at least 1ms");
        }
    }

    record TokenBucketRule(int capacity, int refillTokens, Duration refillPeriod) {
    }

    record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

        static RateLimitDecision permit() {
            return new RateLimitDecision(true, 0);
        }

        static RateLimitDecision deny(long retryAfterSeconds) {
            return new RateLimitDecision(false, retryAfterSeconds);
        }
    }

    private static final class BucketState {

        private double availableTokens;
        private long lastRefillAtMillis;
        private long lastSeenAtMillis;

        private BucketState(int capacity, long createdAtMillis) {
            this.availableTokens = capacity;
            this.lastRefillAtMillis = createdAtMillis;
            this.lastSeenAtMillis = createdAtMillis;
        }

        private synchronized RateLimitDecision tryConsume(TokenBucketRule rule, long nowMillis) {
            refill(rule, nowMillis);
            lastSeenAtMillis = nowMillis;

            if (availableTokens >= 1.0d) {
                availableTokens -= 1.0d;
                return RateLimitDecision.permit();
            }

            double missingTokens = 1.0d - availableTokens;
            long retryAfterMillis = (long) Math.ceil(
                    missingTokens * rule.refillPeriod().toMillis() / rule.refillTokens()
            );
            long retryAfterSeconds = Math.max(1L, (long) Math.ceil(retryAfterMillis / 1000.0d));
            return RateLimitDecision.deny(retryAfterSeconds);
        }

        private synchronized boolean isStale(long nowMillis, long staleThresholdMillis) {
            return nowMillis - lastSeenAtMillis >= staleThresholdMillis;
        }

        private void refill(TokenBucketRule rule, long nowMillis) {
            long elapsedMillis = Math.max(0L, nowMillis - lastRefillAtMillis);
            if (elapsedMillis == 0L) {
                return;
            }

            double tokensToAdd = ((double) elapsedMillis * rule.refillTokens()) / rule.refillPeriod().toMillis();
            availableTokens = Math.min(rule.capacity(), availableTokens + tokensToAdd);
            lastRefillAtMillis = nowMillis;
        }
    }
}
