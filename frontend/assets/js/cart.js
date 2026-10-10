// SUHANAMART - cart.js
// Progressive enhancement only for cart.jsp. Every cart action
// (add/update/remove/clear) is a plain HTML form posting to
// CartServlet, and works correctly with this script absent - this
// file only prevents accidental double-submits (e.g. a user
// double-clicking "Remove" or a quantity button) by disabling the
// clicked button immediately after submit.

(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        var cartForms = document.querySelectorAll(".qty-form form, form[action$='CartServlet']");

        cartForms.forEach(function (form) {
            form.addEventListener("submit", function () {
                var button = form.querySelector("button[type='submit']");
                if (button) {
                    // Let the click register, then disable to block a second submit.
                    setTimeout(function () {
                        button.disabled = true;
                    }, 0);
                }
            });
        });
    });
})();
