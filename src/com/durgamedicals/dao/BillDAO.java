package com.durgamedicals.dao;

import com.durgamedicals.model.Bill;
import com.durgamedicals.model.BillItem;
import com.durgamedicals.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.sql.Timestamp;



import java.sql.Types;
import com.durgamedicals.dao.MedicineDAO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BillDAO.java
 * Data Access Object (DAO) for the 'bills' and 'bill_items' tables in MySQL.
 * Handles invoice persistence, transaction-safe multi-item billing, and search operations.
 *
 * Database Tables:
 *   1. bills:
 *      - bill_id        (INT, PK, AUTO_INCREMENT)
 *      - bill_date      (DATETIME, NULL, DEFAULT CURRENT_TIMESTAMP)
 *      - customer_name  (VARCHAR(100), NULL)
 *      - customer_phone (VARCHAR(15), NULL)
 *      - subtotal       (DECIMAL(10,2), NOT NULL)
 *      - tax_amount     (DECIMAL(10,2), NULL, DEFAULT 0.00)
 *      - total_amount   (DECIMAL(10,2), NOT NULL)
 *      - discount       (DECIMAL(10,2), NOT NULL, DEFAULT 0.00)
 *      - cgst_amount    (DECIMAL(10,2), NOT NULL, DEFAULT 0.00)
 *      - sgst_amount    (DECIMAL(10,2), NOT NULL, DEFAULT 0.00)
 *
 *   2. bill_items:
 *      - item_id       (INT, PK, AUTO_INCREMENT)
 *      - bill_id       (INT, NOT NULL, FK)
 *      - medicine_id   (INT, NOT NULL, FK)
 *      - quantity      (INT, NOT NULL)
 *      - price         (DECIMAL(10,2), NOT NULL)
 *      - amount        (DECIMAL(10,2), NOT NULL)
 *      - medicine_name (VARCHAR(150), NULL)
 */
public class BillDAO {

    // Maximum value allowed by MySQL column DECIMAL(10,2)
    private static final BigDecimal MAX_DECIMAL_10_2 =
            new BigDecimal("99999999.99");

