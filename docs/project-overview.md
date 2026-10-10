# SUHANAMART - Project Overview

**SUHANAMART (Smart Shopping System)** is a Java web application built with JSP, Servlets, and MySQL, following a layered architecture on top of Apache Tomcat 9.

## What it does

A small e-commerce flow, end to end:

- **Account creation & login** - registration with validation, BCrypt password hashing, session-based authentication
- **Product browsing** - list, search by name, filter by category, product detail pages
- **Cart** - add/update/remove items, quantity clamped to live stock
- **Checkout** - places an order as a single database transaction: creates the order, decrements stock (re-checked at commit time), clears the cart - all or nothing
- **Order history** - past orders, scoped so a user can only ever see their own
- **Profile** - the logged-in user's own account details
- **Seller module** - product and stock management, incoming orders (own items only), Pending -> Confirmed -> Shipped -> Delivered status tracking
- **Reviews** - a buyer can rate (1-5) and review a product once the order containing it has been delivered; the product page shows the average rating, a star breakdown and the newest reviews
- **Admin module** - a dashboard (totals by role, products, orders by status), user management (enable/disable), product moderation (view every listing, remove one with no order history), and read-only order monitoring across every buyer/seller
- **AI Chatbot** - a floating assistant (logged-in users only) that answers questions about SUHANAMART products, reviews, and the caller's own orders, backed by an AI provider when configured and by a direct database-driven fallback otherwise

## Architecture

```
frontend/   JSP views, CSS, JS, WEB-INF (the WAR root)
backend/    Java source: controller / service / dao / model / filter / util
database/   SQL schema + seed data
```

- **Controllers** (`com.suhanamart.controller`) handle requests and mutations. `LoginServlet`, `RegisterServlet`, `CartServlet`, `CheckoutServlet`, `LogoutServlet` are POST-action controllers; `ProductServlet` handles product browsing (GET).
- **Services** (`com.suhanamart.service`) hold validation and business rules (`AuthService`, `CartService`, `ProductService`, `OrderService`, `ReviewService`, `AdminService`, `ChatService`); controllers call them, they call the DAOs.
- **DAOs** (`com.suhanamart.dao`) are the only layer that talks to the database, always via `PreparedStatement`.
- **Models** (`com.suhanamart.model`) are plain data classes (`User`, `Product`, `CartItem`, `Order`, `OrderItem`, `Review`, plus the `Role`/`AccountStatus`/`OrderStatus` enums) passed between DAOs, controllers, and views.
- **Views** (JSPs under `frontend/public`) read data already prepared for them (either via `request.setAttribute` from a controller, or by calling a DAO directly for simple authenticated pages like the cart/orders/profile views) - they contain no SQL and no password handling.
- **`AuthFilter`** guards every authenticated page and mutation endpoint (including `/chat`); a user without a valid session is redirected to login before any handler runs. **`RoleFilter`** additionally requires the `SELLER` role for `/seller/*` or the `ADMIN` role for `/admin/*`, with the same role re-checked independently inside each seller/admin controller.

## Technology

Java 17 &middot; Maven &middot; Apache Tomcat 9 &middot; JSP &middot; Servlet API (`javax.servlet`, not `jakarta.servlet` - required for Tomcat 9) &middot; MySQL 8+ &middot; JDBC &middot; jBCrypt

See `setup-guide.md` for how to build and run this locally, and `api-documentation.md` for the servlet endpoints.

See `architecture.md` for the layer rules and the Review Module flow.
