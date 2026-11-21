package com.durgamedicals.dao;

import com.durgamedicals.model.User;
import com.durgamedicals.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * UserDAO.java
 * Data Access Object (DAO) for the 'users' table in MySQL.
 * Handles user account creation, lookup, and existence checking for administrative setup.
 *
 * Database Table: users
 * Columns:
 *   - user_id       (INT, PK, AUTO_INCREMENT)
 *   - username      (VARCHAR(50), NOT NULL, UNIQUE)
 *   - password_hash (VARCHAR(255), NOT NULL) - Salted, hashed password (never plain-text)
 *   - role          (VARCHAR(20), NULL, DEFAULT 'admin')
 */
public class UserDAO {

    // Expected cryptographic constraints matching PasswordUtil.java format
    private static final String REQUIRED_HASH_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int MIN_ALLOWED_ITERATIONS = 600000;
    private static final int MAX_ALLOWED_ITERATIONS = 1000000;
    private static final int EXPECTED_SALT_BYTES = 16;
    private static final int EXPECTED_HASH_BYTES = 32;

    // Allowed application roles (stored in normalized lowercase)
    private static final Set<String> ALLOWED_ROLES = Set.of("admin", "pharmacist", "cashier");

    /**
     * Creates a new user in the database using a transaction.
     * Inserts the user record and assigns the generated user_id upon commit.
     *
     * Security Validations:
     *   - Password hash is structurally validated against expected PBKDF2 parameters.
     *     Callers MUST use PasswordUtil.hashPassword() to generate valid hashes.
     *   - Plain-text passwords and malformed hashes are rejected before touching the DB.
     *   - Role must be explicitly provided and must be one of: "admin", "pharmacist", or "cashier".
     *
     * @param user The User object containing username, password_hash, and role.
     * @return true if the user was successfully created and assigned an ID.
     * @throws IllegalArgumentException if the user fails domain or security validation.
     * @throws IllegalStateException    if a generated ID could not be retrieved, or on environment error.
     * @throws SQLException             if a database access error occurs.
     */
    public boolean createUser(User user) throws SQLException {
        validateUser(user, "createUser");

        String sql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // Start transaction

            int generatedUserId = -1;

            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, user.getUsername().trim());
                stmt.setString(2, user.getPasswordHash());
                stmt.setString(3, user.getRole().trim().toLowerCase(Locale.ROOT));

