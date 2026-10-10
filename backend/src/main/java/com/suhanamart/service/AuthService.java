package com.suhanamart.service;

import com.suhanamart.dao.UserDAO;
import com.suhanamart.dao.UserDAO.AuthRecord;
import com.suhanamart.model.AccountStatus;
import com.suhanamart.model.Role;
import com.suhanamart.util.PasswordUtil;
import com.suhanamart.util.ValidationUtil;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * Login and registration rules, between LoginServlet / RegisterServlet
 * and UserDAO: input validation, password hashing (jBCrypt via
 * PasswordUtil) and the duplicate-email rule live here.
 *
 * Messages carried by ValidationException are the exact texts the
 * registration page has always shown.
 */
public class AuthService {

    private final UserDAO userDAO = new UserDAO();

    /**
     * Verifies an email + password.
     *
     * @return the user's authentication record, or null if the details are wrong OR the
     *         account has been disabled by an admin. A missing user, a wrong password and a
     *         disabled account are all indistinguishable to the caller on purpose - the login
     *         page always shows the same generic message either way.
     */
    public AuthRecord authenticate(String email, String password) throws SQLException {
        if (ValidationUtil.isBlank(email) || ValidationUtil.isBlank(password)) {
            return null;
        }

        AuthRecord record = userDAO.findAuthRecordByEmail(email.trim());
        if (record != null && record.getAccountStatus() == AccountStatus.DISABLED) {
            return null;
        }
        if (record != null && PasswordUtil.verify(password, record.getPasswordHash())) {
            return record;
        }
        return null;
    }

    /**
     * Validates the registration form and creates the account with a BCrypt-hashed
     * password.
     *
     * @param registerAsSeller true only when the "sell on SUHANAMART" box was ticked; the role
     *                         stored is chosen here from the Role enum, never from request text
     * @throws ValidationException bad input, or an account with this email already exists
     */
    public void register(String name, String email, String password, String confirmPassword,
                         boolean registerAsSeller) throws SQLException, ValidationException {

        if (ValidationUtil.isBlank(name) || ValidationUtil.isBlank(email)
                || ValidationUtil.isBlank(password) || ValidationUtil.isBlank(confirmPassword)) {
            throw new ValidationException("All fields are required.");
        }

        name = name.trim();
        email = email.trim();

        if (!ValidationUtil.isValidEmail(email)) {
            throw new ValidationException("Please enter a valid email address.");
        }
        if (!password.equals(confirmPassword)) {
            throw new ValidationException("Password and Confirm Password do not match.");
        }
        if (!ValidationUtil.isValidPasswordLength(password)) {
            throw new ValidationException(
                    "Password must be at least " + ValidationUtil.minPasswordLength() + " characters long.");
        }

        Role role = registerAsSeller ? Role.SELLER : Role.BUYER;

        try {
            if (!userDAO.registerUser(name, email, PasswordUtil.hash(password), role)) {
                throw new ValidationException("Registration failed. Please try again.");
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            // Duplicate email (unique constraint on users.email)
            throw new ValidationException("An account with this email already exists.");
        }
    }
}
