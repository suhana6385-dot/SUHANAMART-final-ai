<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.util.SecurityUtil" %>
<%@ page import="com.suhanamart.model.Product" %>
<%@ page import="com.suhanamart.model.Review" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%!
    private String escapeHtml(String value) {
        if (value == null) { return ""; }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String reviewDate(java.sql.Timestamp timestamp) {
        return timestamp == null ? "" : new java.text.SimpleDateFormat("dd MMM yyyy").format(timestamp);
    }
%>
<%
    // Data set by ProductServlet (/product-details) before this forward.
    String activeNav = "products";

    Product product = (Product) request.getAttribute("product");
    Boolean loadErrorAttr = (Boolean) request.getAttribute("loadError");
    Boolean invalidIdAttr = (Boolean) request.getAttribute("invalidId");
    boolean loadError = loadErrorAttr != null && loadErrorAttr;
    boolean invalidId = invalidIdAttr != null && invalidIdAttr;

    // Sent with every state-changing form so other websites cannot submit it for the user.
    String csrfToken = SecurityUtil.csrfToken(session);
    String pageTitle = product != null ? product.getName() : "Product";
    String[] pageCss = { "dashboard.css", "shop.css", "review.css" };

    // Review Module data, also set by ProductServlet.
    Review.Summary reviewSummary = (Review.Summary) request.getAttribute("reviewSummary");
    if (reviewSummary == null) { reviewSummary = Review.Summary.empty(); }
    @SuppressWarnings("unchecked")
    List<Review> reviews = (List<Review>) request.getAttribute("reviews");
    if (reviews == null) { reviews = Collections.emptyList(); }
    boolean reviewsLoadError = Boolean.TRUE.equals(request.getAttribute("reviewsLoadError"));
    String flashSuccess = (String) request.getAttribute("flashSuccess");
    String flashError = (String) request.getAttribute("flashError");
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>

<%@ include file="/WEB-INF/jspf/navbar.jspf" %>

<main class="dash-main">

    <a href="<%= request.getContextPath() %>/products" class="back-link">&larr; Back to Products</a>

    <% if (flashSuccess != null) { %><div class="alert alert-success"><%= escapeHtml(flashSuccess) %></div><% } %>
    <% if (flashError != null) { %><div class="alert alert-error"><%= escapeHtml(flashError) %></div><% } %>

    <% if (loadError) { %>
        <div class="alert alert-error">Something went wrong loading this product. Please try again later.</div>

    <% } else if (invalidId || product == null) { %>
        <div class="panel">
            <div class="empty-state">
                <span class="empty-icon" aria-hidden="true">&#128269;</span>
                <p>We couldn't find that product.</p>
                <a href="<%= request.getContextPath() %>/products" class="empty-action">Browse Products</a>
            </div>
        </div>

    <% } else { %>
        <div class="details-grid">
            <div class="details-image">
                <% if (product.getImageUrl() != null && !product.getImageUrl().trim().isEmpty()) { %>
                    <img src="<%= escapeHtml(product.getImageUrl()) %>" alt="<%= escapeHtml(product.getName()) %>">
                <% } else { %>
                    &#128230;
                <% } %>
            </div>

            <div class="details-body">
                <span class="product-category"><%= escapeHtml(product.getCategory()) %></span>
                <h1><%= escapeHtml(product.getName()) %></h1>

                <div class="rating-line">
                    <% if (reviewSummary.hasReviews()) { %>
                        <span class="stars" style="--fill: <%= reviewSummary.getAveragePercent() %>%;"
                              role="img" aria-label="<%= reviewSummary.getAverageRating() %> out of 5 stars"></span>
                        <span><strong><%= reviewSummary.getAverageRating() %></strong>
                            (<a href="#reviews"><%= reviewSummary.getReviewCount() %>
                            review<%= reviewSummary.getReviewCount() == 1 ? "" : "s" %></a>)</span>
                    <% } else { %>
                        <span>No reviews yet</span>
                    <% } %>
                </div>
                <div class="details-price">&#8377;<%= product.getPrice() %></div>

                <p class="details-desc">
                    <%= product.getDescription() != null && !product.getDescription().trim().isEmpty()
                            ? escapeHtml(product.getDescription())
                            : "No description available for this product." %>
                </p>

                <% if (product.getStock() <= 0) { %>
                    <p><span class="stock-tag out">Out of stock</span></p>
                <% } else if (product.getStock() <= 5) { %>
                    <p><span class="stock-tag low">Only <%= product.getStock() %> left in stock</span></p>
                <% } else { %>
                    <p><span class="stock-tag">In stock</span></p>
                <% } %>

                <% if (product.getStock() > 0) { %>
                    <form action="<%= request.getContextPath() %>/CartServlet" method="post">
                        <input type="hidden" name="csrfToken" value="<%= csrfToken %>">
                        <input type="hidden" name="action" value="add">
                        <input type="hidden" name="productId" value="<%= product.getProductId() %>">

                        <div class="qty-selector">
                            <label for="quantity">Quantity</label>
                            <input type="number" id="quantity" name="quantity" min="1"
                                   max="<%= product.getStock() %>" value="1">
                        </div>

                        <button type="submit" class="btn btn-primary" style="padding: 12px 28px;">Add to Cart</button>
                    </form>
                <% } %>
            </div>
        </div>

        <section class="panel review-section" id="reviews">
            <div class="panel-header">
                <h2>Customer reviews</h2>
            </div>

            <% if (reviewsLoadError) { %>
                <div class="alert alert-error">Reviews couldn't be loaded right now. Please try again later.</div>

            <% } else if (!reviewSummary.hasReviews()) { %>
                <div class="empty-state">
                    <span class="empty-icon" aria-hidden="true">&#9733;</span>
                    <p>No reviews yet.</p>
                    <span class="empty-subtext">Buyers can review a product once their order has been delivered.</span>
                </div>

            <% } else { %>
                <div class="review-summary">
                    <div class="review-average">
                        <div class="review-average-value"><%= reviewSummary.getAverageRating() %></div>
                        <span class="stars stars-lg" style="--fill: <%= reviewSummary.getAveragePercent() %>%;"
                              role="img" aria-label="<%= reviewSummary.getAverageRating() %> out of 5 stars"></span>
                        <div class="review-average-count">
                            Based on <%= reviewSummary.getReviewCount() %>
                            review<%= reviewSummary.getReviewCount() == 1 ? "" : "s" %>
                        </div>
                    </div>

                    <div class="review-bars">
                        <% for (int star = Review.MAX_RATING; star >= Review.MIN_RATING; star--) { %>
                            <div class="review-bar-row">
                                <span><%= star %> &#9733;</span>
                                <span class="review-bar"><span class="review-bar-fill"
                                      style="width: <%= reviewSummary.getPercent(star) %>%;"></span></span>
                                <span class="review-bar-count"><%= reviewSummary.getCount(star) %></span>
                            </div>
                        <% } %>
                    </div>
                </div>

                <ul class="review-list">
                    <% for (Review review : reviews) { %>
                        <li class="review-item">
                            <div class="review-item-head">
                                <span class="stars stars-sm" style="--fill: <%= review.getRating() * 20 %>%;"
                                      role="img" aria-label="<%= review.getRating() %> out of 5 stars"></span>
                                <span class="review-author"><%= escapeHtml(review.getReviewerName()) %></span>
                                <span class="review-verified">Verified purchase</span>
                                <span class="review-date"><%= escapeHtml(reviewDate(review.getCreatedAt())) %></span>
                            </div>
                            <p class="review-text"><%= escapeHtml(review.getComment()) %></p>
                        </li>
                    <% } %>
                </ul>

                <% if (reviewSummary.getReviewCount() > reviews.size()) { %>
                    <p class="review-note">Showing the <%= reviews.size() %> most recent of
                        <%= reviewSummary.getReviewCount() %> reviews.</p>
                <% } %>
            <% } %>
        </section>
    <% } %>

</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>

<script src="<%= request.getContextPath() %>/assets/js/main.js"></script>

</body>
</html>
