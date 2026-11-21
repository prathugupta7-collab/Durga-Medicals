package com.durgamedicals.service;

import com.durgamedicals.dao.UserDAO;
import com.durgamedicals.model.User;
import com.durgamedicals.util.PasswordUtil;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * UserService.java
 * Service layer class handling user account lifecycle operations.
 *
 * Security Architecture & Session Notes:
 *   1. Initial Setup: 'createInitialAdmin' is the ONLY public method permitted to create
 *      an account without an administrator parameter. It refuses execution if any accounts
 *      already exist in the database.
 *   2. Account Provisioning Safeguard: 'createUser' checks that the supplied 'requestingAdmin'
 *      has the 'admin' role. Note: Checking fields on a mutable User object serves as an
 *      application-level parameter safeguard; it does not independently cryptographically
 *      prove session authenticity. In the full desktop UI architecture, this object must
 *      originate from a trusted, authenticated session context (e.g. established via LoginService).
 *   3. Password Protection: Plain-text passwords are never logged, are never stored in
 *      the database as plain text, and are cryptographically hashed using PasswordUtil
 *      before a User object is constructed and passed to UserDAO.
 */
public class UserService {

    // Permitted roles in the application, matching UserDAO constraints
    private static final Set<String> ALLOWED_ROLES = Set.of("admin", "pharmacist", "cashier");
    private static final String ROLE_ADMIN = "admin";

    // MySQL error code for duplicate key / unique constraint violation
    private static final int MYSQL_DUPLICATE_ENTRY_ERROR_CODE = 1062;

    // JVM-wide class-level lock object to synchronize initial setup across all UserService instances
    private static final Object INITIAL_SETUP_LOCK = new Object();

    private final UserDAO userDAO;

    /**
     * Default constructor initializes its own UserDAO dependency.
     */
    public UserService() {
        this.userDAO = new UserDAO();
    }

    /**
     * Dependency injection constructor (useful for unit testing or custom DAO instances).
     *
     * @param userDAO Non-null UserDAO instance.
     */
    public UserService(UserDAO userDAO) {
        this.userDAO = Objects.requireNonNull(userDAO, "UserDAO cannot be null.");
    }

    /**
     * Creates the initial system administrator account during first-time application launch.
     * This is the only method intentionally designed for first-time administrator setup without an existing administrator session. The createUser() method requires a User object from the application's trusted session context, but its role check alone does not prove session authenticity.
     *
     * Concurrency & Execution Rules:
     *   - Synchronized on a private static lock (INITIAL_SETUP_LOCK) so that concurrent setup calls
     *     across different threads or different UserService instances within the same JVM process
     *     are mutually excluded and serialized.
     *   - Note: This class-level lock protects only a single JVM process. Coordinating setup
     *     across separate application instances or physical computers connecting to the same
     *     MySQL database requires database-level coordination (such as MySQL locks or atomic constraints).
     *   - Checks userDAO.hasAnyUsers(). If ANY account already exists, execution is blocked.
     *   - Forces the created role strictly to 'admin'.
     *
     * @param username Candidate admin username (will be trimmed).
     * @param password Candidate admin password (spaces preserved).
     * @return UserCreationResult containing the created User on success, or user-friendly failure message.
     * @throws UserServiceException if a database communication or configuration error occurs.
     */
    public UserCreationResult createInitialAdmin(String username, String password) throws UserServiceException {
        synchronized (INITIAL_SETUP_LOCK) {
            // 1. Check if the system is genuinely uninitialized
            boolean usersExist;
            try {
                usersExist = userDAO.hasAnyUsers();
            } catch (SQLException e) {
                throw new UserServiceException("Unable to verify user setup status due to a database error.", e);
            } catch (IllegalStateException e) {
                throw new UserServiceException("Database configuration error: " + e.getMessage(), e);
            }

            if (usersExist) {
                return UserCreationResult.failure("Initial administrator setup has already been completed. New accounts must be created by an authenticated administrator.");
            }

            // 2. Execute internal user creation with fixed 'admin' role
            return executeUserCreation(username, password, ROLE_ADMIN);
        }
    }

