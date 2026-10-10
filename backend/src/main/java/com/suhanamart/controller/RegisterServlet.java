package com.suhanamart.controller;

import com.suhanamart.service.AuthService;
import com.suhanamart.service.ValidationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

@WebServlet("/RegisterServlet")
public class RegisterServlet extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(RegisterServlet.class);

    private final AuthService authService = new AuthService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {
            authService.register(
                    request.getParameter("name"),
                    request.getParameter("email"),
                    request.getParameter("password"),
                    request.getParameter("confirmPassword"),
                    // The only thing the browser can ask for is the "sell on SUHANAMART"
                    // checkbox; the role written to the database is chosen in AuthService.
                    "true".equals(request.getParameter("registerAsSeller")));

            request.setAttribute("successMessage", "Account created successfully. Please log in.");
            request.getRequestDispatcher("/public/login.jsp").forward(request, response);

        } catch (ValidationException e) {
            // Bad input or duplicate email - the message is written to be shown to the user.
            forwardWithError(request, response, e.getMessage());

        } catch (SQLException e) {
            // Never expose DB details or passwords to the browser - the
            // user always sees the same generic message below. But log a
            // clear, categorized diagnosis server-side so a misconfigured
            // DB (missing env vars, wrong DB name, missing table, etc.)
            // is easy to spot in catalina.out instead of guessing.
            logDatabaseFailure(e);
            forwardWithError(request, response, "Something went wrong. Please try again later.");
        }
    }

    /**
     * Logs a short, categorized hint about why registration's database
     * call failed, based on common causes, before the full stack trace.
     * This never reaches the browser - it only helps whoever is reading
     * the server log (e.g. via catalina.out) find the real cause fast.
     */
    private void logDatabaseFailure(SQLException e) {
        String message = e.getMessage() != null ? e.getMessage() : "";
        String sqlState = e.getSQLState();

        String hint;
        if (message.contains("Database credentials are missing")) {
            hint = "No DB credentials found via SUHANAMART_DB_USER/PASSWORD env vars, "
                    + "-Ddb.user/-Ddb.password JVM options, or db.properties on the classpath. "
                    + "Easiest local fix: copy backend/src/main/resources/db.properties.example to "
                    + "db.properties in that same folder and fill in your real MySQL credentials.";
        } else if ("28000".equals(sqlState) || message.contains("Access denied")) {
            hint = "MySQL rejected the username/password. Verify "
                    + "SUHANAMART_DB_USER / SUHANAMART_DB_PASSWORD against your MySQL user.";
        } else if ("42000".equals(sqlState) && message.contains("Unknown database")) {
            hint = "The database name in DBConnection's DB_URL does not exist in MySQL. "
                    + "Verify the schema name matches.";
        } else if ("42S02".equals(sqlState) || message.contains("doesn't exist")) {
            hint = "The 'users' table (or a referenced column) is missing in the target "
                    + "database. Run database/schema.sql against your MySQL server to create it.";
        } else if (message.contains("Communications link failure") || message.contains("Connection refused")) {
            hint = "Could not reach the MySQL server at all. Verify MySQL is running and "
                    + "the host/port in DBConnection's DB_URL are correct.";
        } else {
            hint = "Unrecognized database error - see the full stack trace below.";
        }

        logger.error("Registration failed due to a database error: {}", hint, e);
    }

    private void forwardWithError(HttpServletRequest request, HttpServletResponse response,
                                   String message) throws ServletException, IOException {
        request.setAttribute("errorMessage", message);
        request.getRequestDispatcher("/public/register.jsp").forward(request, response);
    }
}
