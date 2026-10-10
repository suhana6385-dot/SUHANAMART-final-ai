-- SUHANAMART migration V2 - Seller module + per-line order status.
--
-- Upgrades an EXISTING database (created from V1__initial_schema.sql, i.e. the
-- original schema.sql / suhanamart.sql) so the Seller module and the order
-- status workflow can work. Fresh installs do not need this file: schema.sql
-- and suhanamart.sql already contain these changes.
--
--   mysql -u your_mysql_username -p < database/migrations/V2__order_status.sql
--
-- What it changes (all additive - no existing data is deleted):
--
--   1. users.role          VARCHAR(20) NOT NULL DEFAULT 'BUYER'
--        Every existing account becomes a BUYER. Seller accounts are
--        created by ticking "I want to sell" on the registration form,
--        or by promoting a user manually (see the end of this file).
--
--   2. products.seller_id  INT NULL  (FK -> users.user_id, ON DELETE SET NULL)
--        Which seller owns a product. Existing products get NULL
--        ("unowned"): buyers still see and can buy them, but no seller
--        can edit or delete them until you assign an owner (see the
--        end of this file).
--
--   3. order_items.status  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
--        Fulfilment status is tracked per order line so that in an
--        order containing products from several sellers, each seller
--        only ever changes the status of their own lines.
--
--   4. orders.status       default changed from 'PLACED' to 'PENDING', and
--        existing 'PLACED' orders are relabelled 'PENDING'. From now on
--        orders.status is the roll-up of the order's line statuses (the
--        least-advanced line) and is maintained by OrderDAO whenever a
--        seller changes a status. Buyers keep reading orders.status, so
--        existing buyer order history/detail pages need no changes.
--
-- Status flow (application-level tracking only - no courier/GPS):
--     PENDING -> CONFIRMED -> SHIPPED -> DELIVERED
--
-- Safe to re-run: every step checks whether it has already been applied.

USE suhanamart;

DROP PROCEDURE IF EXISTS suhanamart_v2_add_column;
DROP PROCEDURE IF EXISTS suhanamart_v2_add_index;
DROP PROCEDURE IF EXISTS suhanamart_v2_add_fk;

DELIMITER $$

-- Adds a column only if it is not already there.
CREATE PROCEDURE suhanamart_v2_add_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
        SET @stmt = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', ddl);
        PREPARE s FROM @stmt;
        EXECUTE s;
        DEALLOCATE PREPARE s;
    END IF;
END$$

-- Adds an index only if no index with that name exists.
CREATE PROCEDURE suhanamart_v2_add_index(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN cols VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx) THEN
        SET @stmt = CONCAT('ALTER TABLE `', tbl, '` ADD INDEX `', idx, '` (', cols, ')');
        PREPARE s FROM @stmt;
        EXECUTE s;
        DEALLOCATE PREPARE s;
    END IF;
END$$

-- Adds a foreign key only if no constraint with that name exists.
CREATE PROCEDURE suhanamart_v2_add_fk(IN tbl VARCHAR(64), IN fk VARCHAR(64), IN ddl VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = tbl
                      AND CONSTRAINT_NAME = fk AND CONSTRAINT_TYPE = 'FOREIGN KEY') THEN
        SET @stmt = CONCAT('ALTER TABLE `', tbl, '` ADD CONSTRAINT `', fk, '` ', ddl);
        PREPARE s FROM @stmt;
        EXECUTE s;
        DEALLOCATE PREPARE s;
    END IF;
END$$

DELIMITER ;

-- 1. users.role ---------------------------------------------------------
CALL suhanamart_v2_add_column('users', 'role', "VARCHAR(20) NOT NULL DEFAULT 'BUYER'");

-- 2. products.seller_id -------------------------------------------------
CALL suhanamart_v2_add_column('products', 'seller_id', 'INT NULL');
CALL suhanamart_v2_add_index('products', 'idx_products_seller', '`seller_id`');
CALL suhanamart_v2_add_fk('products', 'fk_products_seller',
    'FOREIGN KEY (`seller_id`) REFERENCES `users`(`user_id`) ON DELETE SET NULL');

-- 3. order_items.status -------------------------------------------------
CALL suhanamart_v2_add_column('order_items', 'status', "VARCHAR(20) NOT NULL DEFAULT 'PENDING'");
CALL suhanamart_v2_add_index('order_items', 'idx_order_items_status', '`status`');

-- 4. orders.status: new default + relabel legacy rows -------------------
ALTER TABLE orders ALTER COLUMN status SET DEFAULT 'PENDING';
UPDATE orders SET status = 'PENDING' WHERE status = 'PLACED';

DROP PROCEDURE IF EXISTS suhanamart_v2_add_column;
DROP PROCEDURE IF EXISTS suhanamart_v2_add_index;
DROP PROCEDURE IF EXISTS suhanamart_v2_add_fk;

-- ---------------------------------------------------------------
-- OPTIONAL manual steps (left commented out on purpose)
-- ---------------------------------------------------------------
-- Promote an existing account to a seller:
--   UPDATE users SET role = 'SELLER' WHERE email = 'seller@example.com';
--
-- Give the original demo products (which have no owner) to that seller:
--   UPDATE products
--      SET seller_id = (SELECT user_id FROM users WHERE email = 'seller@example.com')
--    WHERE seller_id IS NULL;
