package com.suhanamart.dao;

import com.suhanamart.model.OrderStatus;
import com.suhanamart.model.Review;
import com.suhanamart.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * All database access for the Review Module. Every statement is a
 * PreparedStatement; nothing from the request is ever concatenated into
 * SQL.
 *
 * This class only reads facts and writes rows. WHICH purchases may be
 * reviewed, and what counts as a valid rating or comment, is decided by
 * ReviewService - with one deliberate exception: {@link #insertIfDelivered}
 * repeats the ownership / delivered / not-own-product checks inside the
 * INSERT itself, so the rule holds at the moment of writing even if the
 * order changes between the service's check and the insert.
 */
public class ReviewDAO {

    // ---------- Purchases (what can the buyer review?) ----------

    private static final String PURCHASED_ITEM_SELECT =
            "SELECT oi.order_id, oi.product_id, oi.product_name, p.image_url, p.seller_id, "
            + "o.status AS order_status, oi.status AS item_status, "
            + "EXISTS (SELECT 1 FROM reviews r WHERE r.user_id = o.user_id "
            + "        AND r.order_id = oi.order_id AND r.product_id = oi.product_id) AS reviewed "
            + "FROM order_items oi "
            + "JOIN orders o ON o.order_id = oi.order_id "
            + "JOIN products p ON p.product_id = oi.product_id "
            + "WHERE o.user_id = ? AND oi.order_id = ? ";

    /**
     * One line of an order - but ONLY if the order belongs to this user
     * (o.user_id = ? is in the query itself, so guessing someone else's
     * order or product id returns nothing). Null if there is no such line.
     */
    public Review.PurchasedItem findPurchasedItem(int userId, int orderId, int productId) throws SQLException {
        String sql = PURCHASED_ITEM_SELECT + "AND oi.product_id = ? ORDER BY oi.order_item_id ASC LIMIT 1";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, orderId);
            ps.setInt(3, productId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapPurchasedItem(rs) : null;
            }
        }
    }

    /** Every line of one of this user's orders (empty if the order isn't theirs / doesn't exist). */
    public List<Review.PurchasedItem> findPurchasedItems(int userId, int orderId) throws SQLException {
        String sql = PURCHASED_ITEM_SELECT + "ORDER BY oi.order_item_id ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, orderId);

            try (ResultSet rs = ps.executeQuery()) {
                List<Review.PurchasedItem> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapPurchasedItem(rs));
                }
                return items;
            }
        }
    }

    // ---------- Writing ----------

    /**
     * Stores a review for one of the user's purchases, as ONE statement
     * that inserts only if, right now:
     *
     *   - the order belongs to this user,
     *   - the product is really in that order,
     *   - the order AND that order line are DELIVERED,
     *   - the product is not the user's own (a seller cannot review
     *     their own product).
     *
     * @return the new review's id, or 0 if the purchase is not (or no
     *         longer) eligible and nothing was written
     * @throws java.sql.SQLIntegrityConstraintViolationException (error 1062) if this
     *         user has already reviewed this product for this order - the
     *         UNIQUE (user_id, order_id, product_id) key decides, so two
     *         simultaneous submissions can never both succeed
     */
    public int insertIfDelivered(int userId, int orderId, int productId, int rating, String comment)
            throws SQLException {

        String sql = "INSERT INTO reviews (user_id, product_id, order_id, rating, `comment`) "
                + "SELECT o.user_id, oi.product_id, oi.order_id, ?, ? "
                + "FROM order_items oi "
                + "JOIN orders o ON o.order_id = oi.order_id "
                + "JOIN products p ON p.product_id = oi.product_id "
                + "WHERE o.user_id = ? AND oi.order_id = ? AND oi.product_id = ? "
                + "AND o.status = ? AND oi.status = ? "
                + "AND (p.seller_id IS NULL OR p.seller_id <> o.user_id)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, rating);
            ps.setString(2, comment);
            ps.setInt(3, userId);
            ps.setInt(4, orderId);
            ps.setInt(5, productId);
            ps.setString(6, OrderStatus.DELIVERED.name());
            ps.setString(7, OrderStatus.DELIVERED.name());

            if (ps.executeUpdate() == 0) {
                return 0;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    // ---------- Reading reviews of a product ----------

    /** The newest reviews of a product (at most {@code limit}), with the reviewer's display name. */
    public List<Review> findByProduct(int productId, int limit) throws SQLException {
        String sql = "SELECT r.review_id, r.user_id, r.product_id, r.order_id, r.rating, r.`comment`, "
                + "r.created_at, r.updated_at, u.name AS reviewer_name "
                + "FROM reviews r JOIN users u ON u.user_id = r.user_id "
                + "WHERE r.product_id = ? "
                + "ORDER BY r.created_at DESC, r.review_id DESC LIMIT ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                List<Review> reviews = new ArrayList<>();
                while (rs.next()) {
                    reviews.add(new Review(
                            rs.getInt("review_id"),
                            rs.getInt("user_id"),
                            rs.getInt("product_id"),
                            rs.getInt("order_id"),
                            rs.getInt("rating"),
                            rs.getString("comment"),
                            rs.getTimestamp("created_at"),
                            rs.getTimestamp("updated_at"),
                            rs.getString("reviewer_name")));
                }
                return reviews;
            }
        }
    }

    /**
     * Number of reviews at each star for a product. The average is
     * derived from these exact counts (see Review.Summary) instead of
     * SQL AVG(), so it is rounded once, in one place, with BigDecimal.
     */
    public Review.Summary getSummary(int productId) throws SQLException {
        String sql = "SELECT rating, COUNT(*) AS review_count FROM reviews "
                + "WHERE product_id = ? GROUP BY rating";

        int[] counts = new int[Review.MAX_RATING + 1];

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int rating = rs.getInt("rating");
                    if (rating >= Review.MIN_RATING && rating <= Review.MAX_RATING) {
                        counts[rating] = rs.getInt("review_count");
                    }
                }
            }
        }
        return new Review.Summary(counts);
    }

    private Review.PurchasedItem mapPurchasedItem(ResultSet rs) throws SQLException {
        int sellerId = rs.getInt("seller_id");
        Integer owner = rs.wasNull() ? null : sellerId;

        return new Review.PurchasedItem(
                rs.getInt("order_id"),
                rs.getInt("product_id"),
                rs.getString("product_name"),
                rs.getString("image_url"),
                rs.getString("order_status"),
                rs.getString("item_status"),
                owner,
                rs.getBoolean("reviewed"));
    }
}
