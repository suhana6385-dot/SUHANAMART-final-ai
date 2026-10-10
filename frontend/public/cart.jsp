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
    String pageTitle = "My Cart";
    String[] pageCss = { "dashboard.css", "shop.css" };

    int userId = (Integer) session.getAttribute("userId");

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
        System.err.println("[SUHANAMART] Failed to load cart for user " + userId + ":");
        e.printStackTrace();
        loadError = true;
    }
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>

<%@ include file="/WEB-INF/jspf/navbar.jspf" %>

<main class="dash-main">

    <div class="page-header-row">
        <div>
            <h1 class="page-title">My Cart</h1>
            <p class="page-subtext">Review your items before checkout.</p>
        </div>
    </div>

    <% if (loadError) { %>
        <div class="alert alert-error">Something went wrong loading your cart. Please try again later.</div>

    <% } else if (cartItems.isEmpty()) { %>
        <div class="panel">
            <div class="empty-state">
                <span class="empty-icon" aria-hidden="true">&#128717;</span>
                <p>Your cart is empty.</p>
                <a href="<%= request.getContextPath() %>/products" class="empty-action">Start Shopping</a>
            </div>
        </div>

    <% } else { %>
        <div class="checkout-layout">
            <div>
                <table class="cart-table">
                    <thead>
                        <tr>
                            <th>Product</th>
                            <th>Price</th>
                            <th>Quantity</th>
                            <th>Subtotal</th>
                            <th></th>
                        </tr>
                    </thead>
                    <tbody>
                        <% for (CartItem item : cartItems) { %>
                            <tr>
                                <td>
                                    <div class="cart-product-cell">
                                        <div class="cart-thumb">
                                            <% if (item.getImageUrl() != null && !item.getImageUrl().trim().isEmpty()) { %>
                                                <img src="<%= escapeHtml(item.getImageUrl()) %>" alt="">
                                            <% } else { %>
                                                &#128230;
                                            <% } %>
                                        </div>
                                        <span><%= escapeHtml(item.getProductName()) %></span>
                                    </div>
                                </td>
                                <td>&#8377;<%= item.getUnitPrice() %></td>
                                <td>
                                    <div class="qty-form">
                                        <form action="<%= request.getContextPath() %>/CartServlet" method="post">
                                            <input type="hidden" name="csrfToken" value="<%= csrfToken %>">
                                            <input type="hidden" name="action" value="update">
                                            <input type="hidden" name="cartItemId" value="<%= item.getCartItemId() %>">
                                            <input type="hidden" name="quantity" value="<%= item.getQuantity() - 1 %>">
                                            <button type="submit" class="qty-btn" aria-label="Decrease quantity">&minus;</button>
                                        </form>

                                        <span class="qty-value"><%= item.getQuantity() %></span>

                                        <form action="<%= request.getContextPath() %>/CartServlet" method="post">
                                            <input type="hidden" name="csrfToken" value="<%= csrfToken %>">
                                            <input type="hidden" name="action" value="update">
                                            <input type="hidden" name="cartItemId" value="<%= item.getCartItemId() %>">
                                            <input type="hidden" name="quantity" value="<%= item.getQuantity() + 1 %>">
                                            <button type="submit" class="qty-btn" aria-label="Increase quantity"
                                                    <%= item.getQuantity() >= item.getStock() ? "disabled" : "" %>>+</button>
                                        </form>
                                    </div>
                                    <% if (item.getQuantity() >= item.getStock()) { %>
                                        <div class="page-subtext" style="margin-top:4px;">Max stock reached</div>
                                    <% } %>
                                </td>
                                <td>&#8377;<%= item.getSubtotal() %></td>
                                <td>
                                    <form action="<%= request.getContextPath() %>/CartServlet" method="post">
                                        <input type="hidden" name="csrfToken" value="<%= csrfToken %>">
                                        <input type="hidden" name="action" value="remove">
                                        <input type="hidden" name="cartItemId" value="<%= item.getCartItemId() %>">
                                        <button type="submit" class="remove-link" style="background:none;border:none;cursor:pointer;">Remove</button>
                                    </form>
                                </td>
                            </tr>
                        <% } %>
                    </tbody>
                </table>

                <form action="<%= request.getContextPath() %>/CartServlet" method="post" style="margin-top: 14px;">
                    <input type="hidden" name="csrfToken" value="<%= csrfToken %>">
                    <input type="hidden" name="action" value="clear">
                    <button type="submit" class="btn btn-secondary">Clear Cart</button>
                </form>
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
                <a href="<%= request.getContextPath() %>/public/checkout.jsp" class="btn btn-primary" style="width:100%; margin-top: 16px;">Proceed to Checkout</a>
            </div>
        </div>
    <% } %>

</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>

<script src="<%= request.getContextPath() %>/assets/js/main.js"></script>
<script src="<%= request.getContextPath() %>/assets/js/cart.js"></script>

</body>
</html>
