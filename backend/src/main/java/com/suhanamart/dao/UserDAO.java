package com.suhanamart.dao;

import com.suhanamart.model.AccountStatus;
import com.suhanamart.model.Role;
import com.suhanamart.model.User;
import com.suhanamart.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {

    // ---------- Registration ----------

    /** Registers a regular BUYER account (unchanged behaviour for existing callers). */
    public boolean registerUser(String name, String email, String passwordHash) throws SQLException {
        return registerUser(name, email, passwordHash, Role.BUYER);
    }

    /**
     * Registers an account with an explicit role. The role is written
     * from the Role enum, never from raw request text, so only the
     * roles that exist in the enum can ever reach the database.
     */
    public boolean registerUser(String name, String email, String passwordHash, Role role) throws SQLException {
        String sql = "INSERT INTO users (name, email, password, role) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, passwordHash); // caller is expected to pass an already-hashed value
            ps.setString(4, role.name());

            int rows = ps.executeUpdate();
            return rows > 0;
        }
    }

    // ---------- Lookup without password (safe to use anywhere) ----------
    public User findUserByEmail(String email) throws SQLException {
        String sql = "SELECT user_id, name, email, created_at FROM users WHERE email = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
                return null;
            }
        }
    }

    // ---------- Lookup by id (used by the profile page) ----------
    public User findUserById(int userId) throws SQLException {
        String sql = "SELECT user_id, name, email, created_at FROM users WHERE user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
                return null;
            }
        }
    }

    // ---------- Authentication-only lookup (includes password hash) ----------

    /**
     * Package-private-by-convention record used ONLY inside the
     * authentication flow. Deliberately NOT part of com.suhanamart.model -
     * the password hash must never be exposed outside UserDAO/LoginServlet.
     */
    public static class AuthRecord {
        private final int userId;
        private final String name;
        private final String passwordHash;
        private final Role role;
        private final AccountStatus accountStatus;

        public AuthRecord(int userId, String name, String passwordHash, Role role, AccountStatus accountStatus) {
            this.userId = userId;
            this.name = name;
            this.passwordHash = passwordHash;
            this.role = role;
            this.accountStatus = accountStatus;
        }

        public int getUserId() { return userId; }
        public String getName() { return name; }
        public String getPasswordHash() { return passwordHash; }
        public Role getRole() { return role; }
        public AccountStatus getAccountStatus() { return accountStatus; }
    }

    /**
     * Retrieves the minimal data needed to authenticate a user, including the
     * BCrypt password hash. This is intentionally separate from findUserByEmail()
     * so the hash is never exposed outside the login flow.
     */
    public AuthRecord findAuthRecordByEmail(String email) throws SQLException {
        String sql = "SELECT user_id, name, password, role, account_status FROM users WHERE email = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new AuthRecord(
                            rs.getInt("user_id"),
                            rs.getString("name"),
                            rs.getString("password"),
                            Role.fromString(rs.getString("role")),
                            AccountStatus.fromDb(rs.getString("account_status"))
                    );
                }
                return null;
            }
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("user_id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getTimestamp("created_at")
        );
    }
}
