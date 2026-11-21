package com.durgamedicals.service;

import com.durgamedicals.dao.BillDAO;
import com.durgamedicals.dao.SettingsDAO;
import com.durgamedicals.model.Bill;
import com.durgamedicals.model.BillItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * BillingService.java
 * Service layer class handling pharmacy billing calculations, GST tax computation,
 * discount validation, and coordinating transaction-safe bill persistence via BillDAO.
 *
 * Tax Policy & Monetary Calculation:
 *   - All calculations use BigDecimal with RoundingMode.HALF_UP.
 *   - Double precision floating-point arithmetic is strictly avoided for monetary values.
 *   - GST rates ('cgst_rate' and 'sgst_rate') are dynamically loaded from SettingsDAO.
 *   - Missing, unconfigured, or invalid rates BLOCK bill calculation immediately with a clear error.
 *   - No default GST rate is ever assumed (an explicitly configured 0.00% is valid and allowed).
 *   - Database access errors (SQLException) and environment issues (IllegalStateException) are
 *     distinguished from missing or unconfigured GST settings.
 *   - Stock deduction is intentionally NOT performed here (deferred to InventoryService).
 *   - CalculationResult uses defensive copies so callers cannot tamper with calculated values before saving.
 *   - CalculationResult.success() and markAsSaved() are private to prevent external tampering or forged states.
 */
public class BillingService {

    public static final String SETTING_KEY_CGST_RATE = "cgst_rate";
    public static final String SETTING_KEY_SGST_RATE = "sgst_rate";

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_MONETARY_LIMIT = new BigDecimal("99999999.99");

    private final BillDAO billDAO;
    private final SettingsDAO settingsDAO;

    /**
     * Default constructor initializing dependencies.
     */
    public BillingService() {
        this.billDAO = new BillDAO();
        this.settingsDAO = new SettingsDAO();
    }

    /**
     * Dependency injection constructor (useful for unit testing or custom DAO instances).
     *
     * @param billDAO     Non-null BillDAO instance.
     * @param settingsDAO Non-null SettingsDAO instance.
     */
    public BillingService(BillDAO billDAO, SettingsDAO settingsDAO) {
        this.billDAO = Objects.requireNonNull(billDAO, "BillDAO cannot be null.");
        this.settingsDAO = Objects.requireNonNull(settingsDAO, "SettingsDAO cannot be null.");
    }

