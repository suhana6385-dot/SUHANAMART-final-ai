package com.suhanamart.model;

import java.sql.Timestamp;

/**
 * A user's public profile data. Deliberately has no password field -
 * the password hash never leaves com.suhanamart.dao.UserDAO (see
 * UserDAO's private AuthRecord, used only for login verification).
 *
 * role and accountStatus are null for every existing caller (profile
 * page, findUserByEmail/findUserById) - they are filled in only by
 * AdminDAO.listUsers(), the one place that needs them, through the
 * second constructor below. This keeps every other use of this class
 * unchanged.
 */
public class User {
    private final int userId;
    private final String name;
    private final String email;
    private final Timestamp createdAt;
    private final Role role;
    private final AccountStatus accountStatus;

    public User(int userId, String name, String email, Timestamp createdAt) {
        this(userId, name, email, createdAt, null, null);
    }

    public User(int userId, String name, String email, Timestamp createdAt,
                Role role, AccountStatus accountStatus) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.createdAt = createdAt;
        this.role = role;
        this.accountStatus = accountStatus;
    }

    public int getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Timestamp getCreatedAt() { return createdAt; }
    /** Null unless loaded by AdminDAO.listUsers(). */
    public Role getRole() { return role; }
    /** Null unless loaded by AdminDAO.listUsers(). */
    public AccountStatus getAccountStatus() { return accountStatus; }
}
