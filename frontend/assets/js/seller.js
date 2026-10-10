// SUHANAMART - seller.js
// Presentational helpers for the seller pages only. Nothing here is a
// security control: every permission check, validation rule and status
// rule is enforced again on the server, so these scripts can be
// disabled or bypassed without weakening anything.

(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {

        // Ask before a destructive action: <form data-confirm="Are you sure?">
        document.querySelectorAll("form[data-confirm]").forEach(function (form) {
            form.addEventListener("submit", function (event) {
                if (!window.confirm(form.getAttribute("data-confirm"))) {
                    event.preventDefault();
                }
            });
        });

        // Stop double-submits: once a seller form is submitted, disable its
        // buttons so a second click can't send the same request twice.
        document.querySelectorAll("form").forEach(function (form) {
            form.addEventListener("submit", function (event) {
                if (event.defaultPrevented) { return; }
                // Disable on the next tick so the clicked button's own value is still sent.
                window.setTimeout(function () {
                    form.querySelectorAll("button[type=submit]").forEach(function (button) {
                        button.disabled = true;
                    });
                }, 0);
            });
        });
    });

    // If the page is restored from the browser's back/forward cache, the
    // buttons above would stay disabled - re-enable them.
    window.addEventListener("pageshow", function (event) {
        if (event.persisted) {
            document.querySelectorAll("button[type=submit]").forEach(function (button) {
                button.disabled = false;
            });
        }
    });
})();
