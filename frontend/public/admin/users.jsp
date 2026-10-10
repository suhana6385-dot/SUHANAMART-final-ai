<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.suhanamart.model.AccountStatus" %>
<%@ page import="com.suhanamart.model.Role" %>
<%@ page import="com.suhanamart.model.User" %>
<%@ page import="java.util.List" %>
<%@ include file="/WEB-INF/jspf/admin-util.jspf" %>
<%
    // Data is loaded by AdminUserServlet (GET /admin/users).
    String activeNav = "users";
    String pageTitle = "Users";
    String[] pageCss = { "dashboard.css", "shop.css", "seller.css" };

    @SuppressWarnings("unchecked")
    List<User> users = (List<User>) request.getAttribute("users");
    String roleFilter = (String) request.getAttribute("roleFilter");
    boolean loadError = Boolean.TRUE.equals(request.getAttribute("loadError"));
    String flashSuccess = (String) request.getAttribute("flashSuccess");
    String flashError = (String) request.getAttribute("flashError");
    String ctx = request.getContextPath();
    String csrf = SecurityUtil.csrfToken(session);
    int viewerId = (Integer) session.getAttribute("userId");
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<%@ include file="/WEB-INF/jspf/admin-navbar.jspf" %>

<main class="dash-main">

    <div class="page-header-row">
        <div>
            <h1 class="page-title">Users</h1>
            <p class="page-subtext">Everyone registered on SUHANAMART, across every role.</p>
        </div>
    </div>

    <% if (flashSuccess != null) { %><div class="alert alert-success"><%= h(flashSuccess) %></div><% } %>
    <% if (flashError != null) { %><div class="alert alert-error"><%= h(flashError) %></div><% } %>

    <nav class="filter-tabs" aria-label="Filter users by role">
        <a class="filter-tab <%= roleFilter == null || roleFilter.isEmpty() ? "active" : "" %>"
           href="<%= ctx %>/admin/users">All</a>
        <% for (Role role : Role.values()) { %>
            <a class="filter-tab <%= role.name().equalsIgnoreCase(roleFilter) ? "active" : "" %>"
               href="<%= ctx %>/admin/users?role=<%= role.name() %>"><%= role.name() %></a>
        <% } %>
    </nav>

    <% if (loadError) { %>
        <div class="alert alert-error">Something went wrong loading users. Please try again later.</div>

    <% } else if (users.isEmpty()) { %>
        <div class="panel">
            <div class="empty-state">
                <span class="empty-icon" aria-hidden="true">&#128101;</span>
                <p>No users found.</p>
            </div>
        </div>

    <% } else { %>
        <div class="table-wrap">
            <table class="data-table">
                <thead>
                    <tr>
                        <th>User</th>
                        <th>Role</th>
                        <th>Status</th>
                        <th>Joined</th>
                        <th></th>
                    </tr>
                </thead>
                <tbody>
                <% for (User u : users) {
                       boolean isAdmin = u.getRole() == Role.ADMIN;
                       boolean isSelf = u.getUserId() == viewerId;
                       boolean active = u.getAccountStatus() == AccountStatus.ACTIVE; %>
                    <tr>
                        <td data-label="User">
                            <div class="cell-title"><%= h(u.getName()) %><%= isSelf ? " (you)" : "" %></div>
                            <div class="cell-sub"><%= h(u.getEmail()) %></div>
                        </td>
                        <td data-label="Role">
                            <span class="badge badge-role-<%= u.getRole().name().toLowerCase() %>"><%= u.getRole().name() %></span>
                        </td>
                        <td data-label="Status">
                            <span class="badge badge-status-<%= active ? "active" : "disabled" %>">
                                <%= active ? "Active" : "Disabled" %>
                            </span>
                        </td>
                        <td data-label="Joined"><%= h(fmt(u.getCreatedAt())) %></td>
                        <td>
                            <% if (isAdmin || isSelf) { %>
                                <span class="muted">&mdash;</span>
                            <% } else { %>
                                <form method="post" action="<%= ctx %>/admin/users/status"
                                      data-confirm="<%= active ? "Disable" : "Enable" %> the account for &quot;<%= h(u.getName()) %>&quot;?">
                                    <input type="hidden" name="csrfToken" value="<%= h(csrf) %>">
                                    <input type="hidden" name="userId" value="<%= u.getUserId() %>">
                                    <input type="hidden" name="action" value="<%= active ? "disable" : "enable" %>">
                                    <button type="submit" class="btn btn-sm <%= active ? "btn-danger" : "btn-secondary" %>">
                                        <%= active ? "Disable" : "Enable" %>
                                    </button>
                                </form>
                            <% } %>
                        </td>
                    </tr>
                <% } %>
                </tbody>
            </table>
        </div>
        <p class="result-count" style="margin-top:12px;"><%= users.size() %> user<%= users.size() == 1 ? "" : "s" %></p>
    <% } %>

</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>

<script src="<%= ctx %>/assets/js/main.js"></script>
<script src="<%= ctx %>/assets/js/admin.js"></script>

</body>
</html>
