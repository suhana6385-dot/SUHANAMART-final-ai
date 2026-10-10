# SUHANAMART — Smart Shopping System

A Java JSP/Servlet + MySQL e-commerce capstone project: registration, login, product browsing/search, cart, transactional checkout with order history, a **Seller module** (product & stock management, incoming orders, order-status tracking), a **Review module** (buyers rate and review products from delivered orders), an **Admin module** (dashboard, user/product/order management) and an **AI Chatbot** (answers product, review and the caller's own order questions, with a safe fallback when no AI provider is configured).

Full details: [`docs/project-overview.md`](docs/project-overview.md) · [`docs/setup-guide.md`](docs/setup-guide.md) · [`docs/api-documentation.md`](docs/api-documentation.md)

## Project structure

```
SUHANAMART/
├── frontend/               JSP views, CSS, JS, images, WEB-INF (this is the WAR root)
│   ├── public/              All JSP pages (incl. reviews.jsp, error.jsp)
│   │   ├── seller/          Seller dashboard, products, add/edit product, orders, order details
│   │   └── admin/           Admin dashboard, users, products, orders
│   ├── assets/               css/, js/, images/
│   └── WEB-INF/             web.xml + shared JSP fragments (jspf/)
│
├── backend/
│   └── src/main/
│       ├── java/com/suhanamart/
│       │   ├── controller/   Servlets (Login, Register, Product, Cart, Checkout, Logout, Review,
│       │   │                 SellerDashboard, SellerProduct, SellerOrder, OrderStatus,
│       │   │                 AdminServlet, AdminUserServlet, AdminOrderServlet, ChatServlet)
│       │   ├── service/      Business rules & validation (AuthService, CartService, ProductService,
│       │   │                 OrderService, ReviewService, AdminService, ChatService)
│       │   ├── dao/          Database access (PreparedStatement only): User, Product, Cart, Order, Review, Admin
│       │   ├── model/        Plain data classes (User, Product, CartItem, Order, OrderItem, Review,
│       │   │                 Role, AccountStatus, OrderStatus, SellerOrder, ProductStats)
│       │   ├── filter/       AuthFilter (login), RoleFilter (seller- and admin-only areas), EncodingFilter (UTF-8)
│       │   └── util/         DBConnection, PasswordUtil, ValidationUtil, SecurityUtil, Flash, AiConfig
│       └── resources/        db.properties.example, ai.properties.example, log4j2.xml
│
├── database/                schema.sql, seed-data.sql, suhanamart.sql (combined), migrations/ (V1-V4), README.md
├── docs/                    Project overview, setup guide, API docs, architecture, diagrams, screenshots/
├── tests/                   Manual + e2e test cases, database sanity-check script, and
│                            seller_smoke_test.py, review_smoke_test.py, admin_smoke_test.py, chat_smoke_test.py
└── pom.xml
```

## 1. Requirements

- Java 17 (JDK)
- Apache Maven 3.6+
- Apache **Tomcat 9.0.x** — required exactly. This project uses `javax.servlet-api 4.0.1` (Servlet 4.0). Tomcat 10+ moved to the `jakarta.servlet` namespace and will not load these servlets/filters at all; Tomcat 8.5 does not fully support the `web-app_4_0.xsd` schema this project's `web.xml` declares.
- MySQL 8+

## 2. Java version

Java 17, set via `maven.compiler.source`/`target` in `pom.xml`.

## 3. Maven version

3.6 or newer (uses standard `war` packaging + `maven-war-plugin` 3.4.0; no unusual plugin requirements).

## 4. MySQL setup

Install/start MySQL 8+, then create the database and tables (step 5).

## 5. Database import commands

```bash
mysql -u your_mysql_username -p < database/suhanamart.sql
```

This single script creates the `suhanamart` database, all 6 tables (users, products, cart_items, orders, order_items, reviews), one seeded **admin account**, and a few optional sample products. See [`database/README.md`](database/README.md) for the schema-only / seed-only variants.

## 6. db.properties setup

No password is ever hardcoded in source. `DBConnection.java` resolves credentials in this order: environment variables → JVM system properties → a local `db.properties` file. The simplest path:

```bash
cp backend/src/main/resources/db.properties.example backend/src/main/resources/db.properties
```

Then edit `db.properties` and set your real MySQL username/password. **Never commit the real file** — it's already excluded in `.gitignore`.

Optional - to enable the **AI chatbot**'s live provider, do the same with `ai.properties.example` → `ai.properties` (or set the `SUHANAMART_AI_API_URL` / `SUHANAMART_AI_API_KEY` / `SUHANAMART_AI_MODEL` environment variables instead - see `util/AiConfig.java`). Leaving the API key blank is fine: the chatbot still works, answering from the SUHANAMART database directly instead of through an AI model. Keep this folder to exactly these template/config files (avoid saving stray copies here — Maven copies everything in this folder into the WAR).

## 7. Maven build command

```bash
mvn clean package
```

Produces `target/suhanamart.war`.

## 8. Tomcat deployment instructions

```bash
cp target/suhanamart.war <TOMCAT_HOME>/webapps/
<TOMCAT_HOME>/bin/startup.sh      # Linux/Mac
<TOMCAT_HOME>/bin/startup.bat     # Windows
```

## 9. Application URL

```
http://localhost:8080/suhanamart/
```

Redirects to the login page for a logged-out visitor, or the dashboard if already logged in.

## Seller module

Sellers are ordinary accounts with `users.role = 'SELLER'`. Create one by ticking **"I want to sell products on SUHANAMART"** on the registration page (the only roles the server will ever write are `BUYER` and `SELLER`). Sellers log in to `/seller/dashboard`; everyone else lands on the normal buyer dashboard.

| URL | What it does |
|---|---|
| `/seller/dashboard` | Real counts: total / active (in stock) / low-stock (1-5 left) / out-of-stock products, and orders by status |
| `/seller/products` | Your products only; inline stock update; edit and delete |
| `/seller/products/new`, `/seller/products/edit?id=N` | Add / edit form |
| `/seller/orders[?status=PENDING]` | Incoming orders that contain **your** products (only your lines are shown) |
| `/seller/orders/details?orderId=N` | Customer name/email, payment method, your items, status control |

**Order status:** `Pending -> Confirmed -> Shipped -> Delivered`, one step forward at a time (application-level tracking only - no courier/GPS). Status is tracked **per order line** (`order_items.status`), so in an order with several sellers' products each seller only moves their own lines. The buyer-facing `orders.status` is the least-advanced line status, kept up to date in the same database transaction; buyers see it (and each line's status) on their existing order pages.

