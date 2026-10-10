<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.model.Product" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ include file="/WEB-INF/jspf/seller-util.jspf" %>
<%
    // Rendered by SellerProductServlet (GET /seller/products/edit?id=N, or after a rejected POST /update).
    // The servlet only forwards here for a product the logged-in seller owns.
    String activeNav = "products";
    String pageTitle = "Edit Product";
    String[] pageCss = { "dashboard.css", "shop.css", "seller.css" };

    Product product = (Product) request.getAttribute("product");
    @SuppressWarnings("unchecked")
    Map<String, String> formValues = (Map<String, String>) request.getAttribute("formValues");
    @SuppressWarnings("unchecked")
    List<String> categories = (List<String>) request.getAttribute("categories");
    @SuppressWarnings("unchecked")
    List<String> errors = (List<String>) request.getAttribute("errors");

    String formAction = "/seller/products/update";
    String submitLabel = "Save Changes";
    Integer formProductId = product.getProductId();
    String ctx = request.getContextPath();
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<%@ include file="/WEB-INF/jspf/seller-navbar.jspf" %>

<main class="dash-main">

    <a href="<%= ctx %>/seller/products" class="back-link">&larr; Back to My Products</a>

    <div class="page-header-row">
        <div>
            <h1 class="page-title">Edit Product</h1>
            <p class="page-subtext"><%= h(product.getName()) %> &middot; ID #<%= product.getProductId() %></p>
        </div>
    </div>

    <% if (errors != null && !errors.isEmpty()) { %>
        <div class="alert alert-error">
            Please fix the following:
            <ul class="error-list">
                <% for (String message : errors) { %><li><%= h(message) %></li><% } %>
            </ul>
        </div>
    <% } %>

    <div class="panel form-panel">
        <%@ include file="/WEB-INF/jspf/seller-product-form.jspf" %>
    </div>

</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>

<script src="<%= ctx %>/assets/js/main.js"></script>
<script src="<%= ctx %>/assets/js/seller.js"></script>

</body>
</html>
