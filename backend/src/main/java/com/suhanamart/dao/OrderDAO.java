package com.suhanamart.dao;

import com.suhanamart.model.CartItem;
import com.suhanamart.model.Order;
import com.suhanamart.model.OrderItem;
import com.suhanamart.model.OrderStatus;
import com.suhanamart.model.SellerOrder;
import com.suhanamart.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderDAO {

    private final CartDAO cartDAO = new CartDAO();
    private final ProductDAO productDAO = new ProductDAO();

    /**
     * Thrown when checkout cannot proceed because the cart is empty
     * or one of the items no longer has enough stock. The message is
     * safe to show directly to the user (no SQL/DB details in it).
     */
    public static class CheckoutException extends Exception {
        public CheckoutException(String message) {
            super(message);
        }
    }

    /**
     * Places an order for everything currently in the user's cart, as
     * a single database transaction:
     *
     *   1. Re-reads the cart (server-side, not trusting any client input)
     *   2. Creates the "orders" row
     *   3. For each cart line: decreases stock (only if enough is available),
     *      inserts the matching "order_items" row
     *   4. Clears the cart
     *
     * If anything fails - including insufficient stock discovered at
     * this exact moment - the whole transaction is rolled back and
     * nothing is changed.
     *
     * @return the new order's id
     */
    public int placeOrder(int userId) throws SQLException, CheckoutException {
        List<CartItem> items = cartDAO.getCartItems(userId);

        if (items.isEmpty()) {
            throw new CheckoutException("Your cart is empty.");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : items) {
            total = total.add(item.getSubtotal());
        }

        Connection conn = DBConnection.getConnection();

        try {
            conn.setAutoCommit(false);

            int orderId = insertOrder(conn, userId, total);

            for (CartItem item : items) {
                boolean stockOk = productDAO.decreaseStock(conn, item.getProductId(), item.getQuantity());

                if (!stockOk) {
                    throw new CheckoutException(
                            "\"" + item.getProductName() + "\" no longer has enough stock. "
                                    + "Please update your cart and try again.");
                }

                insertOrderItem(conn, orderId, item);
            }

            cartDAO.clearCart(conn, userId, false);

            conn.commit();
            return orderId;

        } catch (SQLException e) {
            safeRollback(conn);
            throw e;
        } catch (CheckoutException e) {
            // Insufficient stock discovered mid-transaction - roll back
            // everything (order + any order_items already inserted).
            safeRollback(conn);
            throw e;
        } finally {
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    private int insertOrder(Connection conn, int userId, BigDecimal total) throws SQLException {
        String sql = "INSERT INTO orders (user_id, total_amount) VALUES (?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setBigDecimal(2, total);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private void insertOrderItem(Connection conn, int orderId, CartItem item) throws SQLException {
        String sql = "INSERT INTO order_items "
                + "(order_id, product_id, product_name, quantity, unit_price, subtotal) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, item.getProductId());
            ps.setString(3, item.getProductName());
            ps.setInt(4, item.getQuantity());
            ps.setBigDecimal(5, item.getUnitPrice());
            ps.setBigDecimal(6, item.getSubtotal());
            ps.executeUpdate();
        }
    }

    private void safeRollback(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // Best effort - the outer exception is what matters.
        }
    }

    // ---------- Order history ----------

    public List<Order> getOrdersForUser(int userId) throws SQLException {
        String sql = "SELECT order_id, total_amount, status, payment_method, created_at "
                + "FROM orders WHERE user_id = ? ORDER BY created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                List<Order> orders = new ArrayList<>();
                while (rs.next()) {
                    orders.add(mapOrder(rs));
                }
                return orders;
            }
        }
    }

    /**
     * Fetches one order's summary, but ONLY if it belongs to the given
     * user - this is what stops one user viewing another user's order
     * by guessing an order id in the URL. Returns null if the order
     * doesn't exist or doesn't belong to this user.
     */
    public Order getOrderForUser(int orderId, int userId) throws SQLException {
        String sql = "SELECT order_id, total_amount, status, payment_method, created_at "
                + "FROM orders WHERE order_id = ? AND user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, orderId);
            ps.setInt(2, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapOrder(rs);
                }
                return null;
            }
        }
    }

    public List<OrderItem> getOrderItems(int orderId) throws SQLException {
        String sql = "SELECT order_item_id, product_id, product_name, quantity, unit_price, subtotal, status "
                + "FROM order_items WHERE order_id = ? ORDER BY order_item_id ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, orderId);

            try (ResultSet rs = ps.executeQuery()) {
                List<OrderItem> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapOrderItem(rs));
                }
                return items;
            }
        }
    }

    // ---------------------------------------------------------------
    // Seller module
    //
    // A seller's orders are found by joining order_items -> products and
    // filtering on products.seller_id = ?. Only that seller's own lines
    // are ever selected, so an order that mixes several sellers'
    // products shows each seller just their own part of it.
    // ---------------------------------------------------------------

    private static final String SELLER_ORDER_SELECT =
            "SELECT o.order_id, o.created_at, o.payment_method, "
            + "u.name AS customer_name, u.email AS customer_email, "
            + "oi.order_item_id, oi.product_id, oi.product_name, oi.quantity, "
            + "oi.unit_price, oi.subtotal, oi.status "
            + "FROM order_items oi "
            + "JOIN products p ON p.product_id = oi.product_id "
            + "JOIN orders o ON o.order_id = oi.order_id "
            + "JOIN users u ON u.user_id = o.user_id "
            + "WHERE p.seller_id = ? ";

    private static final String SELLER_ORDER_SORT =
            "ORDER BY o.created_at DESC, o.order_id DESC, oi.order_item_id ASC";

    /** All orders containing this seller's products, newest first; optionally only lines at one status. */
    public List<SellerOrder> findSellerOrders(int sellerId, OrderStatus statusFilter) throws SQLException {
        String sql = SELLER_ORDER_SELECT
                + (statusFilter != null ? "AND oi.status = ? " : "")
                + SELLER_ORDER_SORT;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, sellerId);
            if (statusFilter != null) {
                ps.setString(2, statusFilter.name());
            }

            try (ResultSet rs = ps.executeQuery()) {
                return groupIntoSellerOrders(rs);
            }
        }
    }

    /** One order as this seller sees it, or null if the order has none of their products. */
    public SellerOrder findSellerOrder(int orderId, int sellerId) throws SQLException {
        String sql = SELLER_ORDER_SELECT + "AND o.order_id = ? " + SELLER_ORDER_SORT;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, sellerId);
            ps.setInt(2, orderId);

            try (ResultSet rs = ps.executeQuery()) {
                List<SellerOrder> orders = groupIntoSellerOrders(rs);
                return orders.isEmpty() ? null : orders.get(0);
            }
        }
    }

    /**
     * How many distinct orders this seller has at each status (a status
     * with no orders is present with 0). Counted from order_items so it
     * reflects the seller's own lines only.
     */
    public Map<OrderStatus, Integer> countSellerOrdersByStatus(int sellerId) throws SQLException {
        String sql = "SELECT oi.status AS status, COUNT(DISTINCT oi.order_id) AS order_count "
                + "FROM order_items oi JOIN products p ON p.product_id = oi.product_id "
                + "WHERE p.seller_id = ? GROUP BY oi.status";

        Map<OrderStatus, Integer> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status, 0);
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, sellerId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderStatus status = OrderStatus.fromDb(rs.getString("status"));
                    counts.merge(status, rs.getInt("order_count"), Integer::sum);
                }
            }
        }
        return counts;
    }

    // ---------------------------------------------------------------
    // Admin-facing order queries (AdminService). Same shape as the
    // seller queries above, but with NO seller_id filter - every line
    // of every order, across all sellers. Read-only: the Admin module
    // only monitors orders, it never changes their status.
    // ---------------------------------------------------------------

    private static final String ADMIN_ORDER_SELECT =
            "SELECT o.order_id, o.created_at, o.payment_method, "
            + "u.name AS customer_name, u.email AS customer_email, "
            + "oi.order_item_id, oi.product_id, oi.product_name, oi.quantity, "
            + "oi.unit_price, oi.subtotal, oi.status "
            + "FROM order_items oi "
            + "JOIN orders o ON o.order_id = oi.order_id "
            + "JOIN users u ON u.user_id = o.user_id ";

    /** Every order in the system, newest first; optionally only lines at one status. Admin use only. */
    public List<SellerOrder> findAllOrders(OrderStatus statusFilter) throws SQLException {
        String sql = "SELECT order_id FROM orders ORDER BY created_at DESC, order_id DESC";
        // Page the order ids first so a status filter on one line doesn't
        // drop the rest of that order's lines from the result.
        List<Integer> orderIds = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                orderIds.add(rs.getInt("order_id"));
            }
        }
        if (orderIds.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < orderIds.size(); i++) {
            placeholders.append(i == 0 ? "?" : ",?");
        }
        String itemSql = ADMIN_ORDER_SELECT + "WHERE o.order_id IN (" + placeholders + ") "
                + "ORDER BY o.created_at DESC, o.order_id DESC, oi.order_item_id ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(itemSql)) {

            int i = 1;
            for (Integer id : orderIds) {
                ps.setInt(i++, id);
            }

            try (ResultSet rs = ps.executeQuery()) {
                List<SellerOrder> orders = groupIntoSellerOrders(rs);
                if (statusFilter == null) {
                    return orders;
                }
                List<SellerOrder> filtered = new ArrayList<>();
                for (SellerOrder order : orders) {
                    if (order.getStatus() == statusFilter) {
                        filtered.add(order);
                    }
                }
                return filtered;
            }
        }
    }

    /** One order with ALL of its lines (every seller), or null if it doesn't exist. Admin use only. */
    public SellerOrder findAnyOrder(int orderId) throws SQLException {
        String sql = ADMIN_ORDER_SELECT + "WHERE o.order_id = ? " + SELLER_ORDER_SORT;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, orderId);

            try (ResultSet rs = ps.executeQuery()) {
                List<SellerOrder> orders = groupIntoSellerOrders(rs);
                return orders.isEmpty() ? null : orders.get(0);
            }
        }
    }

    /** How many orders in the whole system are at each overall status (a status with none is present with 0). Admin use only. */
    public Map<OrderStatus, Integer> countAllOrdersByStatus() throws SQLException {
        String sql = "SELECT status, COUNT(*) AS order_count FROM orders GROUP BY status";

        Map<OrderStatus, Integer> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status, 0);
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                OrderStatus status = OrderStatus.fromDb(rs.getString("status"));
                counts.merge(status, rs.getInt("order_count"), Integer::sum);
            }
        }
        return counts;
    }

    /**
     * Moves this seller's lines of one order from {@code from} to
     * {@code to}, then refreshes the order-level status buyers see.
     * Returns the number of lines changed (0 = nothing matched).
     *
     * Everything happens in one transaction:
     *   1. lock the order row, so two sellers updating the same order
     *      at the same moment are handled one after the other;
     *   2. compare-and-set: the UPDATE only touches lines that belong
     *      to this seller (JOIN on products.seller_id) AND are still at
     *      {@code from} - so a stale page, a double-click, or a
     *      tampered request simply changes nothing;
     *   3. set orders.status to the least-advanced status across ALL
     *      lines of the order (an order is only "Shipped" for the buyer
     *      once every seller has shipped their part).
     *
     * Deciding WHETHER from -> to is an allowed step is the service
     * layer's job (OrderService); this method only applies it safely.
     */
    public int updateSellerItemsStatus(int orderId, int sellerId, OrderStatus from, OrderStatus to)
            throws SQLException {

        String lockSql = "SELECT order_id FROM orders WHERE order_id = ? FOR UPDATE";
        String updateSql = "UPDATE order_items oi JOIN products p ON p.product_id = oi.product_id "
                + "SET oi.status = ? "
                + "WHERE oi.order_id = ? AND p.seller_id = ? AND oi.status = ?";
        String statusesSql = "SELECT status FROM order_items WHERE order_id = ? FOR UPDATE";
        String rollupSql = "UPDATE orders SET status = ? WHERE order_id = ?";

        Connection conn = DBConnection.getConnection();

        try {
            conn.setAutoCommit(false);

            try (PreparedStatement lock = conn.prepareStatement(lockSql)) {
                lock.setInt(1, orderId);
                try (ResultSet rs = lock.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return 0;
                    }
                }
            }

            int changed;
            try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                update.setString(1, to.name());
                update.setInt(2, orderId);
                update.setInt(3, sellerId);
                update.setString(4, from.name());
                changed = update.executeUpdate();
            }

            if (changed == 0) {
                conn.rollback();
                return 0;
            }

            List<OrderStatus> lineStatuses = new ArrayList<>();
            try (PreparedStatement statuses = conn.prepareStatement(statusesSql)) {
                statuses.setInt(1, orderId);
                try (ResultSet rs = statuses.executeQuery()) {
                    while (rs.next()) {
                        lineStatuses.add(OrderStatus.fromDb(rs.getString("status")));
                    }
                }
            }

            try (PreparedStatement rollup = conn.prepareStatement(rollupSql)) {
                rollup.setString(1, OrderStatus.leastAdvanced(lineStatuses).name());
                rollup.setInt(2, orderId);
                rollup.executeUpdate();
            }

            conn.commit();
            return changed;

        } catch (SQLException e) {
            safeRollback(conn);
            throw e;
        } finally {
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    private List<SellerOrder> groupIntoSellerOrders(ResultSet rs) throws SQLException {
        // LinkedHashMap keeps the ORDER BY (newest order first).
        Map<Integer, List<OrderItem>> itemsByOrder = new LinkedHashMap<>();
        Map<Integer, Object[]> headerData = new LinkedHashMap<>();

        while (rs.next()) {
            int orderId = rs.getInt("order_id");

            if (!headerData.containsKey(orderId)) {
                headerData.put(orderId, new Object[] {
                        rs.getTimestamp("created_at"),
                        rs.getString("payment_method"),
                        rs.getString("customer_name"),
                        rs.getString("customer_email")
                });
                itemsByOrder.put(orderId, new ArrayList<>());
            }
            itemsByOrder.get(orderId).add(mapOrderItem(rs));
        }

        List<SellerOrder> orders = new ArrayList<>();
        for (Map.Entry<Integer, Object[]> entry : headerData.entrySet()) {
            Object[] h = entry.getValue();
            orders.add(new SellerOrder(
                    entry.getKey(),
                    (java.sql.Timestamp) h[0],
                    (String) h[1],
                    (String) h[2],
                    (String) h[3],
                    itemsByOrder.get(entry.getKey())));
        }
        return orders;
    }

    private OrderItem mapOrderItem(ResultSet rs) throws SQLException {
        return new OrderItem(
                rs.getInt("order_item_id"),
                rs.getInt("product_id"),
                rs.getString("product_name"),
                rs.getInt("quantity"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("subtotal"),
                rs.getString("status")
        );
    }

    private Order mapOrder(ResultSet rs) throws SQLException {
        return new Order(
                rs.getInt("order_id"),
                rs.getBigDecimal("total_amount"),
                rs.getString("status"),
                rs.getString("payment_method"),
                rs.getTimestamp("created_at")
        );
    }
}
