package com.suhanamart.util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * One-time messages that survive a redirect (Post/Redirect/Get). A
 * controller calls success()/error() just before redirecting; the
 * NEXT page-rendering request calls consume(), which moves the
 * message onto request attributes "flashSuccess" / "flashError" and
 * removes it from the session so a page refresh never shows it twice.
 */
public final class Flash {

    private static final String KEY_SUCCESS = "flashSuccess";
    private static final String KEY_ERROR = "flashError";

    private Flash() {
        // Utility class - no instances.
    }

    public static void success(HttpServletRequest request, String message) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(KEY_SUCCESS, message);
        }
    }

    public static void error(HttpServletRequest request, String message) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(KEY_ERROR, message);
        }
    }

    public static void consume(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        Object success = session.getAttribute(KEY_SUCCESS);
        Object error = session.getAttribute(KEY_ERROR);
        if (success != null) {
            request.setAttribute(KEY_SUCCESS, success);
            session.removeAttribute(KEY_SUCCESS);
        }
        if (error != null) {
            request.setAttribute(KEY_ERROR, error);
            session.removeAttribute(KEY_ERROR);
        }
    }
}
