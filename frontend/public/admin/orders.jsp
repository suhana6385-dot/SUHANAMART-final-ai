<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.model.OrderItem" %>
<%@ page import="com.suhanamart.model.OrderStatus" %>
<%@ page import="com.suhanamart.model.SellerOrder" %>
<%@ page import="java.util.List" %>
<%@ include file="/WEB-INF/jspf/admin-util.jspf" %>
<%
    // Data is loaded by AdminOrderServlet (GET /admin/orders). Read-only: the
    // Admin module monitors orders but never changes their status.
    String activeNav = "orders";
    String pageTitle = "Orders";
    String[] pageCss = { "dashboard.css", "shop.css", "seller.css" };

    @SuppressWarnings("unchecked")
    List<SellerOrder> orders = (List<SellerOrder>) request.getAttribute("orders");
    OrderStatus activeFilter = (OrderStatus) request.getAttribute("activeFilter");
    SellerOrder selectedOrder = (SellerOrder) request.getAttribute("selectedOrder");
    boolean selectedOrderMissing = Boolean.TRUE.equals(request.getAttribute("selectedOrderMissing"));
    boolean loadError = Boolean.TRUE.equals(request.getAttribute("loadError"));
    String ctx = request.getContextPath();
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<%@ include file="/WEB-INF/jspf/admin-navbar.jspf" %>

<main class="dash-main">

    <div class="page-header-row">
        <div>
            <h1 class="page-title">Orders</h1>
            <p class="page-subtext">Every order on SUHANAMART, across every buyer and seller.</p>
        </div>
    </div>

    <% if (selectedOrderMissing) { %>
        <div class="alert alert-error">We couldn't find that order.</div>
    <% } %>

    <% if (selectedOrder != null) { %>
        <section class="panel" style="margin-bottom:20px;">
            <div class="panel-header">
                <h2>Order #<%= selectedOrder.getOrderId() %></h2>
            </div>
            <div class="detail-grid">
                <div>
                    <p class="muted">Placed <%= h(fmt(selectedOrder.getCreatedAt())) %></p>
                    <p class="muted">Payment: <%= h(selectedOrder.getPaymentMethod()) %></p>
                </div>
                <div>
                    <p class="muted">Customer: <%= h(selectedOrder.getCustomerName()) %></p>
                    <p class="muted"><%= h(selectedOrder.getCustomerEmail()) %></p>
                </div>
            </div>
            <span class="badge badge-<%= selectedOrder.getStatus().getCssClass() %>"><%= h(selectedOrder.getStatus().getLabel()) %></span>
            <ul class="line-list" style="margin-top:14px;">
                <% for (OrderItem item : selectedOrder.getItems()) { %>
                    <li><%= h(item.getProductName()) %> &times; <%= item.getQuantity() %>
                        &mdash; &#8377;<%= item.getSubtotal() %>
                        <span class="badge badge-<%= OrderStatus.fromDb(item.getStatus()).getCssClass() %>">
                            <%= h(OrderStatus.fromDb(item.getStatus()).getLabel()) %>
                        </span>
                    </li>
                <% } %>
            </ul>
            <p class="detail-note">Total: &#8377;<%= selectedOrder.getSellerTotal() %></p>
            <a href="<%= ctx %>/admin/orders<%= activeFilter != null ? "?status=" + activeFilter.name() : "" %>" class="btn btn-secondary btn-sm" style="margin-top:10px;">Close details</a>
        </section>
    <% } %>

    <nav class="filter-tabs" aria-label="Filter orders by status">
        <a class="filter-tab <%= activeFilter == null ? "active" : "" %>" href="<%= ctx %>/admin/orders">All</a>
        <% for (OrderStatus status : OrderStatus.values()) { %>
            <a class="filter-tab <%= status == activeFilter ? "active" : "" %>"
               href="<%= ctx %>/admin/orders?status=<%= status.name() %>"><%= h(status.getLabel()) %></a>
        <% } %>
    </nav>

    <% if (loadError) { %>
        <div class="alert alert-error">Something went wrong loading orders. Please try again later.</div>

    <% } else if (orders.isEmpty()) { %>
        <div class="panel">
            <div class="empty-state">
                <span class="empty-icon" aria-hidden="true">&#128230;</span>
                <p><%= activeFilter == null ? "No orders yet." : "No " + h(activeFilter.getLabel()) + " orders." %></p>
            </div>
        </div>

    <% } else { %>
        <div class="table-wrap">
            <table class="data-table">
                <thead>
                    <tr>
                        <th>Order</th>
                        <th>Date</th>
                        <th>Customer</th>
                        <th class="col-num">Total</th>
                        <th>Status</th>
                        <th></th>
                    </tr>
                </thead>
                <tbody>
                <% for (SellerOrder order : orders) { %>
                    <tr>
                        <td data-label="Order"><span class="cell-title">#<%= order.getOrderId() %></span></td>
                        <td data-label="Date"><%= h(fmt(order.getCreatedAt())) %></td>
                        <td data-label="Customer">
                            <div class="cell-title"><%= h(order.getCustomerName()) %></div>
                            <div class="cell-sub"><%= h(order.getCustomerEmail()) %></div>
                        </td>
                        <td data-label="Total" class="col-num">&#8377;<%= order.getSellerTotal() %></td>
                        <td data-label="Status">
                            <span class="badge badge-<%= order.getStatus().getCssClass() %>"><%= h(order.getStatus().getLabel()) %></span>
                        </td>
                        <td>
                            <a class="btn btn-secondary btn-sm"
                               href="<%= ctx %>/admin/orders?orderId=<%= order.getOrderId() %><%= activeFilter != null ? "&status=" + activeFilter.name() : "" %>">View</a>
                        </td>
                    </tr>
                <% } %>
                </tbody>
            </table>
        </div>
        <p class="result-count" style="margin-top:12px;"><%= orders.size() %> order<%= orders.size() == 1 ? "" : "s" %></p>
    <% } %>

</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>

<script src="<%= ctx %>/assets/js/main.js"></script>
<script src="<%= ctx %>/assets/js/admin.js"></script>

</body>
</html>
