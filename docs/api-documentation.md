# SUHANAMART - Servlet Endpoints

Not a REST API - these are traditional form-posting/redirecting MVC endpoints, documented here for reference. All endpoints except Login/Register/Logout require an active session (enforced by `AuthFilter`); an unauthenticated request is redirected to `/public/login.jsp`.

| Method | Path | Auth required | Purpose |
|---|---|---|---|
| POST | `/RegisterServlet` | No | Create an account. Params: `name`, `email`, `password`, `confirmPassword`, optional `registerAsSeller=true` (creates a `SELLER` account; any other value creates a `BUYER`). On success, forwards to login with a success message. On duplicate email, returns a specific error. |
| POST | `/LoginServlet` | No | Authenticate. Params: `email`, `password`, optional `rememberMe`. On success, creates a session (`userId`, `name`, `role`) and redirects sellers to `/seller/dashboard`, everyone else to `/public/dashboard.jsp`. |
| GET | `/LogoutServlet` | No | Invalidates the session, redirects to login. |
| POST | `/chat` | Yes | AI chatbot. Requires `csrfToken` (else `403`). Param: `message` (1-500 chars, no control characters). Returns a small JSON object `{"error": boolean, "reply": string}` - `200` on success, `400` invalid input, `403` no/bad CSRF, `429` rate limit (10/min/user), `500` on an unexpected server error. Never a stack trace. |
| GET | `/products` | Yes | Product list. Optional query params: `q` (name search), `category`. Forwards to `products.jsp` with `products`, `categories`, `keyword`, `category`, `loadError` request attributes. |
| GET | `/product-details` | Yes | Single product. Required query param: `id`. Forwards to `product-details.jsp` with `product`, `invalidId`, `loadError`, and the Review Module attributes `reviewSummary`, `reviews`, `reviewsLoadError` (plus a one-time `flashSuccess`/`flashError`). |
| POST | `/CartServlet` | Yes | Cart mutation. Requires the hidden `csrfToken` field (else `403`). Input is validated by `CartService` (bad ids/quantities change nothing; quantities are limited to stock). Param `action` = `add` \| `update` \| `remove` \| `clear`, plus `productId`/`cartItemId`/`quantity` as relevant. Always redirects back to `/public/cart.jsp`. |
| POST | `/CheckoutServlet` | Yes | Requires the hidden `csrfToken` field (else `403`). Places an order from the current cart, as one database transaction (see `OrderDAO.placeOrder`). On success, redirects to `/public/order-details.jsp?orderId=...&justPlaced=1`. On empty cart or insufficient stock, forwards back to checkout with a specific error message. |

## Review endpoints

Both require a login (`AuthFilter`); the buyer id always comes from the session. Errors are shown as a one-time message on the page the buyer is sent to - never as a stack trace.

| Method | Path | Purpose |
|---|---|---|
| GET | `/reviews?orderId=N&productId=M` | Review form. Forwards to `public/reviews.jsp` with `reviewItem`, `formRating`, `formComment`, `errors`, `commentMinLength`, `commentMaxLength`. If the ids are missing/invalid, or the purchase is not the buyer's / not delivered / already reviewed / the buyer's own product, redirects to `/public/orders.jsp` (or the order page) with an explanation. |
| POST | `/reviews` | Params: `orderId`, `productId`, `rating` (`1`-`5`), `comment` (5-1000 chars), `csrfToken` (else `403`). Success: redirect to `/product-details?id=M#reviews` with "Thank you! Your review has been posted." Invalid input: the form is shown again with the reasons and what was typed. Duplicate / ineligible: redirect with the reason. |

Rules live in `ReviewService`: order belongs to the buyer, product is in the order, order and line are `DELIVERED`, one review per buyer + order + product, sellers cannot review their own products.

## Seller endpoints

All require a session whose `role` is `SELLER` (`RoleFilter`, re-checked in each controller): not logged in -> redirect to login, logged in as a buyer -> `403`. Every `POST` also requires the hidden `csrfToken` field (else `403`). The seller id is always taken from the session. A product/order that isn't yours behaves exactly like one that doesn't exist.

