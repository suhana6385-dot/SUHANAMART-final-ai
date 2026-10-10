-- SUHANAMART migration V3 - Review Module (F8).
--
-- Adds the `reviews` table so buyers can rate (1-5) and comment on products
-- they have actually received. Fresh installs do not need this file:
-- schema.sql and suhanamart.sql already contain it.
--
--   mysql -u your_mysql_username -p < database/migrations/V3__reviews.sql
--
-- Requires the tables from V1 + V2 (users, products, orders, order_items).
--
-- What it does (purely additive - no existing table or row is modified):
--
--   reviews
--     review_id   INT AUTO_INCREMENT        primary key
--     user_id     INT NOT NULL              the buyer      -> users.user_id
--     product_id  INT NOT NULL              the product    -> products.product_id
--     order_id    INT NOT NULL              the order it was bought in -> orders.order_id
--     rating      TINYINT NOT NULL          1..5 (CHECK constraint; also validated in Java)
--     comment     VARCHAR(1000) NOT NULL    review text
--     created_at  TIMESTAMP                 when the review was written
--     updated_at  TIMESTAMP                 refreshed automatically if a row is ever modified
--
--   Duplicate prevention: UNIQUE (user_id, order_id, product_id) - one review
--   per buyer per product per order, enforced by the database itself even if
--   two requests race each other.
--
--   Foreign keys are ON DELETE CASCADE: deleting a user, order or product
--   removes its reviews instead of leaving orphans. (Products that appear in
--   an order cannot be deleted by sellers anyway, so in normal use this only
--   matters for cleanup of test data.)
--
--   Index idx_reviews_product_created serves the product-details page:
--   "all reviews of product X, newest first" and the average-rating query.
--
-- Which purchases may be reviewed (order must be DELIVERED, buyer must own the
-- order, product must be in it, ...) is business logic and lives in
-- com.suhanamart.service.ReviewService - not in this script.
--
-- Safe to re-run: CREATE TABLE IF NOT EXISTS leaves an existing table alone.

USE suhanamart;

CREATE TABLE IF NOT EXISTS reviews (
    review_id  INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT           NOT NULL,
    product_id INT           NOT NULL,
    order_id   INT           NOT NULL,
    rating     TINYINT       NOT NULL,
    `comment`  VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_reviews_user_order_product (user_id, order_id, product_id),
    INDEX idx_reviews_product_created (product_id, created_at),
    INDEX idx_reviews_order (order_id),

    CONSTRAINT chk_reviews_rating  CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_reviews_user     FOREIGN KEY (user_id)    REFERENCES users(user_id)       ON DELETE CASCADE,
    CONSTRAINT fk_reviews_product  FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_order    FOREIGN KEY (order_id)   REFERENCES orders(order_id)     ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
