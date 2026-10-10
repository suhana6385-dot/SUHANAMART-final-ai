package com.suhanamart.controller;

import com.suhanamart.model.User;
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
 * Admin user management: GET /admin/users (list, optional ?role=
 * filter) and POST /admin/users/status (enable/disable). The user's
 * own id always comes from the session (SecurityUtil.currentAdminId),
 * never from a request parameter, and AdminService itself refuses to
 * let an admin disable their own account or any admin account.
 */
@WebServlet({"/admin/users", "/admin/users/status"})
public class AdminUserServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LogManager.getLogger(AdminUserServlet.class);
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

        Flash.consume(request);
        String roleFilter = request.getParameter("role");

        List<User> users = Collections.emptyList();
        boolean loadError = false;
        try {
            users = adminService.listUsers(roleFilter);
        } catch (SQLException e) {
            logger.error("Failed to load the admin user list", e);
            loadError = true;
        }

        request.setAttribute("users", users);
        request.setAttribute("roleFilter", roleFilter);
        request.setAttribute("loadError", loadError);
        request.getRequestDispatcher("/public/admin/users.jsp").forward(request, response);
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

        Integer targetUserId = ValidationUtil.parsePositiveInt(request.getParameter("userId"));
        boolean enable = "enable".equals(request.getParameter("action"));

        if (targetUserId == null) {
            Flash.error(request, "Invalid user.");
            response.sendRedirect(request.getContextPath() + "/admin/users");
            return;
        }

        try {
            adminService.setAccountStatus(targetUserId, enable, adminId);
            Flash.success(request, enable ? "Account enabled." : "Account disabled.");

        } catch (ValidationException | NotFoundException e) {
            Flash.error(request, e.getMessage());

        } catch (SQLException e) {
            logger.error("Admin failed to change account status for user {}", targetUserId, e);
            Flash.error(request, GENERIC_ERROR);
        }

        response.sendRedirect(request.getContextPath() + "/admin/users");
    }
}
