package com.suhanamart.dao;

import com.suhanamart.model.CartItem;
import com.suhanamart.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CartDAO {

    public List<CartItem> getCartItems(int userId) throws SQLException {
        String sql = "SELECT c.cart_item_id, c.product_id, c.quantity, "
                + "p.name, p.image_url, p.price, p.stock "
                + "FROM cart_items c JOIN products p ON c.product_id = p.product_id "
                + "WHERE c.user_id = ? ORDER BY c.created_at ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                List<CartItem> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(new CartItem(
                            rs.getInt("cart_item_id"),
                            rs.getInt("product_id"),
                            rs.getString("name"),
                            rs.getString("image_url"),
                            rs.getBigDecimal("price"),
                            rs.getInt("quantity"),
                            rs.getInt("stock")
                    ));
                }
                return items;
            }
        }
    }

    public BigDecimal getCartTotal(int userId) throws SQLException {
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : getCartItems(userId)) {
            total = total.add(item.getSubtotal());
        }
        return total;
    }

    /**
     * Adds a product to the cart, or increases its quantity if it is
     * already there. The requested quantity is clamped to the
     * product's current stock so the cart can never hold more than
     * is actually available.
     */
    public void addToCart(int userId, int productId, int quantity) throws SQLException {
        String upsertSql =
                "INSERT INTO cart_items (user_id, product_id, quantity) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE quantity = LEAST(quantity + ?, "
                        + "(SELECT stock FROM products WHERE product_id = ?))";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(upsertSql)) {

            int clamped = Math.max(1, quantity);

            ps.setInt(1, userId);
            ps.setInt(2, productId);
            ps.setInt(3, clamped);
            ps.setInt(4, clamped);
            ps.setInt(5, productId);
            ps.executeUpdate();
        }
    }

    /**
     * Sets a cart item's quantity directly (used by the +/- controls).
     * A quantity of 0 or less removes the item. The new quantity is
     * clamped between 1 and the product's current stock.
     */
    public void updateQuantity(int userId, int cartItemId, int newQuantity) throws SQLException {
        if (newQuantity <= 0) {
            removeItem(userId, cartItemId);
            return;
        }

        String sql = "UPDATE cart_items c "
                + "JOIN products p ON c.product_id = p.product_id "
                + "SET c.quantity = LEAST(?, p.stock) "
                + "WHERE c.cart_item_id = ? AND c.user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, newQuantity);
            ps.setInt(2, cartItemId);
            ps.setInt(3, userId);
            ps.executeUpdate();
        }
    }

    public void removeItem(int userId, int cartItemId) throws SQLException {
        String sql = "DELETE FROM cart_items WHERE cart_item_id = ? AND user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, cartItemId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public void clearCart(int userId) throws SQLException {
        clearCart(DBConnection.getConnection(), userId, true);
    }

    /**
     * Clears the cart using an existing connection (used by checkout,
     * as part of the same transaction that creates the order).
     * closeConnection controls whether this method closes the
     * connection when done - false when the caller (OrderDAO) owns
     * the connection's lifecycle for the whole transaction.
     */
    public void clearCart(Connection conn, int userId, boolean closeConnection) throws SQLException {
        String sql = "DELETE FROM cart_items WHERE user_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } finally {
            if (closeConnection) {
                conn.close();
            }
        }
    }
}
