package com.suhanamart.controller;

import com.suhanamart.util.SecurityUtil;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Shared plumbing for the seller controllers.
 *
 * RoleFilter already keeps non-sellers out of /seller/*, but every seller
 * controller still re-checks the role itself and takes the seller's id
 * from the SESSION - never from a request parameter - so authorization
 * doesn't depend on a single filter being configured correctly.
 */
public abstract class SellerBaseServlet extends HttpServlet {

    /**
     * The logged-in seller's id, or null after sending a 403 (in which
     * case the caller must simply return).
     */
    protected Integer requireSeller(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        Integer sellerId = SecurityUtil.currentSellerId(request);
        if (sellerId == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "This area is only available to seller accounts.");
        }
        return sellerId;
    }

    /**
     * True if the POST carries this session's CSRF token; otherwise
     * sends a 403 and returns false (the caller must simply return).
     */
    protected boolean requireCsrf(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        if (!SecurityUtil.hasValidCsrfToken(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Your session token is missing or has expired. Please go back, reload the page and try again.");
            return false;
        }
        return true;
    }

    protected void redirect(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        response.sendRedirect(request.getContextPath() + path);
    }

    /** Parses a positive int, returning -1 for anything missing, non-numeric, or not positive. */
    protected int parseId(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : -1;
        } catch (Exception e) {
            return -1;
        }
    }
}
