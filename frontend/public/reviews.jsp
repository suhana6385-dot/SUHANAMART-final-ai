<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.model.Review" %>
<%@ page import="com.suhanamart.util.SecurityUtil" %>
<%@ page import="java.util.List" %>
<%!
    private String h(Object value) {
        return SecurityUtil.escapeHtml(value);
    }
%>
<%
    // Data set by ReviewServlet (GET/POST /reviews) before this forward. This page has no
    // logic of its own: it only shows the form and the messages the servlet prepared.
    Review.PurchasedItem item = (Review.PurchasedItem) request.getAttribute("reviewItem");
    if (item == null) {
        // Opened directly instead of through the controller - let the controller decide where to go.
        response.sendRedirect(request.getContextPath() + "/reviews");
        return;
    }

    String activeNav = "orders";
    String pageTitle = "Review Product";
    String[] pageCss = { "dashboard.css", "shop.css", "review.css" };

    String ctx = request.getContextPath();
    String formRating = (String) request.getAttribute("formRating");
    String formComment = (String) request.getAttribute("formComment");
    @SuppressWarnings("unchecked")
    List<String> errors = (List<String>) request.getAttribute("errors");
    Integer minLength = (Integer) request.getAttribute("commentMinLength");
    Integer maxLength = (Integer) request.getAttribute("commentMaxLength");
    String csrf = SecurityUtil.csrfToken(session);

    if (formRating == null) { formRating = ""; }
    if (formComment == null) { formComment = ""; }
    if (minLength == null) { minLength = 5; }
    if (maxLength == null) { maxLength = 1000; }
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>

<%@ include file="/WEB-INF/jspf/navbar.jspf" %>

<main class="dash-main">

    <a href="<%= ctx %>/public/order-details.jsp?orderId=<%= item.getOrderId() %>" class="back-link">&larr; Back to Order #<%= item.getOrderId() %></a>

    <div class="page-header-row">
        <div>
            <h1 class="page-title">Review this product</h1>
            <p class="page-subtext">Tell other shoppers what you thought of it.</p>
        </div>
    </div>

    <div class="panel review-form-panel">

        <div class="review-target">
            <div class="review-target-thumb">
                <% if (item.getImageUrl() != null && !item.getImageUrl().trim().isEmpty()) { %>
                    <img src="<%= h(item.getImageUrl()) %>" alt="">
                <% } else { %>
                    &#128230;
                <% } %>
            </div>
            <div>
                <div class="review-target-name"><%= h(item.getProductName()) %></div>
                <div class="review-target-meta">From Order #<%= item.getOrderId() %></div>
            </div>
        </div>

        <% if (errors != null && !errors.isEmpty()) { %>
            <div class="alert alert-error">
                <ul class="review-errors">
                    <% for (String error : errors) { %>
                        <li><%= h(error) %></li>
                    <% } %>
                </ul>
            </div>
        <% } %>
        <div class="alert alert-error" id="clientMessage" role="alert" hidden></div>

        <form action="<%= ctx %>/reviews" method="post" class="review-form" id="reviewForm"
              data-min="<%= minLength %>" data-max="<%= maxLength %>">
            <input type="hidden" name="csrfToken" value="<%= h(csrf) %>">
            <input type="hidden" name="orderId" value="<%= item.getOrderId() %>">
            <input type="hidden" name="productId" value="<%= item.getProductId() %>">

            <fieldset>
                <legend>Your rating <span class="req">*</span></legend>
                <div class="star-picker">
                    <% for (int star = Review.MAX_RATING; star >= Review.MIN_RATING; star--) { %>
                        <input type="radio" id="rating<%= star %>" name="rating" value="<%= star %>"
                               <%= String.valueOf(star).equals(formRating) ? "checked" : "" %>>
                        <label for="rating<%= star %>" title="<%= star %> star<%= star == 1 ? "" : "s" %>"
                               aria-label="<%= star %> star<%= star == 1 ? "" : "s" %>">&#9733;</label>
                    <% } %>
                </div>
                <span class="star-picker-hint" id="ratingHint" aria-live="polite"></span>
            </fieldset>

            <div>
                <label class="review-label" for="comment">Your review <span class="req">*</span></label>
                <textarea id="comment" name="comment" rows="6" maxlength="<%= maxLength %>"
                          placeholder="What did you like or dislike? How did you use it?"><%= h(formComment) %></textarea>
                <div class="review-counter">
                    <span>At least <%= minLength %> characters</span>
                    <span><span id="commentCount">0</span> / <%= maxLength %></span>
                </div>
            </div>

            <div class="review-form-actions">
                <button type="submit" class="btn btn-primary">Submit Review</button>
                <a href="<%= ctx %>/public/order-details.jsp?orderId=<%= item.getOrderId() %>" class="btn btn-secondary">Cancel</a>
            </div>
        </form>
    </div>

</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>

<script src="<%= ctx %>/assets/js/main.js"></script>
<script src="<%= ctx %>/assets/js/review.js"></script>

</body>
</html>
