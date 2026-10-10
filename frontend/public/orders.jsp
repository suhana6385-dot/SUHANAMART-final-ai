<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.dao.OrderDAO" %>
<%@ page import="com.suhanamart.model.Order" %>
<%@ page import="com.suhanamart.util.Flash" %>
<%@ page import="com.suhanamart.util.SecurityUtil" %>
<%@ page import="java.util.List" %>
<%@ page import="java.sql.SQLException" %>
<%
    String activeNav = "orders";
    String pageTitle = "My Orders";
    String[] pageCss = { "dashboard.css", "shop.css" };

    int userId = (Integer) session.getAttribute("userId");

    // One-time message from ReviewServlet (e.g. "We couldn't find that item in your orders.").
    Flash.consume(request);
    String flashSuccess = (String) request.getAttribute("flashSuccess");
    String flashError = (String) request.getAttribute("flashError");

    OrderDAO orderDAO = new OrderDAO();
    List<Order> orders = java.util.Collections.emptyList();
    boolean loadError = false;

    try {
        orders = orderDAO.getOrdersForUser(userId);
    } catch (SQLException e) {
        System.err.println("[SUHANAMART] Failed to load order history for user " + userId + ":");
        e.printStackTrace();
        loadError = true;
    }
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>

<%@ include file="/WEB-INF/jspf/navbar.jspf" %>

<main class="dash-main">

    <div class="page-header-row">
        <div>
            <h1 class="page-title">My Orders</h1>
            <p class="page-subtext">Your past orders with SUHANAMART.</p>
        </div>
    </div>

    <% if (flashSuccess != null) { %><div class="alert alert-success"><%= SecurityUtil.escapeHtml(flashSuccess) %></div><% } %>
    <% if (flashError != null) { %><div class="alert alert-error"><%= SecurityUtil.escapeHtml(flashError) %></div><% } %>

    <% if (loadError) { %>
        <div class="alert alert-error">Something went wrong loading your orders. Please try again later.</div>

    <% } else if (orders.isEmpty()) { %>
        <div class="panel">
            <div class="empty-state">
                <span class="empty-icon" aria-hidden="true">&#128230;</span>
                <p>No orders yet.</p>
                <a href="<%= request.getContextPath() %>/products" class="empty-action">Start Shopping</a>
            </div>
        </div>

    <% } else { %>
        <% for (Order order : orders) { %>
            <a href="<%= request.getContextPath() %>/public/order-details.jsp?orderId=<%= order.getOrderId() %>" style="display:block;">
                <div class="order-card">
                    <div class="order-card-header">
                        <span class="order-id">Order #<%= order.getOrderId() %></span>
                        <span class="order-status"><%= SecurityUtil.escapeHtml(order.getStatus()) %></span>
                    </div>
                    <div class="order-meta">Placed on <%= order.getCreatedAt() %></div>
                    <div class="order-total">&#8377;<%= order.getTotalAmount() %></div>
                </div>
            </a>
        <% } %>
    <% } %>

</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>

<script src="<%= request.getContextPath() %>/assets/js/main.js"></script>

</body>
</html>