    /**
     * Saves a bill and all its line items using a caller-managed database Connection.
     *
     * Transaction & Resource Rules:
     *   1. Uses the supplied Connection; does NOT commit, rollback, or close the Connection.
     *   2. Requires autoCommit to be disabled (false) on the connection; throws SQLException if enabled.
     *   3. Inserts the bill header and all bill items using PreparedStatement.
     *   4. Captures auto-generated primary keys for the bill and each line item.
     *   5. Does NOT mutate the in-memory Bill or BillItem objects with generated IDs.
     *      Instead, returns a SaveResult holding the generated keys.
     *   6. The caller must call SaveResult.applyGeneratedIds() ONLY AFTER conn.commit() completes.
     *   7. Throws SQLException immediately on any database failure, leaving transaction control
     *      to the caller.
     *
     * @param conn  An active, open Connection with autoCommit set to false.
     * @param bill  The Bill header to insert.
     * @param items The list of BillItem objects to insert.
     * @return SaveResult containing the generated keys, ready to be applied after commit.
     * @throws SQLException             If database insert or key retrieval fails, or if autoCommit is true.
     * @throws IllegalArgumentException If conn is null or validation fails.
     */
    public SaveResult saveBill(Connection conn, Bill bill, List<BillItem> items)
            throws SQLException {

        if (conn == null) {
            throw new IllegalArgumentException("Database connection cannot be null.");
        }

        if (conn.isClosed()) {
            throw new SQLException("Cannot save bill: database connection is closed.");
        }

        if (conn.getAutoCommit()) {
            throw new SQLException(
                    "Cannot save bill: database connection must have autoCommit disabled (setAutoCommit(false)).");
        }

        if (!validateBill(bill, items, "saveBill")) {
            throw new IllegalArgumentException("Bill validation failed. See error log for details.");
        }

        String insertBillSql =
                "INSERT INTO bills (bill_date, customer_name, customer_phone, subtotal, "
                + "tax_amount, total_amount, discount, cgst_amount, sgst_amount) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String insertItemSql =
                "INSERT INTO bill_items (bill_id, medicine_id, quantity, price, amount, medicine_name) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        int tempGeneratedBillId = -1;
        List<Integer> tempGeneratedItemIds = new ArrayList<>(items.size());

        // 1. Insert Bill Header
        try (PreparedStatement billStmt =
                     conn.prepareStatement(insertBillSql, Statement.RETURN_GENERATED_KEYS)) {

            setBillStatementParameters(billStmt, bill);

            int billRows = billStmt.executeUpdate();

            if (billRows <= 0) {
                throw new SQLException("Failed to insert bill header; no rows affected.");
            }

            try (ResultSet rs = billStmt.getGeneratedKeys()) {
                if (rs.next()) {
                    tempGeneratedBillId = rs.getInt(1);
                }
            }

            if (tempGeneratedBillId <= 0) {
                throw new SQLException(
                        "Bill header inserted successfully, but no valid auto-generated bill_id was returned.");
            }
        }

        // 2. Insert All Bill Items
        try (PreparedStatement itemStmt =
                     conn.prepareStatement(insertItemSql, Statement.RETURN_GENERATED_KEYS)) {

            for (int i = 0; i < items.size(); i++) {

                BillItem item = items.get(i);

                itemStmt.setInt(1, tempGeneratedBillId);
                itemStmt.setInt(2, item.getMedicineId());
                itemStmt.setInt(3, item.getQuantity());
                itemStmt.setBigDecimal(4, item.getPrice());
                itemStmt.setBigDecimal(5, item.getAmount());

                if (item.getMedicineName() != null
                        && !item.getMedicineName().trim().isEmpty()) {
                    itemStmt.setString(6, item.getMedicineName().trim());
                } else {
                    itemStmt.setNull(6, Types.VARCHAR);
                }

                int itemRows = itemStmt.executeUpdate();

                if (itemRows <= 0) {
                    throw new SQLException("Failed to insert bill item at index " + i
                            + " (Medicine ID: " + item.getMedicineId() + "); no rows affected.");
                }

                int tempItemId = -1;

                try (ResultSet itemKeys = itemStmt.getGeneratedKeys()) {
                    if (itemKeys.next()) {
                        tempItemId = itemKeys.getInt(1);
                    }
                }

                if (tempItemId <= 0) {
                    throw new SQLException(
                            "Missing or invalid generated item_id at item index " + i + ".");
                }

                tempGeneratedItemIds.add(tempItemId);
            }
        }

        // ---- Stock deduction step ----
        // Deduct purchased quantities from inventory within the same transaction.
        // If any deduction fails (e.g., insufficient stock), an exception is thrown
        // causing the surrounding transaction to roll back.
        MedicineDAO medicineDAO = new MedicineDAO();
        for (BillItem item : items) {
            boolean deducted = medicineDAO.deductStock(conn, item.getMedicineId(), item.getQuantity());
            if (!deducted) {
                throw new SQLException("Insufficient stock for medicine_id=" + item.getMedicineId());
            }
        }


        return new SaveResult(tempGeneratedBillId, tempGeneratedItemIds, bill, items);
    }

