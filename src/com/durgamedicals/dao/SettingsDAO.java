package com.durgamedicals.dao;

import com.durgamedicals.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * SettingsDAO.java
 * Data Access Object (DAO) for the 'settings' table in MySQL.
 * Handles key-value configuration retrieval for application settings (e.g., tax rates, shop metadata).
 *
 * Database Table: settings
 * Columns:
 *   - setting_key   (VARCHAR(100), NOT NULL, PRIMARY KEY)
 *   - setting_value (VARCHAR(255), NULL)
 */
public class SettingsDAO {

    /**
     * Saves a configuration setting (key/value) into the settings table.
     * If the key already exists the value is updated; otherwise a new row is inserted.
     * Uses an upsert query (INSERT ... ON DUPLICATE KEY UPDATE) which works with
     * the primary‑key constraint on setting_key.
     *
     * @param settingKey   The key to store (e.g. "cgst_rate").
     * @param settingValue The value to store (must be non‑null).
     * @throws IllegalArgumentException if the key is null/blank or the value is null.
     * @throws SQLException             if a database error occurs.
     */
    public void setSetting(String settingKey, String settingValue) throws SQLException {
        if (settingKey == null || settingKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Validation Error [setSetting]: settingKey cannot be null or blank.");
        }
        if (settingValue == null) {
            throw new IllegalArgumentException("Validation Error [setSetting]: settingValue cannot be null.");
        }
        String sql = "INSERT INTO settings (setting_key, setting_value) VALUES (?, ?) "
                     + "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, settingKey.trim());
            stmt.setString(2, settingValue);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logSQLException("setSetting", e);
            throw e;
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [setSetting]: " + e.getMessage());
            throw e;
        }
    }

    // Existing getSetting method follows below


    private static final int MAX_KEY_LENGTH = 100;

    /**
     * Retrieves a configuration setting value by its unique setting_key.
     *
     * Return Value & Error Handling Behavior:
     *   - Returns String: The stored setting_value if the key exists and holds a non-null value.
     *   - Returns null: The requested key does not exist in the database, OR the row exists
     *     but its setting_value column contains a SQL NULL. (Note: this method does not
     *     distinguish between a missing row and a row with a null value).
     *   - Throws IllegalArgumentException: settingKey is null, blank, or exceeds 100 characters.
     *   - Throws IllegalStateException: environment configuration issue (e.g. DM_DB_PASSWORD missing).
     *   - Throws SQLException: a database communication or query execution error occurred.
     *     (Ensures database failures are never silently masked as missing settings).
     *
     * @param settingKey The unique key identifying the setting (e.g. "cgst_rate", "sgst_rate").
     * @return The stored setting_value, or null if the key does not exist or holds a SQL NULL.
     * @throws IllegalArgumentException if settingKey is null, blank, or exceeds 100 characters.
     * @throws IllegalStateException    if environment configuration is invalid.
     * @throws SQLException             if a database access error occurs.
     */
    public String getSetting(String settingKey) throws SQLException {
        if (settingKey == null || settingKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Validation Error [getSetting]: settingKey cannot be null or blank.");
        }

        String trimmedKey = settingKey.trim();
        if (trimmedKey.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Validation Error [getSetting]: settingKey exceeds maximum allowed length of "
                    + MAX_KEY_LENGTH + " characters (length: " + trimmedKey.length() + ").");
        }

        String sql = "SELECT setting_value FROM settings WHERE setting_key = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, trimmedKey);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("setting_value");
                }
            }

            // Setting key does not exist in the database
            return null;

        } catch (SQLException e) {
            logSQLException("getSetting", e);
            throw e; // Propagate so caller knows DB failed rather than thinking setting is missing
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [getSetting]: " + e.getMessage());
            throw e;
        }
    }

    // ── Private Helper Methods ────────────────────────────────────────────

    /**
     * Standardized logger for SQL exceptions matching project DAO conventions.
     * Captures operation name, SQL error code, SQL state, and message.
     *
     * @param operation Name of the method/action where the error occurred.
     * @param e         The caught SQLException.
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
