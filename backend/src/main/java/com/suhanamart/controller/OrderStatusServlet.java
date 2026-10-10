package com.suhanamart.controller;

import com.suhanamart.model.OrderStatus;
import com.suhanamart.service.NotFoundException;
import com.suhanamart.service.OrderService;
import com.suhanamart.service.ValidationException;
import com.suhanamart.util.Flash;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * POST /seller/order-status - moves the seller's part of an order to its
 * next status (PENDING -> CONFIRMED -> SHIPPED -> DELIVERED).
 *
 * Params: orderId, status (the target). The browser's "status" value is
 * never trusted: OrderService parses it against the four real statuses,
 * checks that the order contains this seller's products, and only
 * accepts exactly one step forward from the status stored in the
 * database. GET is not supported.
 */
@WebServlet("/seller/order-status")
public class OrderStatusServlet extends SellerBaseServlet {

    private static final Logger logger = LogManager.getLogger(OrderStatusServlet.class);

    private final OrderService orderService = new OrderService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Integer sellerId = requireSeller(request, response);
        if (sellerId == null) {
            return;
        }
        if (!requireCsrf(request, response)) {
            return;
        }

        int orderId = parseId(request.getParameter("orderId"));
        String detailsUrl = "/seller/orders/details?orderId=" + orderId;

        try {
            OrderStatus newStatus = orderService.advanceStatus(sellerId, orderId, request.getParameter("status"));
            Flash.success(request, "Order #" + orderId + " marked as " + newStatus.getLabel() + ".");
            redirect(request, response, detailsUrl);

        } catch (ValidationException e) {
            Flash.error(request, e.getMessage());
            redirect(request, response, detailsUrl);

        } catch (NotFoundException e) {
            Flash.error(request, "That order could not be found in your orders.");
            redirect(request, response, "/seller/orders");

        } catch (SQLException e) {
            logger.error("Failed to update status of order {} for seller {}", orderId, sellerId, e);
            Flash.error(request, "Something went wrong updating the order. Please try again later.");
            redirect(request, response, detailsUrl);
        }
    }
}
