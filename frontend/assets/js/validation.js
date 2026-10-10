// SUHANAMART - validation.js
// Client-side UX only: password visibility, confirm-password matching,
// password strength indicator, and light validation messages, for
// BOTH the login and register forms. Merged from what used to be
// separate login.js/register.js - each setup function checks for its
// own form before doing anything, so loading this one file on both
// pages is safe. Real validation always happens again on the server
// (LoginServlet / RegisterServlet) - this file never replaces it.

(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        setupPasswordToggles();
        setupStrengthMeter();
        setupLoginFormValidation();
        setupRegisterFormValidation();
    });

    function setupPasswordToggles() {
        var toggles = document.querySelectorAll(".password-toggle");

        toggles.forEach(function (button) {
            button.addEventListener("click", function () {
                var targetId = button.getAttribute("data-target");
                var input = document.getElementById(targetId);
                if (!input) { return; }

                var isHidden = input.type === "password";
                input.type = isHidden ? "text" : "password";
                button.textContent = isHidden ? "Hide" : "Show";
                button.setAttribute("aria-label", isHidden ? "Hide password" : "Show password");
            });
        });
    }

    function setupStrengthMeter() {
        var passwordInput = document.getElementById("password");
        var meter = document.getElementById("strengthMeter");
        if (!passwordInput || !meter) { return; }

        passwordInput.addEventListener("input", function () {
            var score = scorePassword(passwordInput.value);
            meter.classList.remove("weak", "fair", "good", "strong");

            if (passwordInput.value.length === 0) {
                return;
            }
            if (score <= 1) {
                meter.classList.add("weak");
            } else if (score === 2) {
                meter.classList.add("fair");
            } else if (score === 3) {
                meter.classList.add("good");
            } else {
                meter.classList.add("strong");
            }
        });
    }

    // Simple heuristic: length + variety of character classes.
    function scorePassword(value) {
        var score = 0;
        if (value.length >= 6) { score++; }
        if (value.length >= 10) { score++; }
        if (/[A-Z]/.test(value) && /[a-z]/.test(value)) { score++; }
        if (/[0-9]/.test(value) && /[^A-Za-z0-9]/.test(value)) { score++; }
        return Math.min(score, 4);
    }

    function setupLoginFormValidation() {
        var form = document.getElementById("loginForm");
        if (!form) { return; }

        var emailInput = document.getElementById("email");
        var passwordInput = document.getElementById("password");
        var submitBtn = document.getElementById("loginBtn");

        form.addEventListener("submit", function (event) {
            var valid = true;

            valid = validateEmail(emailInput) && valid;
            valid = validateRequired(passwordInput, "passwordGroup", "passwordError", "Please enter your password.") && valid;

            if (!valid) {
                event.preventDefault();
                return;
            }

            // Server does the real work; just show a brief loading state.
            if (submitBtn) {
                submitBtn.classList.add("loading");
            }
        });

        emailInput.addEventListener("input", function () {
            clearFieldError("emailGroup");
        });
        passwordInput.addEventListener("input", function () {
            clearFieldError("passwordGroup");
        });
    }

    function setupRegisterFormValidation() {
        var form = document.getElementById("registerForm");
        if (!form) { return; }

        var nameInput = document.getElementById("name");
        var emailInput = document.getElementById("email");
        var passwordInput = document.getElementById("password");
        var confirmInput = document.getElementById("confirmPassword");
        var submitBtn = document.getElementById("registerBtn");

        form.addEventListener("submit", function (event) {
            var valid = true;

            valid = validateRequired(nameInput, "nameGroup", "nameError", "Please enter your full name.") && valid;
            valid = validateEmail(emailInput) && valid;
            valid = validatePasswordLength(passwordInput) && valid;
            valid = validateConfirmPassword(passwordInput, confirmInput) && valid;

            if (!valid) {
                event.preventDefault();
                return;
            }

            if (submitBtn) {
                submitBtn.classList.add("loading");
            }
        });

        [nameInput, emailInput, passwordInput].forEach(function (input) {
            input.addEventListener("input", function () {
                clearFieldError(input.id + "Group");
            });
        });

        confirmInput.addEventListener("input", function () {
            clearFieldError("confirmPasswordGroup");
        });
    }

    function validateEmail(input) {
        var value = input.value.trim();
        var emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

        if (value === "") {
            showFieldError("emailGroup", "emailError", "Please enter your email address.");
            return false;
        }
        if (!emailPattern.test(value)) {
            showFieldError("emailGroup", "emailError", "Please enter a valid email address.");
            return false;
        }
        clearFieldError("emailGroup");
        return true;
    }

    function validatePasswordLength(input) {
        if (input.value.length < 6) {
            showFieldError("passwordGroup", "passwordError", "Password must be at least 6 characters long.");
            return false;
        }
        clearFieldError("passwordGroup");
        return true;
    }

    function validateConfirmPassword(passwordInput, confirmInput) {
        if (confirmInput.value === "") {
            showFieldError("confirmPasswordGroup", "confirmPasswordError", "Please confirm your password.");
            return false;
        }
        if (confirmInput.value !== passwordInput.value) {
            showFieldError("confirmPasswordGroup", "confirmPasswordError", "Passwords do not match.");
            return false;
        }
        clearFieldError("confirmPasswordGroup");
        return true;
    }

    function validateRequired(input, groupId, errorId, message) {
        if (input.value.trim() === "") {
            showFieldError(groupId, errorId, message);
            return false;
        }
        clearFieldError(groupId);
        return true;
    }

    function showFieldError(groupId, errorId, message) {
        var group = document.getElementById(groupId);
        var errorEl = document.getElementById(errorId);
        if (group) { group.classList.add("invalid"); }
        if (errorEl) { errorEl.textContent = message; }
    }

    function clearFieldError(groupId) {
        var group = document.getElementById(groupId);
        if (group) { group.classList.remove("invalid"); }
    }
})();