    /**
     * Standalone convenience method: saves a bill along with all its line items
     * within a self-managed database transaction.
     *
     * Delegates SQL execution to {@link #saveBill(Connection, Bill, List)}.
     * Manages its own Connection: sets autoCommit(false), commits on success,
     * rolls back on failure, applies generated IDs only after commit, and closes
     * the connection quietly in a finally block.
     *
     * @param bill  The Bill header object to save.
     * @param items The List of BillItem objects belonging to this bill.
     * @return true if the bill and all items were successfully saved and committed, false otherwise.
     */
    public boolean saveBill(Bill bill, List<BillItem> items) {

        Connection conn = null;

        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            SaveResult result = saveBill(conn, bill, items);

            conn.commit();

            // Assign generated IDs ONLY AFTER successful commit
            result.applyGeneratedIds();

            return true;

        } catch (SQLException e) {
            rollbackQuietly(conn, "saveBill");
            logSQLException("saveBill", e);

        } catch (IllegalStateException e) {
            rollbackQuietly(conn, "saveBill");
            System.err.println("Configuration Error [saveBill]: " + e.getMessage());

        } catch (IllegalArgumentException e) {
            rollbackQuietly(conn, "saveBill");
            System.err.println("Validation Error [saveBill]: " + e.getMessage());

        } finally {
            closeConnectionQuietly(conn);
        }

