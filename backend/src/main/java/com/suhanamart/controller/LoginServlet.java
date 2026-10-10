package com.suhanamart.controller;

import com.suhanamart.dao.UserDAO.AuthRecord;
import com.suhanamart.model.Role;
import com.suhanamart.service.AuthService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Handles login (renamed from AuthServlet during the project's
 * layer reorganization - behavior is unchanged).
 */
@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(LoginServlet.class);

    private static final String GENERIC_ERROR = "Invalid email or password.";

    private final AuthService authService = new AuthService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String email = request.getParameter("email");
        String password = request.getParameter("password");
        boolean rememberMe = "true".equals(request.getParameter("rememberMe"));

        try {
            // Blank input, unknown email and wrong password all come back as null.
            AuthRecord authRecord = authService.authenticate(email, password);

            boolean loginSuccessful = authRecord != null;

            if (loginSuccessful) {
                // Issue a fresh session id at login so an id planted before login
                // (session fixation) can never become an authenticated session.
                if (request.getSession(false) != null) {
                    request.changeSessionId();
                }
                HttpSession session = request.getSession(true);
                session.setAttribute("userId", authRecord.getUserId());
                session.setAttribute("name", authRecord.getName());
                // Server-side role, read by RoleFilter / the seller controllers.
                session.setAttribute("role", authRecord.getRole().name());

                // "Remember me" only extends how long the existing session
                // stays valid; it does not change how the user is
                // authenticated or what is stored in the session.
                if (rememberMe) {
                    session.setMaxInactiveInterval(60 * 60 * 24 * 30); // 30 days
                } else {
                    session.setMaxInactiveInterval(60 * 30); // 30 minutes
                }

                // Sellers and admins land on their own dashboard; buyers on the existing one.
                String landingPage;
                if (authRecord.getRole() == Role.SELLER) {
                    landingPage = "/seller/dashboard";
                } else if (authRecord.getRole() == Role.ADMIN) {
                    landingPage = "/admin/dashboard";
                } else {
                    landingPage = "/public/dashboard.jsp";
                }
                response.sendRedirect(request.getContextPath() + landingPage);
            } else {
                request.setAttribute("errorMessage", GENERIC_ERROR);
                request.getRequestDispatcher("/public/login.jsp").forward(request, response);
            }

        } catch (SQLException e) {
            // Log server-side; don't leak DB details to the user
            logger.error("Login failed due to a database error", e);
            request.setAttribute("errorMessage", "Something went wrong. Please try again later.");
            request.getRequestDispatcher("/public/login.jsp").forward(request, response);
        }
    }
}
