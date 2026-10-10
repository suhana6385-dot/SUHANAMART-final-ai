#!/usr/bin/env python3
"""
SUHANAMART - AI Chatbot (ChatService / ChatServlet) end-to-end smoke test.

Drives a RUNNING deployment over plain HTTP. Covers: login requirement,
CSRF, input validation (empty/too long/control chars), rate limiting
(10/minute), product/review context (fallback mode - no AI key
configured), order context scoped to the caller's own orders, and that
the widget is never rendered for an anonymous visitor.

The live-AI-provider success/timeout/error paths are covered separately
(see mock_ai/server.py and the README note) because they require the
server to be started with SUHANAMART_AI_API_URL pointed at a mock
provider - not assumed here, since a default run has no AI key
configured at all (fallback-only mode, which THIS script fully covers).

Usage:
    python3 tests/chat_smoke_test.py                       # http://localhost:8080/suhanamart
    python3 tests/chat_smoke_test.py http://host:8080/app

Creates throw-away users/products on every run - development database only.
Exit code 0 = every check passed.
"""

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
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        cookies = urllib.request.HTTPCookieProcessor(self.jar)
        self.plain = urllib.request.build_opener(cookies, NoRedirect)

    def request(self, method, path, data=None):
        url = BASE + path
        body = urllib.parse.urlencode(data, encoding="utf-8").encode("ascii") if data is not None else None
        req = urllib.request.Request(url, data=body, method=method)
        if body is not None:
            req.add_header("Content-Type", "application/x-www-form-urlencoded")
        try:
            resp = self.plain.open(req, timeout=15)
        except urllib.error.HTTPError as e:
            resp = e
        text = resp.read().decode("utf-8", errors="replace")
        return (resp.status if hasattr(resp, "status") else resp.code), resp.headers, text

    def get(self, path):
        return self.request("GET", path)

    def post(self, path, data):
        return self.request("POST", path, data=data)


def csrf_from(page):
    m = re.search(r'name="csrfToken" value="([^"]+)"', page)
    return m.group(1) if m else None


def chat_csrf(page):
    m = re.search(r'data-csrf-token="([^"]+)"', page)
    return m.group(1) if m else None


def register(client, name, email, seller=False):
    data = {"name": name, "email": email, "password": PASSWORD, "confirmPassword": PASSWORD}
    if seller:
        data["registerAsSeller"] = "true"
    return client.post("/RegisterServlet", data)


def login(client, email):
    return client.post("/LoginServlet", {"email": email, "password": PASSWORD})


def add_product(seller, name, price="100", stock="20"):
    import html as htmllib
    _, _, page = seller.get("/seller/products/new")
    token = csrf_from(page)
    seller.post("/seller/products/create", {"csrfToken": token, "name": name, "description": "d",
                                            "price": price, "category": "ChatTest", "imageUrl": "", "stock": stock})
    _, _, page = seller.get("/seller/products")
    for n, pid in re.findall(r'class="cell-title">(.*?)</div>\s*<div class="cell-sub">ID #(\d+)</div>', page, re.S):
        if htmllib.unescape(n) == name:
            return int(pid)
    raise SystemExit("could not create product " + name)


def buy(buyer, product_ids):
    token = csrf_from(buyer.get("/products")[2])
    for pid in product_ids:
        buyer.post("/CartServlet", {"csrfToken": token, "action": "add", "productId": pid, "quantity": 1})
    s, h, _ = buyer.post("/CheckoutServlet", {"csrfToken": token})
    m = re.search(r"orderId=(\d+)", h.get("Location", ""))
    if s != 302 or not m:
        raise SystemExit("checkout failed")
    return int(m.group(1))


