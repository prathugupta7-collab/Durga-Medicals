package com.durgamedicals.dao;

import com.durgamedicals.model.Supplier;
import com.durgamedicals.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SupplierDAO.java
 * Data Access Object (DAO) for the 'suppliers' table in MySQL.
 * Handles all database operations (CRUD: Create, Read, Update, Delete) for suppliers.
 *
 * Database Table: suppliers
 * Columns:
 *   - supplier_id   (INT, PK, AUTO_INCREMENT)
 *   - supplier_name (VARCHAR(100), NOT NULL)
 *   - phone         (VARCHAR(15), NULL)
 *   - address       (VARCHAR(255), NULL)
 */
public class SupplierDAO {

    /** Adds a new supplier to the database. */
    public boolean addSupplier(Supplier supplier) {
        if (supplier == null) {
            System.err.println("Validation Error [addSupplier]: Supplier object cannot be null.");
            return false;
        }
        if (supplier.getSupplierName() == null || supplier.getSupplierName().trim().isEmpty()) {
            System.err.println("Validation Error [addSupplier]: Supplier name cannot be empty or blank.");
            return false;
        }
        String sql = "INSERT INTO suppliers (supplier_name, phone, address) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, supplier.getSupplierName().trim());
            stmt.setString(2, supplier.getPhone());
            stmt.setString(3, supplier.getAddress());
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        supplier.setSupplierId(rs.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            logSQLException("addSupplier", e);
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [addSupplier]: " + e.getMessage());
        }
        return false;
    }

    /** Retrieves all suppliers ordered by ID ascending. */
    public List<Supplier> getAllSuppliers() {
        List<Supplier> list = new ArrayList<>();
        String sql = "SELECT supplier_id, supplier_name, phone, address FROM suppliers ORDER BY supplier_id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Supplier s = new Supplier(
                        rs.getInt("supplier_id"),
                        rs.getString("supplier_name"),
                        rs.getString("phone"),
                        rs.getString("address"));
                list.add(s);
            }
        } catch (SQLException e) {
            logSQLException("getAllSuppliers", e);
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [getAllSuppliers]: " + e.getMessage());
        }
        return list;
    }

    /** Searches suppliers by keyword and orders by ID ascending. */
    public List<Supplier> searchSuppliers(String keyword) {
        List<Supplier> list = new ArrayList<>();
        String sql;
        if (keyword == null || keyword.trim().isEmpty()) {
            sql = "SELECT supplier_id, supplier_name, phone, address FROM suppliers ORDER BY supplier_id ASC";
        } else {
            sql = "SELECT supplier_id, supplier_name, phone, address FROM suppliers " +
                    "WHERE supplier_name LIKE ? OR phone LIKE ? OR address LIKE ? ORDER BY supplier_id ASC";
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (keyword != null && !keyword.trim().isEmpty()) {
                String pattern = "%" + keyword.trim() + "%";
                stmt.setString(1, pattern);
                stmt.setString(2, pattern);
                stmt.setString(3, pattern);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Supplier s = new Supplier(
                            rs.getInt("supplier_id"),
                            rs.getString("supplier_name"),
                            rs.getString("phone"),
                            rs.getString("address"));
                    list.add(s);
                }
            }
        } catch (SQLException e) {
            logSQLException("searchSuppliers", e);
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [searchSuppliers]: " + e.getMessage());
        }
        return list;
    }

    /** Retrieves a supplier by its ID. */
    public Supplier getSupplierById(int supplierId) {
        if (supplierId <= 0) {
            System.err.println("Validation Error [getSupplierById]: Invalid supplierId (" + supplierId + "). Must be positive.");
            return null;
        }
        String sql = "SELECT supplier_id, supplier_name, phone, address FROM suppliers WHERE supplier_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, supplierId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Supplier(
                            rs.getInt("supplier_id"),
                            rs.getString("supplier_name"),
                            rs.getString("phone"),
                            rs.getString("address"));
                }
            }
        } catch (SQLException e) {
            logSQLException("getSupplierById", e);
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [getSupplierById]: " + e.getMessage());
        }
        return null;
    }

    /** Updates an existing supplier. */
    public boolean updateSupplier(Supplier supplier) {
        if (supplier == null) {
            System.err.println("Validation Error [updateSupplier]: Supplier object cannot be null.");
            return false;
        }
        if (supplier.getSupplierId() <= 0) {
            System.err.println("Validation Error [updateSupplier]: Invalid supplierId (" + supplier.getSupplierId() + ").");
            return false;
        }
        if (supplier.getSupplierName() == null || supplier.getSupplierName().trim().isEmpty()) {
            System.err.println("Validation Error [updateSupplier]: Supplier name cannot be empty or blank.");
            return false;
        }
        String sql = "UPDATE suppliers SET supplier_name = ?, phone = ?, address = ? WHERE supplier_id = ?";
        String checkSql = "SELECT 1 FROM suppliers WHERE supplier_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement upd = conn.prepareStatement(sql)) {
            upd.setString(1, supplier.getSupplierName().trim());
            upd.setString(2, supplier.getPhone());
            upd.setString(3, supplier.getAddress());
            upd.setInt(4, supplier.getSupplierId());
            int affected = upd.executeUpdate();
            if (affected > 0) return true;
            // verify existence
            try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                check.setInt(1, supplier.getSupplierId());
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) return true; // no change but exists
                }
            }
        } catch (SQLException e) {
            logSQLException("updateSupplier", e);
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [updateSupplier]: " + e.getMessage());
        }
        return false;
    }

    /** Deletes a supplier by ID. */
    public boolean deleteSupplier(int supplierId) {
        if (supplierId <= 0) {
            System.err.println("Validation Error [deleteSupplier]: Invalid supplierId (" + supplierId + "). Must be positive.");
            return false;
        }
        String sql = "DELETE FROM suppliers WHERE supplier_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, supplierId);
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            logSQLException("deleteSupplier", e);
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [deleteSupplier]: " + e.getMessage());
        }
        return false;
    }

    /** Logs SQLExceptions in a standard format. */
    private void logSQLException(String operation, SQLException e) {
        System.err.println(String.format(
                "Database Error [%s] - Code: %d, State: %s, Message: %s",
                operation, e.getErrorCode(),
                e.getSQLState() != null ? e.getSQLState() : "N/A",
                e.getMessage()));
    }
}
