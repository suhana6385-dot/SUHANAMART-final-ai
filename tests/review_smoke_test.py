#!/usr/bin/env python3
"""
SUHANAMART - Review Module (F8) end-to-end smoke test.

Drives a RUNNING deployment over plain HTTP (Python 3.8+ standard library
only), the same way a browser would: it registers throw-away accounts, buys
products, has the seller move the orders through Pending -> Confirmed ->
Shipped -> Delivered, and then exercises the review flow plus the
authorization / validation / duplicate / XSS cases.

Usage:
    python3 tests/review_smoke_test.py                       # http://localhost:8080/suhanamart
    python3 tests/review_smoke_test.py http://host:8080/app

Prerequisites: the app is deployed and the database has the V3 schema (fresh
suhanamart.sql, or the V2 + V3 migrations applied). Like seller_smoke_test.py
it creates uniquely named users/products on every run and leaves its rows
behind - run it against a development database only.

Exit code 0 = every check passed.
"""

import html
import http.cookiejar
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/suhanamart").rstrip("/")
RUN = str(int(time.time()))[-7:]
PASSWORD = "Passw0rd!x"

passed = 0
failed = 0


def check(label, condition, detail=""):
    global passed, failed
    if condition:
        passed += 1
        print("  PASS  " + label)
    else:
        failed += 1
        print("  FAIL  " + label + (("   -> " + str(detail)) if detail else ""))


def section(title):
    print("\n== " + title)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


class Client:
    """One browser session (own cookie jar)."""

    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        cookies = urllib.request.HTTPCookieProcessor(self.jar)
        self.plain = urllib.request.build_opener(cookies, NoRedirect)
        self.following = urllib.request.build_opener(cookies)

    def request(self, method, path, data=None, follow=False):
        url = path if path.startswith("http") else BASE + path
        body = urllib.parse.urlencode(data, encoding="utf-8").encode("ascii") if data is not None else None
        req = urllib.request.Request(url, data=body, method=method)
        if body is not None:
            # Deliberately NO charset here: browsers don't send one for form posts, so this
            # also proves the server decodes the body as UTF-8 by itself (EncodingFilter).
            req.add_header("Content-Type", "application/x-www-form-urlencoded")
        opener = self.following if follow else self.plain
        try:
            resp = opener.open(req, timeout=20)
        except urllib.error.HTTPError as e:
            resp = e
        text = resp.read().decode("utf-8", errors="replace")
        return (resp.status if hasattr(resp, "status") else resp.code), resp.headers, text

    def get(self, path, follow=False):
        return self.request("GET", path, follow=follow)

    def post(self, path, data, follow=False):
        return self.request("POST", path, data=data, follow=follow)


def csrf_from(page):
    m = re.search(r'name="csrfToken" value="([^"]+)"', page)
    return m.group(1) if m else None


def location(headers):
    return headers.get("Location", "")


def goto(client, headers):
    """GET the page a redirect points to (Location may be relative to the host root)."""
    return client.get(urllib.parse.urljoin(BASE + "/", location(headers)))


def register(client, name, email, seller):
    data = {"name": name, "email": email, "password": PASSWORD, "confirmPassword": PASSWORD}
    if seller:
        data["registerAsSeller"] = "true"
    return client.post("/RegisterServlet", data)


def login(client, email):
    return client.post("/LoginServlet", {"email": email, "password": PASSWORD})


def add_product(seller, name, price="100", stock="20"):
    _, _, page = seller.get("/seller/products/new")
    token = csrf_from(page)
    seller.post("/seller/products/create", {"csrfToken": token, "name": name, "description": "d",
                                            "price": price, "category": "ReviewTest", "imageUrl": "", "stock": stock})
    _, _, page = seller.get("/seller/products")
    for n, pid in re.findall(r'class="cell-title">(.*?)</div>\s*<div class="cell-sub">ID #(\d+)</div>', page, re.S):
        if html.unescape(n) == name:
            return int(pid)
    raise SystemExit("could not create product " + name)


def buy(buyer, product_ids):
    """Adds each product to the cart, checks out, returns the new order id."""
    token = csrf_from(buyer.get("/products")[2])
    for pid in product_ids:
        buyer.post("/CartServlet", {"csrfToken": token, "action": "add", "productId": pid, "quantity": 1})
    s, h, _ = buyer.post("/CheckoutServlet", {"csrfToken": token})
    m = re.search(r"orderId=(\d+)", location(h))
    if s != 302 or not m:
        raise SystemExit("checkout failed: %s %s" % (s, location(h)))
    return int(m.group(1))


