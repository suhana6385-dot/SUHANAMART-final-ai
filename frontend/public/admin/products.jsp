<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.model.Product" %>
<%@ page import="java.util.List" %>
<%@ include file="/WEB-INF/jspf/admin-util.jspf" %>
<%
    // Data is loaded by AdminServlet (GET /admin/products).
    String activeNav = "products";
    String pageTitle = "Products";
    String[] pageCss = { "dashboard.css", "shop.css", "seller.css" };

    @SuppressWarnings("unchecked")
    List<Product> products = (List<Product>) request.getAttribute("products");
    boolean loadError = Boolean.TRUE.equals(request.getAttribute("loadError"));
    String flashSuccess = (String) request.getAttribute("flashSuccess");
    String flashError = (String) request.getAttribute("flashError");
    String ctx = request.getContextPath();
    String csrf = SecurityUtil.csrfToken(session);
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<%@ include file="/WEB-INF/jspf/admin-navbar.jspf" %>

<main class="dash-main">

    <div class="page-header-row">
        <div>
            <h1 class="page-title">Products</h1>
            <p class="page-subtext">Every listing on SUHANAMART, across every seller.</p>
        </div>
    </div>

    <% if (flashSuccess != null) { %><div class="alert alert-success"><%= h(flashSuccess) %></div><% } %>
    <% if (flashError != null) { %><div class="alert alert-error"><%= h(flashError) %></div><% } %>

    <% if (loadError) { %>
        <div class="alert alert-error">Something went wrong loading products. Please try again later.</div>

    <% } else if (products.isEmpty()) { %>
        <div class="panel">
            <div class="empty-state">
                <span class="empty-icon" aria-hidden="true">&#128230;</span>
                <p>No products yet.</p>
            </div>
        </div>

    <% } else { %>
        <div class="table-wrap">
            <table class="data-table">
                <thead>
                    <tr>
                        <th>Product</th>
                        <th>Seller</th>
                        <th>Category</th>
                        <th class="col-num">Price</th>
                        <th>Stock</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                <% for (Product p : products) {
                       boolean hasImage = p.getImageUrl() != null && !p.getImageUrl().trim().isEmpty(); %>
                    <tr>
                        <td data-label="Product">
                            <div class="cart-product-cell">
                                <div class="cart-thumb thumb-lg">
                                    <% if (hasImage) { %>
                                        <img src="<%= h(p.getImageUrl()) %>" alt="">
                                    <% } else { %>&#128230;<% } %>
                                </div>
                                <div>
                                    <div class="cell-title"><%= h(p.getName()) %></div>
                                    <div class="cell-sub">ID #<%= p.getProductId() %></div>
                                </div>
                            </div>
                        </td>
                        <td data-label="Seller">
                            <%= p.getSellerName() != null ? h(p.getSellerName()) : "<span class=\"muted\">Unowned</span>" %>
                        </td>
                        <td data-label="Category"><%= h(p.getCategory()) %></td>
                        <td data-label="Price" class="col-num">&#8377;<%= p.getPrice() %></td>
                        <td data-label="Stock">
                            <span class="stock-tag <%= p.getStock() == 0 ? "out" : (p.getStock() <= 5 ? "low" : "") %>">
                                <%= p.getStock() == 0 ? "Out of stock" : p.getStock() + " in stock" %>
                            </span>
                        </td>
                        <td data-label="Actions">
                            <form method="post" action="<%= ctx %>/admin/products/remove"
                                  data-confirm="Remove &quot;<%= h(p.getName()) %>&quot;? This can't be undone. Products with order history can't be removed.">
                                <input type="hidden" name="csrfToken" value="<%= h(csrf) %>">
                                <input type="hidden" name="productId" value="<%= p.getProductId() %>">
                                <button type="submit" class="btn btn-danger btn-sm">Remove</button>
                            </form>
                        </td>
                    </tr>
                <% } %>
                </tbody>
            </table>
        </div>
        <p class="result-count" style="margin-top:12px;"><%= products.size() %> product<%= products.size() == 1 ? "" : "s" %></p>
    <% } %>

</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>

<script src="<%= ctx %>/assets/js/main.js"></script>
<script src="<%= ctx %>/assets/js/admin.js"></script>

</body>
</html>
