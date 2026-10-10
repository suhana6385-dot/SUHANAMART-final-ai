// SUHANAMART - review.js
// Presentational helpers for the review form (reviews.jsp) only. Nothing
// here is a security control: the rating range, comment length, ownership,
// delivered-order rule and duplicate check are all enforced again on the
// server (ReviewService / ReviewDAO), so this script can be disabled or
// bypassed without weakening anything.

(function () {
    "use strict";

    var RATING_WORDS = { 1: "Poor", 2: "Fair", 3: "Good", 4: "Very good", 5: "Excellent" };

    document.addEventListener("DOMContentLoaded", function () {
        var form = document.getElementById("reviewForm");
        if (!form) { return; }

        var comment = document.getElementById("comment");
        var counter = document.getElementById("commentCount");
        var hint = document.getElementById("ratingHint");
        var message = document.getElementById("clientMessage");
        var min = parseInt(form.getAttribute("data-min"), 10) || 0;
        var max = parseInt(form.getAttribute("data-max"), 10) || 1000;

        function length() {
            // Count characters (not UTF-16 units), like the server does.
            return Array.from(comment.value.replace(/\r\n/g, "\n").trim()).length;
        }

        function updateCounter() {
            var n = length();
            counter.textContent = String(n);
            counter.classList.toggle("over", n > max);
        }

        function updateHint() {
            var checked = form.querySelector("input[name='rating']:checked");
            hint.textContent = checked ? RATING_WORDS[checked.value] || "" : "";
        }

        comment.addEventListener("input", updateCounter);
        form.querySelectorAll("input[name='rating']").forEach(function (radio) {
            radio.addEventListener("change", updateHint);
        });
        updateCounter();
        updateHint();

        form.addEventListener("submit", function (event) {
            var problems = [];
            if (!form.querySelector("input[name='rating']:checked")) {
                problems.push("Please choose a rating from 1 to 5 stars.");
            }
            var n = length();
            if (n < min) {
                problems.push("Your review must be at least " + min + " characters long.");
            } else if (n > max) {
                problems.push("Your review must be at most " + max + " characters long.");
            }

            if (problems.length > 0) {
                event.preventDefault();
                message.textContent = problems.join(" ");
                message.hidden = false;
                return;
            }
            message.hidden = true;

            // Stop double-submits; re-enabled below if the page is restored from cache.
            window.setTimeout(function () {
                form.querySelectorAll("button[type=submit]").forEach(function (button) {
                    button.disabled = true;
                });
            }, 0);
        });
    });

    window.addEventListener("pageshow", function (event) {
        if (event.persisted) {
            document.querySelectorAll("#reviewForm button[type=submit]").forEach(function (button) {
                button.disabled = false;
            });
        }
    });
})();
