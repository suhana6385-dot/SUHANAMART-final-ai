-- SUHANAMART migration V4 - Admin module + account enable/disable.
--
-- Upgrades an EXISTING database (created from V1 + V2 + V3) so the Admin
-- module can work. Fresh installs do not need this file: schema.sql and
-- suhanamart.sql already contain these changes.
--
--   mysql -u your_mysql_username -p < database/migrations/V4__admin_and_account_status.sql
--
-- What it changes (all additive - no existing data is deleted):
--
--   1. users.account_status  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
--        Lets an admin disable an account (ACTIVE / DISABLED). A
--        disabled account fails login with the same generic "Invalid
--        email or password" message as a wrong password - it is never
--        told why. Checked once, at login time (AuthService); an
--        already-open session for an account disabled mid-session stays
--        valid until that session ends or the user logs out again.
--
--   2. One ADMIN account is seeded, ONLY if no admin account exists yet
--      (so this migration is safe to re-run and never creates a
--      duplicate or overwrites an admin you already created/renamed):
--
--          email:    admin@suhanamart.com
--          password: ChangeMe_Admin#2026
--
--      The password is never stored in plain text - the hash below was
--      produced by the project's own hashing algorithm (jBCrypt / BCrypt,
--      the same library PasswordUtil.hash() uses), exactly as if you had
--      registered an account and then changed its role to ADMIN by hand.
--      There is no "forgot password" or "change password" feature in
--      this project yet, so change this password directly in the
--      database (UPDATE users SET password = ... WHERE email =
--      'admin@suhanamart.com', with a hash from PasswordUtil.hash())
--      immediately after your first login, or edit the email/password
--      below before running this script.
--
-- ADMIN accounts are never created by the registration form (see
-- AuthService.register, which only ever writes BUYER or SELLER) - only
-- by this script or a direct database update.
--
-- Safe to re-run: every step checks whether it has already been applied.

USE suhanamart;

DROP PROCEDURE IF EXISTS suhanamart_v4_add_column;

DELIMITER $$

CREATE PROCEDURE suhanamart_v4_add_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
        SET @stmt = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', ddl);
        PREPARE s FROM @stmt;
        EXECUTE s;
        DEALLOCATE PREPARE s;
    END IF;
END$$

DELIMITER ;

-- 1. users.account_status ------------------------------------------------
CALL suhanamart_v4_add_column('users', 'account_status', "VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'");

DROP PROCEDURE IF EXISTS suhanamart_v4_add_column;

-- 2. Seed exactly one admin account, only if none exists yet -------------
INSERT INTO users (name, email, password, role, account_status)
SELECT 'SUHANAMART Admin', 'admin@suhanamart.com',
       '$2a$10$2/eFU5oY.xOngW6TmhwhiuJDUt2J8ol6Z.K/KCz2i5Q3NB1lJkPu6',
       'ADMIN', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE role = 'ADMIN');

-- ---------------------------------------------------------------
-- OPTIONAL manual steps (left commented out on purpose)
-- ---------------------------------------------------------------
-- Promote an existing account to admin instead of using the seeded one:
--   UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com';
--
-- Disable / re-enable an account by hand:
--   UPDATE users SET account_status = 'DISABLED' WHERE email = 'someone@example.com';
--   UPDATE users SET account_status = 'ACTIVE'   WHERE email = 'someone@example.com';
