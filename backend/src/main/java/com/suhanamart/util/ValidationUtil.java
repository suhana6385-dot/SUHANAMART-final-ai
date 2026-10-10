package com.suhanamart.util;

import java.util.regex.Pattern;

/**
 * Server-side input validation shared by the registration (and
 * potentially future) flows. Extracted from what was previously
 * inline logic in RegisterServlet - behavior is unchanged.
 *
 * This is server-side validation and is always re-checked here
 * regardless of what any client-side JavaScript already validated.
 */
public final class ValidationUtil {

    // Reasonable, not overly strict, email format check.
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern HTTP_URL_PATTERN =
            Pattern.compile("^https?://[^\\s\"'<>`]+$", Pattern.CASE_INSENSITIVE);

    private static final int MIN_PASSWORD_LENGTH = 6;

    private ValidationUtil() {
        // Utility class - no instances.
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isValidPasswordLength(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH;
    }

    public static int minPasswordLength() {
        return MIN_PASSWORD_LENGTH;
    }

    /**
     * Parses a whole number greater than zero (an id or quantity coming
     * from the browser). Returns null for null, blank, non-numeric,
     * out-of-range, zero or negative input - never throws.
     */
    public static Integer parsePositiveInt(String value) {
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * True only for an absolute http:// or https:// URL with a host and
     * no whitespace or HTML-significant characters. Anything else
     * (javascript:, data:, relative paths, ...) is rejected. Product
     * image URLs are rendered straight into &lt;img src&gt; by the buyer
     * pages, so only real web URLs are accepted.
     */
    public static boolean isValidHttpUrl(String value) {
        if (value == null || !HTTP_URL_PATTERN.matcher(value).matches()) {
            return false;
        }
        try {
            java.net.URI uri = new java.net.URI(value);
            return uri.getHost() != null;
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }
}
