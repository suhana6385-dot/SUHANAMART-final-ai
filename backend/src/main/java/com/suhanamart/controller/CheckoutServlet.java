package com.suhanamart.controller;

import com.suhanamart.dao.OrderDAO;
import com.suhanamart.util.SecurityUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Places an order for the logged-in user's current cart. All the real
 * work (transaction, stock check, clearing the cart) happens in
 * OrderDAO.placeOrder - this servlet only wires the session to it and
 * decides where to redirect afterwards. The POST must carry the
 * session's CSRF token, so another website cannot place an order on a
 * logged-in buyer's behalf.
 */
@WebServlet("/CheckoutServlet")
public class CheckoutServlet extends HttpServlet {

    private static final org.apache.logging.log4j.Logger logger =
            org.apache.logging.log4j.LogManager.getLogger(CheckoutServlet.class);

    private final OrderDAO orderDAO = new OrderDAO();

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

        try {
            int orderId = orderDAO.placeOrder(userId);
            response.sendRedirect(request.getContextPath()
                    + "/public/order-details.jsp?orderId=" + orderId + "&justPlaced=1");

        } catch (OrderDAO.CheckoutException e) {
            // Safe, specific message (e.g. "cart is empty", "not enough stock") -
            // never a raw SQL/Java error.
            request.setAttribute("errorMessage", e.getMessage());
            request.getRequestDispatcher("/public/checkout.jsp").forward(request, response);

        } catch (SQLException e) {
            logger.error("Checkout failed for user {}", userId, e);
            request.setAttribute("errorMessage", "Something went wrong placing your order. Please try again later.");
            request.getRequestDispatcher("/public/checkout.jsp").forward(request, response);
        }
    }
}
