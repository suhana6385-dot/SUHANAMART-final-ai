package com.suhanamart.model;

/**
 * Whether a user account may log in (users.account_status column,
 * added by database/migrations/V4__admin_and_account_status.sql).
 *
 * Only an admin can change this (AdminService); it is checked at
 * login time (AuthService.authenticate) - a DISABLED account fails
 * login with the same generic message as a wrong password, so a
 * disabled account can never be distinguished from one that simply
 * doesn't exist.
 */
public enum AccountStatus {

    ACTIVE,
    DISABLED;

    /** TOLERANT read for values from the database: anything unrecognised (including null) is ACTIVE. */
    public static AccountStatus fromDb(String value) {
        if (value != null) {
            for (AccountStatus status : values()) {
                if (status.name().equalsIgnoreCase(value.trim())) {
                    return status;
                }
            }
        }
        return ACTIVE;
    }
}