**How access is enforced (server-side, in layers):** `RoleFilter` (seller session required for `/seller/*` and direct `/public/seller/*.jsp` hits) -> each controller re-checks the session role and takes the seller id from the **session**, never from the request -> every product/order SQL statement contains `seller_id = ?` -> all state-changing requests need a per-session CSRF token. Another seller's product or order is reported as "not found", exactly like one that doesn't exist.

**Upgrading an existing database:** run the migrations you have not applied yet, in order - `V2__order_status.sql` (Seller module), `V3__reviews.sql` (Review module), then `V4__admin_and_account_status.sql` (Admin module: `users.account_status` + one seeded admin account). All are safe to re-run. Fresh installs using `database/suhanamart.sql` already include everything. **The app will not log anyone in until the V2 columns exist**, because login reads `users.role`.

## Review module

Buyers rate (1-5 stars) and review products they have received.

```
Buyer -> My Orders -> Order details -> "Write a review" (only on a delivered item)
      -> /reviews form (stars + comment) -> POST /reviews -> stored -> shown on the product page
```

| URL | What it does |
|---|---|
| `GET /reviews?orderId=N&productId=M` | The review form (`public/reviews.jsp`) - only for a purchase that may be reviewed right now |
| `POST /reviews` | Submits `orderId`, `productId`, `rating`, `comment`, `csrfToken` |
| `/product-details?id=M` | Shows the average rating, star distribution and the newest 20 reviews (`#reviews`) |
| `/public/order-details.jsp?orderId=N` | Shows **Write a review** on eligible lines and **Reviewed** on reviewed ones |

