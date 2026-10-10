-- SUHANAMART migration V1 - initial schema (the original, pre-Seller structure).
--
-- This is the schema exactly as first shipped: five tables (users, products,
-- cart_items, orders, order_items) WITHOUT the Seller-module columns
-- (users.role, products.seller_id, order_items.status). Those arrive in
-- V2__order_status.sql, and the Review Module arrives in V3__reviews.sql.
--
-- Run the migrations in order on a NEW, empty database:
--
--   mysql -u your_mysql_username -p < database/migrations/V1__initial_schema.sql
--   mysql -u your_mysql_username -p < database/migrations/V2__order_status.sql
--   mysql -u your_mysql_username -p < database/migrations/V3__reviews.sql
--
-- (Or run schema.sql / suhanamart.sql once - they already contain the result of
-- V1 + V2 + V3 for fresh installs.)
--
-- Safe to re-run - every statement uses IF NOT EXISTS.

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
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
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
    status         VARCHAR(30)   NOT NULL DEFAULT 'PLACED',
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

    CONSTRAINT fk_order_items_order   FOREIGN KEY (order_id)   REFERENCES orders(order_id)     ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(product_id)
);
