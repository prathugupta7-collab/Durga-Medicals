package com.durgamedicals.util;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * CurrencyUtil.java
 * Utility helper class to format monetary amounts and numbers
 * according to Indian numbering and currency conventions (INR / ₹).
 *
 * Examples:
 *   formatCurrency(123456.75) -> "₹1,23,456.75"
 *   formatAmount(123456.75)   -> "1,23,456.75"
 */
public class CurrencyUtil {

    // Indian English locale ("en", "IN") ensures commas follow Indian numbering:
    // e.g., lakhs and crores (1,00,000 and 1,00,00,000).
    private static final Locale INDIA_LOCALE = Locale.of("en", "IN");

    /**
     * Formats a double value into an Indian currency string with the Rupee symbol.
     * Example: 1500.5 -> "₹1,500.50"
     *
     * @param amount The monetary amount to format.
     * @return Formatted currency string with ₹ symbol.
     * @throws IllegalArgumentException if amount is NaN or Infinite.
     */
    public static String formatCurrency(double amount) {
        validateAmount(amount);

        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(INDIA_LOCALE);
        currencyFormatter.setMinimumFractionDigits(2);
        currencyFormatter.setMaximumFractionDigits(2);

        return currencyFormatter.format(amount);
    }

    /**
     * Formats a double value into standard Indian comma-separated format WITHOUT any currency symbol.
     * Useful for table cells, text boxes, or clean numeric columns.
     * Example: 125000.0 -> "1,25,000.00"
     *
     * @param amount The numeric amount to format.
     * @return Formatted numeric string without symbol.
     * @throws IllegalArgumentException if amount is NaN or Infinite.
     */
    public static String formatAmount(double amount) {
        validateAmount(amount);

        NumberFormat numberFormatter = NumberFormat.getNumberInstance(INDIA_LOCALE);
        numberFormatter.setMinimumFractionDigits(2);
        numberFormatter.setMaximumFractionDigits(2);

        return numberFormatter.format(amount);
    }

    /**
     * Overloaded helper: Formats an amount with optional currency symbol.
     *
     * @param amount The monetary value.
     * @param includeSymbol If true, prepends the ₹ symbol; if false, returns just the formatted numbers.
     * @return Formatted string.
     * @throws IllegalArgumentException if amount is NaN or Infinite.
     */
    public static String format(double amount, boolean includeSymbol) {
        return includeSymbol ? formatCurrency(amount) : formatAmount(amount);
    }

    /**
     * Validates that the input amount is a valid real number.
     * Protects billing and reports from silently treating calculation errors (e.g. division by zero) as valid values.
     *
     * @param amount The numeric amount to check.
     * @throws IllegalArgumentException if amount is NaN or Infinite.
     */
    private static void validateAmount(double amount) {
        if (Double.isNaN(amount)) {
            throw new IllegalArgumentException("Invalid monetary amount: value is NaN (Not a Number).");
        }
        if (Double.isInfinite(amount)) {
            throw new IllegalArgumentException("Invalid monetary amount: value is Infinite.");
        }
    }
}
