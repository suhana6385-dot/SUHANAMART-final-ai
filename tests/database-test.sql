-- SUHANAMART - database sanity-check script.
--
-- Exercises the schema (including the reviews table) directly (independent of the Java app) to
-- verify tables, constraints, and cascades behave as the DAOs
-- expect. Run against a disposable/test database - it inserts and
-- deletes rows.
--
--   mysql -u your_mysql_username -p suhanamart < tests/database-test.sql

USE suhanamart;

-- 1. Insert a test user (password is an obviously-fake bcrypt-shaped
--    string - never a real hash; this script never logs in as this
--    user, it only checks the schema).
INSERT INTO users (name, email, password)
VALUES ('DB Test User', 'db-test-user@example.com', '$2a$10$examplehashdoesnotworkforlogin');

-- 2. Duplicate email must be rejected (tests the UNIQUE constraint
--    UserDAO relies on to detect "account already exists").
-- Uncomment to verify it fails as expected:
-- INSERT INTO users (name, email, password)
-- VALUES ('Duplicate', 'db-test-user@example.com', 'irrelevant');

-- 3. Insert a test product.
INSERT INTO products (name, description, price, category, stock)
VALUES ('DB Test Product', 'Inserted by database-test.sql', 10.00, 'Test', 5);

-- 4. Add it to the test user's cart.
INSERT INTO cart_items (user_id, product_id, quantity)
SELECT u.user_id, p.product_id, 2
FROM users u, products p
WHERE u.email = 'db-test-user@example.com' AND p.name = 'DB Test Product';

-- 5. Adding the same product again should update quantity, not insert
--    a second row (tests the UNIQUE KEY uq_cart_user_product +
--    ON DUPLICATE KEY UPDATE pattern CartDAO.addToCart relies on).
INSERT INTO cart_items (user_id, product_id, quantity)
SELECT u.user_id, p.product_id, 1
FROM users u, products p
WHERE u.email = 'db-test-user@example.com' AND p.name = 'DB Test Product'
ON DUPLICATE KEY UPDATE quantity = quantity + 1;

-- Expect exactly 1 row, quantity = 3
SELECT c.quantity FROM cart_items c
JOIN users u ON c.user_id = u.user_id
WHERE u.email = 'db-test-user@example.com';

-- 6. Place a test order + order item.
INSERT INTO orders (user_id, total_amount)
SELECT user_id, 30.00 FROM users WHERE email = 'db-test-user@example.com';

INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, subtotal)
SELECT o.order_id, p.product_id, p.name, 3, p.price, p.price * 3
FROM orders o, products p
WHERE o.user_id = (SELECT user_id FROM users WHERE email = 'db-test-user@example.com')
  AND p.name = 'DB Test Product'
ORDER BY o.order_id DESC LIMIT 1;

-- 7. Deleting the test user should cascade-delete their cart_items
--    and orders (tests ON DELETE CASCADE).
DELETE FROM users WHERE email = 'db-test-user@example.com';

-- Expect 0 rows in both:
SELECT COUNT(*) AS remaining_cart_items FROM cart_items
WHERE product_id = (SELECT product_id FROM products WHERE name = 'DB Test Product');
SELECT COUNT(*) AS remaining_orders FROM orders
WHERE user_id NOT IN (SELECT user_id FROM users);

-- ---------------------------------------------------------------
-- Review Module (V3__reviews.sql)
-- ---------------------------------------------------------------

-- 8. Set up one DELIVERED purchase to review.
INSERT INTO users (name, email, password)
VALUES ('DB Review User', 'db-review-user@example.com', '$2a$10$examplehashdoesnotworkforlogin');

INSERT INTO products (name, description, price, category, stock)
VALUES ('DB Review Product', 'Inserted by database-test.sql', 10.00, 'Test', 5);

INSERT INTO orders (user_id, total_amount, status)
SELECT user_id, 10.00, 'DELIVERED' FROM users WHERE email = 'db-review-user@example.com';

INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, subtotal, status)
SELECT o.order_id, p.product_id, p.name, 1, p.price, p.price, 'DELIVERED'
FROM orders o, products p
WHERE o.user_id = (SELECT user_id FROM users WHERE email = 'db-review-user@example.com')
  AND p.name = 'DB Review Product';

-- 9. A valid review is accepted.
INSERT INTO reviews (user_id, product_id, order_id, rating, `comment`)
SELECT o.user_id, oi.product_id, oi.order_id, 4, 'Good product, arrived on time.'
FROM orders o JOIN order_items oi ON oi.order_id = o.order_id
WHERE o.user_id = (SELECT user_id FROM users WHERE email = 'db-review-user@example.com');

-- 10. The same buyer + order + product cannot be reviewed twice
--     (UNIQUE KEY uq_reviews_user_order_product). INSERT IGNORE swallows the
--     duplicate-key error so this script keeps running.
-- Expect exactly 1 row, rating 4:
INSERT IGNORE INTO reviews (user_id, product_id, order_id, rating, `comment`)
SELECT o.user_id, oi.product_id, oi.order_id, 1, 'Second review of the same purchase.'
FROM orders o JOIN order_items oi ON oi.order_id = o.order_id
WHERE o.user_id = (SELECT user_id FROM users WHERE email = 'db-review-user@example.com');

SELECT COUNT(*) AS review_rows, MIN(rating) AS rating
FROM reviews r JOIN users u ON u.user_id = r.user_id
WHERE u.email = 'db-review-user@example.com';

-- 11. Ratings outside 1-5 must be rejected (CHECK chk_reviews_rating; enforced by
--     MySQL 8.0.16+ / MariaDB 10.2+). Uncomment ONE at a time to verify each fails:
-- INSERT INTO reviews (user_id, product_id, order_id, rating, `comment`)
-- SELECT user_id, product_id, order_id, 0, 'rating zero' FROM reviews LIMIT 1;
-- INSERT INTO reviews (user_id, product_id, order_id, rating, `comment`)
-- SELECT user_id, product_id, order_id, 6, 'rating six' FROM reviews LIMIT 1;

-- 12. A review must point at a real user / product / order (foreign keys).
--     Uncomment to verify it fails:
-- INSERT INTO reviews (user_id, product_id, order_id, rating, `comment`)
-- VALUES (999999999, 999999999, 999999999, 5, 'orphan review');

-- 13. Average rating for a product, the way the app derives it (exact counts, rounded to 1 dp).
-- Expect 4.0:
SELECT ROUND(SUM(rating * n) / SUM(n), 1) AS average_rating
FROM (SELECT rating, COUNT(*) AS n FROM reviews
      WHERE product_id = (SELECT product_id FROM products WHERE name = 'DB Review Product')
      GROUP BY rating) counts;

-- 14. Deleting the user cascades to their orders and reviews.
DELETE FROM users WHERE email = 'db-review-user@example.com';

-- Expect 0:
SELECT COUNT(*) AS remaining_reviews FROM reviews
WHERE product_id = (SELECT product_id FROM products WHERE name = 'DB Review Product');

-- Cleanup
DELETE FROM products WHERE name IN ('DB Test Product', 'DB Review Product');
