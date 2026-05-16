package com.connecthub.authservice.validation;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ValidationSupport {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_ALLOWED_PATTERN = Pattern.compile("^[0-9\\s()+-]+$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*\\p{L})(?=.*\\d).{8,72}$");
    private static final Pattern OTP_PATTERN = Pattern.compile("^\\d{6}$");

    private ValidationSupport() {
    }

    public static String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizePhoneNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().replaceAll("\\D", "");
        return normalized.isBlank() ? null : normalized;
    }

    public static boolean isValidEmail(String value) {
        String normalized = normalizeEmail(value);
        return normalized != null && EMAIL_PATTERN.matcher(normalized).matches();
    }

    public static boolean isValidPhoneNumber(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String trimmed = value.trim();
        if (!PHONE_ALLOWED_PATTERN.matcher(trimmed).matches()) {
            return false;
        }

        String digitsOnly = trimmed.replaceAll("\\D", "");
        return digitsOnly.length() >= 10 && digitsOnly.length() <= 15;
    }

    public static boolean isValidEmailOrPhone(String value) {
        return isValidEmail(value) || isValidPhoneNumber(value);
    }

    public static boolean isStrongPassword(String value) {
        return value != null && PASSWORD_PATTERN.matcher(value).matches();
    }

    public static boolean isValidOtp(String value) {
        return value != null && OTP_PATTERN.matcher(value.trim()).matches();
    }
}
