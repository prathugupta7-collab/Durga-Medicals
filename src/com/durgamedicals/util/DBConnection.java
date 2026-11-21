package com.durgamedicals.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * DBConnection.java
 * Provides a JDBC connection to the durga_medicals MySQL database.
 *
 * ─────────────────────────────────────────────────────────────────
 * HOW TO USE THIS IN OTHER FILES (e.g. in any DAO):
 *
 *   try (Connection conn = DBConnection.getConnection()) {
 *       // write your SQL query code here
 *   } catch (SQLException e) {
 *       e.printStackTrace();
 *   }
 *
 * The "try-with-resources" block automatically closes the database
 * connection when the code inside it finishes — you never need to
 * remember to close it manually.
 * ─────────────────────────────────────────────────────────────────
 */
public class DBConnection {

    /**
     * Opens and returns a new connection to the MySQL database.
     *
     * This method performs two safety checks before connecting:
     *   1. Is the DM_DB_PASSWORD environment variable set at all?
     *   2. Is it non-empty?
     *
     * If either check fails, it throws an error with step-by-step
     * instructions — no connection attempt is ever made with a
     * missing or blank password.
     *
     * @return  A live Connection object, ready to run SQL queries.
     * @throws  IllegalStateException  if DM_DB_PASSWORD is not set or is empty.
     * @throws  SQLException           if MySQL refuses the connection.
     */
    public static Connection getConnection() throws SQLException {

        // ── Check 1: Is the environment variable set? ──────────────────────
        // System.getenv("DM_DB_PASSWORD") asks Windows for the value of
        // the environment variable named DM_DB_PASSWORD.
        // If Windows has never heard of it, it returns null.
        String password = System.getenv("DM_DB_PASSWORD");

        if (password == null) {
            throw new IllegalStateException(
                "Startup Error: DM_DB_PASSWORD is not set.\n\n"
              + "The application cannot connect to the database because\n"
              + "the DM_DB_PASSWORD environment variable is missing.\n\n"
              + "To fix this, follow these steps:\n"
              + "  1. Open the Windows Start menu and search for:\n"
              + "     'Edit the system environment variables'\n"
              + "  2. Click 'Environment Variables...'\n"
              + "  3. Under 'User variables', click 'New'\n"
              + "  4. Variable name:  DM_DB_PASSWORD\n"
              + "  5. Variable value: (enter your MySQL root password)\n"
              + "  6. Click OK on all windows to save\n"
              + "  7. Restart this application\n"
            );
        }

        // ── Check 2: Is the environment variable non-empty? ───────────────
        // The variable exists but may have been set to a blank value.
        if (password.trim().isEmpty()) {
            throw new IllegalStateException(
                "Startup Error: DM_DB_PASSWORD is empty.\n\n"
              + "The environment variable DM_DB_PASSWORD exists but has\n"
              + "no value. Please update it with your MySQL root password\n"
              + "and restart the application.\n"
            );
        }

        // ── Connect to MySQL ──────────────────────────────────────────────
        // Both checks passed. Now attempt the actual database connection.
        // AppConstants supplies the URL and username.
        // The already-validated local variable `password` is used here —
        // not AppConstants.DB_PASSWORD — because we know it is non-null
        // and non-empty at this point in the code.
        //
        // If MySQL is not running, or the username/password is wrong,
        // DriverManager throws a SQLException describing exactly what
        // went wrong. That exception travels up to the calling code to
        // be handled and shown to the user.
        return DriverManager.getConnection(
            AppConstants.DB_URL,
            AppConstants.DB_USER,
            password                    // ← validated local variable
        );
    }

    /**
     * Tests whether the database is reachable right now.
     *
     * Tries to open a connection and immediately closes it.
     * Returns true if successful, false if anything goes wrong.
     * Used during app startup to give an early warning if MySQL
     * is not running or credentials are incorrect.
     *
     * @return  true if the database is reachable, false otherwise.
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException | IllegalStateException e) {
            return false;
        }
    }
}
