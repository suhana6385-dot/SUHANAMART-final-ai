<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.model.OrderItem" %>
<%@ page import="com.suhanamart.model.OrderStatus" %>
<%@ page import="com.suhanamart.model.SellerOrder" %>
<%@ include file="/WEB-INF/jspf/seller-util.jspf" %>
<%
    // Loaded by SellerOrderServlet (GET /seller/orders/details?orderId=N). "order" is null
    // when the order doesn't exist OR has none of this seller's products (indistinguishable).
    String activeNav = "orders";
    String pageTitle = "Order Details";
    String[] pageCss = { "dashboard.css", "shop.css", "seller.css" };

    SellerOrder order = (SellerOrder) request.getAttribute("order");
    OrderStatus nextStatus = (OrderStatus) request.getAttribute("nextStatus");
    boolean loadError = Boolean.TRUE.equals(request.getAttribute("loadError"));
    String flashSuccess = (String) request.getAttribute("flashSuccess");
    String flashError = (String) request.getAttribute("flashError");
    String ctx = request.getContextPath();
    String csrf = SecurityUtil.csrfToken(session);
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<%@ include file="/WEB-INF/jspf/seller-navbar.jspf" %>

<main class="dash-main">

    <a href="<%= ctx %>/seller/orders" class="back-link">&larr; Back to Incoming Orders</a>

    <% if (flashSuccess != null) { %><div class="alert alert-success"><%= h(flashSuccess) %></div><% } %>
    <% if (flashError != null) { %><div class="alert alert-error"><%= h(flashError) %></div><% } %>

    <% if (loadError) { %>
        <div class="alert alert-error">Something went wrong loading this order. Please try again later.</div>

    <% } else if (order == null) { %>
        <div class="panel">
            <div class="empty-state">
                <span class="empty-icon" aria-hidden="true">&#128230;</span>
                <p>We couldn't find that order in your orders.</p>
                <a href="<%= ctx %>/seller/orders" class="empty-action">View Incoming Orders</a>
            </div>
        </div>

    <% } else {
           OrderStatus current = order.getStatus(); %>

        <div class="page-header-row">
            <div>
                <h1 class="page-title">Order #<%= order.getOrderId() %></h1>
                <p class="page-subtext">Placed on <%= h(fmt(order.getCreatedAt())) %></p>
            </div>
            <span class="badge badge-<%= current.getCssClass() %>"><%= h(current.getLabel()) %></span>
        </div>

        <div class="panel" style="margin-bottom:16px;">
            <div class="panel-header"><h2>Order Status</h2></div>

            <ol class="status-steps" aria-label="Order progress">
                <% for (OrderStatus step : OrderStatus.values()) {
                       String stepClass = step.ordinal() < current.ordinal() ? "done"
                               : (step == current ? "current" : ""); %>
                    <li class="<%= stepClass %>"><%= h(step.getLabel()) %></li>
                <% } %>
            </ol>

            <% if (nextStatus != null) { %>
                <form method="post" action="<%= ctx %>/seller/order-status" class="status-form"
                      data-confirm="Mark order #<%= order.getOrderId() %> as <%= h(nextStatus.getLabel()) %>? The buyer will see the new status.">
                    <input type="hidden" name="csrfToken" value="<%= h(csrf) %>">
                    <input type="hidden" name="orderId" value="<%= order.getOrderId() %>">
                    <input type="hidden" name="status" value="<%= nextStatus.name() %>">
                    <button type="submit" class="btn btn-primary btn-wide">Mark as <%= h(nextStatus.getLabel()) %></button>
                    <span class="muted">Status can only move forward, one step at a time.</span>
                </form>
            <% } else { %>
                <p class="muted">This order has been delivered. Its status can't be changed any more.</p>
            <% } %>
        </div>

        <div class="detail-grid">

            <section class="panel">
                <div class="panel-header"><h2>Customer &amp; Delivery</h2></div>
                <div class="profile-row"><span>Customer</span><span><%= h(order.getCustomerName()) %></span></div>
                <div class="profile-row"><span>Email</span><span><%= h(order.getCustomerEmail()) %></span></div>
                <div class="profile-row"><span>Payment method</span><span><%= h(order.getPaymentMethod()) %></span></div>
                <p class="detail-note">
                    Checkout does not collect a shipping address yet, so the customer's name and email
                    are the only contact details available for this order.
                </p>
            </section>

            <section class="panel">
                <div class="panel-header"><h2>Summary</h2></div>
                <div class="profile-row"><span>Your items</span><span><%= order.getItems().size() %></span></div>
                <div class="profile-row"><span>Total units</span><span><%= order.getTotalQuantity() %></span></div>
                <div class="profile-row"><span>Your total</span><span>&#8377;<%= order.getSellerTotal() %></span></div>
                <p class="detail-note">
                    If this order also contains products from other sellers, their items and amounts are not shown here.
                </p>
            </section>

        </div>

        <div class="panel">
            <div class="panel-header"><h2>Your Items in This Order</h2></div>
            <div class="table-wrap">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>Product</th>
                            <th class="col-num">Unit Price</th>
                            <th class="col-num">Qty</th>
                            <th class="col-num">Subtotal</th>
                            <th>Status</th>
                        </tr>
                    </thead>
                    <tbody>
                    <% for (OrderItem item : order.getItems()) {
                           OrderStatus itemStatus = OrderStatus.fromDb(item.getStatus()); %>
                        <tr>
                            <td data-label="Product"><span class="cell-title"><%= h(item.getProductName()) %></span></td>
                            <td data-label="Unit Price" class="col-num">&#8377;<%= item.getUnitPrice() %></td>
                            <td data-label="Qty" class="col-num"><%= item.getQuantity() %></td>
                            <td data-label="Subtotal" class="col-num">&#8377;<%= item.getSubtotal() %></td>
                            <td data-label="Status">
                                <span class="badge badge-<%= itemStatus.getCssClass() %>"><%= h(itemStatus.getLabel()) %></span>
                            </td>
                        </tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
        </div>

    <% } %>

</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>

<script src="<%= ctx %>/assets/js/main.js"></script>
<script src="<%= ctx %>/assets/js/seller.js"></script>

</body>
</html>
