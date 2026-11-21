package com.durgamedicals.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bill.java
 * Model class representing a customer bill / invoice.
 * Maps directly to the 'bills' table in the MySQL database.
 *
 * Database Table: bills
 * Columns:
 *   - bill_id        (INT, PK, AUTO_INCREMENT)
 *   - bill_date      (DATETIME, NULL, DEFAULT CURRENT_TIMESTAMP)
 *   - customer_name  (VARCHAR(100), NULL)
 *   - customer_phone (VARCHAR(15), NULL)
 *   - subtotal       (DECIMAL(10,2), NOT NULL)
 *   - tax_amount     (DECIMAL(10,2), NULL, DEFAULT 0.00)  - Combined tax (cgst_amount + sgst_amount)
 *   - total_amount   (DECIMAL(10,2), NOT NULL)            - Grand total to pay
 *   - discount       (DECIMAL(10,2), NOT NULL, DEFAULT 0.00)
 *   - cgst_amount    (DECIMAL(10,2), NOT NULL, DEFAULT 0.00)
 *   - sgst_amount    (DECIMAL(10,2), NOT NULL, DEFAULT 0.00)
 *
 * Note: Tax, discount and grand total calculations are handled in BillingService.java.
 * This class serves purely as a data container (DTO).
 */
public class Bill {

    // ── Private Fields ────────────────────────────────────────────────────
    private int billId;
    private LocalDateTime billDate;
    private String customerName;
    private String customerPhone;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal discount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;

    // ── Constructors ──────────────────────────────────────────────────────

    /**
     * Default no-argument constructor.
     * Useful when creating an empty bill object and filling fields via setters.
     */
    public Bill() {
        this.discount = BigDecimal.ZERO;
        this.taxAmount = BigDecimal.ZERO;
        this.cgstAmount = BigDecimal.ZERO;
        this.sgstAmount = BigDecimal.ZERO;
    }

    /**
     * Constructor for creating a NEW bill (before inserting into database).
     * Note: billId is omitted because the database generates it automatically (AUTO_INCREMENT).
     */
    public Bill(LocalDateTime billDate, String customerName, String customerPhone,
                BigDecimal subtotal, BigDecimal taxAmount, BigDecimal totalAmount,
                BigDecimal discount, BigDecimal cgstAmount, BigDecimal sgstAmount) {
        this.billDate = billDate;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.subtotal = subtotal;
        this.taxAmount = taxAmount;
        this.totalAmount = totalAmount;
        this.discount = discount;
        this.cgstAmount = cgstAmount;
        this.sgstAmount = sgstAmount;
    }

    /**
     * Full constructor (for reading an EXISTING bill from the database).
     */
    public Bill(int billId, LocalDateTime billDate, String customerName, String customerPhone,
                BigDecimal subtotal, BigDecimal taxAmount, BigDecimal totalAmount,
                BigDecimal discount, BigDecimal cgstAmount, BigDecimal sgstAmount) {
        this.billId = billId;
        this.billDate = billDate;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.subtotal = subtotal;
        this.taxAmount = taxAmount;
        this.totalAmount = totalAmount;
        this.discount = discount;
        this.cgstAmount = cgstAmount;
        this.sgstAmount = sgstAmount;
    }

    // ── Getters and Setters ────────────────────────────────────────────────

    public int getBillId() {
        return billId;
    }

    public void setBillId(int billId) {
        this.billId = billId;
    }

    public LocalDateTime getBillDate() {
        return billDate;
    }

    public void setBillDate(LocalDateTime billDate) {
        this.billDate = billDate;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getCgstAmount() {
        return cgstAmount;
    }

    public void setCgstAmount(BigDecimal cgstAmount) {
        this.cgstAmount = cgstAmount;
    }

    public BigDecimal getSgstAmount() {
        return sgstAmount;
    }

    public void setSgstAmount(BigDecimal sgstAmount) {
        this.sgstAmount = sgstAmount;
    }

    // ── toString Method ───────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Bill{" +
                "billId=" + billId +
                ", billDate=" + billDate +
                ", customerName='" + customerName + '\'' +
                ", customerPhone='" + customerPhone + '\'' +
                ", subtotal=" + subtotal +
                ", taxAmount=" + taxAmount +
                ", totalAmount=" + totalAmount +
                ", discount=" + discount +
                ", cgstAmount=" + cgstAmount +
                ", sgstAmount=" + sgstAmount +
                '}';
    }
}
