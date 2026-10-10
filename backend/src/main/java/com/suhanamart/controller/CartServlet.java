package com.suhanamart.controller;

import com.suhanamart.service.CartService;
import com.suhanamart.service.NotFoundException;
import com.suhanamart.service.ValidationException;
import com.suhanamart.util.SecurityUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Handles all cart mutations (add / update quantity / remove / clear)
 * as POST requests, then redirects back to /public/cart.jsp so a page
 * refresh never resubmits the action. cart.jsp itself reads the cart
 * directly from CartDAO for display - this servlet only ever changes
 * data, and does so through CartService (which validates the input).
 *
 * AuthFilter already guarantees a logged-in session for this URL, but
 * the user id is still read from the session (never trusted from the
 * request) for every operation, so one user can never modify another
 * user's cart. Every POST must also carry the session's CSRF token, so
 * a page on another site cannot change someone's cart.
 */
@WebServlet("/CartServlet")
public class CartServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final org.apache.logging.log4j.Logger logger =
            org.apache.logging.log4j.LogManager.getLogger(CartServlet.class);

    private final transient CartService cartService = new CartService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Integer userId = SecurityUtil.currentUserId(request);

        if (userId == null) {
            response.sendRedirect(request.getContextPath() + "/public/login.jsp");
            return;
        }

        if (!SecurityUtil.hasValidCsrfToken(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Your session token is missing or has expired. Please go back, reload the page and try again.");
            return;
        }

        String action = request.getParameter("action");

        try {
            if ("add".equals(action)) {
                cartService.addToCart(userId, request.getParameter("productId"), request.getParameter("quantity"));

            } else if ("update".equals(action)) {
                cartService.updateQuantity(userId, request.getParameter("cartItemId"), request.getParameter("quantity"));

            } else if ("remove".equals(action)) {
                cartService.removeItem(userId, request.getParameter("cartItemId"));

            } else if ("clear".equals(action)) {
                cartService.clearCart(userId);
            }

        } catch (ValidationException | NotFoundException e) {
            // A rejected request changes nothing; the cart page simply shows the cart as it is.
            logger.warn("Cart action '{}' rejected for user {}: {}", action, userId, e.getMessage());

        } catch (SQLException e) {
            // Never expose DB details to the browser; log server-side only.
            logger.error("Cart action '{}' failed for user {}", action, userId, e);
        }

        response.sendRedirect(request.getContextPath() + "/public/cart.jsp");
    }
}