| Method | Path | Purpose |
|---|---|---|
| GET | `/seller/dashboard` | Dashboard. Request attributes: `productStats`, `orderCounts`, `recentOrders`, `lowStockProducts`, `lowStockThreshold`, `loadError`. |
| GET | `/seller/products` | The seller's own products (`products`, `lowStockThreshold`, `loadError`). |
| GET | `/seller/products/new` | Add-product form. |
| GET | `/seller/products/edit?id=N` | Edit form; redirects to the list with an error if product N isn't the seller's. |
| POST | `/seller/products/create` | Params: `name`, `description`, `price`, `category`, `imageUrl`, `stock`. Validation errors re-show the form (`errors`, `formValues`). |
| POST | `/seller/products/update` | Same params + `productId`. Ownership is part of the SQL `WHERE`. |
| POST | `/seller/products/stock` | Params: `productId`, `stock` (0-1,000,000). |
| POST | `/seller/products/delete` | Param: `productId`. Refused if any order references the product. |
| GET | `/seller/orders[?status=PENDING\|CONFIRMED\|SHIPPED\|DELIVERED]` | Orders containing the seller's products, showing only the seller's lines (`orders`, `activeFilter`). Unknown filter values are ignored. |
| GET | `/seller/orders/details?orderId=N` | One order as this seller sees it (`order`, `nextStatus`); `404` page if none of its lines are theirs. |
| POST | `/seller/order-status` | Params: `orderId`, `status`. Moves the seller's lines to `status` only if it is exactly the next step (`PENDING -> CONFIRMED -> SHIPPED -> DELIVERED`). GET is not supported (`405`). |

Direct requests to `/public/seller/*.jsp` are redirected to the matching controller URL by `RoleFilter`.

## Admin endpoints

All require a session whose `role` is `ADMIN` (`RoleFilter`, re-checked in each controller via `SecurityUtil.currentAdminId`): not logged in -> redirect to login, logged in as anything else -> `403`. Every `POST` also requires `csrfToken` (else `403`).

| Method | Path | Purpose |
|---|---|---|
| GET | `/admin/dashboard` | Totals (`stats`: `AdminService.DashboardStats` - users by role, products, orders, orders by status), `loadError`. |
| GET | `/admin/users[?role=BUYER\|SELLER\|ADMIN]` | Every user (`users`, `roleFilter`, `loadError`). |
| POST | `/admin/users/status` | Params: `userId`, `action` = `enable` \| `disable`. Refused (with a message, no change) if the target is an `ADMIN` account or the caller's own account. |
| GET | `/admin/products` | Every product across every seller, with the seller's name (`products`, `loadError`). |
| POST | `/admin/products/remove` | Param: `productId`. Refused if the product has any order history. |
| GET | `/admin/orders[?status=...][&orderId=N]` | Every order (`orders`, `activeFilter`, `loadError`); `orderId` also loads one order's full detail (`selectedOrder`, `selectedOrderMissing`) - every line, from every seller. Read-only: never changes an order's status. |

Direct requests to `/public/admin/*.jsp` are redirected to the matching controller URL by `RoleFilter`, same pattern as the Seller module. `AdminService`/`AdminDAO` reuse the existing `OrderDAO` for order monitoring (`findAllOrders`/`findAnyOrder`/`countAllOrdersByStatus` - new methods, same class) instead of duplicating that query logic.

Product input rules (`ProductService`): name 1-150 chars; description <= 2000; price `0.01`-`99999999.99` with at most 2 decimals; category 1-80; stock integer 0-1,000,000; image URL optional, must be an absolute `http://`/`https://` URL <= 500 chars.

## Session attributes

| Key | Type | Set by | Meaning |
|---|---|---|---|
| `userId` | `Integer` | `LoginServlet` | The logged-in user's id. Its presence is what `AuthFilter` checks. |
| `name` | `String` | `LoginServlet` | The logged-in user's display name (for the "Welcome back" greeting). |
| `role` | `String` | `LoginServlet` | `BUYER`, `SELLER` or `ADMIN`. Read by `RoleFilter` and the seller/admin controllers. |
| `csrfToken` | `String` | `SecurityUtil.csrfToken` | Per-session token required on every state-changing `POST` (cart, checkout, reviews, seller, admin, chat). |
| `flashSuccess` / `flashError` | `String` | `Flash` | One-time message carried across a redirect, then removed. |

## Request attributes used for forwards (not query params)

| Key | Set by | Read by |
|---|---|---|
| `errorMessage` / `successMessage` | Any controller that forwards to a JSP after validation | `login.jsp`, `register.jsp`, `checkout.jsp` |
| `products`, `categories`, `keyword`, `category`, `loadError` | `ProductServlet` | `products.jsp` |
| `product`, `invalidId`, `loadError`, `reviewSummary`, `reviews`, `reviewsLoadError` | `ProductServlet` | `product-details.jsp` |
| `reviewItem`, `formRating`, `formComment`, `errors`, `commentMinLength`, `commentMaxLength` | `ReviewServlet` | `reviews.jsp` |
| `stats`, `loadError` | `AdminServlet` | `admin/dashboard.jsp` |
| `users`, `roleFilter`, `loadError` | `AdminUserServlet` | `admin/users.jsp` |
| `products`, `loadError` | `AdminServlet` | `admin/products.jsp` |
| `orders`, `activeFilter`, `selectedOrder`, `selectedOrderMissing`, `loadError` | `AdminOrderServlet` | `admin/orders.jsp` |
