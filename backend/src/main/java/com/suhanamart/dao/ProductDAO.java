package com.suhanamart.dao;

import com.suhanamart.model.Product;
import com.suhanamart.model.ProductStats;
import com.suhanamart.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ProductDAO {

    /**
     * Lists products, optionally filtered by a name search term and/or
     * an exact category. Either or both may be null/blank to skip that
     * filter. Always uses PreparedStatement placeholders - the search
     * term is never concatenated into the SQL string.
     */
    public List<Product> searchProducts(String nameKeyword, String category) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT product_id, name, description, price, category, image_url, stock FROM products WHERE 1=1");

        boolean hasKeyword = nameKeyword != null && !nameKeyword.trim().isEmpty();
        boolean hasCategory = category != null && !category.trim().isEmpty();

        if (hasKeyword) {
            sql.append(" AND name LIKE ?");
        }
        if (hasCategory) {
            sql.append(" AND category = ?");
        }
        sql.append(" ORDER BY name ASC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (hasKeyword) {
                ps.setString(paramIndex++, "%" + nameKeyword.trim() + "%");
            }
            if (hasCategory) {
                ps.setString(paramIndex++, category.trim());
            }

            try (ResultSet rs = ps.executeQuery()) {
                List<Product> products = new ArrayList<>();
                while (rs.next()) {
                    products.add(mapRow(rs));
                }
                return products;
            }
        }
    }

    public Product findById(int productId) throws SQLException {
        String sql = "SELECT product_id, name, description, price, category, image_url, stock "
                + "FROM products WHERE product_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        }
    }

    /**
     * Returns the distinct list of categories currently in use, for
     * building the filter dropdown. Empty list if there are no
     * products yet.
     */
    public List<String> listCategories() throws SQLException {
        String sql = "SELECT DISTINCT category FROM products ORDER BY category ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<String> categories = new ArrayList<>();
            while (rs.next()) {
                categories.add(rs.getString("category"));
            }
            return categories;
        }
    }

    /**
     * Decreases stock for a product by the given quantity, but only if
     * enough stock is currently available (the WHERE clause guards
     * against a race between two simultaneous checkouts). Returns true
     * if the row was actually updated (i.e. stock was sufficient).
     *
     * Intended to be called from inside a transaction managed by the
     * caller (see OrderDAO), using the same Connection so it can be
     * rolled back together with the rest of the order.
     */
    public boolean decreaseStock(Connection conn, int productId, int quantity) throws SQLException {
        String sql = "UPDATE products SET stock = stock - ? WHERE product_id = ? AND stock >= ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setInt(2, productId);
            ps.setInt(3, quantity);
            return ps.executeUpdate() > 0;
        }
    }

    // ---------------------------------------------------------------
    // Seller module
    //
    // Every method below takes the seller's id and puts
    // "seller_id = ?" directly in the SQL WHERE clause, so a seller can
    // only ever read or change rows they own - ownership is enforced by
    // the database query itself, not by the caller remembering to check.
    // ---------------------------------------------------------------

    private static final String SELLER_COLUMNS =
            "product_id, name, description, price, category, image_url, stock, seller_id";

    /** All products owned by this seller, newest first. */
    public List<Product> findBySeller(int sellerId) throws SQLException {
        String sql = "SELECT " + SELLER_COLUMNS + " FROM products "
                + "WHERE seller_id = ? ORDER BY created_at DESC, product_id DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, sellerId);

            try (ResultSet rs = ps.executeQuery()) {
                List<Product> products = new ArrayList<>();
                while (rs.next()) {
                    products.add(mapRowWithSeller(rs));
                }
                return products;
            }
        }
    }

    /** One product, but ONLY if it belongs to this seller; otherwise null. */
    public Product findByIdAndSeller(int productId, int sellerId) throws SQLException {
        String sql = "SELECT " + SELLER_COLUMNS + " FROM products WHERE product_id = ? AND seller_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);
            ps.setInt(2, sellerId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRowWithSeller(rs) : null;
            }
        }
    }

    /** Inserts a product owned by the seller and returns its new id. */
    public int insertForSeller(int sellerId, String name, String description, BigDecimal price,
                               String category, String imageUrl, int stock) throws SQLException {
        String sql = "INSERT INTO products (name, description, price, category, image_url, stock, seller_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name);
            ps.setString(2, description);
            ps.setBigDecimal(3, price);
            ps.setString(4, category);
            ps.setString(5, imageUrl);
            ps.setInt(6, stock);
            ps.setInt(7, sellerId);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** Updates the product only if this seller owns it. Returns false if nothing was updated. */
    public boolean updateOwned(int productId, int sellerId, String name, String description,
                               BigDecimal price, String category, String imageUrl, int stock) throws SQLException {
        String sql = "UPDATE products SET name = ?, description = ?, price = ?, category = ?, "
                + "image_url = ?, stock = ? WHERE product_id = ? AND seller_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, description);
            ps.setBigDecimal(3, price);
            ps.setString(4, category);
            ps.setString(5, imageUrl);
            ps.setInt(6, stock);
            ps.setInt(7, productId);
            ps.setInt(8, sellerId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Sets the stock quantity only if this seller owns the product. */
    public boolean updateStockOwned(int productId, int sellerId, int stock) throws SQLException {
        String sql = "UPDATE products SET stock = ? WHERE product_id = ? AND seller_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, stock);
            ps.setInt(2, productId);
            ps.setInt(3, sellerId);
            return ps.executeUpdate() > 0;
        }
    }

    /** True if any order line references this product (such a product can no longer be deleted). */
    public boolean hasOrderItems(int productId) throws SQLException {
        String sql = "SELECT 1 FROM order_items WHERE product_id = ? LIMIT 1";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Deletes the product only if this seller owns it AND no order line
     * references it (order history must keep pointing at a real
     * product). Returns false if nothing was deleted.
     */
    public boolean deleteOwned(int productId, int sellerId) throws SQLException {
        String sql = "DELETE FROM products WHERE product_id = ? AND seller_id = ? "
                + "AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = products.product_id)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);
            ps.setInt(2, sellerId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Dashboard counts for this seller, computed in one query from products.stock. */
    public ProductStats getStatsForSeller(int sellerId, int lowStockThreshold) throws SQLException {
        String sql = "SELECT COUNT(*) AS total, "
                + "COUNT(CASE WHEN stock > 0 THEN 1 END) AS active, "
                + "COUNT(CASE WHEN stock > 0 AND stock <= ? THEN 1 END) AS low_stock, "
                + "COUNT(CASE WHEN stock = 0 THEN 1 END) AS out_of_stock "
                + "FROM products WHERE seller_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, lowStockThreshold);
            ps.setInt(2, sellerId);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new ProductStats(
                        rs.getInt("total"),
                        rs.getInt("active"),
                        rs.getInt("low_stock"),
                        rs.getInt("out_of_stock"));
            }
        }
    }

    /** This seller's products at or below the threshold (including sold-out ones), emptiest first. */
    public List<Product> findLowStockBySeller(int sellerId, int lowStockThreshold, int limit) throws SQLException {
        String sql = "SELECT " + SELLER_COLUMNS + " FROM products "
                + "WHERE seller_id = ? AND stock <= ? ORDER BY stock ASC, name ASC LIMIT ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, sellerId);
            ps.setInt(2, lowStockThreshold);
            ps.setInt(3, limit);

            try (ResultSet rs = ps.executeQuery()) {
                List<Product> products = new ArrayList<>();
                while (rs.next()) {
                    products.add(mapRowWithSeller(rs));
                }
                return products;
            }
        }
    }

    private Product mapRowWithSeller(ResultSet rs) throws SQLException {
        int sellerId = rs.getInt("seller_id");
        Integer owner = rs.wasNull() ? null : sellerId;

        return new Product(
                rs.getInt("product_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                rs.getString("category"),
                rs.getString("image_url"),
                rs.getInt("stock"),
                owner
        );
    }

    private Product mapRow(ResultSet rs) throws SQLException {
        return new Product(
                rs.getInt("product_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                rs.getString("category"),
                rs.getString("image_url"),
                rs.getInt("stock")
        );
    }
}
