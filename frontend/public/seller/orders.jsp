<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.model.OrderItem" %>
<%@ page import="com.suhanamart.model.OrderStatus" %>
<%@ page import="com.suhanamart.model.SellerOrder" %>
<%@ page import="java.util.List" %>
<%@ include file="/WEB-INF/jspf/seller-util.jspf" %>
<%
    // Data is loaded by SellerOrderServlet (GET /seller/orders). Every order and line
    // shown here contains ONLY this seller's own products.
    String activeNav = "orders";
    String pageTitle = "Incoming Orders";
    String[] pageCss = { "dashboard.css", "shop.css", "seller.css" };

    @SuppressWarnings("unchecked")
    List<SellerOrder> orders = (List<SellerOrder>) request.getAttribute("orders");
    OrderStatus activeFilter = (OrderStatus) request.getAttribute("activeFilter");
    boolean loadError = Boolean.TRUE.equals(request.getAttribute("loadError"));
    String flashSuccess = (String) request.getAttribute("flashSuccess");
    String flashError = (String) request.getAttribute("flashError");
    String ctx = request.getContextPath();
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<%@ include file="/WEB-INF/jspf/seller-navbar.jspf" %>

<main class="dash-main">

    <div class="page-header-row">
        <div>
            <h1 class="page-title">Incoming Orders</h1>
            <p class="page-subtext">Orders that contain your products. You only see your own items in each order.</p>
        </div>
    </div>

    <% if (flashSuccess != null) { %><div class="alert alert-success"><%= h(flashSuccess) %></div><% } %>
    <% if (flashError != null) { %><div class="alert alert-error"><%= h(flashError) %></div><% } %>

    <nav class="filter-tabs" aria-label="Filter orders by status">
        <a class="filter-tab <%= activeFilter == null ? "active" : "" %>" href="<%= ctx %>/seller/orders">All</a>
        <% for (OrderStatus status : OrderStatus.values()) { %>
            <a class="filter-tab <%= status == activeFilter ? "active" : "" %>"
               href="<%= ctx %>/seller/orders?status=<%= status.name() %>"><%= h(status.getLabel()) %></a>
        <% } %>
    </nav>

    <% if (loadError) { %>
        <div class="alert alert-error">Something went wrong loading your orders. Please try again later.</div>

    <% } else if (orders.isEmpty()) { %>
        <div class="panel">
            <div class="empty-state">
                <span class="empty-icon" aria-hidden="true">&#128230;</span>
                <p><%= activeFilter == null ? "No orders yet." : "No " + h(activeFilter.getLabel()) + " orders." %></p>
                <span class="empty-subtext">Orders containing your products will appear here.</span>
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
                        <th>Your Items</th>
                        <th class="col-num">Your Total</th>
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
                        <td data-label="Your Items">
                            <ul class="line-list">
                                <% for (OrderItem item : order.getItems()) { %>
                                    <li><%= h(item.getProductName()) %> &times; <%= item.getQuantity() %></li>
                                <% } %>
                            </ul>
                        </td>
                        <td data-label="Your Total" class="col-num">&#8377;<%= order.getSellerTotal() %></td>
                        <td data-label="Status">
                            <span class="badge badge-<%= order.getStatus().getCssClass() %>"><%= h(order.getStatus().getLabel()) %></span>
                        </td>
                        <td>
                            <a class="btn btn-secondary btn-sm"
                               href="<%= ctx %>/seller/orders/details?orderId=<%= order.getOrderId() %>">View</a>
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
<script src="<%= ctx %>/assets/js/seller.js"></script>

</body>
</html>
