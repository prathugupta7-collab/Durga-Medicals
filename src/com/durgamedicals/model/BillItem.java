package com.durgamedicals.model;

import java.math.BigDecimal;

/**
 * BillItem.java
 * Model class representing a single line-item within a customer's bill.
 * Maps directly to the 'bill_items' table in the MySQL database.
 *
 * Database Table: bill_items
 * Columns:
 *   - item_id       (INT, PK, AUTO_INCREMENT)
 *   - bill_id       (INT, NOT NULL, FK)
 *   - medicine_id   (INT, NOT NULL, FK)
 *   - quantity      (INT, NOT NULL)
 *   - price         (DECIMAL(10,2), NOT NULL) - Selling price at time of sale
 *   - amount        (DECIMAL(10,2), NOT NULL) - Subtotal for this item (quantity * price)
 *   - medicine_name (VARCHAR(150), NULL)     - Snapshot of medicine name at time of sale
 */
public class BillItem {

    // ── Private Fields ────────────────────────────────────────────────────
    private int itemId;
    private int billId;
    private int medicineId;
    private int quantity;
    private BigDecimal price;
    private BigDecimal amount;
    private String medicineName;

    // ── Constructors ──────────────────────────────────────────────────────

    /**
     * Default no-argument constructor.
     * Useful when creating an empty object and setting fields via setters.
     */
    public BillItem() {
    }

    /**
     * Constructor for creating a NEW bill item (before inserting into database).
     * Note: itemId is omitted because the database generates it automatically (AUTO_INCREMENT).
     */
    public BillItem(int billId, int medicineId, int quantity,
                    BigDecimal price, BigDecimal amount, String medicineName) {
        this.billId = billId;
        this.medicineId = medicineId;
        this.quantity = quantity;
        this.price = price;
        this.amount = amount;
        this.medicineName = medicineName;
    }

    /**
     * Full constructor (for reading an EXISTING bill item from the database).
     */
    public BillItem(int itemId, int billId, int medicineId, int quantity,
                    BigDecimal price, BigDecimal amount, String medicineName) {
        this.itemId = itemId;
        this.billId = billId;
        this.medicineId = medicineId;
        this.quantity = quantity;
        this.price = price;
        this.amount = amount;
        this.medicineName = medicineName;
    }

    // ── Getters and Setters ────────────────────────────────────────────────

    public int getItemId() {
        return itemId;
    }

    public void setItemId(int itemId) {
        this.itemId = itemId;
    }

    public int getBillId() {
        return billId;
    }

    public void setBillId(int billId) {
        this.billId = billId;
    }

    public int getMedicineId() {
        return medicineId;
    }

    public void setMedicineId(int medicineId) {
        this.medicineId = medicineId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public void setMedicineName(String medicineName) {
        this.medicineName = medicineName;
    }

    // ── toString Method ───────────────────────────────────────────────────

    @Override
    public String toString() {
        return "BillItem{" +
                "itemId=" + itemId +
                ", billId=" + billId +
                ", medicineId=" + medicineId +
                ", quantity=" + quantity +
                ", price=" + price +
                ", amount=" + amount +
                ", medicineName='" + medicineName + '\'' +
                '}';
    }
}
