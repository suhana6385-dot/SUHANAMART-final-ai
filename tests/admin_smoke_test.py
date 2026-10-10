#!/usr/bin/env python3
"""
SUHANAMART - Admin module end-to-end smoke test.

Drives a RUNNING deployment over plain HTTP (Python 3.8+ standard library
only): logs in as the seeded admin account, and as fresh buyer/seller
accounts, and checks dashboard stats, user management (enable/disable,
self-protection, admin-protection), product moderation (view + remove,
with/without order history), order monitoring, and that buyers/sellers can
never reach the admin area.

Usage:
    python3 tests/admin_smoke_test.py                       # http://localhost:8080/suhanamart
    python3 tests/admin_smoke_test.py http://host:8080/app

Prerequisites: the app is deployed, the database has the V4 schema (fresh
suhanamart.sql, or V2+V3+V4 migrations applied), and the seeded admin
account (admin@suhanamart.com) has NOT had its password changed from the
default documented in V4__admin_and_account_status.sql. Creates throw-away
users/products on every run - run against a development database only.

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
ADMIN_EMAIL = "admin@suhanamart.com"
ADMIN_PASSWORD = "ChangeMe_Admin#2026"

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
    return client.get(urllib.parse.urljoin(BASE + "/", location(headers)))


def register(client, name, email, seller):
    data = {"name": name, "email": email, "password": PASSWORD, "confirmPassword": PASSWORD}
    if seller:
        data["registerAsSeller"] = "true"
    return client.post("/RegisterServlet", data)


def login(client, email, password=PASSWORD):
    return client.post("/LoginServlet", {"email": email, "password": password})


def add_product(seller, name, price="100", stock="20"):
    _, _, page = seller.get("/seller/products/new")
    token = csrf_from(page)
    seller.post("/seller/products/create", {"csrfToken": token, "name": name, "description": "d",
                                            "price": price, "category": "AdminTest", "imageUrl": "", "stock": stock})
    _, _, page = seller.get("/seller/products")
    for n, pid in re.findall(r'class="cell-title">(.*?)</div>\s*<div class="cell-sub">ID #(\d+)</div>', page, re.S):
        if html.unescape(n) == name:
            return int(pid)
    raise SystemExit("could not create product " + name)


def buy(buyer, product_ids):
    token = csrf_from(buyer.get("/products")[2])
    for pid in product_ids:
        buyer.post("/CartServlet", {"csrfToken": token, "action": "add", "productId": pid, "quantity": 1})
    s, h, _ = buyer.post("/CheckoutServlet", {"csrfToken": token})
    m = re.search(r"orderId=(\d+)", location(h))
    if s != 302 or not m:
        raise SystemExit("checkout failed: %s %s" % (s, location(h)))
    return int(m.group(1))


def main():
    print("SUHANAMART admin smoke test against " + BASE + "  (run id " + RUN + ")")

    admin = Client()
    s, h, _ = login(admin, ADMIN_EMAIL, ADMIN_PASSWORD)
    section("1. Admin login and landing page")
    check("admin login succeeds and lands on /admin/dashboard",
          s == 302 and location(h).endswith("/admin/dashboard"), "%s %s" % (s, location(h)))
    s, h, page = admin.get("/admin/dashboard")
    check("dashboard renders with stat cards", s == 200 and "Total Users" in page and "stat-value" in page)
    before_users = int(re.search(r'<span class="stat-value">(\d+)</span>\s*<span class="stat-label">Total Users', page).group(1))
    before_products = int(re.search(r'<span class="stat-value">(\d+)</span>\s*<span class="stat-label">Products', page).group(1))

    section("2. Buyers and sellers can never reach the admin area")
    buyer1, seller1, anon = Client(), Client(), Client()
    emailB = "admin-test-b-%s@example.com" % RUN
    emailS = "admin-test-s-%s@example.com" % RUN
    register(buyer1, "Admin Test Buyer", emailB, False)
    register(seller1, "Admin Test Seller", emailS, True)
    login(buyer1, emailB)
    login(seller1, emailS)
    for path in ("/admin/dashboard", "/admin/users", "/admin/products", "/admin/orders", "/public/admin/dashboard.jsp"):
        s, h, _ = buyer1.get(path)
        check("buyer GET %s -> 403" % path, s == 403, s)
        s, h, _ = seller1.get(path)
        check("seller (not admin) GET %s -> 403" % path, s == 403, s)
        s, h, _ = anon.get(path)
        check("anonymous GET %s -> redirected to login" % path, s == 302 and "login.jsp" in location(h), "%s %s" % (s, location(h)))
    s, h, _ = buyer1.post("/admin/users/status", {"userId": 1, "action": "disable"})
    check("buyer POST /admin/users/status -> 403 (not CSRF, role)", s == 403, s)
    s, h, _ = buyer1.post("/admin/products/remove", {"productId": 1})
    check("buyer POST /admin/products/remove -> 403", s == 403, s)

    section("3. Opening an admin JSP directly is handed to the controller")
    s, h, _ = admin.get("/public/admin/dashboard.jsp")
    check("admin JSP redirected to its controller URL", s == 302 and location(h).endswith("/admin/dashboard"), "%s %s" % (s, location(h)))
    s, h, _ = admin.get("/public/admin/users.jsp")
    check("same for users.jsp", s == 302 and location(h).endswith("/admin/users"))

    section("4. User management")
    s, _, page = admin.get("/admin/users")
    check("user list shows the new buyer and seller", emailB in page and emailS in page)
    check("role badges shown", "badge-role-buyer" in page and "badge-role-seller" in page and "badge-role-admin" in page)
    s, _, page = admin.get("/admin/users?role=SELLER")
    check("role filter narrows the list", emailS in page and emailB not in page)
    token = csrf_from(admin.get("/admin/users")[2])

    s, h, _ = admin.post("/admin/users/status", {"userId": 99999999, "action": "disable"})
    check("no CSRF token -> 403", s == 403, s)
    s, h, page = admin.post("/admin/users/status", {"csrfToken": "forged", "userId": 99999999, "action": "disable"})
    check("wrong CSRF token -> 403", s == 403, s)

    # Find the specific row for our test buyer by scanning forms near its email.
    _, _, page = admin.get("/admin/users?role=BUYER")
    idx = page.find(emailB)
    row = page[idx: idx + 1500]
    m = re.search(r'name="userId" value="(\d+)"', row)
    buyer1_id = int(m.group(1))

    s, h, _ = admin.post("/admin/users/status", {"csrfToken": token, "userId": buyer1_id, "action": "disable"})
    check("admin disables the buyer account", s == 302, s)
    _, _, page = goto(admin, h)
    check("success message shown", "Account disabled" in page)
    s3, _, page3 = Client().post("/LoginServlet", {"email": emailB, "password": PASSWORD})
    check("disabled buyer login shows the same generic message as a wrong password",
          "Invalid email or password" in page3)

    s, h, _ = admin.post("/admin/users/status", {"csrfToken": token, "userId": buyer1_id, "action": "enable"})
    check("admin re-enables the account", s == 302, s)
    s4, h4, _ = Client().post("/LoginServlet", {"email": emailB, "password": PASSWORD})
    check("re-enabled buyer can log in again", s4 == 302 and "dashboard.jsp" in location(h4), "%s %s" % (s4, location(h4)))

    _, _, page = admin.get("/admin/users?role=ADMIN")
    idx = page.find(ADMIN_EMAIL)
    check("the admin's own row has no enable/disable form (shows an em dash)", "&mdash;" in page[idx: idx + 900])
    # The seeded admin's row has no form (just asserted above), so there is no userId input to
    # read from the page. The seeded admin is always the first row inserted by suhanamart.sql,
    # so its id is 1 - used below to confirm an admin can't disable their own account even via
    # a direct POST (AdminService's self-check keys off the session id, which is this admin's).
    admin_row_id = 1

    s, h, page = admin.post("/admin/users/status", {"csrfToken": token, "userId": admin_row_id, "action": "disable"})
    check("admin cannot disable their own admin account even via a direct POST",
          s == 302, s)
    _, _, page2 = goto(admin, h)
    check("...with a clear refusal message", "own account" in page2)

    section("5. Product moderation")
    seller2 = Client()
    emailS2 = "admin-test-s2-%s@example.com" % RUN
    register(seller2, "Admin Test Seller Two", emailS2, True)
    login(seller2, emailS2)
    p_unbought = add_product(seller2, "Admin Removable Product " + RUN)
    p_bought = add_product(seller2, "Admin Protected Product " + RUN)
    buyer2 = Client()
    emailB2 = "admin-test-b2-%s@example.com" % RUN
    register(buyer2, "Admin Test Buyer Two", emailB2, False)
    login(buyer2, emailB2)
    buy(buyer2, [p_bought])

    s, _, page = admin.get("/admin/products")
    check("product list shows both new products with seller name", "Admin Removable Product" in page and "Admin Protected Product" in page and "Admin Test Seller Two" in page)
    after_products = before_products + 2
    check("dashboard product count increased by 2", int(re.search(r'<span class="stat-value">(\d+)</span>\s*<span class="stat-label">Products', admin.get("/admin/dashboard")[2]).group(1)) == after_products)

    s, h, _ = admin.post("/admin/products/remove", {"productId": p_unbought})
    check("no CSRF -> 403", s == 403, s)
    ptoken = csrf_from(admin.get("/admin/products")[2])
    s, h, _ = admin.post("/admin/products/remove", {"csrfToken": ptoken, "productId": p_unbought})
    check("admin removes a never-ordered product", s == 302, s)
    _, _, page = goto(admin, h)
    check("success message", "removed" in page.lower())
    _, _, page = admin.get("/admin/products")
    check("it's gone from the list", "Admin Removable Product" not in page)

    s, h, _ = admin.post("/admin/products/remove", {"csrfToken": ptoken, "productId": p_bought})
    check("admin CANNOT remove a product with order history", s == 302, s)
    _, _, page = goto(admin, h)
    check("...with a clear refusal message", "order history" in page)
    _, _, page = admin.get("/admin/products")
    check("it's still listed", "Admin Protected Product" in page)

    s, h, _ = admin.post("/admin/products/remove", {"csrfToken": ptoken, "productId": 999999999})
    check("removing a non-existent product is refused safely", s == 302, s)
    _, _, page = goto(admin, h)
    check("...with a not-found message, no stack trace", "find that product" in page and "Exception" not in page)

    section("6. Order monitoring (read-only)")
    s, _, page = admin.get("/admin/orders?status=PENDING")
    check("status filter works", s == 200)
    # Find the order placed in section 5 by locating buyer2's email in the admin order list.
    _, _, full_list = admin.get("/admin/orders")
    idx = full_list.find(emailB2)
    check("buyer2's order appears with their email in the admin order list", idx != -1)
    order_row = full_list[max(0, idx - 300): idx]
    m = re.search(r'#(\d+)', order_row)
    check("...with a visible order number", m is not None)
    if m:
        oid = int(m.group(1))
        s, _, detail = admin.get("/admin/orders?orderId=%d" % oid)
        check("admin can open order detail with buyer info and line items",
              "Admin Protected Product" in detail and emailB2 in detail)
    s, _, page2 = admin.get("/admin/orders?orderId=999999999")
    check("non-existent order id handled gracefully, no stack trace",
          s == 200 and "couldn't find that order" in page2 and "Exception" not in page2)

    section("7. Login/registration still behave correctly")
    s, _, page = Client().post("/RegisterServlet", {"name": "x", "email": "cant-be-admin-%s@example.com" % RUN,
                                                     "password": "abcdef", "confirmPassword": "abcdef",
                                                     "role": "ADMIN"})
    check("registration form cannot create an admin (role param is ignored)", "Account created successfully" in page)
    c = Client()
    login(c, "cant-be-admin-%s@example.com" % RUN, "abcdef")
    s, _, page = c.get("/admin/dashboard")
    check("that new account is a buyer, not an admin - 403 on the admin area", s == 403, s)

    print("\n%d passed, %d failed" % (passed, failed))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
