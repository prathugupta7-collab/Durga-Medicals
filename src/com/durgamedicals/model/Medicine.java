package com.durgamedicals.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Medicine.java
 * Model class representing a single medicine record.
 * Maps directly to the 'medicines' table in the MySQL database.
 *
 * Database Table: medicines
 * Columns:
 *   - medicine_id     (INT, PK, AUTO_INCREMENT)
 *   - medicine_name   (VARCHAR(150), NOT NULL)
 *   - category        (VARCHAR(100), NULL)
 *   - purchase_price  (DECIMAL(10,2), NULL)
 *   - selling_price   (DECIMAL(10,2), NULL)
 *   - quantity        (INT, NULL, DEFAULT 0)
 *   - expiry_date     (DATE, NULL)
 *   - supplier_id     (INT, NULL, FK)
 *   - min_quantity    (INT, NOT NULL, DEFAULT 10)
 */
public class Medicine {

    // ── Private Fields ────────────────────────────────────────────────────
    // Using Integer (wrapper object) instead of primitive int for nullable database columns
    // so they can safely hold 'null' if the database value is null.
    private int medicineId;
    private String medicineName;
    private String category;
    private BigDecimal purchasePrice;
    private BigDecimal sellingPrice;
    private Integer quantity;
    private LocalDate expiryDate;
    private Integer supplierId;
    private int minQuantity;

    // ── Constructors ──────────────────────────────────────────────────────

    /**
     * Default no-argument constructor.
     * Useful when creating an empty object and setting fields via setters.
     */
    public Medicine() {
        this.quantity = 0;
        this.minQuantity = 10; // Default threshold
    }

    /**
     * Constructor for creating a NEW medicine (before inserting into the database).
     * Note: medicineId is not passed here because the database generates it automatically (AUTO_INCREMENT).
     */
    public Medicine(String medicineName, String category, BigDecimal purchasePrice,
                    BigDecimal sellingPrice, Integer quantity, LocalDate expiryDate,
                    Integer supplierId, int minQuantity) {
        this.medicineName = medicineName;
        this.category = category;
        this.purchasePrice = purchasePrice;
        this.sellingPrice = sellingPrice;
        this.quantity = quantity;
        this.expiryDate = expiryDate;
        this.supplierId = supplierId;
        this.minQuantity = minQuantity;
    }

    /**
     * Full constructor (for reading an EXISTING medicine from the database).
     */
    public Medicine(int medicineId, String medicineName, String category,
                    BigDecimal purchasePrice, BigDecimal sellingPrice, Integer quantity,
                    LocalDate expiryDate, Integer supplierId, int minQuantity) {
        this.medicineId = medicineId;
        this.medicineName = medicineName;
        this.category = category;
        this.purchasePrice = purchasePrice;
        this.sellingPrice = sellingPrice;
        this.quantity = quantity;
        this.expiryDate = expiryDate;
        this.supplierId = supplierId;
        this.minQuantity = minQuantity;
    }

    // ── Getters and Setters ────────────────────────────────────────────────

    public int getMedicineId() {
        return medicineId;
    }

    public void setMedicineId(int medicineId) {
        this.medicineId = medicineId;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public void setMedicineName(String medicineName) {
        this.medicineName = medicineName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(BigDecimal sellingPrice) {
        this.sellingPrice = sellingPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public Integer getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Integer supplierId) {
        this.supplierId = supplierId;
    }

    public int getMinQuantity() {
        return minQuantity;
    }

    public void setMinQuantity(int minQuantity) {
        this.minQuantity = minQuantity;
    }

    // ── Helper Status Methods ─────────────────────────────────────────────

    /**
     * Checks if this medicine is running low on stock.
     * Returns true if current quantity is less than or equal to minQuantity.
     */
    public boolean isLowStock() {
        int currentQty = (quantity != null) ? quantity : 0;
        return currentQty <= minQuantity;
    }

    /**
     * Checks if this medicine has already expired.
     * Returns true if expiryDate is before today's date.
     */
    public boolean isExpired() {
        if (expiryDate == null) {
            return false;
        }
        return expiryDate.isBefore(LocalDate.now());
    }

    // ── toString Method ───────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Medicine{" +
                "medicineId=" + medicineId +
                ", medicineName=\'" + medicineName + '\'' +
                ", category=\'" + category + '\'' +
                ", purchasePrice=" + purchasePrice +
                ", sellingPrice=" + sellingPrice +
                ", quantity=" + quantity +
                ", expiryDate=" + expiryDate +
                ", supplierId=" + supplierId +
                ", minQuantity=" + minQuantity +
                '}';
    }
}
