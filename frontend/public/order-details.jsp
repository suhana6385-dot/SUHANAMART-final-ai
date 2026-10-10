<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.dao.OrderDAO" %>
<%@ page import="com.suhanamart.model.Order" %>
<%@ page import="com.suhanamart.model.OrderItem" %>
<%@ page import="com.suhanamart.service.ReviewService" %>
<%@ page import="com.suhanamart.util.Flash" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.List" %>
<%@ page import="java.sql.SQLException" %>
<%!
    private String escapeHtml(String value) {
        if (value == null) { return ""; }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    String activeNav = "orders";
    String pageTitle = "Order Details";
    String[] pageCss = { "dashboard.css", "shop.css", "review.css" };

    int userId = (Integer) session.getAttribute("userId");
    boolean justPlaced = "1".equals(request.getParameter("justPlaced"));

    // One-time message from ReviewServlet (e.g. "You have already reviewed this product").
    Flash.consume(request);
    String flashSuccess = (String) request.getAttribute("flashSuccess");
    String flashError = (String) request.getAttribute("flashError");

    OrderDAO orderDAO = new OrderDAO();
    Order order = null;
    List<OrderItem> items = java.util.Collections.emptyList();
    // Which lines may be reviewed (decided by ReviewService; this page only shows the result).
    Map<Integer, ReviewService.Eligibility> reviewState = java.util.Collections.emptyMap();
    boolean loadError = false;
    boolean invalidId = false;

    String orderIdParam = request.getParameter("orderId");
    int orderId = -1;

    try {
        orderId = Integer.parseInt(orderIdParam);
    } catch (Exception e) {
        invalidId = true;
    }

    if (!invalidId) {
        try {
            // getOrderForUser only returns the order if it belongs to
            // this user - prevents viewing someone else's order by
            // guessing an id in the URL.
            order = orderDAO.getOrderForUser(orderId, userId);
            if (order != null) {
                items = orderDAO.getOrderItems(orderId);
            }
        } catch (SQLException e) {
            System.err.println("[SUHANAMART] Failed to load order " + orderId + " for user " + userId + ":");
            e.printStackTrace();
            loadError = true;
        }

        if (order != null && !loadError) {
            try {
                reviewState = new ReviewService().getEligibilityForOrder(userId, orderId);
            } catch (SQLException e) {
                // The order itself still shows; the review actions just aren't offered.
                System.err.println("[SUHANAMART] Failed to load review state for order " + orderId + ":");
                e.printStackTrace();
            }
        }
    }
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>

<%@ include file="/WEB-INF/jspf/navbar.jspf" %>

<main class="dash-main">

    <a href="<%= request.getContextPath() %>/public/orders.jsp" class="back-link">&larr; Back to Orders</a>

    <% if (loadError) { %>
        <div class="alert alert-error">Something went wrong loading this order. Please try again later.</div>

    <% } else if (invalidId || order == null) { %>
        <div class="panel">
            <div class="empty-state">
                <span class="empty-icon" aria-hidden="true">&#128230;</span>
                <p>We couldn't find that order.</p>
                <a href="<%= request.getContextPath() %>/public/orders.jsp" class="empty-action">View My Orders</a>
            </div>
        </div>

    <% } else { %>

        <% if (flashSuccess != null) { %><div class="alert alert-success"><%= escapeHtml(flashSuccess) %></div><% } %>
        <% if (flashError != null) { %><div class="alert alert-error"><%= escapeHtml(flashError) %></div><% } %>

        <% if (justPlaced) { %>
            <div class="alert alert-success">Your order has been placed successfully!</div>
        <% } %>

        <div class="page-header-row">
            <div>
                <h1 class="page-title">Order #<%= order.getOrderId() %></h1>
                <p class="page-subtext">Placed on <%= order.getCreatedAt() %></p>
            </div>
            <span class="order-status"><%= escapeHtml(order.getStatus()) %></span>
        </div>

        <div class="panel">
            <div class="panel-header">
                <h2>Items</h2>
            </div>

            <div class="mini-product-list">
                <% for (OrderItem item : items) { %>
                    <div class="mini-product-row">
                        <span class="mini-product-name"><%= escapeHtml(item.getProductName()) %> &times; <%= item.getQuantity() %></span>
                        <% if (item.getStatus() != null) { %>
                            <span class="order-status"><%= escapeHtml(com.suhanamart.model.OrderStatus.fromDb(item.getStatus()).getLabel()) %></span>
                        <% } %>
                        <% ReviewService.Eligibility eligibility = reviewState.get(item.getProductId()); %>
                        <% if (eligibility == ReviewService.Eligibility.REVIEWABLE) { %>
                            <a class="btn btn-secondary review-action"
                               href="<%= request.getContextPath() %>/reviews?orderId=<%= order.getOrderId() %>&amp;productId=<%= item.getProductId() %>">Write a review</a>
                        <% } else if (eligibility == ReviewService.Eligibility.ALREADY_REVIEWED) { %>
                            <a class="review-done"
                               href="<%= request.getContextPath() %>/product-details?id=<%= item.getProductId() %>#reviews">Reviewed &#10003;</a>
                        <% } %>
                        <span class="mini-product-price">&#8377;<%= item.getSubtotal() %></span>
                    </div>
                <% } %>
            </div>

            <div class="mini-cart-total">
                <span>Total</span>
                <span>&#8377;<%= order.getTotalAmount() %></span>
            </div>

            <div class="profile-row" style="margin-top: 14px;">
                <span>Payment method</span>
                <span><%= escapeHtml(order.getPaymentMethod()) %></span>
            </div>
        </div>
    <% } %>

</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>

<script src="<%= request.getContextPath() %>/assets/js/main.js"></script>

</body>
</html>
