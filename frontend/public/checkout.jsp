<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.util.SecurityUtil" %>
<%@ page import="com.suhanamart.dao.CartDAO" %>
<%@ page import="com.suhanamart.model.CartItem" %>
<%@ page import="java.util.List" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.sql.SQLException" %>
<%!
    private String escapeHtml(String value) {
        if (value == null) { return ""; }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    String activeNav = "cart";
    // Sent with every state-changing form so other websites cannot submit it for the user.
    String csrfToken = SecurityUtil.csrfToken(session);
    String pageTitle = "Checkout";
    String[] pageCss = { "dashboard.css", "shop.css" };

    int userId = (Integer) session.getAttribute("userId");
    String userName = (String) session.getAttribute("name");

    String errorMessage = (String) request.getAttribute("errorMessage");

    CartDAO cartDAO = new CartDAO();
    List<CartItem> cartItems = java.util.Collections.emptyList();
    BigDecimal total = BigDecimal.ZERO;
    boolean loadError = false;

    try {
        cartItems = cartDAO.getCartItems(userId);
        for (CartItem item : cartItems) {
            total = total.add(item.getSubtotal());
        }
    } catch (SQLException e) {
        System.err.println("[SUHANAMART] Failed to load cart for checkout, user " + userId + ":");
        e.printStackTrace();
        loadError = true;
    }
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>

<%@ include file="/WEB-INF/jspf/navbar.jspf" %>

<main class="dash-main">

    <div class="page-header-row">
        <div>
            <h1 class="page-title">Checkout</h1>
            <p class="page-subtext">Review your order before placing it.</p>
        </div>
    </div>

    <% if (errorMessage != null) { %>
        <div class="alert alert-error"><%= escapeHtml(errorMessage) %></div>
    <% } %>

    <% if (loadError) { %>
        <div class="alert alert-error">Something went wrong loading your cart. Please try again later.</div>

    <% } else if (cartItems.isEmpty()) { %>
        <div class="panel">
            <div class="empty-state">
                <span class="empty-icon" aria-hidden="true">&#128717;</span>
                <p>Your cart is empty. Add something before checking out.</p>
                <a href="<%= request.getContextPath() %>/products" class="empty-action">Start Shopping</a>
            </div>
        </div>

    <% } else { %>
        <div class="checkout-layout">
            <div class="panel">
                <div class="panel-header">
                    <h2>Order Summary</h2>
                </div>

                <div class="mini-product-list">
                    <% for (CartItem item : cartItems) { %>
                        <div class="mini-product-row">
                            <span class="mini-product-name"><%= escapeHtml(item.getProductName()) %> &times; <%= item.getQuantity() %></span>
                            <span class="mini-product-price">&#8377;<%= item.getSubtotal() %></span>
                        </div>
                    <% } %>
                </div>

                <div class="profile-row" style="margin-top: 18px;">
                    <span>Deliver to</span>
                    <span><%= escapeHtml(userName) %></span>
                </div>
                <div class="profile-row">
                    <span>Payment method</span>
                    <span>Demo Payment (Cash on Delivery)</span>
                </div>
            </div>

            <div class="cart-summary-panel">
                <div class="summary-row">
                    <span>Items</span>
                    <span><%= cartItems.size() %></span>
                </div>
                <div class="summary-row total">
                    <span>Total</span>
                    <span>&#8377;<%= total %></span>
                </div>

                <form action="<%= request.getContextPath() %>/CheckoutServlet" method="post">
                    <input type="hidden" name="csrfToken" value="<%= csrfToken %>">
                    <button type="submit" class="btn btn-primary" style="width:100%; margin-top: 16px;">Place Order</button>
                </form>

                <p class="page-subtext" style="margin-top: 10px;">
                    This is a demo checkout &mdash; no real payment is processed.
                </p>
            </div>
        </div>
    <% } %>

</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>

<script src="<%= request.getContextPath() %>/assets/js/main.js"></script>

</body>
</html>