    /**
     * Creates a new user account (admin, pharmacist, or cashier).
     * Requires a requesting user object whose role field is set to 'admin'. This role check is an application-level safeguard and does not independently authenticate the user.
     *
     * Safeguard Note:
     *   - Verifies requestingAdmin != null and requestingAdmin.getRole() == 'admin'.
     *   - Callers in the UI layer must pass the trusted User object obtained from LoginService upon login.
     *
     * @param requestingAdmin The administrator performing this action (obtained from active session).
     * @param username        Plain-text username for the new account (will be trimmed).
     * @param password        Plain-text candidate password (spaces preserved, never trimmed).
     * @param role            Requested role ("admin", "pharmacist", or "cashier").
     * @return UserCreationResult containing the created User on success, or user-friendly failure message.
     * @throws UserServiceException if a database communication or configuration error occurs.
     */
    public UserCreationResult createUser(User requestingAdmin, String username, String password, String role) throws UserServiceException {
        // 1. Basic Application-Level Role Safeguard
        if (requestingAdmin == null) {
            return UserCreationResult.failure("Unauthorized: An administrator session object is required to create user accounts.");
        }

        if (requestingAdmin.getRole() == null || !ROLE_ADMIN.equalsIgnoreCase(requestingAdmin.getRole().trim())) {
            return UserCreationResult.failure("Access Denied: Only users with the 'admin' role are permitted to create accounts.");
        }

        // 2. Validate requested role
        if (role == null || role.trim().isEmpty()) {
            return UserCreationResult.failure("Role must be specified.");
        }

        String normalizedRole = role.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(normalizedRole)) {
            return UserCreationResult.failure("Invalid role. Permitted roles are: admin, pharmacist, cashier.");
        }

        // 3. Delegate to internal creation logic
        return executeUserCreation(username, password, normalizedRole);
    }

    /**
     * Checks if any users exist in the system.
     * Used by the UI at startup to decide whether to show the Initial Admin Setup dialog or the Login screen.
     *
     * @return true if 1+ users exist, false if table is confirmed empty.
     * @throws UserServiceException if database is unreachable.
     */
    public boolean hasAnyUsers() throws UserServiceException {
        try {
            return userDAO.hasAnyUsers();
        } catch (SQLException e) {
            throw new UserServiceException("Unable to check user setup status: database is unreachable.", e);
        } catch (IllegalStateException e) {
            throw new UserServiceException("Database configuration error: " + e.getMessage(), e);
        }
    }

    // ── Internal Helpers ──────────────────────────────────────────────────

    /**
     * Internal implementation for input validation, password hashing, and DAO persistence.
     * Plain-text passwords are never logged, are never stored in the database as plain text,
     * and are hashed using PasswordUtil before a User object is constructed and passed to UserDAO.
     */
    private UserCreationResult executeUserCreation(String username, String password, String normalizedRole) throws UserServiceException {
        // 1. Validate username
        if (username == null || username.trim().isEmpty()) {
            return UserCreationResult.failure("Username cannot be empty.");
        }

        String trimmedUsername = username.trim();
        if (trimmedUsername.length() > 50) {
            return UserCreationResult.failure("Username cannot exceed 50 characters.");
        }

        // 2. Validate password (preserving whitespace)
        if (password == null || password.isEmpty()) {
            return UserCreationResult.failure("Password cannot be empty.");
        }

        // 3. Hash password securely using PBKDF2WithHmacSHA256 (600,000 rounds)
        String passwordHash;
        try {
            passwordHash = PasswordUtil.hashPassword(password);
        } catch (IllegalArgumentException e) {
            return UserCreationResult.failure("Invalid password: " + e.getMessage());
        }

        // 4. Construct User model with hashed credentials (plain-text password is not passed to DAO)
        User newUser = new User(trimmedUsername, passwordHash, normalizedRole);

        // 5. Persist to database via UserDAO
        try {
            boolean created = userDAO.createUser(newUser);
            if (created) {
                return UserCreationResult.success(newUser);
            } else {
                return UserCreationResult.failure("Failed to create user account.");
            }
        } catch (SQLException e) {
            // Check for duplicate username (UNIQUE constraint violation)
            if (e.getErrorCode() == MYSQL_DUPLICATE_ENTRY_ERROR_CODE) {
                return UserCreationResult.failure("A user with username '" + trimmedUsername + "' already exists.");
            }
            throw new UserServiceException("Failed to save user account due to a database error.", e);
        } catch (IllegalStateException e) {
            throw new UserServiceException("Database configuration error: " + e.getMessage(), e);
        }
    }

    // ── Supporting Result & Exception Types ────────────────────────────────

    /**
     * Encapsulates the outcome of a user creation operation.
     */
    public static class UserCreationResult {
        private final boolean success;
        private final User user;
        private final String errorMessage;

        private UserCreationResult(boolean success, User user, String errorMessage) {
            this.success = success;
            this.user = user;
            this.errorMessage = errorMessage;
        }

        public static UserCreationResult success(User user) {
            return new UserCreationResult(true, Objects.requireNonNull(user, "User cannot be null on success."), null);
        }

        public static UserCreationResult failure(String errorMessage) {
            return new UserCreationResult(false, null, errorMessage != null ? errorMessage : "Operation failed.");
        }

        public boolean isSuccess() {
            return success;
        }

        public User getUser() {
            return user;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Custom exception thrown when database or configuration errors prevent completing user management operations.
     */
    public static class UserServiceException extends Exception {
        private static final long serialVersionUID = 1L;

        public UserServiceException(String message) {
            super(message);
        }

        public UserServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
