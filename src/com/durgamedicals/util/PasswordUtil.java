package com.durgamedicals.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

/**
 * PasswordUtil.java
 * Security utility class for password hashing and verification.
 *
 * Uses PBKDF2 with HMAC-SHA256, a slow key derivation function recommended by
 * NIST and OWASP to protect user credentials against offline brute-force attacks.
 *
 * Encoded Storage Format:
 *   algorithm:iterations:salt(base64):hash(base64)
 *   Example:
 *     PBKDF2WithHmacSHA256:600000:q8z...==:x9A...==
 *
 * Fits comfortably within the MySQL 'users.password_hash' VARCHAR(255) column.
 */
public class PasswordUtil {

    // ── Cryptographic Constants ───────────────────────────────────────────
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600000;  // 600,000 rounds (OWASP recommended baseline)
    private static final int SALT_BYTES = 16;      // 128-bit cryptographically secure random salt
    private static final int HASH_BYTES = 32;      // 256-bit derived key (32 bytes)
    private static final int KEY_LENGTH_BITS = HASH_BYTES * 8; // 256 bits

    // Bounds for verification validation (prevents denial-of-service / unexpected inputs)
    private static final int MIN_ALLOWED_ITERATIONS = 600000;
    private static final int MAX_ALLOWED_ITERATIONS = 1000000;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Hashes a plain-text password using a unique 16-byte random salt and 600,000 PBKDF2 rounds.
     * Clears sensitive password character buffers in memory immediately after derivation.
     *
     * @param password The plain-text password to hash.
     * @return A composite string formatted as: algorithm:iterations:salt:hash
     * @throws IllegalArgumentException if password is null, empty, or blank.
     */
    public static String hashPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null, empty, or blank.");
        }

        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);

        char[] passwordChars = password.toCharArray();
        byte[] hash;
        try {
            hash = pbkdf2(passwordChars, salt, ITERATIONS, KEY_LENGTH_BITS, ALGORITHM);
        } finally {
            // Overwrite in-memory sensitive character array
            Arrays.fill(passwordChars, '\0');
        }

        String saltBase64 = Base64.getEncoder().encodeToString(salt);
        String hashBase64 = Base64.getEncoder().encodeToString(hash);

        return ALGORITHM + ":" + ITERATIONS + ":" + saltBase64 + ":" + hashBase64;
    }

    /**
     * Verifies a candidate plain-text password against a stored composite hash string.
     * Validates all structural parameters strictly before performing cryptographic operations.
     *
     * @param password   The candidate password to test.
     * @param storedHash The stored composite hash string (algorithm:iterations:salt:hash).
     * @return true if candidate password derives to the exact expected hash; false otherwise.
     */
    public static boolean verifyPassword(String password, String storedHash) {
        if (password == null || storedHash == null || password.isEmpty() || storedHash.isEmpty()) {
            return false;
        }

        String[] parts = storedHash.split(":");
        if (parts.length != 4) {
            System.err.println("Security Warning [verifyPassword]: Malformed stored hash format (expected 4 parts).");
            return false;
        }

        String algorithm = parts[0];
        int iterations;
        byte[] salt;
        byte[] expectedHash;

        try {
            iterations = Integer.parseInt(parts[1]);
            salt = Base64.getDecoder().decode(parts[2]);
            expectedHash = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException e) {
            System.err.println("Security Warning [verifyPassword]: Failed to parse numerical or Base64 components: " + e.getMessage());
            return false;
        }

        // Strict parameter validations before invoking cryptographic derivation
        if (!ALGORITHM.equals(algorithm)) {
            System.err.println("Security Warning [verifyPassword]: Unsupported algorithm in stored hash: " + algorithm);
            return false;
        }

        if (iterations < MIN_ALLOWED_ITERATIONS || iterations > MAX_ALLOWED_ITERATIONS) {
            System.err.println("Security Warning [verifyPassword]: Iteration count out of acceptable bounds (" + iterations + ").");
            return false;
        }

        if (salt.length != SALT_BYTES) {
            System.err.println("Security Warning [verifyPassword]: Salt length is invalid (" + salt.length + " bytes, expected " + SALT_BYTES + ").");
            return false;
        }

        if (expectedHash.length != HASH_BYTES) {
            System.err.println("Security Warning [verifyPassword]: Expected hash length is invalid (" + expectedHash.length + " bytes, expected " + HASH_BYTES + ").");
            return false;
        }

        char[] passwordChars = password.toCharArray();
        byte[] computedHash;
        try {
            computedHash = pbkdf2(passwordChars, salt, iterations, KEY_LENGTH_BITS, algorithm);
        } catch (Exception e) {
            System.err.println("Security Warning [verifyPassword]: Hash derivation failed during verification: " + e.getMessage());
            return false;
        } finally {
            // Overwrite in-memory sensitive character array
            Arrays.fill(passwordChars, '\0');
        }

        // Use timing-resistant comparison for the derived password hashes.
        return MessageDigest.isEqual(expectedHash, computedHash);
    }

    // ── Private Helper Methods ────────────────────────────────────────────

    /**
     * Derives a PBKDF2 hash using PBEKeySpec and SecretKeyFactory.
     * Ensures PBEKeySpec.clearPassword() is called inside a finally block to clear the internal key copy.
     */
    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLengthBits, String algorithm) {
        PBEKeySpec spec = null;
        try {
            spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(algorithm);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cryptographic algorithm not available in current JVM: " + algorithm, e);
        } catch (InvalidKeySpecException e) {
            throw new IllegalStateException("Invalid key specification during PBKDF2 derivation.", e);
        } finally {
            if (spec != null) {
                spec.clearPassword(); // Clears internal character array inside PBEKeySpec
            }
        }
    }
}