def main():
    print("SUHANAMART chat smoke test against " + BASE + "  (run id " + RUN + ")")

    anon = Client()
    seller, buyer1, buyer2 = Client(), Client(), Client()
    eS, eB1, eB2 = ("chat-s-%s@example.com" % RUN, "chat-b1-%s@example.com" % RUN, "chat-b2-%s@example.com" % RUN)
    register(seller, "Chat Seller", eS, True)
    register(buyer1, "Chat Buyer One", eB1, False)
    register(buyer2, "Chat Buyer Two", eB2, False)
    login(seller, eS)
    login(buyer1, eB1)
    login(buyer2, eB2)

    pid = add_product(seller, "Chatbot Test Widget " + RUN, price="249", stock="7")

    section("1. Widget presence and anonymous access")
    s, _, page = anon.get("/products")
    check("anonymous visitor sees no chat widget", "chatWidget" not in page)
    s, _, page = buyer1.get("/products")
    check("logged-in buyer sees the chat widget with a CSRF token", "chatWidget" in page and chat_csrf(page))
    s, h, _ = anon.post("/chat", {"message": "hello"})
    check("anonymous POST /chat -> redirected to login (AuthFilter)", s == 302 and "login.jsp" in h.get("Location", ""), s)

    section("2. CSRF")
    s, h, body = buyer1.post("/chat", {"message": "hello"})
    check("no CSRF token -> 403, JSON body", s == 403 and '"error":true' in body, (s, body))
    token = chat_csrf(buyer1.get("/products")[2])
    s, h, body = buyer1.post("/chat", {"message": "hello", "csrfToken": "forged"})
    check("wrong CSRF token -> 403", s == 403, s)

    section("3. Input validation")
    s, h, body = buyer1.post("/chat", {"message": "", "csrfToken": token})
    check("empty message -> 400 with a message, JSON well-formed", s == 400 and '"error":true' in body, body)
    s, h, body = buyer1.post("/chat", {"message": "   ", "csrfToken": token})
    check("whitespace-only message -> 400", s == 400, body)
    s, h, body = buyer1.post("/chat", {"message": "x" * 501, "csrfToken": token})
    check("message over 500 chars -> 400", s == 400 and "too long" in body, body)
    s, h, body = buyer1.post("/chat", {"message": "x" * 500, "csrfToken": token})
    check("message at exactly 500 chars is accepted", s == 200, (s, body))
    s, h, body = buyer1.post("/chat", {"message": "hello\x00world", "csrfToken": token})
    check("control character in message -> 400", s == 400 and "aren't allowed" in body, body)
    s, h, body = buyer1.post("/chat", {"csrfToken": token})
    check("missing message param entirely -> 400, not a 500/stack trace", s == 400 and "Exception" not in body, body)

    section("4. Product + review context (fallback mode - no AI key configured by default)")
    s, h, body = buyer1.post("/chat", {"message": "Tell me about Chatbot Test Widget " + RUN, "csrfToken": token})
    check("product keyword match -> 200, mentions the product, price and stock", s == 200 and "249" in body and ("stock" in body or "in stock" in body), body)
    check("JSON response is well-formed with error:false", '"error":false' in body, body)
    s, h, body = buyer1.post("/chat", {"message": "Do you have any Zzzznonexistentqqq products?", "csrfToken": token})
    check("no product match -> still a safe, generic 200 response (never an error)", s == 200 and '"error":false' in body, body)

    section("5. Order context - scoped to the caller only")
    oid = buy(buyer1, [pid])
    token1 = chat_csrf(buyer1.get("/products")[2])
    s, h, body = buyer1.post("/chat", {"message": "What's the status of my orders?", "csrfToken": token1})
    check("order-intent question returns the buyer's own order", s == 200 and ("#%d" % oid) in body, body)
    token2 = chat_csrf(buyer2.get("/products")[2])
    s, h, body = buyer2.post("/chat", {"message": "What's the status of my orders?", "csrfToken": token2})
    check("a DIFFERENT buyer asking the same question never sees buyer1's order id",
          s == 200 and ("#%d" % oid) not in body, body)
    s, h, body = buyer1.post("/chat", {"message": "Tell me about the Chatbot Test Widget", "csrfToken": token1})
    check("a product-only question (no order-intent words) does not pull in order data unnecessarily",
          s == 200 and ("#%d" % oid) not in body, body)

    section("6. Rate limiting (10 requests/minute per user)")
    buyer3 = Client()
    eB3 = "chat-b3-%s@example.com" % RUN
    register(buyer3, "Chat Buyer Three", eB3, False)
    login(buyer3, eB3)
    token3 = chat_csrf(buyer3.get("/products")[2])
    statuses = []
    for i in range(10):
        s, h, _ = buyer3.post("/chat", {"message": "ping " + str(i), "csrfToken": token3})
        statuses.append(s)
    check("first 10 requests in a minute all succeed", all(s == 200 for s in statuses), statuses)
    s, h, body = buyer3.post("/chat", {"message": "one too many", "csrfToken": token3})
    check("11th request within the same minute -> 429", s == 429 and '"error":true' in body, (s, body))
    buyer4 = Client()
    eB4 = "chat-b4-%s@example.com" % RUN
    register(buyer4, "Chat Buyer Four", eB4, False)
    login(buyer4, eB4)
    token4 = chat_csrf(buyer4.get("/products")[2])
    s, h, _ = buyer4.post("/chat", {"message": "hi", "csrfToken": token4})
    check("rate limit is per-user: a brand-new user is unaffected by buyer3's limit", s == 200, s)

    print("\n%d passed, %d failed" % (passed, failed))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