    /**
     * Calculates a complete Bill from a list of cart line items and an optional discount.
     * Loads configured CGST and SGST rates from SettingsDAO, validates inputs, and computes:
     *   1. Line Amount     = quantity * unitPrice
     *   2. Subtotal        = SUM(lineAmount)
     *   3. Taxable Amount  = Subtotal - Discount
     *   4. CGST Amount     = Taxable Amount * (CGST Rate / 100)
     *   5. SGST Amount     = Taxable Amount * (SGST Rate / 100)
     *   6. Total Tax       = CGST Amount + SGST Amount (stored in 'tax_amount')
     *   7. Grand Total     = Taxable Amount + Total Tax (stored in 'total_amount')
     *
     * @param cartItems     List of BillItem objects containing medicineId, medicineName, quantity, and price.
     * @param discount      Bill-level discount in Rupees (can be null or zero; cannot be negative).
     * @param customerName  Optional customer name (max 100 chars).
     * @param customerPhone Optional customer phone number (max 15 chars).
     * @return CalculationResult containing defensively copied Bill and items upon success, or error details.
     * @throws BillingDatabaseException if a database error occurs while loading GST rates.
     */
    public CalculationResult calculateBill(List<BillItem> cartItems, BigDecimal discount,
                                          String customerName, String customerPhone) throws BillingDatabaseException {
        // 1. Validate cart presence
        if (cartItems == null || cartItems.isEmpty()) {
            return CalculationResult.failure("Cart cannot be empty. Please add at least one medicine.");
        }

        // 2. Fetch and strictly validate GST rates from settings table
        GSTRates rates;
        try {
            rates = loadConfiguredGSTRates();
        } catch (BillingConfigurationException e) {
            // Configuration error (e.g. rate missing or invalid format in settings)
            return CalculationResult.failure(e.getMessage());
        }

        // 3. Process line items and compute subtotal
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<BillItem> validatedItems = new ArrayList<>(cartItems.size());

        for (int i = 0; i < cartItems.size(); i++) {
            BillItem item = cartItems.get(i);
            if (item == null) {
                return CalculationResult.failure("Cart item at index " + i + " cannot be null.");
            }

            if (item.getMedicineId() <= 0) {
                return CalculationResult.failure("Item at index " + i + " has an invalid medicine ID (" + item.getMedicineId() + ").");
            }

            String medName = item.getMedicineName();
            if (medName == null || medName.trim().isEmpty()) {
                return CalculationResult.failure("Item at index " + i + " must have a valid medicine name.");
            }
            if (medName.trim().length() > 150) {
                return CalculationResult.failure("Medicine name at index " + i + " exceeds 150 characters.");
            }

            int qty = item.getQuantity();
            if (qty <= 0) {
                return CalculationResult.failure("Quantity for '" + medName + "' must be greater than zero.");
            }

            BigDecimal unitPrice = item.getPrice();
            if (unitPrice == null) {
                return CalculationResult.failure("Medicine '" + medName + "' has no price specified.");
            }
            if (unitPrice.compareTo(BigDecimal.ZERO) < 0) {
                return CalculationResult.failure("Medicine '" + medName + "' cannot have a negative price (" + unitPrice + ").");
            }
            if (unitPrice.scale() > 2) {
                return CalculationResult.failure("Price for '" + medName + "' cannot have more than 2 decimal places (" + unitPrice + ").");
            }
            if (unitPrice.compareTo(MAX_MONETARY_LIMIT) > 0) {
                return CalculationResult.failure("Price for '" + medName + "' exceeds maximum allowed limit.");
            }

            // Calculate line item amount: quantity * unitPrice
            BigDecimal lineAmount = unitPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            if (lineAmount.compareTo(MAX_MONETARY_LIMIT) > 0) {
                return CalculationResult.failure("Line total for '" + medName + "' exceeds maximum allowed limit.");
            }

            // Populate validated item
            BillItem validatedItem = new BillItem();
            validatedItem.setMedicineId(item.getMedicineId());
            validatedItem.setMedicineName(medName.trim());
            validatedItem.setQuantity(qty);
            validatedItem.setPrice(unitPrice.setScale(2, RoundingMode.HALF_UP));
            validatedItem.setAmount(lineAmount);

            validatedItems.add(validatedItem);
            subtotal = subtotal.add(lineAmount);
        }

        if (subtotal.compareTo(MAX_MONETARY_LIMIT) > 0) {
            return CalculationResult.failure("Bill subtotal exceeds maximum allowed limit.");
        }

        // 4. Validate customer fields
        String trimmedCustomerName = (customerName != null && !customerName.trim().isEmpty()) ? customerName.trim() : null;
        if (trimmedCustomerName != null && trimmedCustomerName.length() > 100) {
            return CalculationResult.failure("Customer name exceeds maximum allowed length of 100 characters.");
        }

        String trimmedCustomerPhone = (customerPhone != null && !customerPhone.trim().isEmpty()) ? customerPhone.trim() : null;
        if (trimmedCustomerPhone != null && trimmedCustomerPhone.length() > 15) {
            return CalculationResult.failure("Customer phone exceeds maximum allowed length of 15 characters.");
        }

        // 5. Validate and apply discount (check original scale before modifying)
        BigDecimal appliedDiscount;
        if (discount == null) {
            appliedDiscount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            if (discount.compareTo(BigDecimal.ZERO) < 0) {
                return CalculationResult.failure("Discount amount cannot be negative.");
            }
            if (discount.scale() > 2) {
                return CalculationResult.failure("Discount amount cannot have more than 2 decimal places (" + discount + ").");
            }
            appliedDiscount = discount.setScale(2, RoundingMode.HALF_UP);
        }

        if (appliedDiscount.compareTo(subtotal) > 0) {
            return CalculationResult.failure("Discount (₹" + appliedDiscount + ") cannot exceed subtotal (₹" + subtotal + ").");
        }

        // 6. Compute Taxable Base (subtotal - discount)
        BigDecimal taxableBase = subtotal.subtract(appliedDiscount);

        // 7. Calculate CGST & SGST separately using BigDecimal arithmetic
        BigDecimal cgstAmount = taxableBase.multiply(rates.cgstRate())
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

        BigDecimal sgstAmount = taxableBase.multiply(rates.sgstRate())
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

        // Combined tax_amount stored in MySQL
        BigDecimal totalTaxAmount = cgstAmount.add(sgstAmount).setScale(2, RoundingMode.HALF_UP);

        // 8. Final payable total: taxableBase + totalTaxAmount
        BigDecimal grandTotal = taxableBase.add(totalTaxAmount).setScale(2, RoundingMode.HALF_UP);

        if (grandTotal.compareTo(MAX_MONETARY_LIMIT) > 0) {
            return CalculationResult.failure("Grand total exceeds maximum allowed limit.");
        }

        // 9. Assemble populated Bill model object
        Bill bill = new Bill();
        bill.setBillDate(LocalDateTime.now());
        bill.setCustomerName(trimmedCustomerName);
        bill.setCustomerPhone(trimmedCustomerPhone);
        bill.setSubtotal(subtotal);
        bill.setDiscount(appliedDiscount);
        bill.setCgstAmount(cgstAmount);
        bill.setSgstAmount(sgstAmount);
        bill.setTaxAmount(totalTaxAmount);
        bill.setTotalAmount(grandTotal);

        return CalculationResult.success(bill, validatedItems, rates.cgstRate(), rates.sgstRate());
    }