def advance(seller, order_id, through="DELIVERED"):
    """The seller moves their part of an order forward, one legal step at a time."""
    for status in ("CONFIRMED", "SHIPPED", "DELIVERED"):
        _, _, page = seller.get("/seller/orders/details?orderId=%d" % order_id)
        seller.post("/seller/order-status", {"csrfToken": csrf_from(page), "orderId": order_id, "status": status})
        if status == through:
            break


def product_page(client, pid):
    return client.get("/product-details?id=%d" % pid)[2]


def review_count(client, pid):
    m = re.search(r'Based on (\d+)\s+review', product_page(client, pid))
    return int(m.group(1)) if m else 0


def average(client, pid):
    m = re.search(r'class="review-average-value">([\d.]+)<', product_page(client, pid))
    return m.group(1) if m else None


def review_link(client, order_id, pid):
    _, _, page = client.get("/public/order-details.jsp?orderId=%d" % order_id)
    return ("reviews?orderId=%d&amp;productId=%d" % (order_id, pid)) in page, page


def form_token(client, order_id, pid):
    s, _, page = client.get("/reviews?orderId=%d&productId=%d" % (order_id, pid))
    return csrf_from(page) if s == 200 else None


def submit(client, token, order_id, pid, rating, comment):
    return client.post("/reviews", {"csrfToken": token, "orderId": order_id, "productId": pid,
                                    "rating": rating, "comment": comment})


# ---------------------------------------------------------------------------

