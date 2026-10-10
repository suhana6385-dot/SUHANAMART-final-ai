// SUHANAMART - admin.js
// Presentational helpers for the admin pages only, mirroring seller.js
// exactly: confirm before a destructive action, and block double-submits.
// Nothing here is a security control - every permission check and rule
// (an admin can't disable their own account or any admin account, a
// product with order history can't be removed) is enforced again on the
// server, so this script can be disabled or bypassed without weakening
// anything.

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

        // Stop double-submits: once an admin form is submitted, disable its
        // buttons so a second click can't send the same request twice.
        document.querySelectorAll("form").forEach(function (form) {
            form.addEventListener("submit", function (event) {
                if (event.defaultPrevented) { return; }
                window.setTimeout(function () {
                    form.querySelectorAll("button[type=submit]").forEach(function (button) {
                        button.disabled = true;
                    });
                }, 0);
            });
        });
    });

    window.addEventListener("pageshow", function (event) {
        if (event.persisted) {
            document.querySelectorAll("button[type=submit]").forEach(function (button) {
                button.disabled = false;
            });
        }
    });
})();
