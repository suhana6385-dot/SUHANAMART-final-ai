<%@ page contentType="text/html;charset=UTF-8" language="java" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">

    <title>SUHANAMART | Create Account</title>

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
            <h2>Create your account</h2>
            <p>Join SUHANAMART and start shopping smarter today.</p>
        </div>

        <!-- Error / Success Message -->
        <%
            String errorMessage = (String) request.getAttribute("errorMessage");
            String successMessage = (String) request.getAttribute("successMessage");

            // Sticky values so the user doesn't have to retype everything
            // if the server rejects the submission (e.g. duplicate email).
            String oldName = request.getParameter("name");
            String oldEmail = request.getParameter("email");
            if (oldName == null) { oldName = ""; }
            if (oldEmail == null) { oldEmail = ""; }
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

        <!-- Register Form -->
        <form action="<%= request.getContextPath() %>/RegisterServlet" method="post" class="login-form" id="registerForm" novalidate>

            <div class="form-group" id="nameGroup">
                <label for="name">Full name</label>

                <div class="input-wrapper">
                    <span class="input-icon" aria-hidden="true">&#128100;</span>

                    <input
                        type="text"
                        id="name"
                        name="name"
                        placeholder="Enter your full name"
                        autocomplete="name"
                        value="<%= oldName %>"
                        aria-describedby="nameError"
                        required>
                </div>
                <small class="field-error" id="nameError"></small>
            </div>

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
                        value="<%= oldEmail %>"
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
                        placeholder="Create a password"
                        autocomplete="new-password"
                        aria-describedby="passwordError passwordHint"
                        minlength="6"
                        required>

                    <button
                        type="button"
                        class="password-toggle"
                        data-target="password"
                        aria-label="Show password">
                        Show
                    </button>
                </div>

                <div class="strength-meter" id="strengthMeter" aria-hidden="true">
                    <span></span><span></span><span></span><span></span>
                </div>
                <small class="field-hint" id="passwordHint">At least 6 characters. Mix letters, numbers &amp; symbols for a stronger password.</small>
                <small class="field-error" id="passwordError"></small>
            </div>

            <div class="form-group" id="confirmPasswordGroup">
                <label for="confirmPassword">Confirm password</label>

                <div class="input-wrapper">
                    <span class="input-icon" aria-hidden="true">&#9679;</span>

                    <input
                        type="password"
                        id="confirmPassword"
                        name="confirmPassword"
                        placeholder="Re-enter your password"
                        autocomplete="new-password"
                        aria-describedby="confirmPasswordError"
                        required>

                    <button
                        type="button"
                        class="password-toggle"
                        data-target="confirmPassword"
                        aria-label="Show password">
                        Show
                    </button>
                </div>
                <small class="field-error" id="confirmPasswordError"></small>
            </div>

            <div class="remember-row">
                <label class="checkbox-wrapper" for="registerAsSeller">
                    <input type="checkbox" id="registerAsSeller" name="registerAsSeller" value="true"
                           <%= "true".equals(request.getParameter("registerAsSeller")) ? "checked" : "" %>>
                    <span class="checkbox-box" aria-hidden="true"></span>
                    <span class="checkbox-label">I want to sell products on SUHANAMART (seller account)</span>
                </label>
            </div>

            <button type="submit" class="login-btn" id="registerBtn">
                <span class="btn-text">Create account</span>
                <span class="arrow" aria-hidden="true">&#8594;</span>
                <span class="spinner" aria-hidden="true"></span>
            </button>

        </form>

        <!-- Login -->
        <div class="register-section">
            <span>Already have an account?</span>

            <a href="<%= request.getContextPath() %>/public/login.jsp">
                Sign in
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