def main():
    print("SUHANAMART review smoke test against " + BASE + "  (run id " + RUN + ")")

    anon = Client()
    sellerA, sellerB, buyer1, buyer2 = Client(), Client(), Client(), Client()
    emails = {k: "review-%s-%s@example.com" % (k, RUN) for k in ("a", "b", "b1", "b2")}

    section("1. Setup: accounts, products, orders")
    register(sellerA, "Seller A " + RUN, emails["a"], True)
    register(sellerB, "Seller B " + RUN, emails["b"], True)
    register(buyer1, "Buyer One " + RUN, emails["b1"], False)
    register(buyer2, "Buyer Two " + RUN, emails["b2"], False)
    for c, k in ((sellerA, "a"), (sellerB, "b"), (buyer1, "b1"), (buyer2, "b2")):
        login(c, emails[k])
    P1 = add_product(sellerA, "Review Product One " + RUN)
    P2 = add_product(sellerB, "Review Product Two " + RUN)
    check("products created", P1 > 0 and P2 > 0, (P1, P2))

    O0 = buy(buyer1, [P1])          # buyer1: only seller A's product
    O2 = buy(buyer2, [P1])          # buyer2: only seller A's product
    O1 = buy(buyer1, [P1, P2])      # buyer1: two sellers in one order
    OA = buy(sellerA, [P1])         # seller A buys their OWN product
    check("orders placed", len({O0, O1, O2, OA}) == 4, (O0, O1, O2, OA))

    section("2. Access control and bad requests")
    for method, path in (("GET", "/reviews?orderId=%d&productId=%d" % (O0, P1)),
                         ("GET", "/public/reviews.jsp"), ("POST", "/reviews")):
        s, h, _ = anon.request(method, path, data={} if method == "POST" else None)
        check("anonymous %s %s -> login" % (method, path.split("?")[0]), s == 302 and "login.jsp" in location(h), "%s %s" % (s, location(h)))
    for bad in ("", "?orderId=abc&productId=1", "?orderId=-1&productId=%d" % P1, "?orderId=0&productId=0",
                "?orderId=99999999999999&productId=1", "?orderId=%d" % O0, "?orderId=1%%27%%20OR%%201%%3D1--&productId=1"):
        s, h, body = buyer1.get("/reviews" + bad)
        check("GET /reviews%s -> redirected to My Orders, no error page" % bad[:28],
              s == 302 and "orders.jsp" in location(h) and "Exception" not in body, "%s %s" % (s, location(h)))
    s, h, _ = buyer1.get("/public/reviews.jsp")
    check("opening reviews.jsp directly is handed to the controller", s == 302 and location(h).endswith("/reviews"), "%s %s" % (s, location(h)))
    s, _, body = buyer1.get("/reviews?orderId=abc&productId=1", follow=True)
    check("the reason is shown on My Orders", "Choose a delivered item" in body)

    section("3. Not delivered -> no review")
    has_link, page = review_link(buyer1, O0, P1)
    check("pending order offers no Review action", not has_link and "Write a review" not in page)
    s, h, _ = buyer1.get("/reviews?orderId=%d&productId=%d" % (O0, P1))
    check("review form for a pending order is refused", s == 302 and "order-details.jsp" in location(h), "%s %s" % (s, location(h)))
    s, _, body = buyer1.get("/reviews?orderId=%d&productId=%d" % (O0, P1), follow=True)
    check("...with an explanation", "once its order has been delivered" in body)

    advance(sellerA, O0, "SHIPPED")
    has_link, _ = review_link(buyer1, O0, P1)
    check("shipped (not yet delivered) is still not reviewable", not has_link)
    advance(sellerA, O0)
    advance(sellerA, O2)
    advance(sellerA, OA)

    section("4. Delivered -> Review action and form")
    has_link, page = review_link(buyer1, O0, P1)
    check("delivered order shows 'Write a review' for the product", has_link)
    s, _, form = buyer1.get("/reviews?orderId=%d&productId=%d" % (O0, P1))
    token1 = csrf_from(form)
    check("review form renders with stars, textarea and CSRF token",
          s == 200 and token1 and 'name="rating"' in form and 'name="comment"' in form and "Review Product One" in form)
    token2 = form_token(buyer2, O2, P1)
    check("second buyer can open their own form", bool(token2))

    section("5. Validation and CSRF (nothing may be stored)")
    good = "Works really well, would buy again."
    s, _, _ = buyer1.post("/reviews", {"orderId": O0, "productId": P1, "rating": "5", "comment": good})
    check("POST without CSRF token -> 403", s == 403, s)
    s, _, _ = submit(buyer1, "forged", O0, P1, "5", good)
    check("POST with a wrong CSRF token -> 403", s == 403, s)
    s, _, body = submit(buyer1, token2, O0, P1, "5", good)
    check("another session's CSRF token -> 403", s == 403, s)
    bad_ratings = ["", "0", "6", "-1", "abc", "3.5", "5;DROP TABLE reviews", "05", " ", "1 OR 1=1"]
    for r in bad_ratings:
        s, _, body = submit(buyer1, token1, O0, P1, r, good)
        check("rating %r rejected with a message and the typed comment kept" % r,
              s == 200 and "Please choose a rating" in body and good in body, s)
    bad_comments = {"empty": "", "spaces only": "     ", "too short": "ok", "too long": "x" * 1001,
                    "control character": "hello\x00world text"}
    for label, c in bad_comments.items():
        s, _, body = submit(buyer1, token1, O0, P1, "4", c)
        check("comment (%s) rejected" % label, s == 200 and "alert-error" in body, s)
    check("nothing was stored by any invalid request", review_count(buyer1, P1) == 0)

    section("6. Successful review, escaping and encoding")
    tamil = "\u0bae\u0bbf\u0b95\u0bb5\u0bc1\u0bae\u0bcd \u0ba8\u0ba9\u0bcd\u0bb1\u0bbe\u0b95 \u0b89\u0bb3\u0bcd\u0bb3\u0ba4\u0bc1"
    xss = "<script>alert('x')</script> & \"quotes\"\nSecond line. " + tamil
    s, h, _ = submit(buyer1, token1, O0, P1, "4", xss)
    check("valid review -> redirect to the product page", s == 302 and "/product-details?id=%d" % P1 in location(h), "%s %s" % (s, location(h)))
    s, _, page = goto(buyer1, h)
    check("success message shown once", "Your review has been posted" in page)
    page = product_page(buyer1, P1)
    check("review is listed on the product page", review_count(buyer1, P1) == 1 and "Verified purchase" in page)
    check("comment is HTML-escaped (no raw <script>)", "<script>alert('x')</script>" not in page and "&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;" in page)
    check("non-ASCII comment survives (UTF-8 end to end)", tamil in page)
    check("reviewer name shown", ("Buyer One " + RUN) in page)
    check("average and count are right (4.0, 1 review)", average(buyer1, P1) == "4.0")
    check("flash message does not reappear on refresh", "Your review has been posted" not in product_page(buyer1, P1))

    section("7. Duplicate prevention")
    s, h, _ = submit(buyer1, token1, O0, P1, "1", "Trying to review the same purchase twice.")
    check("second review of the same order+product is refused", s == 302 and "order-details.jsp" in location(h), "%s %s" % (s, location(h)))
    _, _, body = goto(buyer1, h)
    check("...with a clear message", "already reviewed" in body)
    check("still exactly one review, rating unchanged", review_count(buyer1, P1) == 1 and average(buyer1, P1) == "4.0")
    has_link, page = review_link(buyer1, O0, P1)
    check("order page now shows 'Reviewed' instead of the action", not has_link and "Reviewed" in page)
    s, h, _ = buyer1.get("/reviews?orderId=%d&productId=%d" % (O0, P1))
    check("form for an already-reviewed item is refused", s == 302 and "order-details.jsp" in location(h))

    section("8. Authorization / IDOR")
    before = review_count(buyer2, P1)
    s, h, _ = buyer2.get("/reviews?orderId=%d&productId=%d" % (O1, P1))
    check("buyer 2 cannot open the form for buyer 1's order", s == 302 and "orders.jsp" in location(h), "%s %s" % (s, location(h)))
    s, h, _ = submit(buyer2, token2, O1, P1, "1", "Trying to review someone else's order.")
    check("buyer 2 cannot POST a review for buyer 1's order", s == 302 and "orders.jsp" in location(h), "%s %s" % (s, location(h)))
    s, h, _ = submit(buyer1, token1, O0, P2, "1", "Product two is not part of order zero.")
    check("product that is not in the order is refused", s == 302 and "orders.jsp" in location(h), "%s %s" % (s, location(h)))
    s, h, _ = submit(buyer1, token1, 99999999, P1, "1", "This order does not exist at all.")
    check("non-existent order is refused", s == 302 and "orders.jsp" in location(h))
    s, h, _ = submit(buyer1, token1, "abc", P1, "1", "Malformed order id in the request.")
    check("malformed order id is refused", s == 302 and "orders.jsp" in location(h))
    s, _, body = buyer2.get("/public/order-details.jsp?orderId=%d" % O0)
    check("buyer 2 cannot see buyer 1's order page (existing protection intact)", "couldn't find that order" in body)
    check("none of these attempts stored anything", review_count(buyer2, P1) == before and review_count(buyer2, P2) == 0)
    s, h, _ = submit(sellerA, "forged", OA, P1, "5", "Seller reviewing own product.")
    check("seller POST needs a token too", s == 403, s)

    section("9. Order-level 'delivered' rule with two sellers")
    advance(sellerA, O1)
    has1, _ = review_link(buyer1, O1, P1)
    has2, _ = review_link(buyer1, O1, P2)
    check("one seller delivered, other not: order not delivered -> no Review action", not has1 and not has2)
    s, h, _ = submit(buyer1, token1, O1, P1, "5", "Seller B has not shipped yet, so refused.")
    check("POST for a not-fully-delivered order is refused", s == 302 and "order-details.jsp" in location(h), "%s %s" % (s, location(h)))
    check("nothing stored", review_count(buyer1, P1) == 1)
    advance(sellerB, O1)
    has1, _ = review_link(buyer1, O1, P1)
    has2, _ = review_link(buyer1, O1, P2)
    check("both sellers delivered: Review action on both lines", has1 and has2)
    s, h, _ = submit(buyer1, token1, O1, P2, "2", "Product two was disappointing overall.")
    check("review for product two accepted", s == 302, s)
    s, h, _ = submit(buyer1, token1, O1, P1, "5", "Second purchase of product one, still great.")
    check("same product, DIFFERENT order -> allowed", s == 302, s)

    section("10. Sellers cannot review their own products")
    has_link, page = review_link(sellerA, OA, P1)
    check("seller's own purchase of their own product offers no Review action", not has_link and "Reviewed" not in page)
    tokenA = csrf_from(sellerA.get("/seller/products/new")[2])
    s, h, _ = submit(sellerA, tokenA, OA, P1, "5", "Five stars for my own product!")
    check("POST refused", s == 302, s)
    s, _, body = goto(sellerA, h)
    check("with the reason", "your own product" in body)

    section("11. Rating summary and display")
    s, h, _ = submit(buyer2, token2, O2, P1, "1", "Did not work for me at all, sorry.")
    check("buyer 2's review accepted", s == 302, s)
    page = product_page(buyer1, P1)
    check("product one: 3 reviews, average (4+5+1)/3 = 3.3", review_count(buyer1, P1) == 3 and average(buyer1, P1) == "3.3", average(buyer1, P1))
    check("rating line under the title shows the average", "<strong>3.3</strong>" in page)
    check("star distribution present (5, 4 and 1 star rows count 1)", len(re.findall(r'class="review-bar-count">1<', page)) == 3)
    check("newest review listed first", page.index("Did not work for me") < page.index("Second purchase of product one"))
    check("product two: 1 review, average 2.0", review_count(buyer1, P2) == 1 and average(buyer1, P2) == "2.0")
    empty_pid = add_product(sellerA, "Review Product Three " + RUN)
    page = product_page(buyer1, empty_pid)
    check("product without reviews shows the empty state", "No reviews yet" in page)

    section("12. Errors never expose internals")
    for path in ("/definitely-not-a-page", "/product-details?id=abc", "/product-details?id=999999"):
        s, _, body = buyer1.get(path)
        check("%s -> friendly page, no stack trace" % path, "Apache Tomcat" not in body and "at com.suhanamart" not in body and "Exception" not in body, s)
    s, _, body = buyer1.post("/reviews", {"rating": "5"})
    check("403 page is the friendly one", s == 403 and "Access denied" in body and "Apache Tomcat" not in body, s)

    print("\n%d passed, %d failed" % (passed, failed))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
