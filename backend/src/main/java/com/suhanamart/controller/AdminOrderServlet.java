package com.suhanamart.controller;

import com.suhanamart.model.OrderStatus;
import com.suhanamart.model.SellerOrder;
import com.suhanamart.service.NotFoundException;
import com.suhanamart.service.AdminService;
import com.suhanamart.util.SecurityUtil;
import com.suhanamart.util.ValidationUtil;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Read-only admin order monitoring: GET /admin/orders, with optional
 * ?status= to filter the list and ?orderId= to also show one order's
 * full detail (every line, from every seller) on the same page - the
 * FINAL STRUCTURE lists a single admin/orders.jsp, with no separate
 * detail page. The Admin module never changes an order's status; that
 * stays the seller's job (OrderStatusServlet).
 */
@WebServlet("/admin/orders")
public class AdminOrderServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LogManager.getLogger(AdminOrderServlet.class);

    private final transient AdminService adminService = new AdminService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Integer adminId = SecurityUtil.currentAdminId(request);
        if (adminId == null) {
            response.sendRedirect(request.getContextPath() + "/public/login.jsp");
            return;
        }

        OrderStatus statusFilter = OrderStatus.parse(request.getParameter("status"));
        Integer selectedOrderId = ValidationUtil.parsePositiveInt(request.getParameter("orderId"));

        List<SellerOrder> orders = Collections.emptyList();
        SellerOrder selectedOrder = null;
        boolean loadError = false;
        boolean selectedOrderMissing = false;

        try {
            orders = adminService.listOrders(statusFilter);
            if (selectedOrderId != null) {
                try {
                    selectedOrder = adminService.getOrder(selectedOrderId);
                } catch (NotFoundException e) {
                    selectedOrderMissing = true;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load the admin order list", e);
            loadError = true;
        }

        request.setAttribute("orders", orders);
        request.setAttribute("activeFilter", statusFilter);
        request.setAttribute("selectedOrder", selectedOrder);
        request.setAttribute("selectedOrderMissing", selectedOrderMissing);
        request.setAttribute("loadError", loadError);
        request.getRequestDispatcher("/public/admin/orders.jsp").forward(request, response);
    }
}
