package com.secondshelf.observability;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class SensitiveDataSanitizer {

    public static final String REDACTED = "[REDACTED]";

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");
    private static final Pattern JWT_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "refreshtoken",
            "accesstoken",
            "authorization",
            "xinternaltoken",
            "internaltoken",
            "dbpassword",
            "springdatasourcepassword",
            "rabbitmqpassword",
            "springrabbitmqpassword"
    );

    public String sanitize(String key, Object value) {
        if (value == null) {
            return null;
        }

        String stringValue = String.valueOf(value);
        if (isSensitiveKey(key) || looksLikeBearerValue(stringValue) || looksLikeJwt(key, stringValue)) {
            return REDACTED;
        }

        return stringValue;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        String normalizedKey = NON_ALPHANUMERIC.matcher(key.toLowerCase(Locale.ROOT)).replaceAll("");
        return SENSITIVE_KEYS.contains(normalizedKey);
    }

    private boolean looksLikeBearerValue(String value) {
        return value.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length());
    }

    private boolean looksLikeJwt(String key, String value) {
        if (!JWT_PATTERN.matcher(value).matches()) {
            return false;
        }

        if (key == null || key.isBlank()) {
            return false;
        }

        String normalizedKey = NON_ALPHANUMERIC.matcher(key.toLowerCase(Locale.ROOT)).replaceAll("");
        return normalizedKey.contains("token") || normalizedKey.contains("authorization");
    }
}