    /**
     * Saves a successfully calculated bill and its line items using BillDAO's atomic transaction.
     * Directly accesses the enclosing BillingService's nested CalculationResult private fields,
     * ensuring that only authentic, non-tampered entities calculated by this service are persisted.
     * Prevents saving the same CalculationResult more than once; allows another attempt if saving fails.
     * Note: Stock deduction is intentionally deferred to InventoryService.
     *
     * @param calculationResult The successful CalculationResult object containing the Bill and items.
     * @return true if the bill was successfully committed to the database, false otherwise.
     */
    public boolean processBill(CalculationResult calculationResult) {
        if (calculationResult == null || !calculationResult.isSuccess()) {
            return false;
        }

        synchronized (calculationResult) {
            if (calculationResult.isSaved()) {
                System.err.println("Billing Warning [processBill]: This calculation result has already been saved to the database.");
                return false;
            }

            // Access the original protected internal entities directly from the nested class
            Bill internalBill = calculationResult.internalBill;
            List<BillItem> internalItems = calculationResult.internalItems;

            if (internalBill == null || internalItems == null || internalItems.isEmpty()) {
                return false;
            }

            boolean saved = billDAO.saveBill(internalBill, internalItems);
            if (saved) {
                // Mark as saved permanently only when database commit succeeds
                calculationResult.markAsSaved();
                return true;
            }

            // If saving fails (e.g. DB error), isSaved remains false so the user can re-try
            return false;
        }
    }

    // ── Public Helper Methods ─────────────────────────────────────────────

    /**
     * Retrieves the currently configured CGST and SGST rates from the settings table.
     *
     * @return GSTRates containing validated non-null CGST and SGST rates.
     * @throws BillingConfigurationException if rates are missing, blank, non-numeric, or out of range.
     * @throws BillingDatabaseException if a database error occurs.
     */
    public GSTRates getConfiguredGSTRates() throws BillingConfigurationException, BillingDatabaseException {
        return loadConfiguredGSTRates();
    }

    // ── Private Helper Methods ────────────────────────────────────────────

    /**
     * Loads CGST and SGST rates from the settings table.
     *
     * Distinct Error Handling:
     *   - Throws BillingDatabaseException: if a SQLException or IllegalStateException occurs
     *     (so callers know the database/environment failed, rather than settings being absent).
     *   - Throws BillingConfigurationException: if rates are missing, blank, non-numeric,
     *     negative, or > 100%.
     *   - Explicit 0.00% is allowed and valid.
     *
     * @return GSTRates containing validated non-null CGST and SGST rates.
     */
    private GSTRates loadConfiguredGSTRates() throws BillingConfigurationException, BillingDatabaseException {
        String cgstStr;
        String sgstStr;

        try {
            cgstStr = settingsDAO.getSetting(SETTING_KEY_CGST_RATE);
            sgstStr = settingsDAO.getSetting(SETTING_KEY_SGST_RATE);
        } catch (SQLException e) {
            throw new BillingDatabaseException("Database Error: Unable to retrieve GST tax rates from settings. Please check your database connection.", e);
        } catch (IllegalStateException e) {
            throw new BillingDatabaseException("Configuration Error: " + e.getMessage(), e);
        }

        if (cgstStr == null || cgstStr.trim().isEmpty()) {
            throw new BillingConfigurationException("Billing Blocked: CGST tax rate is not configured in settings. Please configure GST rates in Settings before creating bills.");
        }
        if (sgstStr == null || sgstStr.trim().isEmpty()) {
            throw new BillingConfigurationException("Billing Blocked: SGST tax rate is not configured in settings. Please configure GST rates in Settings before creating bills.");
        }

        BigDecimal cgstRate;
        BigDecimal sgstRate;

        try {
            cgstRate = new BigDecimal(cgstStr.trim());
        } catch (NumberFormatException e) {
            throw new BillingConfigurationException("Billing Blocked: Configured CGST tax rate ('" + cgstStr.trim() + "') is not a valid number. Please verify Settings.");
        }

        try {
            sgstRate = new BigDecimal(sgstStr.trim());
        } catch (NumberFormatException e) {
            throw new BillingConfigurationException("Billing Blocked: Configured SGST tax rate ('" + sgstStr.trim() + "') is not a valid number. Please verify Settings.");
        }

        if (cgstRate.compareTo(BigDecimal.ZERO) < 0 || cgstRate.compareTo(ONE_HUNDRED) > 0) {
            throw new BillingConfigurationException("Billing Blocked: CGST rate must be between 0% and 100% (configured: " + cgstRate + "%).");
        }
        if (sgstRate.compareTo(BigDecimal.ZERO) < 0 || sgstRate.compareTo(ONE_HUNDRED) > 0) {
            throw new BillingConfigurationException("Billing Blocked: SGST rate must be between 0% and 100% (configured: " + sgstRate + "%).");
        }

        return new GSTRates(cgstRate, sgstRate);
    }

