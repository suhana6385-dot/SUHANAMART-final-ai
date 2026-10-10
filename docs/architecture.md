# SUHANAMART - Architecture

Diagrams: [`er-diagram.png`](er-diagram.png) (database) and [`use-case-diagram.png`](use-case-diagram.png) (what buyers and sellers can do).

## Layers

```
Browser
   |  HTTP
Filters      EncodingFilter (UTF-8 for every request) -> AuthFilter (login required)
   |                                                    RoleFilter  (seller-only areas)
Controller   com.suhanamart.controller   reads the request, calls a service, chooses the next page
   |
Service      com.suhanamart.service      validation + business rules (the ONLY place rules live)
   |
DAO          com.suhanamart.dao          SQL, always PreparedStatement
   |
Database     MySQL                        constraints (FK, UNIQUE, CHECK) as the last line of defence
```

- **Views** are JSPs in `frontend/public` (fragments in `frontend/WEB-INF/jspf`). They render data prepared for them and escape every dynamic value (`SecurityUtil.escapeHtml`). No SQL and no business decisions.
- **Models** (`com.suhanamart.model`) are small immutable data classes; the password hash never leaves `UserDAO` (`AuthRecord`).
- **Utilities** (`com.suhanamart.util`): `DBConnection` (credentials from environment / JVM properties / a local `db.properties`, never in source), `PasswordUtil` (jBCrypt), `ValidationUtil`, `SecurityUtil` (escaping, session helpers, CSRF token), `Flash` (one-time messages across a redirect).
- **Errors**: services throw `ValidationException` (message safe to show) or `NotFoundException`; controllers turn them into a message and a redirect/forward; `SQLException`s are logged with Log4j2 and replaced by a generic sentence; `public/error.jsp` (via `web.xml`) covers anything unhandled, without a stack trace.
- **Security in every state-changing request**: session-based user id (never a request parameter), per-session CSRF token, ownership enforced inside the SQL `WHERE`, fresh session id at login.

Existing simple buyer pages (`cart`, `checkout`, `dashboard`, `orders`, `order-details`, `profile`) load their data from a scriptlet that calls a DAO/service; everything newer goes controller -> service -> DAO.

## Modules

| Module | Controllers | Service | DAO | Pages |
|---|---|---|---|---|
| Auth | `LoginServlet`, `RegisterServlet`, `LogoutServlet` | `AuthService` | `UserDAO` | `login.jsp`, `register.jsp` |
| Catalog | `ProductServlet` (`/products`, `/product-details`) | - | `ProductDAO` | `products.jsp`, `product-details.jsp` |
| Cart & checkout | `CartServlet`, `CheckoutServlet` | `CartService` | `CartDAO`, `OrderDAO` | `cart.jsp`, `checkout.jsp` |
| Orders (buyer) | - | - | `OrderDAO` | `orders.jsp`, `order-details.jsp` |
| Seller | `SellerDashboardServlet`, `SellerProductServlet`, `SellerOrderServlet`, `OrderStatusServlet` (shared `SellerBaseServlet`) | `ProductService`, `OrderService` | `ProductDAO`, `OrderDAO` | `public/seller/*.jsp` |
| **Reviews** | `ReviewServlet` (`/reviews`) | `ReviewService` | `ReviewDAO` | `reviews.jsp`, review section of `product-details.jsp`, review action in `order-details.jsp` |
| **Admin** | `AdminServlet` (`/admin/dashboard`, `/admin/products[/remove]`), `AdminUserServlet` (`/admin/users[/status]`), `AdminOrderServlet` (`/admin/orders`) | `AdminService` | `AdminDAO` (+ the existing `OrderDAO`, `ProductDAO` for read-only monitoring - see below) | `public/admin/*.jsp` |
| **AI Chatbot** | `ChatServlet` (`/chat`) | `ChatService` | *(none of its own - reads through the existing `ProductDAO`, `ReviewService`, `OrderDAO`)* | the floating widget in `footer.jspf` + `main.js` |

`AdminService` and `ChatService` are deliberately **not** limited to one DAO: `AdminService` uses the existing `OrderDAO` (two new admin-scoped methods, `findAllOrders`/`findAnyOrder`/`countAllOrdersByStatus`, alongside the seller-scoped ones already there) and `ProductDAO.findById` for order/product lookups it would otherwise have to duplicate, and `ChatService` reads `ProductDAO`, `ReviewService` and `OrderDAO` directly rather than through a dedicated Chat DAO, since it only ever *reads* existing, already-scoped data (nothing chat-specific is written to the database). This follows the same "reuse a working DAO rather than duplicate its query logic" rule the rest of the project already follows (e.g. `OrderDAO` itself calls into `CartDAO`/`ProductDAO` during checkout).

## Order status

`PENDING -> CONFIRMED -> SHIPPED -> DELIVERED`, tracked per order line (`order_items.status`) so each seller only moves their own lines. `orders.status` is the least-advanced line, updated in the same transaction. Reviews depend on it (below).

## Review module (F8)

```
order-details.jsp  --"Write a review"-->  GET /reviews?orderId=N&productId=M
                                              |
                                          ReviewServlet -> ReviewService.getReviewableItem -> ReviewDAO.findPurchasedItem
                                              |                       (ownership is in the SQL: orders.user_id = session user)
                                          reviews.jsp (stars, comment, CSRF token)
                                              |  POST /reviews
                                          ReviewServlet -> CSRF check
                                              -> ReviewService.submitReview
                                                   1. ids are positive integers
                                                   2. rating is exactly 1..5
                                                   3. comment 5..1000 chars, no control characters
                                                   4. purchase is the buyer's, product is in the order,
                                                      order AND line are DELIVERED, not already reviewed,
                                                      not the seller's own product
                                                   5. ReviewDAO.insertIfDelivered  (INSERT ... SELECT that repeats the
                                                      ownership/delivered checks) + UNIQUE(user_id, order_id, product_id)
                                              -> redirect to /product-details?id=M#reviews
product-details    <-- ProductServlet -> ReviewService.getRatingSummary / getRecentReviews -> ReviewDAO
```

