# SUHANAMART - Manual Test Cases

These have not been executed against a live deployment as part of this reorganization (no Tomcat/MySQL available in the environment that built this) - see the honesty note in the root `README.md`. Run through these after deploying locally.

## Registration

| # | Steps | Expected |
|---|---|---|
| 1 | Leave all fields empty, submit | Client-side validation blocks submit; if bypassed, server shows "All fields are required." |
| 2 | Enter an invalid email format | "Please enter a valid email address." |
| 3 | Enter a password under 6 characters | "Password must be at least 6 characters long." |
| 4 | Password and Confirm Password don't match | "Password and Confirm Password do not match." |
| 5 | Valid new details | "Account created successfully. Please log in." + forwarded to login |
| 6 | Register again with the same email | "An account with this email already exists." |

## Login

| # | Steps | Expected |
|---|---|---|
| 1 | Correct email + password | Redirected to `/public/dashboard.jsp`, greeted by name |
| 2 | Wrong password | "Invalid email or password." (same generic message as a wrong email - doesn't reveal which one) |
| 3 | Non-existent email | "Invalid email or password." |
| 4 | Empty fields | Same generic error |
| 5 | Check "Remember me", log in, close and reopen browser within 30 days | Still logged in |

## Session protection

| # | Steps | Expected |
|---|---|---|
| 1 | Visit `/public/dashboard.jsp` directly with no session | Redirected to login |
| 2 | Visit `/products`, `/public/cart.jsp`, `/public/checkout.jsp`, `/public/orders.jsp`, `/public/profile.jsp` with no session | Each redirects to login |
| 3 | Log in, then click Logout | Session invalidated, redirected to login; revisiting dashboard afterward redirects to login again |

## Products

| # | Steps | Expected |
|---|---|---|
| 1 | Visit Products with an empty database | "No products available yet." empty state |
| 2 | Search for a name that doesn't exist | "No products match your search." + Clear filters link |
| 3 | Filter by category | Only matching products shown |
| 4 | Open a product detail page with a valid id | Full details, quantity selector, Add to Cart |
| 5 | Open `/product-details?id=999999` (non-existent) | "We couldn't find that product." |
| 6 | Open `/product-details?id=abc` (non-numeric) | Same safe not-found state, no stack trace |

## Cart

| # | Steps | Expected |
|---|---|---|
| 1 | Add a product to cart | Appears in cart with correct price/quantity |
| 2 | Add the same product again | Quantity increases (not a duplicate row) |
| 3 | Increase quantity past available stock | Blocked/clamped at stock; "Max stock reached" shown |
| 4 | Decrease quantity to 0 | Item removed |
| 5 | Click Remove on an item | Item removed, total recalculates |
| 6 | Click Clear Cart | Cart empty, "Your cart is empty." shown |

## Checkout

| # | Steps | Expected |
|---|---|---|
| 1 | Go to checkout with an empty cart | Redirected/blocked with an empty-cart message |
| 2 | Place a valid order | Order created, stock decremented by the ordered quantity, cart cleared, redirected to order confirmation with a success banner |
| 3 | Simulate insufficient stock (e.g. reduce stock in DB between adding to cart and checkout) | Order NOT created, specific "no longer has enough stock" message, cart and stock unchanged (transaction rolled back) |

## Orders / Profile

| # | Steps | Expected |
|---|---|---|
| 1 | View order history after placing an order | Order appears, newest first |
| 2 | Open an order's details | Correct items, quantities, total, payment method |
| 3 | As User A, guess User B's order id in the URL | "We couldn't find that order." (never shows another user's order) |
| 4 | View profile | Correct name, email, member-since date from the database |

## Seller module

Automated coverage: `python3 tests/seller_smoke_test.py http://localhost:8080/suhanamart` (HTTP only, development DB). The cases below are the ones a browser is needed for, plus the main flows.

| # | Steps | Expected |
|---|---|---|
| 1 | Register with "I want to sell" ticked, then log in | Lands on `/seller/dashboard`; nav shows Dashboard, My Products, Add Product, Incoming Orders, Logout |
| 2 | Register WITHOUT the tick, log in, open `/seller/dashboard` | 403 Forbidden |
| 3 | Logged out, open `/seller/products` or `/public/seller/products.jsp` | Redirected to login |
| 4 | Add a product (all fields) | Redirected to My Products with "Product added."; it appears with the right price/stock |
| 5 | Add a product with price `abc`, stock `-1`, image `javascript:x` | Form re-shown with each error; typed values kept |
| 6 | Change a product's stock from the list | "Stock updated."; badge switches between In stock / Low / Out of stock at 5 and 0 |
| 7 | Edit a product, then delete a never-ordered one | Edit saved; delete asks for confirmation first, then removes it |
| 8 | Delete a product that a buyer has ordered | Refused with an explanation; set stock to 0 instead |
| 9 | As seller B, open seller A's `/seller/products/edit?id=<A's id>` | Redirected to your list with "could not be found" - never A's data |
| 10 | Buyer orders products from two sellers in one order | Each seller sees the order with ONLY their own items and total |
| 11 | Seller opens the order | Customer name/email, payment method, order date, items, status stepper |
| 12 | Click "Mark as Confirmed" (confirm dialog), then repeat until Delivered | One step at a time; after Delivered there is no button |
| 13 | Buyer opens the order after each step | Line status updates immediately; the order status changes only once every seller has reached that step |
| 14 | Dashboard | Counts match what you see in My Products / Incoming Orders |
| 15 | Phone width (375px): My Products and Incoming Orders | Tables turn into stacked cards, no sideways scrolling; hamburger menu works |

## Review module

Automated coverage: `python3 tests/review_smoke_test.py http://localhost:8080/suhanamart` (HTTP only, development DB). The cases below need a browser, plus the main flows.

| # | Steps | Expected |
|---|---|---|
| 1 | As a buyer, open a **Pending/Confirmed/Shipped** order | Item rows show their status but **no** "Write a review" button |
| 2 | Have the seller(s) move the order to **Delivered**, reload the order page | Each item row shows a **Write a review** button (an order with two sellers needs both to have delivered) |
| 3 | Click it | Review form: product name/image, five stars, comment box with a live character counter, Submit/Cancel |
| 4 | Hover over the stars, click the 4th star | Stars 1-4 turn gold; the hint reads "Very good"; tabbing to the stars and using the arrow keys also works |
| 5 | Submit with no star, or a comment shorter than 5 characters | Message appears (instantly from the script, and from the server if the script is disabled); what you typed is kept |
| 6 | Submit a valid review | Redirected to the product page with "Thank you! Your review has been posted."; the review is listed with your name, stars, date and "Verified purchase"; the average and star bars include it |
| 7 | Refresh the product page | The thank-you message does not come back |
| 8 | Go back to the order page | The item now shows **Reviewed** (link to the product's reviews) instead of the button |
| 9 | Use the browser Back button to the form and submit again, or double-click Submit | Only one review exists; the second attempt says you have already reviewed it |
| 10 | Post a comment containing `<b>bold</b> <script>alert(1)</script>` and Tamil text | Shown as plain text (nothing executes or turns bold); the Tamil text is intact |
| 11 | Post a multi-line comment | Line breaks are kept; a very long unbroken word does not break the layout |
| 12 | As another buyer, open `/reviews?orderId=<first buyer's order>&productId=<id>` | Sent to My Orders with "We couldn't find that item in your orders." |
| 13 | Product with no reviews | "No reviews yet" and an explanation that reviews come after delivery |
| 14 | Product with more than 20 reviews | Only the newest 20 are listed, with a "Showing the 20 most recent of N" note; average/count use all of them |
| 15 | Phone width (375px): product reviews and the review form | Summary stacks above the bars, buttons full width, no sideways scrolling |

## Admin module

Automated coverage: `python3 tests/admin_smoke_test.py http://localhost:8080/suhanamart` (HTTP only, development DB, needs the seeded admin's default password unchanged). The cases below need a browser.

| # | Steps | Expected |
|---|---|---|
| 1 | Log in as `admin@suhanamart.com` | Lands on `/admin/dashboard`; buyer/seller navbars show an "Admin Dashboard" link too |
| 2 | Dashboard stat cards | Click one (e.g. "Pending Orders") | Goes straight to the filtered orders list |
| 3 | Users &rarr; filter by role | Buyer/Seller/Admin tabs narrow the list; role and status badges are color-coded |
| 4 | Disable a buyer's account, then try to log in as that buyer | Generic "Invalid email or password" - never says "disabled" |
| 5 | Re-enable it | That buyer can log in again immediately |
| 6 | Look at the admin's own row | No Enable/Disable button (an em dash instead) |
| 7 | Products &rarr; Remove a listing with no orders | Confirm dialog, then it disappears from the list |
| 8 | Products &rarr; Remove a listing that HAS been ordered | Confirm dialog, then a message explains it can't be removed; it's still listed |
| 9 | Orders &rarr; filter by status, click "View" on one | Shows buyer name/email, payment method, every line (from every seller), and the total |
| 10 | As a buyer or seller, try to open `/admin/dashboard` directly | 403 (friendly error page, no stack trace) |
| 11 | Phone width (375px) | Tables scroll inside their own box; the page itself never scrolls sideways |

## AI Chatbot

Automated coverage: `python3 tests/chat_smoke_test.py http://localhost:8080/suhanamart` (HTTP only, development DB, fallback mode - no AI key needed). The cases below need a browser.

| # | Steps | Expected |
|---|---|---|
| 1 | Log out, look at any page | No floating chat button |
| 2 | Log in, look at any page | A floating "Ask SUHANAMART" button, bottom-right |
| 3 | Open it, ask about a product by name | A reply mentioning its price, stock and rating (if it has reviews) |
| 4 | Ask "what's the status of my orders?" | A reply listing your own recent orders only - never another buyer's |
| 5 | Ask something unrelated to SUHANAMART | A polite reply that it can only help with SUHANAMART shopping questions (if an AI provider is configured), or the generic "I can help with..." fallback line otherwise |
| 6 | Send 11 messages within a minute | The 11th shows a "sending too quickly" message instead of failing silently |
| 7 | Close and reopen the panel | Conversation so far is still shown (cleared only on page reload - there is no server-side chat history) |
| 8 | Phone width (375px) | Panel takes most of the screen width; the toggle button shrinks to an icon only |

## Responsive layout

Check at 320px, 375px, 768px, 1024px, 1440px width: no horizontal scrolling, nav collapses to a toggle menu on narrow widths, product grid reflows, cart table remains usable.