    /**
     * Creates a deep defensive copy of a Bill object copying every field.
     */
    private static Bill copyBill(Bill source) {
        if (source == null) {
            return null;
        }
        Bill copy = new Bill();
        copy.setBillId(source.getBillId());
        copy.setBillDate(source.getBillDate());
        copy.setCustomerName(source.getCustomerName());
        copy.setCustomerPhone(source.getCustomerPhone());
        copy.setSubtotal(source.getSubtotal());
        copy.setDiscount(source.getDiscount());
        copy.setCgstAmount(source.getCgstAmount());
        copy.setSgstAmount(source.getSgstAmount());
        copy.setTaxAmount(source.getTaxAmount());
        copy.setTotalAmount(source.getTotalAmount());
        return copy;
    }

    /**
     * Creates a deep defensive copy of a BillItem object copying every field.
     */
    private static BillItem copyBillItem(BillItem source) {
        if (source == null) {
            return null;
        }
        BillItem copy = new BillItem();
        copy.setItemId(source.getItemId());
        copy.setBillId(source.getBillId());
        copy.setMedicineId(source.getMedicineId());
        copy.setQuantity(source.getQuantity());
        copy.setPrice(source.getPrice());
        copy.setAmount(source.getAmount());
        copy.setMedicineName(source.getMedicineName());
        return copy;
    }

    // ── Supporting Record & Result Types ──────────────────────────────────

    /**
     * Pair representing verified CGST and SGST rates.
     */
    public record GSTRates(BigDecimal cgstRate, BigDecimal sgstRate) {}

    /**
     * Outcome carrier for a bill calculation attempt.
     * Protects internally stored Bill and BillItem records from external mutation
     * via deep defensive copying on creation and accessors.
     */
    public static class CalculationResult {
        private final boolean success;
        private final Bill internalBill;
        private final List<BillItem> internalItems;
        private final BigDecimal cgstRate;
        private final BigDecimal sgstRate;
        private final String errorMessage;
        private boolean saved;

        private CalculationResult(boolean success, Bill bill, List<BillItem> items,
                                  BigDecimal cgstRate, BigDecimal sgstRate, String errorMessage) {
            this.success = success;
            if (bill != null) {
                // Defensive copy stored internally on creation
                this.internalBill = copyBill(bill);
            } else {
                this.internalBill = null;
            }

            if (items != null) {
                // Defensive copy of all items stored internally on creation
                this.internalItems = new ArrayList<>(items.size());
                for (BillItem item : items) {
                    this.internalItems.add(copyBillItem(item));
                }
            } else {
                this.internalItems = null;
            }

            this.cgstRate = cgstRate;
            this.sgstRate = sgstRate;
            this.errorMessage = errorMessage;
            this.saved = false;
        }

        /**
         * Private factory method: Only BillingService can produce a successful CalculationResult.
         * External classes cannot forge successful calculation outcomes.
         */
        private static CalculationResult success(Bill bill, List<BillItem> items, BigDecimal cgstRate, BigDecimal sgstRate) {
            return new CalculationResult(true, bill, items, cgstRate, sgstRate, null);
        }

        /**
         * Public factory method to construct a calculation failure.
         */
        public static CalculationResult failure(String errorMessage) {
            return new CalculationResult(false, null, null, null, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        /**
         * Returns a defensive copy of the calculated Bill to prevent external modification.
         */
        public synchronized Bill getBill() {
            return copyBill(this.internalBill);
        }

        /**
         * Returns a new list containing defensive copies of all calculated BillItems.
         */
        public synchronized List<BillItem> getItems() {
            if (this.internalItems == null) {
                return null;
            }
            List<BillItem> copies = new ArrayList<>(this.internalItems.size());
            for (BillItem item : this.internalItems) {
                copies.add(copyBillItem(item));
            }
            return copies;
        }

        public BigDecimal getCgstRate() {
            return cgstRate;
        }

        public BigDecimal getSgstRate() {
            return sgstRate;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public synchronized boolean isSaved() {
            return saved;
        }

        private synchronized void markAsSaved() {
            this.saved = true;
        }
    }

    /**
     * Exception thrown when missing or invalid tax configuration prevents billing.
     */
    public static class BillingConfigurationException extends Exception {
        private static final long serialVersionUID = 1L;

        public BillingConfigurationException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when technical database errors prevent reading configuration.
     */
    public static class BillingDatabaseException extends Exception {
        private static final long serialVersionUID = 1L;

        public BillingDatabaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
