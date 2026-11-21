package com.durgamedicals.dao;

import com.durgamedicals.model.Medicine;
import com.durgamedicals.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MedicineDAO.java
 * Data Access Object (DAO) for the 'medicines' table in MySQL.
 */
public class MedicineDAO {

    private static final BigDecimal MAX_DECIMAL_10_2 = new BigDecimal("99999999.99");

    /** Adds a new medicine to the inventory. */
    public boolean addMedicine(Medicine medicine) {
        if (!validateMedicine(medicine, "addMedicine")) {
            return false;
        }
        String sql = "INSERT INTO medicines (medicine_name, category, purchase_price, selling_price, "
                + "quantity, expiry_date, supplier_id, min_quantity) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                setMedicineStatementParameters(stmt, medicine);
                int affectedRows = stmt.executeUpdate();
                if (affectedRows > 0) {
                    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            int generatedId = generatedKeys.getInt(1);
                            if (generatedId > 0) {
                                conn.commit();
                                medicine.setMedicineId(generatedId);
                                return true;
                            }
                        }
                    }
                    conn.rollback();
                    System.err.println("Database Error [addMedicine]: Insert succeeded but generated ID was missing or invalid. Rolled back.");
                } else {
                    conn.rollback();
                }
            }
        } catch (SQLException e) {
            rollbackQuietly(conn, "addMedicine");
            logSQLException("addMedicine", e);
        } catch (IllegalStateException e) {
            rollbackQuietly(conn, "addMedicine");
            System.err.println("Configuration Error [addMedicine]: " + e.getMessage());
        } finally {
            closeConnectionQuietly(conn);
        }
        return false;
    }

    /** Updates an existing medicine. */
    public boolean updateMedicine(Medicine medicine) {
        if (medicine == null) {
            System.err.println("Validation Error [updateMedicine]: Medicine object cannot be null.");
            return false;
        }
        if (medicine.getMedicineId() <= 0) {
            System.err.println("Validation Error [updateMedicine]: Invalid medicineId (" + medicine.getMedicineId() + "). Must be positive.");
            return false;
        }
        if (!validateMedicine(medicine, "updateMedicine")) {
            return false;
        }
        String updateSql = "UPDATE medicines SET medicine_name = ?, category = ?, purchase_price = ?, selling_price = ?, "
                + "quantity = ?, expiry_date = ?, supplier_id = ?, min_quantity = ? WHERE medicine_id = ?";
        String checkSql = "SELECT 1 FROM medicines WHERE medicine_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
            setMedicineStatementParameters(updateStmt, medicine);
            updateStmt.setInt(9, medicine.getMedicineId());
            int affectedRows = updateStmt.executeUpdate();
            if (affectedRows > 0) {
                return true;
            }
            // verify existence
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, medicine.getMedicineId());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        return true; // exists with unchanged values
                    }
                }
            }
            System.err.println("Not Found [updateMedicine]: No medicine exists with ID " + medicine.getMedicineId());
        } catch (SQLException e) {
            logSQLException("updateMedicine", e);
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [updateMedicine]: " + e.getMessage());
        }
        return false;
    }

    /** Deletes a medicine by its ID. */
    public boolean deleteMedicine(int medicineId) {
        if (medicineId <= 0) {
            System.err.println("Validation Error [deleteMedicine]: Invalid medicineId (" + medicineId + "). Must be positive.");
            return false;
        }
        String sql = "DELETE FROM medicines WHERE medicine_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, medicineId);
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            logSQLException("deleteMedicine", e);
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [deleteMedicine]: " + e.getMessage());
        }
        return false;
    }

    /** Searches medicines by keyword. */
    public List<Medicine> searchMedicines(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllMedicines();
        }
        List<Medicine> searchResults = new ArrayList<>();
        String sql = "SELECT medicine_id, medicine_name, category, purchase_price, selling_price, quantity, expiry_date, supplier_id, min_quantity "
                + "FROM medicines WHERE medicine_name LIKE ? OR category LIKE ? ORDER BY medicine_id ASC";
        String pattern = "%" + keyword.trim() + "%";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    searchResults.add(mapResultSetToMedicine(rs));
                }
            }
        } catch (SQLException e) {
            logSQLException("searchMedicines", e);
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [searchMedicines]: " + e.getMessage());
        }
        return searchResults;
    }

    /** Retrieves all medicines. */
    public List<Medicine> getAllMedicines() {
        List<Medicine> medicineList = new ArrayList<>();
        String sql = "SELECT medicine_id, medicine_name, category, purchase_price, selling_price, quantity, expiry_date, supplier_id, min_quantity FROM medicines ORDER BY medicine_id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                medicineList.add(mapResultSetToMedicine(rs));
            }
        } catch (SQLException e) {
            logSQLException("getAllMedicines", e);
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [getAllMedicines]: " + e.getMessage());
        }
        return medicineList;
    }

    /** Retrieves a medicine by ID. */
    public Medicine getMedicineById(int medicineId) {
        if (medicineId <= 0) {
            System.err.println("Validation Error [getMedicineById]: Invalid medicineId (" + medicineId + "). Must be positive.");
            return null;
        }
        String sql = "SELECT medicine_id, medicine_name, category, purchase_price, selling_price, quantity, expiry_date, supplier_id, min_quantity FROM medicines WHERE medicine_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, medicineId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToMedicine(rs);
                }
            }
        } catch (SQLException e) {
            logSQLException("getMedicineById", e);
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [getMedicineById]: " + e.getMessage());
        }
        return null;
    }

    /** Retrieves low stock medicines. */
    public List<Medicine> getLowStockMedicines() {
        List<Medicine> lowStockList = new ArrayList<>();
        String sql = "SELECT medicine_id, medicine_name, category, purchase_price, selling_price, quantity, expiry_date, supplier_id, min_quantity "
                + "FROM medicines WHERE COALESCE(quantity, 0) <= min_quantity ORDER BY quantity ASC, medicine_name ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                lowStockList.add(mapResultSetToMedicine(rs));
            }
        } catch (SQLException e) {
            logSQLException("getLowStockMedicines", e);
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [getLowStockMedicines]: " + e.getMessage());
        }
        return lowStockList;
    }

    /** Retrieves expired medicines. */
    public List<Medicine> getExpiredMedicines() {
        List<Medicine> expiredList = new ArrayList<>();
        String sql = "SELECT medicine_id, medicine_name, category, purchase_price, selling_price, quantity, expiry_date, supplier_id, min_quantity "
                + "FROM medicines WHERE expiry_date IS NOT NULL AND expiry_date < CURRENT_DATE ORDER BY expiry_date ASC, medicine_name ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                expiredList.add(mapResultSetToMedicine(rs));
            }
        } catch (SQLException e) {
            logSQLException("getExpiredMedicines", e);
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [getExpiredMedicines]: " + e.getMessage());
        }
        return expiredList;
    }

    /** Deducts stock for a given medicine within the provided connection. */
    public boolean deductStock(Connection conn, int medicineId, int qty) throws SQLException {
        if (conn == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }
        if (medicineId <= 0) {
            throw new IllegalArgumentException("Invalid medicineId for stock deduction.");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity to deduct must be positive.");
        }
        String sql = "UPDATE medicines SET quantity = quantity - ? WHERE medicine_id = ? AND quantity >= ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, qty);
            stmt.setInt(2, medicineId);
            stmt.setInt(3, qty);
            return stmt.executeUpdate() == 1;
        }
    }

    // ── Private Helper Methods ────────────────────────────────────────────
    private boolean validateMedicine(Medicine medicine, String operation) {
        if (medicine == null) {
            System.err.println("Validation Error [" + operation + "]: Medicine object cannot be null.");
            return false;
        }
        if (medicine.getMedicineName() == null || medicine.getMedicineName().trim().isEmpty()) {
            System.err.println("Validation Error [" + operation + "]: Medicine name cannot be empty or blank.");
            return false;
        }
        if (medicine.getMedicineName().trim().length() > 150) {
            System.err.println("Validation Error [" + operation + "]: Medicine name exceeds maximum length of 150 characters.");
            return false;
        }
        if (medicine.getCategory() != null && medicine.getCategory().trim().length() > 100) {
            System.err.println("Validation Error [" + operation + "]: Category exceeds maximum length of 100 characters.");
            return false;
        }
        if (!validateDecimal10_2(medicine.getPurchasePrice(), "Purchase price", operation)) {
            return false;
        }
        if (!validateDecimal10_2(medicine.getSellingPrice(), "Selling price", operation)) {
            return false;
        }
        if (medicine.getQuantity() != null && medicine.getQuantity() < 0) {
            System.err.println("Validation Error [" + operation + "]: Quantity cannot be negative (" + medicine.getQuantity() + ").");
            return false;
        }
        if (medicine.getMinQuantity() < 0) {
            System.err.println("Validation Error [" + operation + "]: Minimum quantity cannot be negative (" + medicine.getMinQuantity() + ").");
            return false;
        }
        if (medicine.getSupplierId() != null && medicine.getSupplierId() <= 0) {
            System.err.println("Validation Error [" + operation + "]: Supplier ID must be positive (" + medicine.getSupplierId() + ").");
            return false;
        }
        return true;
    }

    private boolean validateDecimal10_2(BigDecimal amount, String fieldName, String operation) {
        if (amount == null) {
            return true;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            System.err.println("Validation Error [" + operation + "]: " + fieldName + " cannot be negative (" + amount + ").");
            return false;
        }
        if (amount.compareTo(MAX_DECIMAL_10_2) > 0) {
            System.err.println("Validation Error [" + operation + "]: " + fieldName + " exceeds maximum allowed value of 99,999,999.99 (" + amount + ").");
            return false;
        }
        if (amount.scale() > 2) {
            System.err.println("Validation Error [" + operation + "]: " + fieldName + " cannot have more than 2 decimal places (" + amount + ").");
            return false;
        }
        return true;
    }

    private Medicine mapResultSetToMedicine(ResultSet rs) throws SQLException {
        int id = rs.getInt("medicine_id");
        String name = rs.getString("medicine_name");
        String category = rs.getString("category");
        BigDecimal purchasePrice = rs.getBigDecimal("purchase_price");
        BigDecimal sellingPrice = rs.getBigDecimal("selling_price");
        int qtyVal = rs.getInt("quantity");
        Integer quantity = rs.wasNull() ? null : qtyVal;
        Date sqlDate = rs.getDate("expiry_date");
        LocalDate expiryDate = (sqlDate != null) ? sqlDate.toLocalDate() : null;
        int supplierIdVal = rs.getInt("supplier_id");
        Integer supplierId = rs.wasNull() ? null : supplierIdVal;
        int minQuantity = rs.getInt("min_quantity");
        return new Medicine(id, name, category, purchasePrice, sellingPrice, quantity, expiryDate, supplierId, minQuantity);
    }

    private void setMedicineStatementParameters(PreparedStatement stmt, Medicine medicine) throws SQLException {
        stmt.setString(1, medicine.getMedicineName().trim());
        if (medicine.getCategory() != null && !medicine.getCategory().trim().isEmpty()) {
            stmt.setString(2, medicine.getCategory().trim());
        } else {
            stmt.setNull(2, Types.VARCHAR);
        }
        if (medicine.getPurchasePrice() != null) {
            stmt.setBigDecimal(3, medicine.getPurchasePrice());
        } else {
            stmt.setNull(3, Types.DECIMAL);
        }
        if (medicine.getSellingPrice() != null) {
            stmt.setBigDecimal(4, medicine.getSellingPrice());
        } else {
            stmt.setNull(4, Types.DECIMAL);
        }
        if (medicine.getQuantity() != null) {
            stmt.setInt(5, medicine.getQuantity());
        } else {
            stmt.setNull(5, Types.INTEGER);
        }
        if (medicine.getExpiryDate() != null) {
            stmt.setDate(6, Date.valueOf(medicine.getExpiryDate()));
        } else {
            stmt.setNull(6, Types.DATE);
        }
        if (medicine.getSupplierId() != null) {
            stmt.setInt(7, medicine.getSupplierId());
        } else {
            stmt.setNull(7, Types.INTEGER);
        }
        stmt.setInt(8, medicine.getMinQuantity());
    }

    private void rollbackQuietly(Connection conn, String operation) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                System.err.println("Rollback Warning [" + operation + "]: Failed to rollback transaction: " + ex.getMessage());
            }
        }
    }

    private void closeConnectionQuietly(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException ex) {
                System.err.println("Close Connection Warning: " + ex.getMessage());
            }
        }
    }

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