Design decisions:

- **"Delivered" means the order status is `DELIVERED` and so is the item's own status.** Because the order status is the least-advanced line, in a multi-seller order the buyer can review only after every seller has delivered. (To allow reviewing a line as soon as *its* seller delivers, drop the `o.status = ?` condition in `ReviewDAO.insertIfDelivered` and the order-status half of `ReviewService.evaluate`.)
- **One review per buyer + order + product**, so a repeat purchase of the same product can be reviewed again. The unique key makes this hold even for simultaneous requests.
- **Sellers can't review their own products** (a seller can also be a buyer).
- **Average rating** = sum of stars / number of reviews, computed from exact per-star counts with `BigDecimal`, rounded half-up to one decimal (`Review.Summary`).
- **Reviews are not editable/deletable yet**; `reviews.updated_at` is in place for when they are.

## Admin module

```
RoleFilter (ADMIN session required for /admin/* and /public/admin/*)
   |
AdminServlet / AdminUserServlet / AdminOrderServlet
   |
AdminService  - the only place admin rules live:
   |             - an admin can never disable their own account, or any ADMIN account
   |             - a product can only be removed if it has no order history
   v
AdminDAO (users/products) + OrderDAO (orders, read-only, reused)
```

Design decisions:

- **No new model classes.** `User` and `Product` each gained one additional constructor (role/accountStatus on `User`; sellerName on `Product`) instead of introducing `AdminUserView`/`AdminProductView` DTOs - every other caller of the original constructors is unaffected. Order monitoring reuses `SellerOrder` (built from ALL of an order's lines instead of one seller's) rather than a new type.
- **Self- and admin-protection is enforced twice.** Once in `AdminService` (the acting admin's own id), and again inside `AdminDAO.setAccountStatus`'s own `UPDATE ... WHERE role <> 'ADMIN'` - so even a bug in the service layer could not disable every admin account and lock the system out of its own admin tooling.
- **Account status, not a session kill-switch.** Disabling an account is checked once, at login (`AuthService.authenticate`); an already-open session for an account disabled mid-session remains valid until it ends. Adding an active server-side session registry to revoke sessions immediately was judged out of scope for this capstone.
- **Product moderation is view + remove-when-safe, not activate/deactivate.** The FINAL STRUCTURE explicitly allows skipping activate/deactivate "when not compatible with current schema"; adding a `products.listing_status` column for it would have been the single biggest schema change in this task for a feature with no stated requirement beyond "if compatible". Removing an unordered listing reuses the exact safety rule a seller's own delete already relies on (`NOT EXISTS ... order_items`).

## AI Chatbot

```
footer.jspf (widget markup, only rendered for a logged-in session)
   |  POST /chat  (message, csrfToken)
ChatServlet  - HTTP plumbing only: auth/CSRF/rate-limit checks, calls ChatService, writes JSON
   |
ChatService.answer(userId, message)
   1. validate (1-500 chars, no control characters)
   2. gather context - ONLY what the message asks about:
        - keywords extracted from the message -> ProductDAO.searchProducts (<= 5 matches)
        - each match's rating -> ReviewService.getRatingSummary
        - IF the message looks order-related AND userId != null -> OrderDAO.getOrdersForUser(userId)
          (already scoped to one user - this can never return another buyer's orders)
   3. IF an API key is configured (AiConfig.isConfigured()):
        call the provider (5s connect / 8s total timeout) with the context as a system
        message instructing it to answer ONLY from that context
        -> on success, return its reply
        -> on ANY failure (non-200, timeout, I/O error, unexpected shape) fall through
   4. Fallback: build a plain-language answer directly from the SAME context
      (never an exception, never an empty reply)
```

Design decisions:

- **Why Anthropic's Messages API, and why no JSON library.** It was the one AI endpoint reachable for testing in the environment that built this, and the project's own `pom.xml` has no JSON dependency to reuse; adding one (e.g. Jackson/Gson) for a single, fixed-shape request and one extracted response field was judged unnecessary complexity ("do not blindly add dependencies"). `ChatService.jsonString`/`extractText` are two small, narrowly-scoped methods - not general JSON (de)serializers - unit-tested for normal input, special characters, and malformed/unexpected shapes. Swapping providers means changing `buildRequestBody`/`extractText` to match that provider's shape; `AiConfig` (URL/key/model) is already provider-agnostic.
- **Fallback-by-default, not fail-by-default.** With no API key configured (the out-of-the-box state - `ai.properties.example` ships with a blank key), the chatbot works immediately, answering from the database directly. This was chosen over returning an error so the feature is usable and demoable without any external account.
- **Rate limiting is in-memory (`ConcurrentHashMap` + per-user sliding window), not a database table.** Consistent with the rest of the app's single-instance assumptions (sessions, CSRF tokens); a multi-instance deployment would need a shared store (e.g. the database, or a cache) instead - noted as a known limitation in the README.
- **Scope is enforced structurally, not just by asking the AI nicely.** Every query ChatService issues is parameterized with server-computed values (extracted keywords, the caller's own session id) - there is no code path where a value from the message is ever concatenated into SQL or used to pick which user's data to read. The system prompt sent to the AI additionally asks it to stay within scope and never invent data, but the database-access boundary does not depend on the AI obeying that instruction.
