<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.util.SecurityUtil" %>
<%@ page import="com.suhanamart.model.Product" %>
<%@ page import="java.util.List" %>
<%!
    private String escapeHtml(String value) {
        if (value == null) { return ""; }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    // All data below is set by ProductServlet (/products) before this
    // forward - this page only renders it. Direct requests to this
    // JSP are blocked by AuthFilter, but even if reached without the
    // servlet's attributes, the empty-list/false defaults below keep
    // the page safe.
    String activeNav = "products";
    // Sent with every state-changing form so other websites cannot submit it for the user.
    String csrfToken = SecurityUtil.csrfToken(session);
    String pageTitle = "Products";
    String[] pageCss = { "dashboard.css", "shop.css" };

    String keyword = (String) request.getAttribute("keyword");
    String category = (String) request.getAttribute("category");

    @SuppressWarnings("unchecked")
    List<Product> products = (List<Product>) request.getAttribute("products");
    @SuppressWarnings("unchecked")
    List<String> categories = (List<String>) request.getAttribute("categories");
    Boolean loadErrorAttr = (Boolean) request.getAttribute("loadError");

    if (products == null) { products = java.util.Collections.emptyList(); }
    if (categories == null) { categories = java.util.Collections.emptyList(); }
    boolean loadError = loadErrorAttr != null && loadErrorAttr;
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>

<%@ include file="/WEB-INF/jspf/navbar.jspf" %>

<main class="dash-main">

    <div class="page-header-row">
        <div>
            <h1 class="page-title">Products</h1>
            <p class="page-subtext">Browse everything available in SUHANAMART.</p>
        </div>
    </div>

    <form class="search-bar" action="<%= request.getContextPath() %>/products" method="get">
        <input type="text" name="q" placeholder="Search products by name..."
               value="<%= escapeHtml(keyword != null ? keyword : "") %>">

        <% if (!categories.isEmpty()) { %>
            <select name="category">
                <option value="">All categories</option>
                <% for (String c : categories) { %>
                    <option value="<%= escapeHtml(c) %>" <%= c.equals(category) ? "selected" : "" %>>
                        <%= escapeHtml(c) %>
                    </option>
                <% } %>
            </select>
        <% } %>

        <button type="submit">Search</button>
    </form>

    <% if (loadError) { %>
        <div class="alert alert-error">Something went wrong loading products. Please try again later.</div>
    <% } else if (products.isEmpty()) { %>
        <div class="panel">
            <div class="empty-state">
                <span class="empty-icon" aria-hidden="true">&#128722;</span>
                <p><%= (keyword != null && !keyword.trim().isEmpty()) || (category != null && !category.trim().isEmpty())
                        ? "No products match your search."
                        : "No products available yet." %></p>
                <% if ((keyword != null && !keyword.trim().isEmpty()) || (category != null && !category.trim().isEmpty())) { %>
                    <a href="<%= request.getContextPath() %>/products" class="empty-action">Clear filters</a>
                <% } %>
            </div>
        </div>
    <% } else { %>
        <p class="result-count"><%= products.size() %> product<%= products.size() == 1 ? "" : "s" %> found</p>

        <div class="product-grid">
            <% for (Product p : products) { %>
                <div class="product-card">
                    <a href="<%= request.getContextPath() %>/product-details?id=<%= p.getProductId() %>" class="product-image">
                        <% if (p.getImageUrl() != null && !p.getImageUrl().trim().isEmpty()) { %>
                            <img src="<%= escapeHtml(p.getImageUrl()) %>" alt="<%= escapeHtml(p.getName()) %>">
                        <% } else { %>
                            &#128230;
                        <% } %>
                    </a>
                    <div class="product-body">
                        <span class="product-category"><%= escapeHtml(p.getCategory()) %></span>
                        <a href="<%= request.getContextPath() %>/product-details?id=<%= p.getProductId() %>" class="product-name"><%= escapeHtml(p.getName()) %></a>
                        <p class="product-desc"><%= escapeHtml(p.getDescription()) %></p>

                        <div class="product-price-row">
                            <span class="product-price">&#8377;<%= p.getPrice() %></span>
                            <% if (p.getStock() <= 0) { %>
                                <span class="stock-tag out">Out of stock</span>
                            <% } else if (p.getStock() <= 5) { %>
                                <span class="stock-tag low">Only <%= p.getStock() %> left</span>
                            <% } else { %>
                                <span class="stock-tag">In stock</span>
                            <% } %>
                        </div>

                        <div class="product-actions">
                            <a href="<%= request.getContextPath() %>/product-details?id=<%= p.getProductId() %>" class="btn btn-secondary">View Details</a>

                            <form action="<%= request.getContextPath() %>/CartServlet" method="post" style="flex:1;">
                                <input type="hidden" name="csrfToken" value="<%= csrfToken %>">
                                <input type="hidden" name="action" value="add">
                                <input type="hidden" name="productId" value="<%= p.getProductId() %>">
                                <input type="hidden" name="quantity" value="1">
                                <button type="submit" class="btn btn-primary" style="width:100%;"
                                        <%= p.getStock() <= 0 ? "disabled" : "" %>>
                                    Add to Cart
                                </button>
                            </form>
                        </div>
                    </div>
                </div>
            <% } %>
        </div>
    <% } %>

</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>

<script src="<%= request.getContextPath() %>/assets/js/main.js"></script>

</body>
</html>
