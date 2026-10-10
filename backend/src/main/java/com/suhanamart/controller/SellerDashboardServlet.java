package com.suhanamart.controller;

import com.suhanamart.model.OrderStatus;
import com.suhanamart.model.Product;
import com.suhanamart.model.ProductStats;
import com.suhanamart.model.SellerOrder;
import com.suhanamart.service.OrderService;
import com.suhanamart.service.ProductService;
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
import java.util.Map;

/**
 * GET /seller/dashboard - loads the seller's real product and order
 * numbers and forwards to /public/seller/dashboard.jsp. Every figure
 * shown there is computed from the database for THIS seller only.
 */
@WebServlet("/seller/dashboard")
public class SellerDashboardServlet extends SellerBaseServlet {

    private static final Logger logger = LogManager.getLogger(SellerDashboardServlet.class);

    private static final int RECENT_ORDERS_SHOWN = 5;
    private static final int LOW_STOCK_SHOWN = 5;

    private final ProductService productService = new ProductService();
    private final OrderService orderService = new OrderService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Integer sellerId = requireSeller(request, response);
        if (sellerId == null) {
            return;
        }

        Flash.consume(request);

        boolean loadError = false;
        ProductStats productStats = new ProductStats(0, 0, 0, 0);
        Map<OrderStatus, Integer> orderCounts = Collections.emptyMap();
        List<SellerOrder> recentOrders = Collections.emptyList();
        List<Product> lowStockProducts = Collections.emptyList();

        try {
            productStats = productService.getStats(sellerId);
            lowStockProducts = productService.getLowStockProducts(sellerId, LOW_STOCK_SHOWN);
            orderCounts = orderService.getSellerOrderCounts(sellerId);
            recentOrders = orderService.getRecentSellerOrders(sellerId, RECENT_ORDERS_SHOWN);
        } catch (SQLException e) {
            logger.error("Failed to load seller dashboard for seller {}", sellerId, e);
            loadError = true;
        }

        request.setAttribute("productStats", productStats);
        request.setAttribute("orderCounts", orderCounts);
        request.setAttribute("recentOrders", recentOrders);
        request.setAttribute("lowStockProducts", lowStockProducts);
        request.setAttribute("lowStockThreshold", ProductService.LOW_STOCK_THRESHOLD);
        request.setAttribute("loadError", loadError);

        request.getRequestDispatcher("/public/seller/dashboard.jsp").forward(request, response);
    }
}
