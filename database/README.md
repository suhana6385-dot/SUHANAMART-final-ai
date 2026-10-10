# SUHANAMART Database

MySQL 8+. All scripts are idempotent (safe to run more than once).

## Files

| File | Contents | When to use it |
|---|---|---|
| `schema.sql` | `CREATE DATABASE` + all 6 tables, no data | Setting up a fresh environment where you don't want sample products |
| `seed-data.sql` | 4 sample products (INSERT only) | After `schema.sql`, if you want something to click around with immediately |
| `suhanamart.sql` | `schema.sql` + `seed-data.sql` combined | One-shot setup - the fastest way to get a working local database |
| `migrations/V1__initial_schema.sql` | The original 5-table schema (before the Seller module) | Building a database step by step from the beginning. Not needed for fresh installs. |
| `migrations/V2__order_status.sql` | Adds seller roles, product ownership and order-line status to an **existing** database | Upgrading a database created before the Seller module. Not needed for fresh installs. |
| `migrations/V3__reviews.sql` | Adds the `reviews` table (Review module) | Upgrading a database that does not have `reviews` yet. Not needed for fresh installs. |
| `migrations/V4__admin_and_account_status.sql` | Adds `users.account_status` and seeds one `ADMIN` account (Admin module) | Upgrading a database that does not have these yet. Not needed for fresh installs. |

## Quick start

```bash
mysql -u your_mysql_username -p < suhanamart.sql
```

Or, structure and data separately:

```bash
mysql -u your_mysql_username -p < schema.sql
mysql -u your_mysql_username -p suhanamart < seed-data.sql
```

## Upgrading an existing database

`CREATE TABLE IF NOT EXISTS` does not alter tables that already exist, so re-running `schema.sql` will not upgrade an old database. Run the migration instead:

```bash
mysql -u your_mysql_username -p < migrations/V2__order_status.sql
mysql -u your_mysql_username -p < migrations/V3__reviews.sql
mysql -u your_mysql_username -p < migrations/V4__admin_and_account_status.sql
```

V2 only adds columns/indexes/a foreign key and relabels legacy `PLACED` orders as `PENDING`; V3 only creates the new `reviews` table; V4 only adds `users.account_status` and seeds one admin account (only if no `ADMIN` account exists yet - never a duplicate); no rows are deleted. `V1__initial_schema.sql` is the original `schema.sql` as first shipped. To compare your own database with the expected structure: `SHOW CREATE TABLE users; ... products; ... orders; ... order_items; ... reviews;`.

## Tables

- **`users`** - registered accounts. Passwords are stored as BCrypt hashes only (`password` column), never plain text. `role` is `BUYER` (default), `SELLER`, or `ADMIN` (never created by registration - see `AuthService.register`). `account_status` (added in V4) is `ACTIVE` (default) or `DISABLED`; checked once, at login.
- **`products`** - the product catalog. `stock` is decremented atomically at checkout. `seller_id` is the owning seller (NULL = unowned, e.g. the sample products; `ON DELETE SET NULL`).
- **`cart_items`** - one row per `(user_id, product_id)`; quantity is updated in place rather than inserting duplicate rows.
- **`orders`** - one row per placed order, with a `total_amount` snapshot. `status` starts as `PENDING` and is the roll-up (least-advanced line) of the order's `order_items.status`, maintained by `OrderDAO`.
- **`reviews`** - one row per buyer review: `user_id`, `product_id`, `order_id`, `rating` (1-5, `CHECK`), `comment`, `created_at`, `updated_at`. `UNIQUE (user_id, order_id, product_id)` allows one review per buyer per product per order; foreign keys cascade on delete. Whether a purchase may be reviewed (order and line `DELIVERED`, buyer owns the order, ...) is decided in `ReviewService`, not by the database.
- **`order_items`** - line items for each order, storing `product_name`/`unit_price` as a snapshot at purchase time (so a later price or name change never rewrites order history). `status` is `PENDING -> CONFIRMED -> SHIPPED -> DELIVERED`, changed only by the seller who owns the product.

## Credentials

No database password is stored anywhere in this project's source. See the root `README.md` and `backend/src/main/resources/db.properties.example` for how `DBConnection.java` resolves credentials (environment variables, JVM system properties, or a local `db.properties` file you create yourself and never commit).
