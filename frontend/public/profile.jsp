<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.dao.UserDAO" %>
<%@ page import="com.suhanamart.model.User" %>
<%@ page import="java.sql.SQLException" %>
<%!
    private String escapeHtml(String value) {
        if (value == null) { return ""; }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    String activeNav = "profile";
    String pageTitle = "My Profile";
    String[] pageCss = { "dashboard.css", "shop.css" };

    int userId = (Integer) session.getAttribute("userId");

    UserDAO userDAO = new UserDAO();
    User user = null;
    boolean loadError = false;

    try {
        user = userDAO.findUserById(userId);
    } catch (SQLException e) {
        System.err.println("[SUHANAMART] Failed to load profile for user " + userId + ":");
        e.printStackTrace();
        loadError = true;
    }
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>

<%@ include file="/WEB-INF/jspf/navbar.jspf" %>

<main class="dash-main">

    <div class="page-header-row">
        <div>
            <h1 class="page-title">My Profile</h1>
            <p class="page-subtext">Your SUHANAMART account information.</p>
        </div>
    </div>

    <% if (loadError) { %>
        <div class="alert alert-error">Something went wrong loading your profile. Please try again later.</div>

    <% } else if (user == null) { %>
        <div class="panel">
            <div class="empty-state">
                <span class="empty-icon" aria-hidden="true">&#128100;</span>
                <p>We couldn't find your account details.</p>
            </div>
        </div>

    <% } else { %>
        <div class="profile-card">
            <div class="profile-row">
                <span>Full name</span>
                <span><%= escapeHtml(user.getName()) %></span>
            </div>
            <div class="profile-row">
                <span>Email</span>
                <span><%= escapeHtml(user.getEmail()) %></span>
            </div>
            <div class="profile-row">
                <span>Member since</span>
                <span><%= user.getCreatedAt() %></span>
            </div>

            <a href="<%= request.getContextPath() %>/public/orders.jsp" class="btn btn-secondary" style="width:100%; margin-top: 18px;">View Order History</a>
            <a href="<%= request.getContextPath() %>/LogoutServlet" class="btn btn-primary" style="width:100%; margin-top: 10px;">Logout</a>
        </div>
    <% } %>

</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>

<script src="<%= request.getContextPath() %>/assets/js/main.js"></script>

</body>
</html>
