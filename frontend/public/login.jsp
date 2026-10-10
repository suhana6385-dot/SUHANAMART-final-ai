<%@ page contentType="text/html;charset=UTF-8" language="java" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">

    <title>SUHANAMART | Login</title>

    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/style.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/auth.css">
</head>

<body>

<div class="page-wrapper">

    <div class="login-card">

        <!-- Brand -->
        <div class="brand">
            <div class="brand-icon">S</div>

            <div>
                <h1>SUHANAMART</h1>
                <p>Smart Shopping System</p>
            </div>
        </div>

        <!-- Welcome -->
        <div class="welcome-section">
            <h2>Welcome back</h2>
            <p>Sign in to continue shopping with SUHANAMART.</p>
        </div>

        <!-- Error / Success Message -->
        <%
            String errorMessage = (String) request.getAttribute("errorMessage");
            String successMessage = (String) request.getAttribute("successMessage");
        %>

        <div id="messageArea" aria-live="polite">
            <% if (errorMessage != null) { %>
                <div class="message error-message">
                    <span aria-hidden="true">!</span>
                    <%= errorMessage %>
                </div>
            <% } %>

            <% if (successMessage != null) { %>
                <div class="message success-message">
                    <span aria-hidden="true">&#10003;</span>
                    <%= successMessage %>
                </div>
            <% } %>
        </div>

        <!-- Login Form -->
        <form action="<%= request.getContextPath() %>/LoginServlet" method="post" class="login-form" id="loginForm" novalidate>

            <div class="form-group" id="emailGroup">
                <label for="email">Email address</label>

                <div class="input-wrapper">
                    <span class="input-icon" aria-hidden="true">&#9993;</span>

                    <input
                        type="email"
                        id="email"
                        name="email"
                        placeholder="Enter your email"
                        autocomplete="email"
                        aria-describedby="emailError"
                        required>
                </div>
                <small class="field-error" id="emailError"></small>
            </div>

            <div class="form-group" id="passwordGroup">
                <label for="password">Password</label>

                <div class="input-wrapper">
                    <span class="input-icon" aria-hidden="true">&#9679;</span>

                    <input
                        type="password"
                        id="password"
                        name="password"
                        placeholder="Enter your password"
                        autocomplete="current-password"
                        aria-describedby="passwordError"
                        required>

                    <button
                        type="button"
                        class="password-toggle"
                        data-target="password"
                        aria-label="Show password">
                        Show
                    </button>
                </div>
                <small class="field-error" id="passwordError"></small>
            </div>

            <div class="remember-row">
                <label class="checkbox-wrapper" for="rememberMe">
                    <input type="checkbox" id="rememberMe" name="rememberMe" value="true">
                    <span class="checkbox-box" aria-hidden="true"></span>
                    <span class="checkbox-label">Remember me</span>
                </label>
            </div>

            <button type="submit" class="login-btn" id="loginBtn">
                <span class="btn-text">Sign in</span>
                <span class="arrow" aria-hidden="true">&#8594;</span>
                <span class="spinner" aria-hidden="true"></span>
            </button>

        </form>

        <!-- Register -->
        <div class="register-section">
            <span>Don't have an account?</span>

            <a href="<%= request.getContextPath() %>/public/register.jsp">
                Create an account
            </a>
        </div>

        <div class="footer-text">
            &copy; 2026 SUHANAMART. All rights reserved.
        </div>

    </div>

</div>

<script src="<%= request.getContextPath() %>/assets/js/validation.js"></script>

</body>
</html>
