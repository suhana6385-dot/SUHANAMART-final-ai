-- SUHANAMART optional demo seed data.
--
-- Run this AFTER schema.sql (it needs the products table to exist).
-- Entirely optional and safe to delete/skip - only inserts sample
-- products if the products table is currently empty, so re-running
-- this script will not create duplicates.
--
--   mysql -u your_mysql_username -p suhanamart < database/seed-data.sql

USE suhanamart;

INSERT INTO products (name, description, price, category, image_url, stock)
SELECT * FROM (
    SELECT 'Wireless Mouse'      AS name, 'Ergonomic 2.4GHz wireless mouse.'      AS description, 599.00  AS price, 'Electronics' AS category, NULL AS image_url, 40 AS stock UNION ALL
    SELECT 'Cotton T-Shirt',              'Soft everyday cotton t-shirt.',                 399.00,          'Apparel',                NULL,             75 UNION ALL
    SELECT 'Stainless Water Bottle',      '1-litre insulated steel bottle.',               499.00,          'Home & Kitchen',         NULL,             60 UNION ALL
    SELECT 'Notebook (Pack of 3)',        'Ruled 200-page notebooks, pack of 3.',          249.00,          'Stationery',             NULL,            100
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM products);
