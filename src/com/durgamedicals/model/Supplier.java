package com.durgamedicals.model;

/**
 * Supplier.java
 * Model class representing a medicine supplier or distributor.
 * Maps directly to the 'suppliers' table in the MySQL database.
 *
 * Database Table: suppliers
 * Columns:
 *   - supplier_id   (INT, PK, AUTO_INCREMENT)
 *   - supplier_name (VARCHAR(100), NOT NULL)
 *   - phone         (VARCHAR(15), NULL)
 *   - address       (VARCHAR(255), NULL)
 */
public class Supplier {

    // ── Private Fields ────────────────────────────────────────────────────
    private int supplierId;
    private String supplierName;
    private String phone;
    private String address;

    // ── Constructors ──────────────────────────────────────────────────────

    /**
     * Default no-argument constructor.
     * Useful when creating an empty supplier object and filling fields via setters.
     */
    public Supplier() {
    }

    /**
     * Constructor for creating a NEW supplier (before inserting into the database).
     * Note: supplierId is not passed here because the database generates it automatically (AUTO_INCREMENT).
     */
    public Supplier(String supplierName, String phone, String address) {
        this.supplierName = supplierName;
        this.phone = phone;
        this.address = address;
    }

    /**
     * Full constructor (for reading an EXISTING supplier from the database).
     */
    public Supplier(int supplierId, String supplierName, String phone, String address) {
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.phone = phone;
        this.address = address;
    }

    // ── Getters and Setters ────────────────────────────────────────────────

    public int getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(int supplierId) {
        this.supplierId = supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    // ── toString Method ───────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Supplier{" +
                "supplierId=" + supplierId +
                ", supplierName='" + supplierName + '\'' +
                ", phone='" + phone + '\'' +
                ", address='" + address + '\'' +
                '}';
    }
}
