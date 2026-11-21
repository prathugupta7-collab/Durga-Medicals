package com.durgamedicals.service;

import com.durgamedicals.dao.UserDAO;
import com.durgamedicals.model.User;
import com.durgamedicals.util.PasswordUtil;

import java.sql.SQLException;
import java.util.Objects;

/**
 * LoginService.java
 * Service layer class handling user authentication and credential verification.
 * Coordinates between UI login screens, UserDAO, and PasswordUtil.
 */
public class LoginService {

    private final UserDAO userDAO;

    /**
     * Default constructor initializes its own UserDAO dependency.
     */
    public LoginService() {
        this.userDAO = new UserDAO();
    }

    /**
     * Dependency injection constructor (useful for unit testing or custom DAO instances).
     *
     * @param userDAO Non-null UserDAO instance.
     */
    public LoginService(UserDAO userDAO) {
        this.userDAO = Objects.requireNonNull(userDAO, "UserDAO cannot be null.");
    }

    /**
     * Authenticates a user with a username and plain-text candidate password.
     *
     * Result Handling:
     *   - If credentials are valid, returns LoginResult.success(user).
     *   - If username/password is wrong or blank, returns LoginResult.failure(message).
     *   - If a database or environment error occurs, throws LoginException so the UI
     *     can distinguish system outages from bad user credentials.
     *
     * @param username Candidate username.
     * @param password Candidate plain-text password.
     * @return LoginResult containing the authenticated User upon success, or failure message.
     * @throws LoginException if database communication fails or if an environment error occurs.
     */
    public LoginResult login(String username, String password) throws LoginException {
        // 1. Validate inputs safely before querying the database
        if (username == null || username.trim().isEmpty()) {
            return LoginResult.failure("Username cannot be empty.");
        }
        if (password == null || password.isEmpty()) {
            return LoginResult.failure("Password cannot be empty.");
        }

        User user;
        try {
            // 2. Fetch user by username from database
            user = userDAO.findByUsername(username.trim());
        } catch (SQLException e) {
            // Do not mask database errors as invalid credentials
            throw new LoginException("Unable to connect to the database. Please check your connection and try again.", e);
        } catch (IllegalStateException e) {
            // Environment configuration issue (e.g. DM_DB_PASSWORD missing)
            throw new LoginException("Database configuration error: " + e.getMessage(), e);
        }

        // 3. Check if user exists
        if (user == null) {
            // A generic error message reduces explicit username disclosure in responses,
            // though it does not completely prevent timing-based username enumeration.
            return LoginResult.failure("Invalid username or password.");
        }

        // 4. Verify entered password against stored PBKDF2 hash using PasswordUtil.
        // PasswordUtil.verifyPassword() safely handles null, empty, or malformed hashes by returning false.
        boolean passwordMatches = PasswordUtil.verifyPassword(password, user.getPasswordHash());

        if (!passwordMatches) {
            return LoginResult.failure("Invalid username or password.");
        }

        // 5. Authentication succeeded: return successful LoginResult with authenticated User
        return LoginResult.success(user);
    }

    /**
     * Checks if any users exist in the system.
     * Used by the UI on launch to determine whether to show the Login dialog
     * or the First-Time Administrator Setup screen.
     *
     * @return true if at least one user exists; false if table is genuinely empty.
     * @throws LoginException if a database error prevents verifying user existence.
     */
    public boolean hasAnyUsers() throws LoginException {
        try {
            return userDAO.hasAnyUsers();
        } catch (SQLException e) {
            throw new LoginException("Unable to check user setup status: database is unreachable.", e);
        } catch (IllegalStateException e) {
            throw new LoginException("Database configuration error: " + e.getMessage(), e);
        }
    }

    // ── Supporting Result & Exception Types ────────────────────────────────

    /**
     * Encapsulates the outcome of an authentication attempt.
     */
    public static class LoginResult {
        private final boolean success;
        private final User user;
        private final String errorMessage;

        private LoginResult(boolean success, User user, String errorMessage) {
            this.success = success;
            this.user = user;
            this.errorMessage = errorMessage;
        }

        public static LoginResult success(User user) {
            return new LoginResult(true, Objects.requireNonNull(user, "User cannot be null on success."), null);
        }

        public static LoginResult failure(String errorMessage) {
            return new LoginResult(false, null, errorMessage != null ? errorMessage : "Authentication failed.");
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
     * Custom exception thrown when technical or database errors prevent completing authentication.
     */
    public static class LoginException extends Exception {
        private static final long serialVersionUID = 1L;

        public LoginException(String message) {
            super(message);
        }

        public LoginException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
