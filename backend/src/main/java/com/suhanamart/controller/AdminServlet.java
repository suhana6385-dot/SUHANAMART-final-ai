package com.suhanamart.controller;

import com.suhanamart.model.Product;
import com.suhanamart.service.AdminService;
import com.suhanamart.service.NotFoundException;
import com.suhanamart.service.ValidationException;
import com.suhanamart.util.Flash;
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
 * Admin dashboard (GET /admin/dashboard) and product moderation
 * (GET /admin/products, POST /admin/products/remove). Mapped to two
 * paths on one servlet, the same pattern ProductServlet uses for the
 * buyer catalog.
 *
 * RoleFilter has already confirmed the caller is logged in as an
 * ADMIN before any request reaches here; every method re-confirms it
 * with SecurityUtil.currentAdminId as a second, independent layer.
 */
@WebServlet({"/admin/dashboard", "/admin/products", "/admin/products/remove"})
public class AdminServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LogManager.getLogger(AdminServlet.class);
    private static final String GENERIC_ERROR = "Something went wrong. Please try again later.";

    private final transient AdminService adminService = new AdminService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Integer adminId = SecurityUtil.currentAdminId(request);
        if (adminId == null) {
            response.sendRedirect(request.getContextPath() + "/public/login.jsp");
            return;
        }

        String path = request.getServletPath();
        if ("/admin/products".equals(path)) {
            showProducts(request, response);
        } else {
            showDashboard(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Integer adminId = SecurityUtil.currentAdminId(request);
        if (adminId == null) {
            response.sendRedirect(request.getContextPath() + "/public/login.jsp");
            return;
        }

        if (!SecurityUtil.hasValidCsrfToken(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Your session token is missing or has expired. Please go back, reload the page and try again.");
            return;
        }

        Integer productId = ValidationUtil.parsePositiveInt(request.getParameter("productId"));
        if (productId == null) {
            Flash.error(request, "Invalid product.");
            response.sendRedirect(request.getContextPath() + "/admin/products");
            return;
        }

        try {
            adminService.removeListing(productId);
            Flash.success(request, "Product removed.");

        } catch (ValidationException | NotFoundException e) {
            Flash.error(request, e.getMessage());

        } catch (SQLException e) {
            logger.error("Admin failed to remove product {}", productId, e);
            Flash.error(request, GENERIC_ERROR);
        }

        response.sendRedirect(request.getContextPath() + "/admin/products");
    }

    private void showDashboard(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Flash.consume(request);
        try {
            request.setAttribute("stats", adminService.getDashboardStats());
            request.setAttribute("loadError", false);
        } catch (SQLException e) {
            logger.error("Failed to load the admin dashboard", e);
            request.setAttribute("loadError", true);
        }
        request.getRequestDispatcher("/public/admin/dashboard.jsp").forward(request, response);
    }

    private void showProducts(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Flash.consume(request);
        List<Product> products = Collections.emptyList();
        boolean loadError = false;
        try {
            products = adminService.listProducts();
        } catch (SQLException e) {
            logger.error("Failed to load the admin product list", e);
            loadError = true;
        }
        request.setAttribute("products", products);
        request.setAttribute("loadError", loadError);
        request.getRequestDispatcher("/public/admin/products.jsp").forward(request, response);
    }
}
