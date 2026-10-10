package com.suhanamart.util;

import com.suhanamart.model.Role;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Small security helpers shared by the seller module:
 *
 *  - HTML output escaping for JSP pages
 *  - who is the current seller (read from the SERVER-SIDE session only)
 *  - a per-session CSRF token for state-changing (POST) requests
 */
public final class SecurityUtil {

    /** Session attribute LoginServlet sets to the user's role name (BUYER / SELLER). */
    public static final String SESSION_ROLE = "role";

    private static final String SESSION_CSRF = "csrfToken";
    public static final String CSRF_PARAM = "csrfToken";

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecurityUtil() {
        // Utility class - no instances.
    }

    // ---------- Output escaping ----------

    /** Escapes text for safe placement in HTML content or a quoted attribute. */
    public static String escapeHtml(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':  out.append("&amp;");  break;
                case '<':  out.append("&lt;");   break;
                case '>':  out.append("&gt;");   break;
                case '"':  out.append("&quot;"); break;
                case '\'': out.append("&#39;");  break;
                default:   out.append(c);
            }
        }
        return out.toString();
    }

    // ---------- Current user / role ----------

    /**
     * Returns the logged-in user's id ONLY if they are a seller,
     * otherwise null. Both values come from the server-side session
     * that LoginServlet populated - never from request parameters.
     */
    public static Integer currentSellerId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object userId = session.getAttribute("userId");
        Object role = session.getAttribute(SESSION_ROLE);

        if (userId instanceof Integer && Role.SELLER.name().equals(role)) {
            return (Integer) userId;
        }
        return null;
    }

    /**
     * The logged-in user's id (any role), or null if nobody is logged in.
     * Read from the server-side session only - never from a request
     * parameter.
     */
    public static Integer currentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object userId = session.getAttribute("userId");
        return userId instanceof Integer ? (Integer) userId : null;
    }

    public static boolean isLoggedIn(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("userId") != null;
    }

    public static boolean isSeller(HttpServletRequest request) {
        return currentSellerId(request) != null;
    }

    /**
     * Returns the logged-in user's id ONLY if they are an admin,
     * otherwise null. Same pattern as currentSellerId: both values
     * come from the server-side session only.
     */
    public static Integer currentAdminId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object userId = session.getAttribute("userId");
        Object role = session.getAttribute(SESSION_ROLE);

        if (userId instanceof Integer && Role.ADMIN.name().equals(role)) {
            return (Integer) userId;
        }
        return null;
    }

    public static boolean isAdmin(HttpServletRequest request) {
        return currentAdminId(request) != null;
    }

    // ---------- CSRF ----------

    /** Returns this session's CSRF token, creating it on first use. */
    public static String csrfToken(HttpSession session) {
        Object existing = session.getAttribute(SESSION_CSRF);
        if (existing instanceof String) {
            return (String) existing;
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_CSRF, token);
        return token;
    }

    /** True only if the request carries this session's CSRF token (constant-time compare). */
    public static boolean hasValidCsrfToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object expected = session.getAttribute(SESSION_CSRF);
        String supplied = request.getParameter(CSRF_PARAM);
        if (!(expected instanceof String) || supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(
                ((String) expected).getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }
}
