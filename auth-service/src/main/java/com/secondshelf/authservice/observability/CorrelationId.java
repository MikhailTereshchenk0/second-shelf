package com.secondshelf.authservice.observability;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.UUID;

public final class CorrelationId {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String currentOrGenerate() {
        return StringUtils.hasText(current()) ? current() : UUID.randomUUID().toString();
    }

    public static String resolve(String candidate) {
        if (StringUtils.hasText(candidate)) {
            return candidate.trim();
        }
        return currentOrGenerate();
    }

    public static Scope openScope(String candidate) {
        String previous = current();
        String resolved = resolve(candidate);
        MDC.put(MDC_KEY, resolved);

        return () -> {
            if (StringUtils.hasText(previous)) {
                MDC.put(MDC_KEY, previous);
            } else {
                MDC.remove(MDC_KEY);
            }
        };
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
