package com.secondshelf.userservice.validation.password;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;

public final class PasswordPolicyRules {

    private static final int MIN_LENGTH = 10;
    private static final int MAX_LENGTH = 100;

    private PasswordPolicyRules() {
    }

    public static Optional<String> validate(String password, String username, String email) {
        if (password == null || password.isBlank()) {
            return Optional.empty();
        }
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return Optional.of("Password must be between 10 and 100 characters long.");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            return Optional.of("Password must not contain whitespace.");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            return Optional.of("Password must contain at least one lowercase letter.");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            return Optional.of("Password must contain at least one uppercase letter.");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            return Optional.of("Password must contain at least one digit.");
        }
        if (password.chars().noneMatch(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))) {
            return Optional.of("Password must contain at least one special character.");
        }

        String normalizedPassword = password.toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(username)
                && normalizedPassword.contains(username.trim().toLowerCase(Locale.ROOT))) {
            return Optional.of("Password must not contain username.");
        }

        String emailLocalPart = extractEmailLocalPart(email);
        if (StringUtils.hasText(emailLocalPart)
                && normalizedPassword.contains(emailLocalPart.toLowerCase(Locale.ROOT))) {
            return Optional.of("Password must not contain email local-part.");
        }

        return Optional.empty();
    }

    private static String extractEmailLocalPart(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return null;
        }
        String localPart = email.substring(0, atIndex).trim();
        return StringUtils.hasText(localPart) ? localPart : null;
    }
}
