<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.dao.ProductDAO" %>
<%@ page import="com.suhanamart.dao.CartDAO" %>
<%@ page import="com.suhanamart.dao.OrderDAO" %>
<%@ page import="com.suhanamart.model.Product" %>
<%@ page import="com.suhanamart.model.CartItem" %>
<%@ page import="com.suhanamart.model.Order" %>
<%@ page import="java.util.List" %>
<%!
    /**
     * Minimal HTML-escaping so the user's name (which came from their
     * own registration input) can never break out of the markup here,
     * e.g. via a name containing "<script>" or stray quotes.
     */
    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
%>
<%
    // AuthFilter already guarantees session != null and userId != null
    // for this page, but we still read defensively in case that ever
    // changes.
    String sessionName = (String) session.getAttribute("name");
    String displayName = escapeHtml(sessionName != null ? sessionName : "there");
    String activeNav = "dashboard";
    String pageTitle = "Dashboard";
    String[] pageCss = { "dashboard.css" };

    int userId = (Integer) session.getAttribute("userId");

    ProductDAO productDAO = new ProductDAO();
    CartDAO cartDAO = new CartDAO();
    OrderDAO orderDAO = new OrderDAO();

    List<Product> featuredProducts = java.util.Collections.emptyList();
    List<CartItem> cartItems = java.util.Collections.emptyList();
    List<Order> recentOrdersToShow = java.util.Collections.emptyList();
    int totalOrdersCount = 0;
    java.math.BigDecimal totalSpent = java.math.BigDecimal.ZERO;

    try {
        featuredProducts = productDAO.searchProducts(null, null);
        if (featuredProducts.size() > 4) {
            featuredProducts = featuredProducts.subList(0, 4);
        }

        cartItems = cartDAO.getCartItems(userId);

        List<Order> recentOrders = orderDAO.getOrdersForUser(userId);
        totalOrdersCount = recentOrders.size();
        for (Order order : recentOrders) {
            totalSpent = totalSpent.add(order.getTotalAmount());
        }
        recentOrdersToShow = recentOrders.size() > 3 ? recentOrders.subList(0, 3) : recentOrders;

    } catch (java.sql.SQLException e) {
        // Database hiccup: show the dashboard with safe empty states
        // rather than an error page. Full detail stays server-side.
        System.err.println("[SUHANAMART] Dashboard data load failed for user " + userId + ":");
        e.printStackTrace();
    }
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>

<%@ include file="/WEB-INF/jspf/navbar.jspf" %>

<main class="dash-main">

    <!-- Welcome section -->
    <section class="welcome-banner">
        <h1>Welcome back, <%= displayName %> <span aria-hidden="true">&#128075;</span></h1>
        <p>Here's what's happening with your SUHANAMART account today.</p>
    </section>

    <!-- Quick actions -->
    <section class="quick-actions">
        <a href="<%= request.getContextPath() %>/products" class="action-card">
            <span class="action-icon" aria-hidden="true">&#128722;</span>
            <span class="action-label">Browse Products</span>
        </a>

        <a href="<%= request.getContextPath() %>/public/cart.jsp" class="action-card">
            <span class="action-icon" aria-hidden="true">&#128717;</span>
            <span class="action-label">My Cart</span>
        </a>

        <a href="<%= request.getContextPath() %>/public/orders.jsp" class="action-card">
            <span class="action-icon" aria-hidden="true">&#128230;</span>
            <span class="action-label">My Orders</span>
        </a>

        <a href="<%= request.getContextPath() %>/public/profile.jsp" class="action-card">
            <span class="action-icon" aria-hidden="true">&#128100;</span>
            <span class="action-label">My Profile</span>
        </a>
    </section>

    <!-- Stats -->
    <section class="stats-row">
        <div class="stat-card">
            <span class="stat-value"><%= totalOrdersCount %></span>
            <span class="stat-label">Total Orders</span>
        </div>

        <div class="stat-card">
            <span class="stat-value"><%= cartItems.size() %></span>
            <span class="stat-label">Cart Items</span>
        </div>

        <div class="stat-card">
            <span class="stat-value">&#8377;<%= totalSpent %></span>
            <span class="stat-label">Total Spent</span>
        </div>
    </section>

    <div class="dash-grid">

        <!-- Featured products -->
        <section class="panel">
            <div class="panel-header">
                <h2>Featured Products</h2>
            </div>

            <% if (featuredProducts.isEmpty()) { %>
                <div class="empty-state">
                    <span class="empty-icon" aria-hidden="true">&#128717;</span>
                    <p>No products available yet.</p>
                    <span class="empty-subtext">Check back soon &mdash; new products are on the way.</span>
                </div>
            <% } else { %>
                <div class="mini-product-list">
                    <% for (Product p : featuredProducts) { %>
                        <a class="mini-product-row" href="<%= request.getContextPath() %>/product-details?id=<%= p.getProductId() %>">
                            <span class="mini-product-name"><%= escapeHtml(p.getName()) %></span>
                            <span class="mini-product-price">&#8377;<%= p.getPrice() %></span>
                        </a>
                    <% } %>
                </div>
            <% } %>
        </section>

        <!-- Cart summary -->
        <section class="panel">
            <div class="panel-header">
                <h2>Cart Summary</h2>
            </div>

            <% if (cartItems.isEmpty()) { %>
                <div class="empty-state">
                    <span class="empty-icon" aria-hidden="true">&#128717;</span>
                    <p>Your cart is empty.</p>
                    <a href="<%= request.getContextPath() %>/products" class="empty-action">Start Shopping</a>
                </div>
            <% } else {
                java.math.BigDecimal cartTotal = java.math.BigDecimal.ZERO;
                for (CartItem item : cartItems) {
                    cartTotal = cartTotal.add(item.getSubtotal());
                }
            %>
                <div class="mini-product-list">
                    <% for (CartItem item : cartItems) { %>
                        <div class="mini-product-row">
                            <span class="mini-product-name"><%= escapeHtml(item.getProductName()) %> &times; <%= item.getQuantity() %></span>
                            <span class="mini-product-price">&#8377;<%= item.getSubtotal() %></span>
                        </div>
                    <% } %>
                </div>
                <div class="mini-cart-total">
                    <span>Total</span>
                    <span>&#8377;<%= cartTotal %></span>
                </div>
                <a href="<%= request.getContextPath() %>/public/cart.jsp" class="empty-action">View Cart</a>
            <% } %>
        </section>

    </div>

    <!-- Recent orders -->
    <section class="panel panel-wide">
        <div class="panel-header">
            <h2>Recent Orders</h2>
        </div>

        <% if (recentOrdersToShow.isEmpty()) { %>
            <div class="empty-state">
                <span class="empty-icon" aria-hidden="true">&#128230;</span>
                <p>No orders yet.</p>
                <a href="<%= request.getContextPath() %>/products" class="empty-action">Start Shopping</a>
            </div>
        <% } else { %>
            <div class="mini-product-list">
                <% for (Order order : recentOrdersToShow) { %>
                    <a class="mini-product-row" href="<%= request.getContextPath() %>/public/order-details.jsp?orderId=<%= order.getOrderId() %>">
                        <span class="mini-product-name">Order #<%= order.getOrderId() %> &mdash; <%= order.getStatus() %></span>
                        <span class="mini-product-price">&#8377;<%= order.getTotalAmount() %></span>
                    </a>
                <% } %>
            </div>
        <% } %>
    </section>

</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>

<script src="<%= request.getContextPath() %>/assets/js/main.js"></script>

</body>
</html>