                int affectedRows = stmt.executeUpdate();
                if (affectedRows <= 0) {
                    conn.rollback();
                    return false;
                }

                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        generatedUserId = generatedKeys.getInt(1);
                    }
                }

                if (generatedUserId <= 0) {
                    // Roll back to ensure no record is committed without a verified ID
                    conn.rollback();
                    throw new IllegalStateException("Database Error [createUser]: User inserted but MySQL failed to return a valid generated ID. Transaction rolled back.");
                }
            }

            conn.commit();
            user.setUserId(generatedUserId);
            // Normalize role on the in-memory object to lowercase matching database storage
            user.setRole(user.getRole().trim().toLowerCase(Locale.ROOT));
            return true;

        } catch (SQLException e) {
            rollbackQuietly(conn, "createUser");
            logSQLException("createUser", e);
            throw e;
        } catch (IllegalStateException e) {
            rollbackQuietly(conn, "createUser");
            System.err.println("Configuration/State Error [createUser]: " + e.getMessage());
            throw e;
        } finally {
            closeConnectionQuietly(conn);
        }
    }

    /**
     * Finds a user account by username.
     * Note: String comparison behavior (case-sensitivity) depends on the MySQL collation
     * configured on the 'username' column (e.g. utf8mb4_general_ci is case-insensitive).
     *
     * Distinguishability:
     *   - Returns User object: user was found.
     *   - Returns null: query completed successfully and no user matches that username.
     *   - Throws SQLException: a database connection or query execution error occurred.
     *
     * @param username The username to look up.
     * @return The matching User object, or null if no such user exists.
     * @throws IllegalArgumentException if username is null or blank.
     * @throws SQLException             if a database error occurs.
     */
    public User findByUsername(String username) throws SQLException {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Validation Error [findByUsername]: Username cannot be null or blank.");
        }

        String sql = "SELECT user_id, username, password_hash, role FROM users WHERE username = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username.trim());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }

            return null; // Successfully verified: no user exists with this username

        } catch (SQLException e) {
            logSQLException("findByUsername", e);
            throw e;
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [findByUsername]: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Checks whether any user accounts exist in the database.
     *
     * Distinguishability:
     *   - Returns true: at least one user exists in the database.
     *   - Returns false: table was queried successfully and is confirmed empty.
     *   - Throws SQLException: database is offline or unreachable (NOT an empty table).
     *
     * @return true if at least one user exists, false if table is genuinely empty.
     * @throws SQLException if a database error occurs.
     */
    public boolean hasAnyUsers() throws SQLException {
        String sql = "SELECT 1 FROM users LIMIT 1";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            return rs.next(); // True if 1+ rows exist, false if table is empty

        } catch (SQLException e) {
            logSQLException("hasAnyUsers", e);
            throw e; // Propagate so caller knows DB failed rather than thinking table is empty
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [hasAnyUsers]: " + e.getMessage());
            throw e;
        }
    }

    // ── Private Helper Methods ────────────────────────────────────────────

    /**
     * Validates domain constraints on the User object prior to database operations.
     */
    private void validateUser(User user, String operation) {
        if (user == null) {
            throw new IllegalArgumentException("Validation Error [" + operation + "]: User object cannot be null.");
        }

        // Username validation (VARCHAR(50), NOT NULL)
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Validation Error [" + operation + "]: Username cannot be null or blank.");
        }
        if (user.getUsername().trim().length() > 50) {
            throw new IllegalArgumentException("Validation Error [" + operation + "]: Username exceeds maximum length of 50 characters.");
        }

        // Password hash structural validation
        validatePasswordHash(user.getPasswordHash(), operation);

        // Strict Role validation: must be non-null, non-blank, and exactly one of the allowed roles
        if (user.getRole() == null || user.getRole().trim().isEmpty()) {
            throw new IllegalArgumentException("Validation Error [" + operation + "]: Role must be explicitly specified and cannot be null or blank.");
        }

        String normalizedRole = user.getRole().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(normalizedRole)) {
            throw new IllegalArgumentException("Validation Error [" + operation + "]: Invalid role '" + user.getRole() + "'. Allowed roles are: " + ALLOWED_ROLES);
        }
        if (normalizedRole.length() > 20) {
            throw new IllegalArgumentException("Validation Error [" + operation + "]: Role exceeds maximum length of 20 characters.");
        }
    }

    /**
     * Performs structural validation on the password hash string to verify it adheres
     * to the expected algorithm, iteration range, salt length, and derived hash length.
     *
     * Note: Structural validation checks format conformance, but does not prove cryptographic
     * origin. Callers must always use PasswordUtil.hashPassword() to generate hashes.
     * Plain-text passwords and malformed strings are rejected.
     */
    private void validatePasswordHash(String hash, String operation) {
        if (hash == null || hash.trim().isEmpty()) {
            throw new IllegalArgumentException("Validation Error [" + operation + "]: Password hash cannot be null or blank.");
        }
        if (hash.length() > 255) {
            throw new IllegalArgumentException("Validation Error [" + operation + "]: Password hash exceeds maximum length of 255 characters.");
        }

        String[] parts = hash.split(":");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Security Error [" + operation + "]: Invalid password hash format. Expected 4 colon-separated parts (algorithm:iterations:salt:hash). Plain-text passwords are not permitted. Use PasswordUtil.hashPassword() to generate hashes.");
        }

        String algorithm = parts[0];
        int iterations;
        byte[] salt;
        byte[] derivedHash;

        try {
            iterations = Integer.parseInt(parts[1]);
            salt = Base64.getDecoder().decode(parts[2]);
            derivedHash = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Security Error [" + operation + "]: Malformed numerical or Base64 components in password hash: " + e.getMessage());
        }

        if (!REQUIRED_HASH_ALGORITHM.equals(algorithm)) {
            throw new IllegalArgumentException("Security Error [" + operation + "]: Unsupported hash algorithm (" + algorithm + "). Expected: " + REQUIRED_HASH_ALGORITHM);
        }

        if (iterations < MIN_ALLOWED_ITERATIONS || iterations > MAX_ALLOWED_ITERATIONS) {
            throw new IllegalArgumentException("Security Error [" + operation + "]: Iteration count (" + iterations + ") is outside permitted range [" + MIN_ALLOWED_ITERATIONS + ", " + MAX_ALLOWED_ITERATIONS + "].");
        }

        if (salt.length != EXPECTED_SALT_BYTES) {
            throw new IllegalArgumentException("Security Error [" + operation + "]: Invalid salt length (" + salt.length + " bytes). Expected: " + EXPECTED_SALT_BYTES + " bytes.");
        }

        if (derivedHash.length != EXPECTED_HASH_BYTES) {
            throw new IllegalArgumentException("Security Error [" + operation + "]: Invalid derived hash length (" + derivedHash.length + " bytes). Expected: " + EXPECTED_HASH_BYTES + " bytes.");
        }
    }

    /**
     * Maps the current row of a ResultSet to a User model object.
     */
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        int userId = rs.getInt("user_id");
        String username = rs.getString("username");
        String passwordHash = rs.getString("password_hash");
        String role = rs.getString("role");

        return new User(userId, username, passwordHash, role);
    }

    private void rollbackQuietly(Connection conn, String operation) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                System.err.println("Rollback Warning [" + operation + "]: Failed to rollback transaction: " + ex.getMessage());
            }
        }
    }

    private void closeConnectionQuietly(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException ex) {
                System.err.println("Close Connection Warning: " + ex.getMessage());
            }
        }
    }

    /**
     * Standardized logger for SQL exceptions.
     */
    private void logSQLException(String operation, SQLException e) {
        System.err.println(String.format(
                "Database Error [%s] - Error Code: %d, SQL State: %s, Message: %s",
                operation,
                e.getErrorCode(),
                (e.getSQLState() != null ? e.getSQLState() : "N/A"),
                e.getMessage()
        ));
    }
}
