package com.suhanamart.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Thin wrapper around jBCrypt so password hashing/verification lives
 * in exactly one place instead of being duplicated across
 * RegisterServlet and LoginServlet. Behavior is unchanged from the
 * previous inline calls - this is purely an extraction.
 */
public final class PasswordUtil {

    private PasswordUtil() {
        // Utility class - no instances.
    }

    /** Hashes a plain-text password for storage. Never store the raw password. */
    public static String hash(String plainTextPassword) {
        return BCrypt.hashpw(plainTextPassword, BCrypt.gensalt());
    }

    /** Verifies a plain-text password against a previously stored BCrypt hash. */
    public static boolean verify(String plainTextPassword, String storedHash) {
        return storedHash != null && BCrypt.checkpw(plainTextPassword, storedHash);
    }
}
