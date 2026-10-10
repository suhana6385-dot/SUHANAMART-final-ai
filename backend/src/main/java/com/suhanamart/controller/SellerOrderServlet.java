package com.suhanamart.controller;

import com.suhanamart.model.OrderStatus;
import com.suhanamart.model.SellerOrder;
import com.suhanamart.service.NotFoundException;
import com.suhanamart.service.OrderService;
import com.suhanamart.util.Flash;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Incoming orders, read-only (status changes are POSTed to
 * OrderStatusServlet).
 *
 *   GET /seller/orders?status=PENDING   orders containing MY products
 *   GET /seller/orders/details?orderId=N   one order, showing only MY items
 *
 * A seller never sees lines belonging to other sellers, and asking for
 * an order that has none of their products is answered exactly like
 * asking for an order that doesn't exist.
 */
@WebServlet("/seller/orders/*")
public class SellerOrderServlet extends SellerBaseServlet {

    private static final Logger logger = LogManager.getLogger(SellerOrderServlet.class);

    private final OrderService orderService = new OrderService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Integer sellerId = requireSeller(request, response);
        if (sellerId == null) {
            return;
        }

        String path = request.getPathInfo();

        if (path == null || "/".equals(path)) {
            showOrders(request, response, sellerId);
        } else if ("/details".equals(path)) {
            showOrderDetails(request, response, sellerId);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void showOrders(HttpServletRequest request, HttpServletResponse response, int sellerId)
            throws ServletException, IOException {

        Flash.consume(request);

        String statusParam = request.getParameter("status");
        OrderStatus activeFilter = OrderStatus.parse(statusParam);

        List<SellerOrder> orders = Collections.emptyList();
        boolean loadError = false;

        try {
            orders = orderService.listSellerOrders(sellerId, statusParam);
        } catch (SQLException e) {
            logger.error("Failed to load orders for seller {}", sellerId, e);
            loadError = true;
        }

        request.setAttribute("orders", orders);
        request.setAttribute("activeFilter", activeFilter);
        request.setAttribute("loadError", loadError);
        request.getRequestDispatcher("/public/seller/orders.jsp").forward(request, response);
    }

    private void showOrderDetails(HttpServletRequest request, HttpServletResponse response, int sellerId)
            throws ServletException, IOException {

        Flash.consume(request);

        int orderId = parseId(request.getParameter("orderId"));
        SellerOrder order = null;
        boolean loadError = false;

        try {
            order = orderService.getSellerOrder(sellerId, orderId);
        } catch (NotFoundException e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } catch (SQLException e) {
            logger.error("Failed to load order {} for seller {}", orderId, sellerId, e);
            loadError = true;
        }

        request.setAttribute("order", order);
        request.setAttribute("nextStatus", order != null ? order.getStatus().next() : null);
        request.setAttribute("loadError", loadError);
        request.getRequestDispatcher("/public/seller/order-details.jsp").forward(request, response);
    }
}
