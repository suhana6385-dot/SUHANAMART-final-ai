<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.model.OrderItem" %>
<%@ page import="com.suhanamart.model.OrderStatus" %>
<%@ page import="com.suhanamart.model.Product" %>
<%@ page import="com.suhanamart.model.ProductStats" %>
<%@ page import="com.suhanamart.model.SellerOrder" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ include file="/WEB-INF/jspf/seller-util.jspf" %>
<%
    // Data is loaded by SellerDashboardServlet (GET /seller/dashboard).
    // Opening this JSP URL directly is redirected there by RoleFilter.
    String activeNav = "dashboard";
    String pageTitle = "Seller Dashboard";
    String[] pageCss = { "dashboard.css", "shop.css", "seller.css" };

    String sellerName = (String) session.getAttribute("name");
    ProductStats stats = (ProductStats) request.getAttribute("productStats");
    @SuppressWarnings("unchecked")
    Map<OrderStatus, Integer> orderCounts = (Map<OrderStatus, Integer>) request.getAttribute("orderCounts");
    @SuppressWarnings("unchecked")
    List<SellerOrder> recentOrders = (List<SellerOrder>) request.getAttribute("recentOrders");
    @SuppressWarnings("unchecked")
    List<Product> lowStockProducts = (List<Product>) request.getAttribute("lowStockProducts");
    int lowStockThreshold = (Integer) request.getAttribute("lowStockThreshold");
    boolean loadError = Boolean.TRUE.equals(request.getAttribute("loadError"));

    String flashSuccess = (String) request.getAttribute("flashSuccess");
    String flashError = (String) request.getAttribute("flashError");
    String ctx = request.getContextPath();
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<%@ include file="/WEB-INF/jspf/seller-navbar.jspf" %>

<main class="dash-main">

    <section class="welcome-banner">
        <h1>Welcome, <%= h(sellerName != null ? sellerName : "seller") %></h1>
        <p>Your SUHANAMART seller dashboard &mdash; products, stock and incoming orders at a glance.</p>
    </section>

    <% if (flashSuccess != null) { %><div class="alert alert-success"><%= h(flashSuccess) %></div><% } %>
    <% if (flashError != null) { %><div class="alert alert-error"><%= h(flashError) %></div><% } %>

    <% if (loadError) { %>
        <div class="alert alert-error">Something went wrong loading your dashboard. Please try again later.</div>
    <% } else { %>

        <h2 class="section-label">Products</h2>
        <section class="stats-row stats-row-4">
            <a class="stat-card" href="<%= ctx %>/seller/products">
                <span class="stat-value"><%= stats.getTotal() %></span>
                <span class="stat-label">Total Products</span>
            </a>
            <a class="stat-card" href="<%= ctx %>/seller/products">
                <span class="stat-value"><%= stats.getActive() %></span>
                <span class="stat-label">Active Products</span>
                <span class="stat-note">In stock and available to buy</span>
            </a>
            <a class="stat-card" href="<%= ctx %>/seller/products">
                <span class="stat-value"><%= stats.getLowStock() %></span>
                <span class="stat-label">Low Stock</span>
                <span class="stat-note">1 to <%= lowStockThreshold %> left</span>
            </a>
            <a class="stat-card" href="<%= ctx %>/seller/products">
                <span class="stat-value"><%= stats.getOutOfStock() %></span>
                <span class="stat-label">Out of Stock</span>
                <span class="stat-note">Stock is 0</span>
            </a>
        </section>

        <h2 class="section-label">Orders</h2>
        <section class="stats-row stats-row-4">
            <% for (OrderStatus status : OrderStatus.values()) {
                   Integer count = orderCounts.get(status); %>
                <a class="stat-card" href="<%= ctx %>/seller/orders?status=<%= status.name() %>">
                    <span class="stat-value"><%= count != null ? count : 0 %></span>
                    <span class="stat-label"><%= h(status.getLabel()) %> Orders</span>
                </a>
            <% } %>
        </section>

        <div class="dash-grid">

            <section class="panel">
                <div class="panel-header"><h2>Recent Incoming Orders</h2></div>
                <% if (recentOrders.isEmpty()) { %>
                    <div class="empty-state">
                        <span class="empty-icon" aria-hidden="true">&#128230;</span>
                        <p>No orders yet.</p>
                        <span class="empty-subtext">Orders containing your products will appear here.</span>
                    </div>
                <% } else { %>
                    <div class="mini-product-list">
                        <% for (SellerOrder order : recentOrders) { %>
                            <a class="mini-product-row"
                               href="<%= ctx %>/seller/orders/details?orderId=<%= order.getOrderId() %>">
                                <span class="mini-product-name">
                                    Order #<%= order.getOrderId() %> &mdash; <%= h(order.getCustomerName()) %>
                                </span>
                                <span class="badge badge-<%= order.getStatus().getCssClass() %>"><%= h(order.getStatus().getLabel()) %></span>
                            </a>
                        <% } %>
                    </div>
                    <a href="<%= ctx %>/seller/orders" class="empty-action">View All Orders</a>
                <% } %>
            </section>

            <section class="panel">
                <div class="panel-header"><h2>Needs Restocking</h2></div>
                <% if (lowStockProducts.isEmpty()) { %>
                    <div class="empty-state">
                        <span class="empty-icon" aria-hidden="true">&#128717;</span>
                        <p>Nothing to restock.</p>
                        <span class="empty-subtext">Products with <%= lowStockThreshold %> or fewer left show up here.</span>
                    </div>
                <% } else { %>
                    <div class="mini-product-list">
                        <% for (Product p : lowStockProducts) { %>
                            <a class="mini-product-row" href="<%= ctx %>/seller/products/edit?id=<%= p.getProductId() %>">
                                <span class="mini-product-name"><%= h(p.getName()) %></span>
                                <span class="stock-tag <%= p.getStock() == 0 ? "out" : "low" %>">
                                    <%= p.getStock() == 0 ? "Out of stock" : p.getStock() + " left" %>
                                </span>
                            </a>
                        <% } %>
                    </div>
                <% } %>
            </section>

        </div>
    <% } %>

</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>

<script src="<%= ctx %>/assets/js/main.js"></script>
<script src="<%= ctx %>/assets/js/seller.js"></script>

</body>
</html>
