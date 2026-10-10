#!/usr/bin/env python3
"""
SUHANAMART - Seller module end-to-end smoke test.

Drives a RUNNING deployment over plain HTTP (no browser, no extra
packages - Python 3.8+ standard library only), the same way a browser
would: it registers throw-away accounts, logs in, and exercises seller
product management, incoming orders, status updates, buyer visibility,
and the authorization/attack cases.

Usage:
    python3 tests/seller_smoke_test.py                       # http://localhost:8080/suhanamart
    python3 tests/seller_smoke_test.py http://host:8080/app

Prerequisites: the app is deployed, the database has the V2 schema
(fresh suhanamart.sql, or the V2__order_status.sql migration applied).
The script creates uniquely-named users/products on every run and
never touches existing data, but it does leave its test rows behind -
run it against a development database only.

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
RUN = str(int(time.time()))[-7:]          # makes names/emails unique per run
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
        """Returns (status, headers, body). Redirects are NOT followed unless follow=True."""
        url = path if path.startswith("http") else BASE + path
        body = urllib.parse.urlencode(data, encoding="utf-8").encode("ascii") if data is not None else None
        req = urllib.request.Request(url, data=body, method=method)
        if body is not None:
            req.add_header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        opener = self.following if follow else self.plain
        try:
            resp = opener.open(req, timeout=20)
        except urllib.error.HTTPError as e:
            resp = e
        text = resp.read().decode("utf-8", errors="replace")
        return resp.status if hasattr(resp, "status") else resp.code, resp.headers, text

    def get(self, path, follow=False):
        return self.request("GET", path, follow=follow)

    def post(self, path, data, follow=False):
        return self.request("POST", path, data=data, follow=follow)


def csrf_from(page):
    m = re.search(r'name="csrfToken" value="([^"]+)"', page)
    return m.group(1) if m else None


def location(headers):
    return headers.get("Location", "")


def register(client, name, email, seller):
    data = {"name": name, "email": email, "password": PASSWORD, "confirmPassword": PASSWORD}
    if seller:
        data["registerAsSeller"] = "true"
    return client.post("/RegisterServlet", data)


def login(client, email):
    return client.post("/LoginServlet", {"email": email, "password": PASSWORD})


def seller_csrf(client):
    """A CSRF token as a seller's browser would obtain it (from the add-product form)."""
    _, _, page = client.get("/seller/products/new")
    return csrf_from(page)


def add_product(client, name, price="100", stock="10", category="SmokeTest", image="", description="d"):
    token = seller_csrf(client)
    return client.post("/seller/products/create", {
        "csrfToken": token, "name": name, "description": description, "price": price,
        "category": category, "imageUrl": image, "stock": stock})


def product_ids(client):
    """{product name (unescaped): id} for the seller's own product list."""
    _, _, page = client.get("/seller/products")
    out = {}
    for name, pid in re.findall(r'class="cell-title">(.*?)</div>\s*<div class="cell-sub">ID #(\d+)</div>', page, re.S):
        out[html.unescape(name)] = int(pid)
    return out


def buyer_csrf(client):
    """A CSRF token as a buyer's browser would obtain it (from the add-to-cart forms on /products)."""
    _, _, page = client.get("/products")
    return csrf_from(page)


def list_page(client):
    return client.get("/seller/products")[2]


def stat_values(client):
    _, _, page = client.get("/seller/dashboard")
    return [int(v) for v in re.findall(r'class="stat-value">(\d+)<', page)]


# ---------------------------------------------------------------------------

