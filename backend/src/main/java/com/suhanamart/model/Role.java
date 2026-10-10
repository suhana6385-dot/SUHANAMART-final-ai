package com.suhanamart.model;

/**
 * The account types a user can have (users.role column).
 *
 * Anything that is not recognised is treated as BUYER - the least
 * privileged role - so a missing or unexpected database value can never
 * accidentally grant seller OR admin access. ADMIN accounts are never
 * created through registration (see AuthService.register) - only by a
 * seed script (database/migrations/V4__admin_and_account_status.sql) or
 * a direct database update.
 */
public enum Role {
    BUYER,
    SELLER,
    ADMIN;

    public static Role fromString(String value) {
        if (value != null) {
            for (Role role : values()) {
                if (role.name().equalsIgnoreCase(value.trim())) {
                    return role;
                }
            }
        }
        return BUYER;
    }
}