**Rules (all enforced server-side in `ReviewService`, with the delivered/ownership check repeated inside the INSERT in `ReviewDAO`):** logged in; the order belongs to the buyer; the product is in that order; the order **and** that order line are `DELIVERED` (for an order with several sellers this means every seller has delivered their part); rating is exactly 1-5; comment 5-1000 characters with no control characters; one review per buyer + order + product (database `UNIQUE` key - also holds under simultaneous submissions); a seller cannot review their own product; CSRF token required. Comments are stored as typed and HTML-escaped on output. The average is computed from exact per-star counts and rounded half-up to one decimal.

Layering: `reviews.jsp` -> `ReviewServlet` -> `ReviewService` -> `ReviewDAO` -> database. No SQL in JSPs or servlets.

## Admin module

One admin account is seeded by `database/suhanamart.sql` / `V4__admin_and_account_status.sql`:

```
email:    admin@suhanamart.com
password: ChangeMe_Admin#2026
```

There is no registration path or password-reset feature for admins (by design - see [`SECURITY REQUIREMENTS`](#security-notes) below); change this password directly in the database after your first login (`UPDATE users SET password = '<a PasswordUtil.hash() output>' WHERE email = 'admin@suhanamart.com';`). The registration form can never create an `ADMIN` account - `AuthService.register` only ever writes `BUYER` or `SELLER`, regardless of what a request sends.

| URL | What it does |
|---|---|
| `/admin/dashboard` | Totals: users by role, products, orders, orders by status |
| `/admin/users[?role=BUYER\|SELLER\|ADMIN]` | Every user; enable/disable an account |
| `/admin/products` | Every listing, across every seller, with the seller's name; remove a listing |
| `/admin/orders[?status=...][&orderId=N]` | Every order, across every buyer/seller; status filter; click into one order for its buyer and full line items (read-only - the Admin module never changes an order's status; that stays the seller's job) |

**Rules, enforced server-side in `AdminService`/`AdminDAO` (not just the UI):**
- An admin can never disable their own account, or any `ADMIN`-role account - enforced twice: once in `AdminService` (the acting admin's own id) and again inside the `UPDATE ... WHERE role <> 'ADMIN'` itself (any admin account, by anyone), so the system can never end up with no way to log back in.
- A disabled account fails login with the exact same generic message as a wrong password - it is never told why.
- A product can only be removed if it has **no order history** (same rule a seller's own delete already used); removing it would otherwise corrupt past orders and reviews.
- `RoleFilter` requires an `ADMIN` session for every `/admin/*` URL and the `/public/admin/*.jsp` files directly (redirected to the right controller, same pattern as the Seller module); every admin controller re-checks `SecurityUtil.currentAdminId` independently; every state-changing POST needs a CSRF token.

## AI Chatbot

A floating "Ask SUHANAMART" widget (bottom-right, every page) for logged-in users only - it is not rendered at all in the HTML for an anonymous visitor, and `/chat` itself requires a session.

```
Chat UI (footer.jspf + main.js) -> POST /chat -> ChatServlet -> ChatService
    -> SUHANAMART database (ProductDAO, ReviewService, OrderDAO - read-only, scoped)
    -> an AI provider (Anthropic Messages API, if configured) -> reply
       (if not configured, or it fails/times out: a direct, templated reply
        from the very same database context - never an error, never empty)
```

**Scope - deliberately narrow, not a database query interface:**
- **Products:** name, description, category, price, stock - looked up by keywords extracted from the message (`ProductDAO.searchProducts`), never a raw value run as SQL.
- **Reviews:** a matched product's average rating and review count (`ReviewService.getRatingSummary`) - no reviewer names.
- **Orders:** *only* the signed-in caller's own recent orders (order id, date, total, status) - `OrderDAO.getOrdersForUser` is already scoped to one `user_id`, and this context is only gathered when the message looks like it's asking about orders.

**Security:**
- Login required (`AuthFilter` covers `/chat`); CSRF token required on every POST.
- Input validated: 1-500 characters, no control characters.
- **Rate limit: 10 requests/minute per user**, enforced in-memory in `ChatService` (one Tomcat instance - see Known limitations).
- The API key lives only in `ai.properties` / environment variables (`AiConfig`), is never logged, and is never sent to the browser.
- Timeouts (5s connect / 8s total) and any provider error/HTTP-non-200/unexpected response fall back to the database-grounded template reply - the browser always gets a normal-looking JSON reply, never a stack trace, and the real cause (if any) is logged server-side only.
- No JSON library dependency was added (see `docs/architecture.md`) - the request body and the one `"text"` field this project reads from the response are built/parsed by two small, narrowly-scoped hand-written methods, unit-tested for well-formed input, malformed input, and an unexpected response shape.

## 10. Default/test account

One **admin** account is seeded (see the Admin module section above: `admin@suhanamart.com` / `ChangeMe_Admin#2026`) - its password hash was produced by the project's own hashing algorithm (jBCrypt), exactly as if the account had been registered and then promoted by hand; there is no plain-text password stored anywhere. There is no seeded buyer or seller account - register one through `/public/register.jsp` (tick the seller checkbox for a seller account). The four sample products have no owner (`seller_id` is NULL): buyers can buy them, but no seller can edit them until you assign an owner - see the last lines of `V2__order_status.sql` for the one-line SQL.

## 11. Troubleshooting

If registration/login shows "Something went wrong. Please try again later.", check `<TOMCAT_HOME>/logs/catalina.out` (Log4j2 also writes to `logs/suhanamart.log` relative to Tomcat's working directory) for a line from `RegisterServlet`/`LoginServlet` naming the specific cause — missing credentials, wrong DB name, missing table, wrong host/port, etc. The browser intentionally never shows this detail.

## 12. Known limitations

- Reviews cannot be edited or deleted after posting (the table has an `updated_at` column ready for it); one buyer can review the same product again from a *different* order.
- The existing buyer JSPs (`cart`, `checkout`, `dashboard`, `orders`, `order-details`, `profile`) still load their data by calling DAOs/services from a scriptlet instead of through a controller. This is how they were delivered and working, so it was left alone.
- Promoting a user to seller by hand: `UPDATE users SET role = 'SELLER' WHERE email = '...';` (same idea for `'ADMIN'`, though the seeded admin account above is the intended path).
- Checkout does not collect a shipping address, so sellers see the customer's name and email (plus payment method) as the only delivery contact details.
- A product that has ever been ordered can't be deleted (order history must keep pointing at it); a seller sets its stock to 0 instead, and the Admin module's "Remove" button is refused for the same reason. There is no product active/inactive flag - "Active" on a seller's dashboard means "in stock", and the Admin module's product moderation is view-everything + remove-when-safe rather than a separate activate/deactivate toggle (deliberately: the FINAL STRUCTURE allows this "only when compatible with current schema", and adding a new column for it felt like more schema surface than this scope needed - straightforward to add later if wanted).
- Seller and admin lists (products, orders, users) are not paginated.
- Checkout uses a labeled demo/manual payment method only — no real payment gateway integration.
- Product images are optional external URLs (`products.image_url`); no local image upload feature.
- **Admin:** disabling an account takes effect at that user's *next* login - an already-open session for an account disabled mid-session stays valid until it ends or the user logs out. There is no admin audit log of who changed what.
- **Chatbot:** the 10-requests/minute rate limit is in-memory in `ChatService`, so it resets if the application restarts, and (if this were ever deployed across multiple Tomcat instances behind a load balancer) is per-instance, not global - consistent with the rest of the app's session/CSRF handling, which makes the same single-instance assumption. The chatbot has no memory of earlier messages in the same conversation - each message is answered independently.
- See the verification note below for exactly what has and has not been tested.

## Security notes

- Passwords are hashed with BCrypt (`PasswordUtil`) - never stored or logged in plain text.
- Every SQL query uses `PreparedStatement`.
- Every state-changing form (cart, checkout, review, seller pages) carries a per-session CSRF token that the servlet checks. Login/registration forms do not (they have no session to protect yet).
- A fresh session id is issued at login (`changeSessionId`), so a session id planted before login can't be reused.
- `public/error.jsp` (mapped in `web.xml`) replaces Tomcat's default error pages, so a stack trace is never shown.
- Request bodies are decoded as UTF-8 for every request (`EncodingFilter`), so non-ASCII names and review comments are stored correctly.
- Seller pages are protected by `RoleFilter` + per-controller role checks + `seller_id` in every seller SQL query; POSTs require a CSRF token; all seller-page output is HTML-escaped; status changes are whitelisted and strictly one step forward.
- No database credentials are hardcoded anywhere in source.
- `AuthFilter` blocks both the servlet endpoints *and* the underlying `/public/*.jsp` URLs directly, so a logged-out user can't bypass authentication by guessing a JSP's URL.
- Checkout runs as a single database transaction (order + order items + stock decrement + cart clear); any failure - including a stock check that fails at the last moment - rolls back everything.
- Server errors are logged via Log4j2 server-side only; the browser always sees a generic message, never a stack trace, SQL detail, or password.

## Honesty note on verification

Everything below was actually run in the environment that prepared this delivery (no browser, no Maven Central access):

- **Compile:** all sources compile with `javac --release 17` against the project's own jars and the Servlet 4.0.1 API.
- **Runtime:** the built application (WEB-INF/classes + lib, all JSPs compiled by Jasper) was deployed on **Apache Tomcat 9.0.118** (embedded, `javax.servlet`) against **MariaDB 10.11**, and driven over HTTP: `tests/seller_smoke_test.py` **113 checks, 0 failures**; `tests/review_smoke_test.py` **81 checks, 0 failures**; plus a buyer regression run (registration, login, browsing, search, cart, checkout, orders, CSRF) of 48 checks, 0 failures. Twelve simultaneous review POSTs for one purchase stored exactly one review.
- **Database:** `V1` -> `V2` -> `V3` apply cleanly and can be re-run; the result has the same columns, keys and constraints as `suhanamart.sql`/`schema.sql` (only physical column order differs for columns added by `ALTER`). `tests/database-test.sql` runs; a rating outside 1-5 and an orphan review are rejected by the database.

**NOT VERIFIED:**

- **`mvn clean package` was not run** (Maven Central is unreachable from that environment). `pom.xml` and its dependencies are unchanged; compilation was instead verified directly with `javac` against the exact dependency versions `pom.xml` declares, substituted from apt/GitHub where Maven Central could not be reached (see below). Please run `mvn clean package` yourself.
- **The real `com.mysql:mysql-connector-j:9.4.0` and `log4j-api`/`log4j-core:2.23.1` jars `pom.xml` declares were not used** - apt-installed MariaDB Connector/J 2.7.6 and Log4j2 2.19.0 were, via a small test-only compatibility shim for the MySQL driver class name (`DBConnection.java` itself is unchanged - it still names `com.mysql.cj.jdbc.Driver`). `jbcrypt-0.4` was built from its real upstream source for this testing, so password hashing/verification (including the seeded admin account) is the genuine algorithm, not a stand-in.
- **MySQL 8 itself was not used** - MariaDB 10.11 was. The SQL is standard, but please run the migrations/tests once on your MySQL 8 server.
- Your **live database** was not inspected (only the scripts in this project), so run `SHOW CREATE TABLE` on your tables if it was created by hand.
- The standalone Tomcat 9 installation was not used (an embedded Tomcat 9 with the same engine libraries was).
- **The real AI provider (`api.anthropic.com` with a real API key) was not called** - see the mock-server note above for exactly what was and wasn't confirmed about the chatbot's provider integration.
- No real-browser walkthrough: star/chat hover and keyboard behaviour, layout at phone widths, and the JavaScript helpers (`review.js`, `seller.js`, `admin.js`, the chat widget in `main.js`) are untested. Follow [`tests/manual-test-cases.md`](tests/manual-test-cases.md).

To re-run the automated checks against your own deployment: `python3 tests/seller_smoke_test.py http://localhost:8080/suhanamart`, `review_smoke_test.py`, `admin_smoke_test.py` and `chat_smoke_test.py` the same way (development database only - they leave their test rows behind; `admin_smoke_test.py` needs the seeded admin account's default password unchanged).
