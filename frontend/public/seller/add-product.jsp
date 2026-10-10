<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ include file="/WEB-INF/jspf/seller-util.jspf" %>
<%
    // Rendered by SellerProductServlet (GET /seller/products/new, or after a rejected POST /create).
    String activeNav = "add-product";
    String pageTitle = "Add Product";
    String[] pageCss = { "dashboard.css", "shop.css", "seller.css" };

    @SuppressWarnings("unchecked")
    Map<String, String> formValues = (Map<String, String>) request.getAttribute("formValues");
    @SuppressWarnings("unchecked")
    List<String> categories = (List<String>) request.getAttribute("categories");
    @SuppressWarnings("unchecked")
    List<String> errors = (List<String>) request.getAttribute("errors");

    String formAction = "/seller/products/create";
    String submitLabel = "Add Product";
    Integer formProductId = null;
    String ctx = request.getContextPath();
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<%@ include file="/WEB-INF/jspf/seller-navbar.jspf" %>

<main class="dash-main">

    <a href="<%= ctx %>/seller/products" class="back-link">&larr; Back to My Products</a>

    <div class="page-header-row">
        <div>
            <h1 class="page-title">Add Product</h1>
            <p class="page-subtext">List a new product for buyers to find and order.</p>
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
