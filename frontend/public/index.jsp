<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    // Simple entry point: send logged-in users straight to their
    // dashboard, everyone else to the login page. No content of its
    // own - this page never renders.
    boolean loggedIn = session.getAttribute("userId") != null;

    if (loggedIn && "SELLER".equals(session.getAttribute("role"))) {
        response.sendRedirect(request.getContextPath() + "/seller/dashboard");
    } else if (loggedIn) {
        response.sendRedirect(request.getContextPath() + "/public/dashboard.jsp");
    } else {
        response.sendRedirect(request.getContextPath() + "/public/login.jsp");
    }
%>
