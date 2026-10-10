package com.suhanamart.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Server-side configuration for the AI chatbot (ChatService), resolved
 * the same way DBConnection resolves database credentials, and in the
 * same priority order:
 *
 *   1. Environment variables   SUHANAMART_AI_API_URL / _API_KEY / _MODEL
 *   2. JVM system properties   -Dai.apiUrl=... -Dai.apiKey=... -Dai.model=...
 *   3. ai.properties on the classpath (copy ai.properties.example and
 *      fill it in - keep the real file out of version control, exactly
 *      like db.properties)
 *
 * The API key is NEVER hard-coded, never logged, and never sent to the
 * browser. If no key is configured anywhere above, isConfigured()
 * returns false and ChatService never attempts a network call - it
 * goes straight to its own fallback, so the chatbot is always safe to
 * deploy even before an AI provider is set up.
 */
public final class AiConfig {

    private static final String DEFAULT_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_MODEL = "claude-3-5-haiku-latest";

    private static Properties fileProps;

    private AiConfig() {
        // Utility class - no instances.
    }

    public static String apiUrl() {
        String value = resolve("SUHANAMART_AI_API_URL", "ai.apiUrl", "apiUrl");
        return value != null ? value : DEFAULT_API_URL;
    }

    public static String apiKey() {
        return resolve("SUHANAMART_AI_API_KEY", "ai.apiKey", "apiKey");
    }

    public static String model() {
        String value = resolve("SUHANAMART_AI_MODEL", "ai.model", "model");
        return value != null ? value : DEFAULT_MODEL;
    }

    /** True only when an API key was actually found - the one thing that decides whether to call out at all. */
    public static boolean isConfigured() {
        String key = apiKey();
        return key != null && !key.trim().isEmpty();
    }

    private static String resolve(String envVar, String sysPropKey, String fileKey) {
        String fromEnv = System.getenv(envVar);
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }
        String fromSysProp = System.getProperty(sysPropKey);
        if (fromSysProp != null && !fromSysProp.trim().isEmpty()) {
            return fromSysProp.trim();
        }
        String fromFile = loadFileProps().getProperty(fileKey);
        return fromFile != null && !fromFile.trim().isEmpty() ? fromFile.trim() : null;
    }

    private static synchronized Properties loadFileProps() {
        if (fileProps != null) {
            return fileProps;
        }
        Properties props = new Properties();
        try (InputStream in = AiConfig.class.getClassLoader().getResourceAsStream("ai.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            // No ai.properties on the classpath (or it's unreadable) - the chatbot simply
            // falls back to its own canned answers; this is not an error worth logging.
        }
        fileProps = props;
        return fileProps;
    }
}
