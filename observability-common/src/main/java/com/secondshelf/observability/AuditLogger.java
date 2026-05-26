package com.secondshelf.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.regex.Pattern;

public final class AuditLogger {

    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final Pattern SAFE_VALUE = Pattern.compile("^[A-Za-z0-9._:\\-\\[\\]]+$");

    private final Logger logger;
    private final SensitiveDataSanitizer sanitizer;

    private AuditLogger(Logger logger, SensitiveDataSanitizer sanitizer) {
        this.logger = logger;
        this.sanitizer = sanitizer;
    }

    public static AuditLogger forClass(Class<?> type) {
        return new AuditLogger(LoggerFactory.getLogger(type), new SensitiveDataSanitizer());
    }

    public void log(AuditEvent event) {
        String rendered = render(event);
        if (event.getOutcome() == AuditOutcome.FAILURE) {
            logger.warn(rendered);
            return;
        }
        logger.info(rendered);
    }

    private String render(AuditEvent event) {
        StringBuilder builder = new StringBuilder("security_audit");
        append(builder, "eventType", event.getEventType());
        append(builder, "outcome", event.getOutcome().name());
        append(builder, "actorUserId", event.getActorUserId());
        append(builder, "targetUserId", event.getTargetUserId());
        append(builder, "entityId", event.getEntityId());
        append(builder, "correlationId", MDC.get(CORRELATION_ID_MDC_KEY));
        append(builder, "reason", sanitizer.sanitize("reason", event.getReason()));
        append(builder, "errorCode", sanitizer.sanitize("errorCode", event.getErrorCode()));

        for (Map.Entry<String, Object> entry : event.getAttributes().entrySet()) {
            append(builder, entry.getKey(), sanitizer.sanitize(entry.getKey(), entry.getValue()));
        }

        return builder.toString();
    }

    private void append(StringBuilder builder, String key, Object value) {
        if (value == null) {
            return;
        }

        String stringValue = String.valueOf(value);
        if (stringValue.isBlank()) {
            return;
        }

        builder.append(' ')
                .append(key)
                .append('=')
                .append(formatValue(stringValue));
    }

    private String formatValue(String value) {
        if (SAFE_VALUE.matcher(value).matches()) {
            return value;
        }

        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }
}
