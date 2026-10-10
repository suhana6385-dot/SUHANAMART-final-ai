-- SUHANAMART database - combined script (schema + optional seed data).
-- Equivalent to running schema.sql then seed-data.sql. Safe to re-run -
-- every statement uses IF NOT EXISTS.
--
--   mysql -u your_mysql_username -p < database/suhanamart.sql

--
-- ALREADY HAVE A DATABASE from an earlier version? CREATE TABLE IF NOT EXISTS will NOT
-- alter existing tables. Run database/migrations/V2__order_status.sql (and then
-- V3__reviews.sql) instead.

CREATE DATABASE IF NOT EXISTS suhanamart;

USE suhanamart;

-- ---------------------------------------------------------------
-- users
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id    INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100)  NOT NULL,
    email      VARCHAR(150)  NOT NULL UNIQUE,
    password   VARCHAR(255)  NOT NULL,
    role       VARCHAR(20)   NOT NULL DEFAULT 'BUYER',   -- BUYER | SELLER | ADMIN
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | DISABLED (V4)
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Exactly one ADMIN account (see migrations/V4__admin_and_account_status.sql for details
-- on the password / how to change it). Safe to re-run - never creates a duplicate.
INSERT INTO users (name, email, password, role, account_status)
SELECT 'SUHANAMART Admin', 'admin@suhanamart.com',
       '$2a$10$2/eFU5oY.xOngW6TmhwhiuJDUt2J8ol6Z.K/KCz2i5Q3NB1lJkPu6',
       'ADMIN', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE role = 'ADMIN');

-- ---------------------------------------------------------------
-- products
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products (
    product_id  INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150)    NOT NULL,
    description TEXT,
    price       DECIMAL(10,2)   NOT NULL,
    category    VARCHAR(80)     NOT NULL DEFAULT 'General',
    image_url   VARCHAR(500),
    stock       INT             NOT NULL DEFAULT 0,
    seller_id   INT             NULL,   -- owning seller; NULL = unowned (e.g. demo seed data)
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_products_seller (seller_id),
    CONSTRAINT fk_products_seller FOREIGN KEY (seller_id) REFERENCES users(user_id) ON DELETE SET NULL
);

-- ---------------------------------------------------------------
-- cart_items - one row per (user, product); quantity updated in place
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cart_items (
    cart_item_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id      INT NOT NULL,
    product_id   INT NOT NULL,
    quantity     INT NOT NULL DEFAULT 1,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uq_cart_user_product (user_id, product_id),
    CONSTRAINT fk_cart_user    FOREIGN KEY (user_id)    REFERENCES users(user_id)       ON DELETE CASCADE,
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------
-- orders
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    order_id       INT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT NOT NULL,
    total_amount   DECIMAL(10,2) NOT NULL,
    payment_method VARCHAR(80)   NOT NULL DEFAULT 'Demo Payment (Cash on Delivery)',
    status         VARCHAR(30)   NOT NULL DEFAULT 'PENDING',   -- roll-up of order_items.status
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------
-- order_items - snapshot of price at time of purchase
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_items (
    order_item_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id      INT NOT NULL,
    product_id    INT NOT NULL,
    product_name  VARCHAR(150) NOT NULL,
    quantity      INT NOT NULL,
    unit_price    DECIMAL(10,2) NOT NULL,
    subtotal      DECIMAL(10,2) NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',   -- PENDING | CONFIRMED | SHIPPED | DELIVERED

    INDEX idx_order_items_status (status),
    CONSTRAINT fk_order_items_order   FOREIGN KEY (order_id)   REFERENCES orders(order_id)     ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(product_id)
);

-- ---------------------------------------------------------------
-- reviews - buyer ratings (1-5) + comments on delivered purchases.
-- One review per (buyer, order, product); see migrations/V3__reviews.sql
-- for the full column-by-column description.
-- ---------------------------------------------------------------
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

-- ---------------------------------------------------------------
-- OPTIONAL demo seed data (see seed-data.sql for the standalone version)
-- ---------------------------------------------------------------
USE suhanamart;

INSERT INTO products (name, description, price, category, image_url, stock)
SELECT * FROM (
    SELECT 'Wireless Mouse'      AS name, 'Ergonomic 2.4GHz wireless mouse.'      AS description, 599.00  AS price, 'Electronics' AS category, NULL AS image_url, 40 AS stock UNION ALL
    SELECT 'Cotton T-Shirt',              'Soft everyday cotton t-shirt.',                 399.00,          'Apparel',                NULL,             75 UNION ALL
    SELECT 'Stainless Water Bottle',      '1-litre insulated steel bottle.',               499.00,          'Home & Kitchen',         NULL,             60 UNION ALL
    SELECT 'Notebook (Pack of 3)',        'Ruled 200-page notebooks, pack of 3.',          249.00,          'Stationery',             NULL,            100
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM products);
