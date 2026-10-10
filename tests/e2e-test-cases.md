# SUHANAMART - End-to-End Test Cases

End-to-end means: a real HTTP request goes through the filters, servlet, service, DAO and a real database, and the resulting page is checked. Three scripts do this (Python 3.8+ standard library only) against a **running deployment and a development database** - they register throw-away accounts and leave their rows behind.

```bash
python3 tests/seller_smoke_test.py http://localhost:8080/suhanamart   # Seller module + buyer flows it touches
python3 tests/review_smoke_test.py http://localhost:8080/suhanamart   # Review module
```

Both exit with code 0 only if every check passes. (The manual, browser-only cases are in `manual-test-cases.md`; the schema-level checks are in `database-test.sql`.)

## E2E-1  Registration, login, roles (seller_smoke_test.py, section 1-2)

Seller and buyer registration, duplicate email, generic login error, role-based landing page, buyer -> 403 on `/seller/*`, anonymous -> login on every protected URL.

## E2E-2  Seller product management (section 3-5)

CSRF required on every POST, field validation (price/stock/URL), create/edit/stock/delete, one seller can never see or change another seller's product, XSS in product names is escaped.

## E2E-3  Order flow (section 6-9, review_smoke_test.py section 1)

Cart -> checkout (needs CSRF token) -> order with two sellers' products -> each seller sees only their own lines -> status can only move Pending -> Confirmed -> Shipped -> Delivered, one step at a time -> the buyer's order status is the least-advanced line.

## E2E-4  Review module (review_smoke_test.py)

| Area | Cases |
|---|---|
| Access | anonymous GET/POST `/reviews` and `/public/reviews.jsp` -> login; opening `reviews.jsp` directly is handed to the controller; missing/garbage/negative/huge ids -> My Orders with a message, never an error page |
| Eligibility | pending and shipped orders show no Review action and refuse the form; delivered order shows it; with two sellers the order must be delivered by **both** before either item can be reviewed |
| CSRF | POST with no token, a wrong token, or another session's token -> 403 |
| Validation | ratings `""`, `0`, `6`, `-1`, `abc`, `3.5`, `05`, `1 OR 1=1`, `5;DROP TABLE reviews` rejected; comment empty / spaces / too short / 1001 chars / control character rejected; the typed comment is kept; **nothing is stored** by any invalid request |
| Success | redirect to the product page with a one-time thank-you; review listed; average and count correct; HTML escaped (`<script>` never appears raw); Tamil text survives |
| Duplicates | second review of the same buyer + order + product refused with a clear message; order page switches from the button to "Reviewed"; the same product from a *different* order is allowed; 12 simultaneous POSTs store exactly one |
| Authorization | another buyer cannot open the form or POST for someone else's order; a product not in the order, a non-existent order and a malformed id are refused; sellers cannot review their own product |
| Rating summary | average `(4+5+1)/3 = 3.3`, 3 reviews, star bars, newest first, single-review product `2.0`, empty state |
| Errors | unknown URL, `product-details?id=abc`, `id=999999` and a 403 show the friendly error page: no "Apache Tomcat", no stack trace, no exception class names |

## E2E-5  Buyer regression (cart and checkout hardening)

Not a separate script in this repository: covered by `seller_smoke_test.py` section 6/10 (checkout with and without a CSRF token, empty-cart checkout) and by the manual cases in `manual-test-cases.md` (Cart, Checkout, Orders/Profile). Cart quantity rules (`CartService`): adding twice merges; quantity is limited to stock; `0`, negative, non-numeric, non-existent product and out-of-stock adds change nothing and never show an error page.

## E2E-6  Admin module (admin_smoke_test.py)

| Area | Cases |
|---|---|
| Access control | buyer/seller/anonymous all refused on every `/admin/*` URL and the `/public/admin/*.jsp` files directly (403 or redirect-to-login as appropriate); opening an admin JSP directly is handed to its controller |
| User management | role filter; enable/disable with and without CSRF; a disabled account's login shows the exact same generic message as a wrong password; re-enabling restores login; an admin can never disable their own account, or any admin account (enforced in both `AdminService` and the SQL `UPDATE` itself) |
| Product moderation | listing shows every seller's products with the seller's name; dashboard product count matches; removing a never-ordered product succeeds; removing a product WITH order history is refused with a clear message and the listing stays; removing a non-existent id is refused safely, no stack trace |
| Order monitoring | status filter; opening one order's detail shows the buyer's name/email and every line, from every seller; a non-existent order id is handled gracefully |
| Registration boundary | the public registration form can never create an `ADMIN` account, even if a `role=ADMIN` parameter is sent |

## E2E-7  AI Chatbot (chat_smoke_test.py)

| Area | Cases |
|---|---|
| Access | the chat widget markup is absent from the page entirely for an anonymous visitor; `POST /chat` without a session redirects to login |
| CSRF | missing or wrong token -> 403, JSON body |
| Validation | empty, whitespace-only, over 500 chars, and a control character all -> 400 with a safe message; exactly 500 chars is accepted; a missing `message` param entirely is handled, not a 500 |
| Context (fallback mode - no AI key configured) | a product-name question returns that product's price/stock/rating; a question matching no product still gets a safe, generic 200 reply, never an error |
| Order scoping | an order-intent question returns the CALLER's own recent orders only - a different buyer asking the same question never sees it; a product-only question does not pull in order data |
| Rate limiting | the first 10 requests in a minute all succeed; the 11th -> 429; the limit is strictly per-user (a different user is unaffected) |

Separately (not run by the script above, since it needs the server started with its AI endpoint pointed at a mock provider): `tools/mock_ai/server.py`-backed runs confirmed the real success/HTTP-500/timeout/malformed-response paths through `ChatService.tryAiProvider`, and `ChatService.extractText`/`jsonString` were unit-tested in isolation (reflection-based, no HTTP) for normal input, escaped characters, and malformed/unexpected shapes.

## What these scripts do not cover

A real browser (star/chat hover and keyboard behaviour, layout at phone widths, the JavaScript helpers), Maven packaging, a standalone Tomcat/MySQL installation, and a real AI provider call with a real API key. See the honesty note in the root `README.md`.
