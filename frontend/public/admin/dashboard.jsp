<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.model.OrderStatus" %>
<%@ page import="com.suhanamart.service.AdminService.DashboardStats" %>
<%@ page import="java.util.Map" %>
<%@ include file="/WEB-INF/jspf/admin-util.jspf" %>
<%
    // Data is loaded by AdminServlet (GET /admin/dashboard). Opening this JSP URL
    // directly is redirected there by RoleFilter.
    String activeNav = "dashboard";
    String pageTitle = "Admin Dashboard";
    String[] pageCss = { "dashboard.css", "shop.css", "seller.css" };

    String adminName = (String) session.getAttribute("name");
    DashboardStats stats = (DashboardStats) request.getAttribute("stats");
    boolean loadError = Boolean.TRUE.equals(request.getAttribute("loadError"));
    String flashSuccess = (String) request.getAttribute("flashSuccess");
    String flashError = (String) request.getAttribute("flashError");
    String ctx = request.getContextPath();
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<%@ include file="/WEB-INF/jspf/admin-navbar.jspf" %>

<main class="dash-main">

    <section class="welcome-banner">
        <h1>Welcome, <%= h(adminName != null ? adminName : "admin") %></h1>
        <p>SUHANAMART admin dashboard &mdash; users, products and orders at a glance.</p>
    </section>

    <% if (flashSuccess != null) { %><div class="alert alert-success"><%= h(flashSuccess) %></div><% } %>
    <% if (flashError != null) { %><div class="alert alert-error"><%= h(flashError) %></div><% } %>

    <% if (loadError) { %>
        <div class="alert alert-error">Something went wrong loading the dashboard. Please try again later.</div>
    <% } else { %>

        <h2 class="section-label">Users</h2>
        <section class="stats-row stats-row-4">
            <a class="stat-card" href="<%= ctx %>/admin/users">
                <span class="stat-value"><%= stats.getTotalUsers() %></span>
                <span class="stat-label">Total Users</span>
            </a>
            <a class="stat-card" href="<%= ctx %>/admin/users?role=BUYER">
                <span class="stat-value"><%= stats.getBuyerCount() %></span>
                <span class="stat-label">Buyers</span>
            </a>
            <a class="stat-card" href="<%= ctx %>/admin/users?role=SELLER">
                <span class="stat-value"><%= stats.getSellerCount() %></span>
                <span class="stat-label">Sellers</span>
            </a>
            <a class="stat-card" href="<%= ctx %>/admin/users?role=ADMIN">
                <span class="stat-value"><%= stats.getAdminCount() %></span>
                <span class="stat-label">Admins</span>
            </a>
        </section>

        <h2 class="section-label">Catalog &amp; Orders</h2>
        <section class="stats-row stats-row-4">
            <a class="stat-card" href="<%= ctx %>/admin/products">
                <span class="stat-value"><%= stats.getProductCount() %></span>
                <span class="stat-label">Products</span>
            </a>
            <a class="stat-card" href="<%= ctx %>/admin/orders">
                <span class="stat-value"><%= stats.getOrderCount() %></span>
                <span class="stat-label">Total Orders</span>
            </a>
            <% Map<OrderStatus, Integer> byStatus = stats.getOrdersByStatus();
               OrderStatus[] highlight = { OrderStatus.PENDING, OrderStatus.DELIVERED }; %>
            <% for (OrderStatus status : highlight) { Integer count = byStatus.get(status); %>
                <a class="stat-card" href="<%= ctx %>/admin/orders?status=<%= status.name() %>">
                    <span class="stat-value"><%= count != null ? count : 0 %></span>
                    <span class="stat-label"><%= h(status.getLabel()) %> Orders</span>
                </a>
            <% } %>
        </section>

        <section class="panel">
            <div class="panel-header"><h2>Orders by Status</h2></div>
            <div class="mini-product-list">
                <% for (OrderStatus status : OrderStatus.values()) {
                       Integer count = byStatus.get(status); %>
                    <a class="mini-product-row" href="<%= ctx %>/admin/orders?status=<%= status.name() %>">
                        <span class="mini-product-name"><%= h(status.getLabel()) %></span>
                        <span class="badge badge-<%= status.getCssClass() %>"><%= count != null ? count : 0 %></span>
                    </a>
                <% } %>
            </div>
        </section>

    <% } %>

</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>

<script src="<%= ctx %>/assets/js/main.js"></script>
<script src="<%= ctx %>/assets/js/admin.js"></script>

</body>
</html>