def main():
    print("SUHANAMART seller smoke test against " + BASE + "  (run id " + RUN + ")")

    anon = Client()
    sellerA, sellerB, sellerC, buyer = Client(), Client(), Client(), Client()
    emails = {k: "smoke-%s-%s@example.com" % (k, RUN) for k in ("a", "b", "c", "buyer")}

    # ------------------------------------------------------------------
    section("1. Registration and login (existing flows + new role)")
    s, _, page = register(sellerA, "Seller A " + RUN, emails["a"], True)
    check("seller registration succeeds", s == 200 and "Account created successfully" in page)
    register(sellerB, "Seller B " + RUN, emails["b"], True)
    register(sellerC, "Seller C " + RUN, emails["c"], True)
    s, _, page = register(buyer, "Buyer " + RUN, emails["buyer"], False)
    check("buyer registration succeeds", s == 200 and "Account created successfully" in page)
    s, _, page = register(Client(), "Dup", emails["buyer"], False)
    check("duplicate email still rejected", "already exists" in page)
    s, _, page = login(Client(), emails["buyer"].replace("smoke", "nobody"))
    check("unknown user still gets the generic login error", "Invalid email or password" in page)

    s, h, _ = login(sellerA, emails["a"])
    check("seller login redirects to the seller dashboard", s == 302 and location(h).endswith("/seller/dashboard"), location(h))
    login(sellerB, emails["b"])
    login(sellerC, emails["c"])
    s, h, _ = login(buyer, emails["buyer"])
    check("buyer login redirects to the buyer dashboard", s == 302 and location(h).endswith("/public/dashboard.jsp"), location(h))

    # ------------------------------------------------------------------
    section("2. Role-based access control")
    for path in ("/seller/dashboard", "/seller/products", "/seller/orders", "/public/seller/products.jsp"):
        s, h, _ = anon.get(path)
        check("anonymous %s -> login" % path, s == 302 and "login.jsp" in location(h), "%s %s" % (s, location(h)))
    for path in ("/seller/dashboard", "/seller/products", "/seller/products/new", "/seller/orders",
                 "/public/seller/dashboard.jsp", "/public/seller/add-product.jsp"):
        s, _, _ = buyer.get(path)
        check("buyer GET %s -> 403" % path, s == 403, s)
    s, _, _ = buyer.post("/seller/products/create", {"name": "x", "price": "1", "stock": "1", "category": "c"})
    check("buyer POST create product -> 403", s == 403, s)
    s, _, _ = buyer.post("/seller/order-status", {"orderId": "1", "status": "CONFIRMED"})
    check("buyer POST order-status -> 403", s == 403, s)
    s, h, _ = sellerA.get("/public/seller/products.jsp")
    check("seller opening a seller JSP directly is sent to the controller",
          s == 302 and location(h).endswith("/seller/products"), "%s %s" % (s, location(h)))
    s, _, _ = sellerA.get("/seller/order-status")
    check("order-status does not accept GET", s == 405, s)
    for path in ("/products", "/public/cart.jsp", "/public/orders.jsp", "/public/profile.jsp"):
        s, h, _ = anon.get(path)
        check("existing page %s still requires login" % path, s == 302 and "login.jsp" in location(h))

    # ------------------------------------------------------------------
    section("3. Seller product management")
    s, _, _ = sellerA.post("/seller/products/create", {"name": "NoToken", "price": "1", "stock": "1", "category": "c"})
    check("POST without CSRF token -> 403", s == 403, s)
    s, _, _ = sellerA.post("/seller/products/create", {"csrfToken": "forged", "name": "Forged", "price": "1", "stock": "1", "category": "c"})
    check("POST with a wrong CSRF token -> 403", s == 403, s)

    bad_cases = [
        ("empty name", dict(name="", price="10", stock="1"), "Product name is required"),
        ("price not a number", dict(name="P", price="abc", stock="1"), "Price must be a number"),
        ("price with exponent", dict(name="P", price="1e3", stock="1"), "Price must be a number"),
        ("price zero", dict(name="P", price="0", stock="1"), "greater than 0"),
        ("price negative", dict(name="P", price="-5", stock="1"), "Price must be a number"),
        ("price too many decimals", dict(name="P", price="1.999", stock="1"), "Price must be a number"),
        ("stock negative", dict(name="P", price="10", stock="-1"), "Stock must be a whole number"),
        ("stock decimal", dict(name="P", price="10", stock="2.5"), "Stock must be a whole number"),
        ("stock too large", dict(name="P", price="10", stock="99999999"), "Stock must be a whole number"),
        ("javascript: image URL", dict(name="P", price="10", stock="1", image="javascript:alert(1)"), "http:// or https://"),
        ("relative image URL", dict(name="P", price="10", stock="1", image="/images/a.png"), "http:// or https://"),
        ("name too long", dict(name="N" * 151, price="10", stock="1"), "at most 150"),
    ]
    for label, kwargs, expected in bad_cases:
        s, _, page = add_product(sellerA, **kwargs)
        check("rejected: " + label, s == 200 and expected in page and "Please fix the following" in page, "%s" % s)

    s, _, page = add_product(sellerA, name="Bad Product", price="abc", stock="7", category="KeepMe", description="typed text")
    check("rejected form keeps what the seller typed", 'value="Bad Product"' in page and 'value="KeepMe"' in page and "typed text" in page)

    pA1, pA2, pA3 = "SmokeA1-" + RUN, "SmokeA2-" + RUN, "SmokeA3-" + RUN
    s, h, _ = add_product(sellerA, pA1, price="250.50", stock="10", image="https://example.com/a.jpg")
    check("valid product created (redirect to list)", s == 302 and location(h).endswith("/seller/products"), "%s %s" % (s, location(h)))
    add_product(sellerA, pA2, price="99", stock="3")           # low stock
    add_product(sellerA, pA3, price="10", stock="0")           # out of stock
    s, _, page = sellerA.get("/seller/products")
    check("flash message shown once after add", "Product added." in page)
    s, _, page = sellerA.get("/seller/products")
    check("flash message not repeated on refresh", "Product added." not in page)
    idsA = product_ids(sellerA)
    check("seller A sees exactly their 3 products", all(n in idsA for n in (pA1, pA2, pA3)) and len(idsA) == 3, idsA)

    xss = "<script>alert('x')</script>" + RUN
    add_product(sellerA, xss, price="5", stock="1")
    page = list_page(sellerA)
    check("HTML in a product name is escaped (no XSS)", "<script>alert('x')" not in page and "&lt;script&gt;" in page)
    tamil = "தமிழ் Tea " + RUN
    add_product(sellerA, tamil, price="5", stock="1")
    check("non-ASCII (Tamil) product name round-trips", tamil in list_page(sellerA))
    idsA = product_ids(sellerA)
    xss_id, tamil_id = idsA[xss], idsA[tamil]
    token = seller_csrf(sellerA)
    sellerA.post("/seller/products/delete", {"csrfToken": token, "productId": xss_id})
    sellerA.post("/seller/products/delete", {"csrfToken": token, "productId": tamil_id})

    # Dashboard numbers: 3 products (2 in stock, 1 low: pA2, 1 out: pA3)
    vals = stat_values(sellerA)
    check("dashboard product stats are real (total 3, active 2, low 1, out 1)", vals[:4] == [3, 2, 1, 1], vals)
    check("dashboard order stats start at zero", vals[4:8] == [0, 0, 0, 0], vals)

    # ------------------------------------------------------------------
    section("4. Ownership: seller B against seller A's product")
    add_product(sellerB, "SmokeB1-" + RUN, price="40", stock="20")
    idsB = product_ids(sellerB)
    pB1 = "SmokeB1-" + RUN
    check("seller B sees only their own product", list(idsB) == [pB1], idsB)
    a1 = idsA[pA1]
    tokenB = seller_csrf(sellerB)

    s, h, _ = sellerB.get("/seller/products/edit?id=%d" % a1)
    check("B cannot open A's edit form (redirected away)", s == 302 and location(h).endswith("/seller/products"), "%s %s" % (s, location(h)))
    s, _, page = sellerB.get("/seller/products")
    check("...with a 'not found' message", "could not be found" in page)

    sellerB.post("/seller/products/update", {"csrfToken": tokenB, "productId": a1, "name": "HACKED", "price": "1",
                                              "category": "x", "imageUrl": "", "stock": "1", "description": ""})
    sellerB.post("/seller/products/stock", {"csrfToken": tokenB, "productId": a1, "stock": "999"})
    sellerB.post("/seller/products/delete", {"csrfToken": tokenB, "productId": a1})
    page = list_page(sellerA)
    check("A's product survives B's update/stock/delete attempts unchanged",
          pA1 in page and "HACKED" not in page and "250.50" in page and "999" not in page)
    s, _, page = sellerB.get("/seller/products")
    check("B never gained A's product", pA1 not in page)

    seed_id = 1
    sellerB.post("/seller/products/stock", {"csrfToken": tokenB, "productId": seed_id, "stock": "0"})
    sellerB.post("/seller/products/delete", {"csrfToken": tokenB, "productId": seed_id})
    s, _, page = buyer.get("/product-details?id=%d" % seed_id)
    check("unowned (demo) product is untouchable by any seller", s == 200 and "We couldn't find" not in page)

    # ------------------------------------------------------------------
    section("5. Editing, stock updates, deletion (own products)")
    token = seller_csrf(sellerA)
    s, _, page = sellerA.get("/seller/products/edit?id=%d" % a1)
    check("A can open own edit form, pre-filled", s == 200 and 'value="250.5' in page and pA1 in page)
    s, h, _ = sellerA.post("/seller/products/update", {
        "csrfToken": token, "productId": a1, "name": pA1, "description": "updated", "price": "300",
        "category": "SmokeTest", "imageUrl": "", "stock": "10"})
    check("edit saved", s == 302)
    check("...new price visible", "300.00" in list_page(sellerA) or "300" in list_page(sellerA))
    s, _, _ = sellerA.post("/seller/products/update", {
        "csrfToken": token, "productId": a1, "name": pA1, "description": "updated", "price": "300",
        "category": "SmokeTest", "imageUrl": "", "stock": "10"})
    page = list_page(sellerA)
    check("saving with NO changes still succeeds (not mistaken for 'not yours')", s == 302 and "Product updated." in page, s)
    s, _, page = sellerA.post("/seller/products/update", {
        "csrfToken": token, "productId": a1, "name": "", "description": "", "price": "x",
        "category": "", "imageUrl": "", "stock": "-1"})
    check("invalid edit re-shows the edit form with errors", s == 200 and "Please fix the following" in page and "Edit Product" in page)

    sellerA.post("/seller/products/stock", {"csrfToken": token, "productId": a1, "stock": "12"})
    check("stock update works", 'value="12"' in list_page(sellerA))
    sellerA.post("/seller/products/stock", {"csrfToken": token, "productId": a1, "stock": "-3"})
    page = list_page(sellerA)
    check("negative stock rejected", 'value="12"' in page)
    sellerA.post("/seller/products/stock", {"csrfToken": token, "productId": a1, "stock": "10"})

    add_product(sellerA, "SmokeDelete-" + RUN, price="1", stock="1")
    del_id = product_ids(sellerA)["SmokeDelete-" + RUN]
    sellerA.post("/seller/products/delete", {"csrfToken": token, "productId": del_id})
    check("never-ordered product can be deleted",
          "was deleted" in list_page(sellerA) and "SmokeDelete-" + RUN not in product_ids(sellerA))

    # ------------------------------------------------------------------
    section("6. Buyer places ONE order containing two sellers' products")
    b1 = idsB[pB1]
    s, _, _ = buyer.post("/CartServlet", {"action": "add", "productId": a1, "quantity": 2})
    check("cart POST without a CSRF token -> 403", s == 403, s)
    s, _, _ = buyer.post("/CheckoutServlet", {})
    check("checkout POST without a CSRF token -> 403", s == 403, s)
    buyer_token = buyer_csrf(buyer)
    for pid in (a1, b1):
        s, _, _ = buyer.post("/CartServlet", {"csrfToken": buyer_token, "action": "add", "productId": pid, "quantity": 2})
    s, h, _ = buyer.post("/CheckoutServlet", {"csrfToken": buyer_token})
    m = re.search(r"orderId=(\d+)", location(h))
    check("checkout redirects to the new order", s == 302 and m is not None, location(h))
    order_id = int(m.group(1))
    oid = str(order_id)

    s, _, page = buyer.get("/public/order-details.jsp?orderId=" + oid)
    check("buyer sees the new order as PENDING", 'class="order-status">PENDING<' in page, "")
    check("...and each line as Pending", page.count('class="order-status">Pending<') == 2)
    page = list_page(sellerA)
    check("checkout reduced A's stock (10 -> 8)", 'value="8"' in page)

    # ------------------------------------------------------------------
    section("7. Seller incoming orders (privacy between sellers)")
    _, _, page = sellerA.get("/seller/orders")
    check("A sees the order", ">#" + oid + "<" in page)
    check("A sees the customer name", "Buyer " + RUN in page)
    check("A sees their own product in it", pA1 in page)
    check("A does NOT see B's product", pB1 not in page)
    _, _, page = sellerA.get("/seller/orders/details?orderId=" + oid)
    check("A's order page shows customer email, payment method",
          emails["buyer"] in page and "Demo Payment" in page)
    check("A's order page shows only A's line and A's total (600.00)",
          pA1 in page and pB1 not in page and "600.00" in page)
    _, _, page = sellerB.get("/seller/orders/details?orderId=" + oid)
    check("B's order page shows only B's line and B's total (80.00)",
          pB1 in page and pA1 not in page and "80.00" in page)
    s, _, page = sellerC.get("/seller/orders/details?orderId=" + oid)
    check("seller C (no products in it) gets 404 / not-found", s == 404 and "couldn't find" in page, s)
    s, _, _ = sellerC.get("/seller/orders/details?orderId=999999")
    check("non-existent order id is indistinguishable (404)", s == 404, s)
    s, _, page = sellerC.get("/seller/orders")
    check("seller C's order list is empty", ">#" + oid + "<" not in page)
    s, _, page = sellerA.get("/seller/orders?status=PENDING")
    check("status filter shows the pending order", ">#" + oid + "<" in page)
    s, _, page = sellerA.get("/seller/orders?status=SHIPPED")
    check("status filter hides it under Shipped", ">#" + oid + "<" not in page)
    s, _, page = sellerA.get("/seller/orders?status=%27%20OR%201%3D1--")
    check("garbage status filter is ignored safely", s == 200 and ">#" + oid + "<" in page)
    vals = stat_values(sellerA)
    check("A's dashboard now counts 1 pending order", vals[4:8] == [1, 0, 0, 0], vals)

    # ------------------------------------------------------------------
    section("8. Order status management")

    def set_status(client, status, order=oid):
        token = seller_csrf(client)
        s, h, _ = client.post("/seller/order-status", {"csrfToken": token, "orderId": order, "status": status})
        page = client.get("/seller/orders/details?orderId=" + order)[2]
        return s, page

    for bad in ("DELIVERED", "SHIPPED", "PENDING", "PLACED", "CANCELLED", "", "confirmed; DROP TABLE orders"):
        s, page = set_status(sellerA, bad)
        check("A: invalid/skipping status %r refused" % bad[:20], 'badge badge-pending">Pending' in page and "Order #%s marked" % oid not in page)
    s, _, _ = sellerA.post("/seller/order-status", {"orderId": oid, "status": "CONFIRMED"})
    check("status change without CSRF token -> 403", s == 403, s)
    s, page = set_status(sellerC, "CONFIRMED")
    check("C (not in this order) cannot change it", "Order #%s marked" % oid not in page)

    s, page = set_status(sellerA, "CONFIRMED")
    check("A: Pending -> Confirmed", "marked as Confirmed" in page and 'badge badge-confirmed">Confirmed' in page)
    s, page = set_status(sellerA, "CONFIRMED")
    check("A: repeating the same change is refused", "can only be moved to Shipped" in page)
    s, page = set_status(sellerA, "DELIVERED")
    check("A: cannot skip Confirmed -> Delivered", "can only be moved to Shipped" in page and 'badge-delivered">Delivered' not in page)

    _, _, page = buyer.get("/public/order-details.jsp?orderId=" + oid)
    check("buyer: order stays PENDING while seller B has not confirmed", 'class="order-status">PENDING<' in page)
    check("buyer: A's line already shows Confirmed, B's still Pending",
          'class="order-status">Confirmed<' in page and 'class="order-status">Pending<' in page)
    _, _, page = sellerB.get("/seller/orders/details?orderId=" + oid)
    check("B's own status was NOT changed by A", 'badge badge-pending">Pending' in page)

    s, page = set_status(sellerB, "CONFIRMED")
    check("B: Pending -> Confirmed", "marked as Confirmed" in page)
    _, _, page = buyer.get("/public/order-details.jsp?orderId=" + oid)
    check("buyer: order is now CONFIRMED (every seller confirmed)", 'class="order-status">CONFIRMED<' in page)
    _, _, page = buyer.get("/public/orders.jsp")
    check("buyer order history shows the new status", "CONFIRMED" in page)

    set_status(sellerA, "SHIPPED")
    _, _, page = buyer.get("/public/order-details.jsp?orderId=" + oid)
    check("buyer: order stays CONFIRMED while B has not shipped", 'class="order-status">CONFIRMED<' in page)
    set_status(sellerB, "SHIPPED")
    _, _, page = buyer.get("/public/order-details.jsp?orderId=" + oid)
    check("buyer: order is SHIPPED once both shipped", 'class="order-status">SHIPPED<' in page)

    set_status(sellerA, "DELIVERED")
    set_status(sellerB, "DELIVERED")
    _, _, page = buyer.get("/public/order-details.jsp?orderId=" + oid)
    check("buyer: order is DELIVERED", 'class="order-status">DELIVERED<' in page)
    s, page = set_status(sellerA, "PENDING")
    check("A: nothing can change a delivered order", "already been delivered" in page)
    _, _, page = sellerA.get("/seller/orders/details?orderId=" + oid)
    check("delivered order page offers no status button", "Mark as" not in page)
    vals = stat_values(sellerA)
    check("A's dashboard now counts 1 delivered, 0 pending", vals[4:8] == [0, 0, 0, 1], vals)

    # ------------------------------------------------------------------
    section("9. Deleting a product that has been ordered")
    token = seller_csrf(sellerA)
    sellerA.post("/seller/products/delete", {"csrfToken": token, "productId": a1})
    page = list_page(sellerA)
    check("ordered product cannot be deleted; seller is told why",
          pA1 in page and "already ordered it" in page)
    sellerA.post("/seller/products/stock", {"csrfToken": token, "productId": a1, "stock": "0"})
    check("...but its stock can be set to 0 instead", "Out of stock" in list_page(sellerA))

    # ------------------------------------------------------------------
    section("10. Buyer flows unaffected")
    s, _, page = buyer.get("/products")
    check("buyer product list still works and shows seller products", s == 200 and pB1 in page)
    s, _, page = buyer.get("/public/dashboard.jsp")
    check("buyer dashboard still works", s == 200 and "Recent Orders" in page)
    check("buyer navbar has no seller link", "Seller Dashboard" not in page)
    s, _, page = sellerA.get("/public/dashboard.jsp")
    check("a seller's buyer-dashboard shows a link back to the seller dashboard", "Seller Dashboard" in page)
    s, h, _ = buyer.post("/CheckoutServlet", {"csrfToken": buyer_token})
    check("checkout with an empty cart still shows the friendly error", s == 200 and "cart is empty" in _)
    s, h, _ = sellerA.get("/LogoutServlet")
    s2, h2, _ = sellerA.get("/seller/dashboard")
    check("logout ends the seller session", s == 302 and s2 == 302 and "login.jsp" in location(h2))

    print("\n%d passed, %d failed" % (passed, failed))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
