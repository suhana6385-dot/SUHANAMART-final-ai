<%@ page contentType="text/html;charset=UTF-8" language="java" isErrorPage="true" %>
<%
    // Friendly error page (mapped in WEB-INF/web.xml). Deliberately shows NO exception
    // text, stack trace or server details - only a generic message for the status code.
    // The real cause is in the server log.
    Object statusAttr = request.getAttribute("javax.servlet.error.status_code");
    int status = statusAttr instanceof Integer ? (Integer) statusAttr : 500;

    String title;
    String message;
    if (status == 403) {
        title = "Access denied";
        message = "You don't have permission to do that. If your session expired, please log in again.";
    } else if (status == 404) {
        title = "Page not found";
        message = "We couldn't find the page you were looking for.";
    } else {
        title = "Something went wrong";
        message = "An unexpected error occurred. Please try again in a moment.";
    }
    response.setStatus(status);

    String[] pageCss = { "dashboard.css", "shop.css" };
    String pageTitle = title;
%>
<%@ include file="/WEB-INF/jspf/header.jspf" %>

<main class="dash-main">
    <div class="panel">
        <div class="empty-state">
            <span class="empty-icon" aria-hidden="true">&#9888;</span>
            <p><%= title %></p>
            <span class="empty-subtext"><%= message %></span>
            <a href="<%= request.getContextPath() %>/index.jsp" class="empty-action">Back to SUHANAMART</a>
        </div>
    </div>
</main>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>
</body>
</html>
