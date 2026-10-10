// SUHANAMART - main.js
// Purely presentational: toggles the mobile navigation menu on every
// authenticated page (dashboard, products, cart, checkout, orders,
// profile). No authentication or business logic lives here - all
// page data comes from the server-rendered JSP/servlet.
// (Renamed from the old dashboard.js during the project reorganization.)

(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        var toggle = document.getElementById("navToggle");
        var nav = document.getElementById("dashNav");
        if (!toggle || !nav) { return; }

        toggle.addEventListener("click", function () {
            var isOpen = nav.classList.toggle("open");
            toggle.setAttribute("aria-expanded", isOpen ? "true" : "false");
        });

        // Close the menu after tapping a link (nice on mobile).
        nav.querySelectorAll("a").forEach(function (link) {
            link.addEventListener("click", function () {
                nav.classList.remove("open");
                toggle.setAttribute("aria-expanded", "false");
            });
        });
    });
})();

// ---------------------------------------------------------------------
// AI chat widget (footer.jspf). Only present in the page at all when the
// visitor is logged in (footer.jspf checks the session), so this code is
// a no-op on every page where the widget markup doesn't exist.
//
// This talks to the server (POST /chat) - unlike the rest of this file,
// which stays purely presentational - but it carries no business logic
// of its own: validation, rate limiting, database access and the AI call
// all happen in ChatService; this script only sends the message, shows
// the reply as plain text (never innerHTML, so nothing it displays can
// execute), and disables the form while a request is in flight.
// ---------------------------------------------------------------------
(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        var widget = document.getElementById("chatWidget");
        if (!widget) { return; }

        var toggle = document.getElementById("chatToggle");
        var panel = document.getElementById("chatPanel");
        var closeBtn = document.getElementById("chatClose");
        var messages = document.getElementById("chatMessages");
        var form = document.getElementById("chatForm");
        var input = document.getElementById("chatInput");
        var csrfToken = widget.getAttribute("data-csrf-token");

        function setOpen(open) {
            panel.hidden = !open;
            toggle.setAttribute("aria-expanded", open ? "true" : "false");
            if (open) { input.focus(); }
        }

        toggle.addEventListener("click", function () { setOpen(panel.hidden); });
        closeBtn.addEventListener("click", function () { setOpen(false); });

        function addMessage(text, cssClass) {
            var bubble = document.createElement("div");
            bubble.className = "chat-message " + cssClass;
            // textContent only - never innerHTML - so a reply can never inject markup or script.
            bubble.textContent = text;
            messages.appendChild(bubble);
            messages.scrollTop = messages.scrollHeight;
        }

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            var text = input.value.trim();
            if (!text) { return; }

            addMessage(text, "chat-message-user");
            input.value = "";
            input.disabled = true;
            form.querySelector("button[type=submit]").disabled = true;

            var body = new URLSearchParams();
            body.set("message", text);
            body.set("csrfToken", csrfToken);

            fetch(contextPath() + "/chat", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: body.toString()
            })
                .then(function (response) {
                    return response.json().catch(function () {
                        return { error: true, reply: "Something went wrong. Please try again later." };
                    });
                })
                .then(function (data) {
                    addMessage(data.reply, data.error ? "chat-message-error" : "chat-message-bot");
                })
                .catch(function () {
                    addMessage("Couldn't reach the server. Please check your connection and try again.", "chat-message-error");
                })
                .finally(function () {
                    input.disabled = false;
                    form.querySelector("button[type=submit]").disabled = false;
                    input.focus();
                });
        });

        function contextPath() {
            // The app may be deployed under any context root - derive it from this
            // script's own src="<ctx>/assets/js/main.js" instead of hard-coding "/suhanamart".
            var script = document.querySelector('script[src*="/assets/js/main.js"]');
            if (!script) { return ""; }
            return script.getAttribute("src").split("/assets/js/main.js")[0];
        }
    });
})();
