package com.durgamedicals.util;

/**
 * AppConstants.java
 * Central configuration file for the Durga Medicals application.
 * All app-wide fixed values are defined here in one place.
 */
public class AppConstants {

    // ── Application Info ──────────────────────────────────────────────────
    public static final String APP_NAME    = "Durga Medicals";
    public static final String APP_VERSION = "1.0";
    public static final String APP_TAGLINE = "Pharmacy Billing & Management System";

    // ── Database Connection ───────────────────────────────────────────────
    // The password is read from a Windows environment variable named
    // DM_DB_PASSWORD — it is never written in this source file.
    // If the variable is not set, DB_PASSWORD is empty ("") and the
    // connection will fail immediately with a clear error on startup.
    public static final String DB_URL      = "jdbc:mysql://localhost:3306/durga_medicals"
                                           + "?useSSL=false"
                                           + "&serverTimezone=Asia/Kolkata"
                                           + "&allowPublicKeyRetrieval=true";
    public static final String DB_USER     = "root";
    public static final String DB_PASSWORD = System.getenv("DM_DB_PASSWORD") != null
                                           ? System.getenv("DM_DB_PASSWORD")
                                           : "";

    // ── GST Rate Sentinel ─────────────────────────────────────────────────
    // -1.0 is a special signal meaning "GST rates not configured."
    // No real GST rate can ever be negative, so this is unambiguous.
    //
    // How it works:
    //   1. SettingsDAO tries to load CGST and SGST rates from the
    //      settings table in the database.
    //   2. If loading fails for any reason, it returns -1.0.
    //   3. BillingService checks for -1.0 before every bill.
    //   4. If either rate is -1.0 → bill is BLOCKED and a clear
    //      error message is shown to the pharmacist.
    //   5. A bill can only be completed when both rates are
    //      properly configured in the Settings screen.
    //
    // This ensures the app NEVER silently generates a bill with
    // missing, wrong, or zero-by-accident GST rates.
    public static final double GST_RATE_NOT_CONFIGURED = -1.0;

    // ── Low-Stock Threshold Fallback ──────────────────────────────────────
    // Used only when a medicine has no min_quantity value in the database.
    public static final int DEFAULT_MIN_QUANTITY = 10;

    // ── Main Window Size (pixels) ─────────────────────────────────────────
    public static final int WINDOW_WIDTH  = 1280;
    public static final int WINDOW_HEIGHT = 780;
}
