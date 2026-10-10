<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.model.Product" %>
<%@ page import="java.util.List" %>
<%@ include file="/WEB-INF/jspf/seller-util.jspf" %>
<%
    // Data is loaded by SellerProductServlet (GET /seller/products).
    String activeNav = "products";
    String pageTitle = "My Products";
    String[] pageCss = { "dashboard.css", "shop.css", "seller.css" };

    @SuppressWarnings("unchecked")
    List<Product> products = (List<Product>) request.getAttribute("products");
    int lowStockThreshold = (Integer) request.getAttribute("lowStockThreshold");
    boolean loadError = Boolean.TRUE.equals(request.getAttribute("loadError"));
    String flashSuccess = (String) request.getAttribute("flashSuccess");
    String flashError = (String) request.getAttribute("flashError");
    String ctx = request.getContextPath();
    String csrf = SecurityUtil.csrfToken(session);
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<%@ include file="/WEB-INF/jspf/seller-navbar.jspf" %>

<main class="dash-main">

    <div class="page-header-row">
        <div>
            <h1 class="page-title">My Products</h1>
            <p class="page-subtext">Products you sell on SUHANAMART. Only you can see and change these.</p>
        </div>
        <a href="<%= ctx %>/seller/products/new" class="btn btn-primary btn-wide">+ Add Product</a>
    </div>

    <% if (flashSuccess != null) { %><div class="alert alert-success"><%= h(flashSuccess) %></div><% } %>
    <% if (flashError != null) { %><div class="alert alert-error"><%= h(flashError) %></div><% } %>

    <% if (loadError) { %>
        <div class="alert alert-error">Something went wrong loading your products. Please try again later.</div>

    <% } else if (products.isEmpty()) { %>
        <div class="panel">
            <div class="empty-state">
                <span class="empty-icon" aria-hidden="true">&#128717;</span>
                <p>You haven't added any products yet.</p>
                <a href="<%= ctx %>/seller/products/new" class="empty-action">Add Your First Product</a>
            </div>
        </div>

    <% } else { %>
        <div class="table-wrap">
            <table class="data-table">
                <thead>
                    <tr>
                        <th>Product</th>
                        <th>Category</th>
                        <th class="col-num">Price</th>
                        <th>Stock</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                <% for (Product p : products) {
                       boolean soldOut = p.getStock() == 0;
                       boolean low = !soldOut && p.getStock() <= lowStockThreshold;
                       boolean hasImage = p.getImageUrl() != null && !p.getImageUrl().trim().isEmpty(); %>
                    <tr>
                        <td data-label="Product">
                            <div class="cart-product-cell">
                                <div class="cart-thumb thumb-lg">
                                    <% if (hasImage) { %>
                                        <img src="<%= h(p.getImageUrl()) %>" alt="">
                                    <% } else { %>&#128717;<% } %>
                                </div>
                                <div>
                                    <div class="cell-title"><%= h(p.getName()) %></div>
                                    <div class="cell-sub">ID #<%= p.getProductId() %></div>
                                </div>
                            </div>
                        </td>
                        <td data-label="Category"><%= h(p.getCategory()) %></td>
                        <td data-label="Price" class="col-num">&#8377;<%= p.getPrice() %></td>
                        <td data-label="Stock">
                            <div class="stock-cell">
                                <span class="stock-tag <%= soldOut ? "out" : (low ? "low" : "") %>">
                                    <%= soldOut ? "Out of stock" : (low ? "Low: " + p.getStock() : p.getStock() + " in stock") %>
                                </span>
                                <form class="stock-form" method="post" action="<%= ctx %>/seller/products/stock">
                                    <input type="hidden" name="csrfToken" value="<%= h(csrf) %>">
                                    <input type="hidden" name="productId" value="<%= p.getProductId() %>">
                                    <input type="number" name="stock" min="0" max="1000000" step="1" required
                                           value="<%= p.getStock() %>" aria-label="New stock quantity for <%= h(p.getName()) %>">
                                    <button type="submit" class="btn btn-secondary btn-sm">Update</button>
                                </form>
                            </div>
                        </td>
                        <td data-label="Actions">
                            <div class="row-actions">
                                <a class="btn btn-secondary btn-sm"
                                   href="<%= ctx %>/seller/products/edit?id=<%= p.getProductId() %>">Edit</a>
                                <form method="post" action="<%= ctx %>/seller/products/delete"
                                      data-confirm="Delete &quot;<%= h(p.getName()) %>&quot;? This can't be undone.">
                                    <input type="hidden" name="csrfToken" value="<%= h(csrf) %>">
                                    <input type="hidden" name="productId" value="<%= p.getProductId() %>">
                                    <button type="submit" class="btn btn-danger btn-sm">Delete</button>
                                </form>
                            </div>
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
<script src="<%= ctx %>/assets/js/seller.js"></script>

</body>
</html>
