package com.durgamedicals.model;

/**
 * User.java
 * Model class representing an application user (e.g., admin, pharmacist, cashier).
 * Maps directly to the 'users' table in the MySQL database.
 *
 * Database Table: users
 * Columns:
 *   - user_id       (INT, PK, AUTO_INCREMENT)
 *   - username      (VARCHAR(50), NOT NULL, UNIQUE)
 *   - password_hash (VARCHAR(255), NOT NULL) - Secure hashed password (never plain-text)
 *   - role          (VARCHAR(20), NULL, DEFAULT 'admin')
 *
 * Note: Password hashing and authentication logic will be handled separately in the DAO/service layer.
 * This class serves purely as a data container (DTO).
 */
public class User {

    // ── Private Fields ────────────────────────────────────────────────────
    private int userId;
    private String username;
    private String passwordHash;
    private String role;

    // ── Constructors ──────────────────────────────────────────────────────

    /**
     * Default no-argument constructor.
     * Useful when creating an empty user object and filling fields via setters.
     */
    public User() {
    }

    /**
     * Constructor for creating a NEW user (before inserting into the database).
     * Note: userId is omitted because the database generates it automatically (AUTO_INCREMENT).
     */
    public User(String username, String passwordHash, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /**
     * Full constructor (for reading an EXISTING user from the database).
     */
    public User(int userId, String username, String passwordHash, String role) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    // ── Getters and Setters ────────────────────────────────────────────────

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    // ── toString Method ───────────────────────────────────────────────────

    /**
     * String representation of User.
     * Security best practice: Omits the actual passwordHash value from toString()
     * to prevent sensitive credential hashes from leaking into system logs.
     */
    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
}