        return false;
    }

    /**
     * Retrieves a single bill by its unique ID.
     *
     * @param billId The primary key ID of the bill.
     * @return The matching Bill object, or null if not found or on error.
     */
    public Bill getBillById(int billId) {

        if (billId <= 0) {
            System.err.println("Validation Error [getBillById]: Invalid billId ("
                    + billId + "). Must be positive.");
            return null;
        }

        String sql = "SELECT bill_id, bill_date, customer_name, customer_phone, subtotal, "
                + "tax_amount, total_amount, discount, cgst_amount, sgst_amount "
                + "FROM bills WHERE bill_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, billId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToBill(rs);
                }
            }

        } catch (SQLException e) {
            logSQLException("getBillById", e);

        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [getBillById]: " + e.getMessage());
        }

        return null;
    }

    /**
     * Retrieves all items belonging to a specific bill.
     *
     * @param billId The ID of the bill whose items are to be fetched.
     * @return A List of BillItem objects. Returns an empty list if none exist or on error.
     */
    public List<BillItem> getItemsByBillId(int billId) {

        List<BillItem> itemList = new ArrayList<>();

        if (billId <= 0) {
            System.err.println("Validation Error [getItemsByBillId]: Invalid billId ("
                    + billId + "). Must be positive.");
            return itemList;
        }

        String sql = "SELECT item_id, bill_id, medicine_id, quantity, price, amount, medicine_name "
                + "FROM bill_items WHERE bill_id = ? ORDER BY item_id ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, billId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    itemList.add(mapResultSetToBillItem(rs));
                }
            }

        } catch (SQLException e) {
            logSQLException("getItemsByBillId", e);

        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [getItemsByBillId]: " + e.getMessage());
        }

        return itemList;
    }

    /**
     * Retrieves all bills from the database, ordered by bill date descending (newest bills first).
     *
     * @return A List of Bill objects. Returns an empty list if none exist or on error.
     */
    public List<Bill> getAllBills() {

        List<BillItem> ignoredList;
        List<Bill> billList = new ArrayList<>();

        String sql = "SELECT bill_id, bill_date, customer_name, customer_phone, subtotal, "
                + "tax_amount, total_amount, discount, cgst_amount, sgst_amount "
                + "FROM bills ORDER BY bill_date DESC, bill_id DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                billList.add(mapResultSetToBill(rs));
            }

        } catch (SQLException e) {
            logSQLException("getAllBills", e);

        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [getAllBills]: " + e.getMessage());
        }

        return billList;
    }

    /**
     * Searches bills by bill ID (exact match if keyword is a number),
     * or partial match on customer name or customer phone.
     *
     * @param keyword Search term. If empty or blank, returns all bills.
     * @return A List of matching Bill objects, ordered newest first.
     */
    public List<Bill> searchBills(String keyword) {

        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllBills();
        }

        String trimmed = keyword.trim();
        List<Bill> results = new ArrayList<>();

        // Check if keyword is an integer for direct bill_id lookup
        Integer searchId = null;

        try {
            searchId = Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
            // Not a numeric ID; search only by name/phone
        }

        StringBuilder sql = new StringBuilder(
                "SELECT bill_id, bill_date, customer_name, customer_phone, subtotal, "
                + "tax_amount, total_amount, discount, cgst_amount, sgst_amount "
                + "FROM bills WHERE customer_name LIKE ? OR customer_phone LIKE ? "
        );

        if (searchId != null) {
            sql.append("OR bill_id = ? ");
        }

        sql.append("ORDER BY bill_date DESC, bill_id DESC");

        String pattern = "%" + trimmed + "%";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            stmt.setString(1, pattern);
            stmt.setString(2, pattern);

            if (searchId != null) {
                stmt.setInt(3, searchId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToBill(rs));
                }
            }

        } catch (SQLException e) {
            logSQLException("searchBills", e);

        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [searchBills]: " + e.getMessage());
        }

        return results;
    }

    // ── Supporting Result Class ───────────────────────────────────────────

    /**
     * Holds generated database primary keys from a connection-aware save operation.
     * Prevents premature mutation of in-memory Java objects until the transaction
     * coordinator explicitly commits the database transaction.
     */
    public static class SaveResult {

        private final int generatedBillId;
        private final List<Integer> generatedItemIds;
        private final Bill bill;
        private final List<BillItem> items;
        private boolean applied;

        /**
         * Private constructor: only BillDAO can instantiate valid SaveResult objects.
         * Stores defensive copies of the lists while retaining direct references to the
         * original BillItem objects for post-commit ID assignment.
         */
        private SaveResult(
                int generatedBillId,
                List<Integer> generatedItemIds,
                Bill bill,
                List<BillItem> items) {

            this.generatedBillId = generatedBillId;

            this.generatedItemIds = (generatedItemIds != null)
                    ? Collections.unmodifiableList(new ArrayList<>(generatedItemIds))
                    : Collections.emptyList();

            this.bill = bill;

            this.items = (items != null)
                    ? Collections.unmodifiableList(new ArrayList<>(items))
                    : Collections.emptyList();

            this.applied = false;
        }

        public int getGeneratedBillId() {
            return generatedBillId;
        }

        public List<Integer> getGeneratedItemIds() {
            return generatedItemIds;
        }

        public synchronized boolean isApplied() {
            return applied;
        }

        /**
         * Assigns the generated primary keys to the in-memory Bill and BillItem objects.
         * MUST be invoked ONLY AFTER the database transaction has committed successfully.
         *
         * Validates all IDs and items before mutating any object. If any validation
         * fails, throws IllegalStateException and leaves all objects unmutated.
         *
         * @throws IllegalStateException If bill ID is invalid, counts mismatch, or any item is null/non-positive.
         */
        public synchronized void applyGeneratedIds() {

            if (applied) {
                return;
            }

            // 1. Validate generated bill ID
            if (generatedBillId <= 0) {
                throw new IllegalStateException(
                        "Cannot apply generated IDs: invalid generated bill ID ("
                                + generatedBillId + ").");
            }

            // 2. Validate Bill reference presence
            if (bill == null) {
                throw new IllegalStateException(
                        "Cannot apply generated IDs: Bill object is null.");
            }

            // 3. Validate items count matches generatedItemIds count
            if (items.size() != generatedItemIds.size()) {
                throw new IllegalStateException(String.format(
                        "Cannot apply generated IDs: item count mismatch (items: %d, generated IDs: %d).",
                        items.size(), generatedItemIds.size()
                ));
            }

            // 4. Validate every generated item ID is positive and item reference is non-null
            for (int i = 0; i < generatedItemIds.size(); i++) {

                Integer itemId = generatedItemIds.get(i);

                if (itemId == null || itemId <= 0) {
                    throw new IllegalStateException(
                            "Cannot apply generated IDs: invalid generated item ID at index "
                                    + i + " (" + itemId + ").");
                }

                if (items.get(i) == null) {
                    throw new IllegalStateException(
                            "Cannot apply generated IDs: BillItem at index " + i + " is null.");
                }
            }

            // 5. Mutation step: mutate objects ONLY after all validations have passed
            bill.setBillId(generatedBillId);

            for (int i = 0; i < items.size(); i++) {
                BillItem item = items.get(i);
                item.setBillId(generatedBillId);
                item.setItemId(generatedItemIds.get(i));
            }

            // 6. Mark applied only after successful assignment
            this.applied = true;
        }
    }

    // ── Private Helper Methods ────────────────────────────────────────────

    /**
     * Validates domain and schema constraints on the Bill and its items before database insertion.
     */
    private boolean validateBill(Bill bill, List<BillItem> items, String operation) {

        if (bill == null) {
            System.err.println("Validation Error [" + operation + "]: Bill header cannot be null.");
            return false;
        }

        if (items == null || items.isEmpty()) {
            System.err.println("Validation Error [" + operation + "]: Bill must contain at least one item.");
            return false;
        }

        // Customer name length bound (VARCHAR(100))
        if (bill.getCustomerName() != null && bill.getCustomerName().trim().length() > 100) {
            System.err.println("Validation Error [" + operation + "]: Customer name exceeds maximum length of 100 characters.");
            return false;
        }

        // Customer phone length bound (VARCHAR(15))
        if (bill.getCustomerPhone() != null && bill.getCustomerPhone().trim().length() > 15) {
            System.err.println("Validation Error [" + operation + "]: Customer phone exceeds maximum length of 15 characters.");
            return false;
        }

        // Monetary validations for Bill header
        if (!validateRequiredDecimal(bill.getSubtotal(), "Subtotal", operation)) return false;
        if (!validateRequiredDecimal(bill.getTotalAmount(), "Total amount", operation)) return false;
        if (!validateOptionalDecimal(bill.getTaxAmount(), "Tax amount", operation)) return false;
        if (!validateOptionalDecimal(bill.getDiscount(), "Discount", operation)) return false;
        if (!validateOptionalDecimal(bill.getCgstAmount(), "CGST amount", operation)) return false;
        if (!validateOptionalDecimal(bill.getSgstAmount(), "SGST amount", operation)) return false;

        // Line-item validations
        for (int i = 0; i < items.size(); i++) {

            BillItem item = items.get(i);

            if (item == null) {
                System.err.println("Validation Error [" + operation + "]: Item at index " + i + " cannot be null.");
                return false;
            }

            if (item.getMedicineId() <= 0) {
                System.err.println("Validation Error [" + operation + "]: Invalid medicine ID ("
                        + item.getMedicineId() + ") at item index " + i);
                return false;
            }

            if (item.getQuantity() <= 0) {
                System.err.println("Validation Error [" + operation + "]: Item quantity must be positive at item index " + i);
                return false;
            }

            if (!validateRequiredDecimal(item.getPrice(), "Item price", operation)) return false;
            if (!validateRequiredDecimal(item.getAmount(), "Item amount", operation)) return false;

            if (item.getMedicineName() != null && item.getMedicineName().trim().length() > 150) {
                System.err.println("Validation Error [" + operation + "]: Item medicine name exceeds 150 characters at index " + i);
                return false;
            }
        }

        return true;
    }

    private boolean validateRequiredDecimal(
            BigDecimal value, String fieldName, String operation) {

        if (value == null) {
            System.err.println("Validation Error [" + operation + "]: "
                    + fieldName + " is required and cannot be null.");
            return false;
        }

        return checkDecimalBounds(value, fieldName, operation);
    }

    private boolean validateOptionalDecimal(
            BigDecimal value, String fieldName, String operation) {

        if (value == null) {
            return true;
        }

        return checkDecimalBounds(value, fieldName, operation);
    }

    private boolean checkDecimalBounds(
            BigDecimal value, String fieldName, String operation) {

        if (value.compareTo(BigDecimal.ZERO) < 0) {
            System.err.println("Validation Error [" + operation + "]: "
                    + fieldName + " cannot be negative (" + value + ").");
            return false;
        }

        if (value.compareTo(MAX_DECIMAL_10_2) > 0) {
            System.err.println("Validation Error [" + operation + "]: "
                    + fieldName + " exceeds maximum limit 99,999,999.99 (" + value + ").");
            return false;
        }

        if (value.scale() > 2) {
            System.err.println("Validation Error [" + operation + "]: "
                    + fieldName + " cannot have more than 2 decimal places (" + value + ").");
            return false;
        }

        return true;
    }

    /**
     * Binds Bill header values to an INSERT statement.
     */
    private void setBillStatementParameters(PreparedStatement stmt, Bill bill)
            throws SQLException {

        // bill_date (nullable DATETIME)
        if (bill.getBillDate() != null) {
            stmt.setTimestamp(1, Timestamp.valueOf(bill.getBillDate()));
        } else {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
        }

        // customer_name (nullable VARCHAR(100))
        if (bill.getCustomerName() != null && !bill.getCustomerName().trim().isEmpty()) {
            stmt.setString(2, bill.getCustomerName().trim());
        } else {
            stmt.setNull(2, Types.VARCHAR);
        }

        // customer_phone (nullable VARCHAR(15))
        if (bill.getCustomerPhone() != null && !bill.getCustomerPhone().trim().isEmpty()) {
            stmt.setString(3, bill.getCustomerPhone().trim());
        } else {
            stmt.setNull(3, Types.VARCHAR);
        }

        stmt.setBigDecimal(4, bill.getSubtotal());
        stmt.setBigDecimal(5, (bill.getTaxAmount() != null) ? bill.getTaxAmount() : BigDecimal.ZERO);
        stmt.setBigDecimal(6, bill.getTotalAmount());
        stmt.setBigDecimal(7, (bill.getDiscount() != null) ? bill.getDiscount() : BigDecimal.ZERO);
        stmt.setBigDecimal(8, (bill.getCgstAmount() != null) ? bill.getCgstAmount() : BigDecimal.ZERO);
        stmt.setBigDecimal(9, (bill.getSgstAmount() != null) ? bill.getSgstAmount() : BigDecimal.ZERO);
    }

    /**
     * Maps a ResultSet row to a Bill model object.
     */
    private Bill mapResultSetToBill(ResultSet rs) throws SQLException {

        int id = rs.getInt("bill_id");
        Timestamp ts = rs.getTimestamp("bill_date");
        LocalDateTime billDate = (ts != null) ? ts.toLocalDateTime() : null;

        String customerName = rs.getString("customer_name");
        String customerPhone = rs.getString("customer_phone");

        BigDecimal subtotal = rs.getBigDecimal("subtotal");
        BigDecimal taxAmount = rs.getBigDecimal("tax_amount");
        BigDecimal totalAmount = rs.getBigDecimal("total_amount");
        BigDecimal discount = rs.getBigDecimal("discount");
        BigDecimal cgstAmount = rs.getBigDecimal("cgst_amount");
        BigDecimal sgstAmount = rs.getBigDecimal("sgst_amount");

        return new Bill(id, billDate, customerName, customerPhone, subtotal,
                taxAmount, totalAmount, discount, cgstAmount, sgstAmount);
    }

    /**
     * Maps a ResultSet row to a BillItem model object.
     */
    private BillItem mapResultSetToBillItem(ResultSet rs) throws SQLException {

        int itemId = rs.getInt("item_id");
        int billId = rs.getInt("bill_id");
        int medicineId = rs.getInt("medicine_id");
        int quantity = rs.getInt("quantity");

        BigDecimal price = rs.getBigDecimal("price");
        BigDecimal amount = rs.getBigDecimal("amount");
        String medicineName = rs.getString("medicine_name");

        return new BillItem(itemId, billId, medicineId, quantity, price, amount, medicineName);
    }

    private void rollbackQuietly(Connection conn, String operation) {

        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                System.err.println("Rollback Warning [" + operation
                        + "]: Failed to rollback transaction: " + ex.getMessage());
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