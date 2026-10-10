package com.suhanamart.dao;

import com.suhanamart.model.AccountStatus;
import com.suhanamart.model.Product;
import com.suhanamart.model.Role;
import com.suhanamart.model.User;
import com.suhanamart.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Database access for the Admin module (AdminService -> AdminDAO ->
 * database). Covers what no other DAO already provides: counting and
 * listing users across all roles, enabling/disabling an account, and
 * listing/removing products across every seller. Order monitoring
 * reuses the existing OrderDAO (findAllOrders / findAnyOrder /
 * countAllOrdersByStatus) instead of duplicating that query logic here.
 *
 * Every statement is a PreparedStatement.
 */
public class AdminDAO {

    // ---------------------------------------------------------------
    // Dashboard counts
    // ---------------------------------------------------------------

    /** Total users by role (a role with no users is present with 0), plus the product count. */
    public Map<Role, Integer> countUsersByRole() throws SQLException {
        String sql = "SELECT role, COUNT(*) AS user_count FROM users GROUP BY role";

        Map<Role, Integer> counts = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            counts.put(role, 0);
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Role role = Role.fromString(rs.getString("role"));
                counts.merge(role, rs.getInt("user_count"), Integer::sum);
            }
        }
        return counts;
    }

    public int countProducts() throws SQLException {
        return countRows("SELECT COUNT(*) FROM products");
    }

    public int countOrders() throws SQLException {
        return countRows("SELECT COUNT(*) FROM orders");
    }

    private int countRows(String sql) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ---------------------------------------------------------------
    // User management
    // ---------------------------------------------------------------

    /** Every user, optionally filtered to one role, newest first. Role and account status are always populated. */
    public List<User> listUsers(Role roleFilter) throws SQLException {
        String sql = "SELECT user_id, name, email, role, account_status, created_at FROM users "
                + (roleFilter != null ? "WHERE role = ? " : "")
                + "ORDER BY created_at DESC, user_id DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (roleFilter != null) {
                ps.setString(1, roleFilter.name());
            }

            try (ResultSet rs = ps.executeQuery()) {
                List<User> users = new ArrayList<>();
                while (rs.next()) {
                    users.add(new User(
                            rs.getInt("user_id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getTimestamp("created_at"),
                            Role.fromString(rs.getString("role")),
                            AccountStatus.fromDb(rs.getString("account_status"))));
                }
                return users;
            }
        }
    }

    /**
     * Enables or disables an account. The WHERE clause itself refuses to
     * touch an ADMIN account or the admin's own row, so this can never be
     * used (by a bug or a forged request) to lock every admin out of the
     * system - that is enforced here, not only in AdminService.
     *
     * @return true if a row was actually changed
     */
    public boolean setAccountStatus(int targetUserId, AccountStatus status, int actingAdminId) throws SQLException {
        String sql = "UPDATE users SET account_status = ? "
                + "WHERE user_id = ? AND user_id <> ? AND role <> 'ADMIN'";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            ps.setInt(2, targetUserId);
            ps.setInt(3, actingAdminId);
            return ps.executeUpdate() > 0;
        }
    }

    // ---------------------------------------------------------------
    // Product moderation
    // ---------------------------------------------------------------

    /** Every product in the catalog, newest first, with its seller's name (null for unowned/demo products). */
    public List<Product> listProducts() throws SQLException {
        String sql = "SELECT p.product_id, p.name, p.description, p.price, p.category, p.image_url, "
                + "p.stock, p.seller_id, u.name AS seller_name "
                + "FROM products p LEFT JOIN users u ON u.user_id = p.seller_id "
                + "ORDER BY p.created_at DESC, p.product_id DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Product> products = new ArrayList<>();
            while (rs.next()) {
                int sellerIdValue = rs.getInt("seller_id");
                Integer sellerId = rs.wasNull() ? null : sellerIdValue;

                products.add(new Product(
                        rs.getInt("product_id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBigDecimal("price"),
                        rs.getString("category"),
                        rs.getString("image_url"),
                        rs.getInt("stock"),
                        sellerId,
                        rs.getString("seller_name")));
            }
            return products;
        }
    }

    /**
     * Removes a listing regardless of which seller owns it - but only if
     * it has never been ordered (same safety rule ProductDAO.deleteOwned
     * uses for a seller removing their own product), so order history
     * and existing reviews are never left pointing at a missing product.
     *
     * @return true if the listing was actually removed
     */
    public boolean removeListing(int productId) throws SQLException {
        String sql = "DELETE FROM products WHERE product_id = ? "
                + "AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = products.product_id)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);
            return ps.executeUpdate() > 0;
        }
    }
}
