package com.durgamedicals.ui;

import com.durgamedicals.dao.MedicineDAO;
import com.durgamedicals.model.Bill;
import com.durgamedicals.model.BillItem;
import com.durgamedicals.model.Medicine;
import com.durgamedicals.service.BillingService;
import com.durgamedicals.service.BillingService.CalculationResult;
import com.durgamedicals.service.BillingService.GSTRates;
import com.durgamedicals.service.BillingService.BillingConfigurationException;
import com.durgamedicals.service.BillingService.BillingDatabaseException;
import com.durgamedicals.util.CurrencyUtil;
import com.durgamedicals.util.FontUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * BillingPanel – full functional UI for creating a pharmacy bill.
 *
 * Features:
 *   • Medicine selector with live search (client‑side filtering)
 *   • Quantity spinner (validated against available stock)
 *   • Bill-level discount input with live recalculation
 *   • Add Item button – adds a line to the cart table
 *   • Cart table shows: ID, Name, Price, Qty, Subtotal, Remove button
 *   • Live financial breakdown: Subtotal, Discount, CGST (rate + amount), SGST (rate + amount), Grand Total
 *   • Consistent GST calculation via BillingService using saved settings
 *   • Graceful handling & red error warning when GST rates are unconfigured
 *   • Clear Cart and Save Bill actions
 *   • Displays generated bill ID on successful save and refreshes medicine stock
 *
 * All UI colours follow the project theme and fonts are loaded via FontUtil.
 */
public class BillingPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    // ── Theme colours ───────────────────────────────────────────────────────
    public static final Color BACKGROUND_COLOR = Color.WHITE;
    public static final Color HEADING_COLOR    = Color.decode("#171717"); // black
    public static final Color BUTTON_COLOR     = Color.decode("#A3B18A"); // Hazel Green
    public static final Color ERROR_COLOR      = Color.decode("#D9534F"); // Red warning/error

    // ── Services & data ───────────────────────────────────────────────────
    private final BillingService billingService = new BillingService();
    private final MedicineDAO    medicineDAO    = new MedicineDAO();

    // All medicines loaded from database and filtered locally.
    private final List<Medicine> allMedicines = new ArrayList<>();

    // UI components
    private final JTextField tfCustomerName = new JTextField(15);
    private final JTextField tfCustomerPhone = new JTextField(15);
    private final JComboBox<Medicine> cbMedicines = new JComboBox<>();
    private final JTextField tfSearch = new JTextField(15);
    private final JSpinner spQuantity = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
    private final JTextField tfDiscount = new JTextField("0.00", 15);
    private final JButton btnAddItem = new JButton("Add Item");
    private final JTable tblCart;
    private final DefaultTableModel cartModel;

    // Summary & Totals components
    private final JLabel lblSubtotal = new JLabel("Subtotal: ₹0.00");
    private final JLabel lblDiscount = new JLabel("Discount: ₹0.00");
    private final JLabel lblCgst = new JLabel("CGST: ₹0.00");
    private final JLabel lblSgst = new JLabel("SGST: ₹0.00");
    private final JLabel lblGrandTotal = new JLabel("Grand Total: ₹0.00");
    private final JLabel lblStatus = new JLabel("");
    private final JButton btnClear = new JButton("Clear");
    private final JButton btnSave = new JButton("Save Bill");
    private final JButton btnPrint = new JButton("Print Bill");
    
    // New button for printing the last saved bill
    
    // Holds the most recently saved bill and its items for printing
    private Bill lastSavedBill = null;
    private List<BillItem> lastSavedItems = null;

    // In‑memory cart representation – kept in sync with the table model.
    private final List<BillItem> cartItems = new ArrayList<>();

    public BillingPanel() {
        setBackground(BACKGROUND_COLOR);
        setLayout(new BorderLayout());

        // ---------- Header ----------
        JLabel heading = new JLabel("Billing", SwingConstants.CENTER);
        heading.setFont(FontUtil.getRalewayBold(24f));
        heading.setForeground(HEADING_COLOR);
        heading.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        add(heading, BorderLayout.NORTH);

        // ---------- Form (left side) ----------
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(BACKGROUND_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // Row 0 – Customer Name
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Customer Name:"), gbc);
        gbc.gridx = 1;
        formPanel.add(tfCustomerName, gbc);

        // Row 1 – Customer Phone
        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Customer Phone:"), gbc);
        gbc.gridx = 1;
        formPanel.add(tfCustomerPhone, gbc);

        // Row 2 – Search box
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Search Medicine:"), gbc);
        gbc.gridx = 1;
        formPanel.add(tfSearch, gbc);

        // Row 3 – Medicine selector
        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Medicine:"), gbc);
        gbc.gridx = 1;
        cbMedicines.setRenderer(new MedicineRenderer());
        formPanel.add(cbMedicines, gbc);

        // Row 4 – Quantity spinner
        gbc.gridx = 0; gbc.gridy = 4;
        formPanel.add(new JLabel("Quantity:"), gbc);
        gbc.gridx = 1;
        spQuantity.setPreferredSize(new Dimension(80, spQuantity.getPreferredSize().height));
        formPanel.add(spQuantity, gbc);

        // Row 5 – Discount input field
        gbc.gridx = 0; gbc.gridy = 5;
        formPanel.add(new JLabel("Discount (₹):"), gbc);
        gbc.gridx = 1;
        formPanel.add(tfDiscount, gbc);

        // Row 6 – Add Item button
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        btnAddItem.setBackground(BUTTON_COLOR);
        btnAddItem.setForeground(Color.WHITE);
        btnAddItem.setFocusPainted(false);
        btnAddItem.setFont(FontUtil.getRalewayBold(13f));
        formPanel.add(btnAddItem, gbc);

        add(formPanel, BorderLayout.WEST);

        // ---------- Cart Table ----------
        String[] columns = {"ID", "Name", "Price (₹)", "Qty", "Subtotal (₹)", "Remove"};
        cartModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return col == 5; }
        };
        tblCart = new JTable(cartModel);
        tblCart.setRowHeight(28);
        tblCart.getColumn("Remove").setCellRenderer(new ButtonRenderer());
        tblCart.getColumn("Remove").setCellEditor(new ButtonEditor(new JCheckBox()));
        JScrollPane scroll = new JScrollPane(tblCart);
        scroll.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(scroll, BorderLayout.CENTER);

        // ---------- Summary & Action Buttons ----------
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 8));
        bottomPanel.setBackground(BACKGROUND_COLOR);
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.decode("#E0E0E0")),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));

        // Summary row (financial breakdown)
        JPanel summaryPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 4));
        summaryPanel.setBackground(BACKGROUND_COLOR);

        lblSubtotal.setFont(FontUtil.getRalewayBold(13f));
        lblDiscount.setFont(FontUtil.getRalewayBold(13f));
        lblCgst.setFont(FontUtil.getRalewayBold(13f));
        lblSgst.setFont(FontUtil.getRalewayBold(13f));
        lblGrandTotal.setFont(FontUtil.getRalewayBold(16f));
        lblGrandTotal.setForeground(HEADING_COLOR);

        summaryPanel.add(lblSubtotal);
        summaryPanel.add(lblDiscount);
        summaryPanel.add(lblCgst);
        summaryPanel.add(lblSgst);
        summaryPanel.add(lblGrandTotal);
        bottomPanel.add(summaryPanel, BorderLayout.NORTH);

        // Actions row (status warning on left, buttons on right)
        JPanel actionPanel = new JPanel(new BorderLayout(10, 0));
        actionPanel.setBackground(BACKGROUND_COLOR);

        lblStatus.setFont(FontUtil.getRalewayBold(12f));
        lblStatus.setForeground(ERROR_COLOR);
        actionPanel.add(lblStatus, BorderLayout.CENTER);

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnGroup.setBackground(BACKGROUND_COLOR);

        btnClear.setBackground(BUTTON_COLOR);
        btnClear.setForeground(Color.WHITE);
        btnClear.setFocusPainted(false);
        btnClear.setFont(FontUtil.getRalewayBold(12f));
        btnGroup.add(btnClear);

        btnSave.setBackground(BUTTON_COLOR);
        btnSave.setForeground(Color.WHITE);
        btnSave.setFocusPainted(false);
        btnSave.setFont(FontUtil.getRalewayBold(12f));
        btnGroup.add(btnSave);

        actionPanel.add(btnGroup, BorderLayout.EAST);
        bottomPanel.add(actionPanel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        // ---------- Initialize Data & Listeners ----------
        reloadMedicines();
        attachListeners();
        recalcTotals();
    }

    /** Reload all medicines from the database and refresh the selector. */
    public void reloadMedicines() {
        allMedicines.clear();
        List<Medicine> fresh = medicineDAO.getAllMedicines();
        if (fresh != null) {
            allMedicines.addAll(fresh);
        }
        filterMedicines(tfSearch.getText());
    }

    /** Populate the combo box with a list of medicines. */
    private void populateMedicineComboBox(List<Medicine> meds) {
        DefaultComboBoxModel<Medicine> model = new DefaultComboBoxModel<>();
        for (Medicine m : meds) {
            model.addElement(m);
        }
        cbMedicines.setModel(model);
        if (model.getSize() > 0) {
            cbMedicines.setSelectedIndex(0);
        }
    }

    /** Filter medicines based on the search field (client‑side). */
    private void filterMedicines(String keyword) {
        if (keyword == null) keyword = "";
        String lower = keyword.toLowerCase();
        List<Medicine> filtered = new ArrayList<>();
        for (Medicine m : allMedicines) {
            if (m.getMedicineName() != null && m.getMedicineName().toLowerCase().contains(lower)) {
                filtered.add(m);
            }
        }
        populateMedicineComboBox(filtered);
    }

    /** Re‑calculate financial totals and GST tax breakdown live. */
    private void recalcTotals() {
        // If cart is empty, show zero amounts but display active GST rates or configuration errors
        if (cartItems.isEmpty()) {
            lblSubtotal.setText("Subtotal: " + CurrencyUtil.formatCurrency(0.0));
            lblDiscount.setText("Discount: " + CurrencyUtil.formatCurrency(0.0));
            lblGrandTotal.setText("Grand Total: " + CurrencyUtil.formatCurrency(0.0));
            btnSave.setEnabled(false);

            try {
                GSTRates rates = billingService.getConfiguredGSTRates();
                String cgstRateStr = rates.cgstRate().stripTrailingZeros().toPlainString();
                String sgstRateStr = rates.sgstRate().stripTrailingZeros().toPlainString();
                lblCgst.setText(String.format("CGST (%s%%): %s", cgstRateStr, CurrencyUtil.formatCurrency(0.0)));
                lblSgst.setText(String.format("SGST (%s%%): %s", sgstRateStr, CurrencyUtil.formatCurrency(0.0)));
                lblStatus.setText("");
            } catch (BillingConfigurationException e) {
                lblCgst.setText("CGST: Not Configured");
                lblSgst.setText("SGST: Not Configured");
                lblStatus.setText(e.getMessage());
            } catch (BillingDatabaseException e) {
                lblCgst.setText("CGST: Error");
                lblSgst.setText("SGST: Error");
                lblStatus.setText(e.getMessage());
            }
            return;
        }

        // Cart contains items: parse discount
        BigDecimal discount = BigDecimal.ZERO;
        String discountText = tfDiscount.getText().trim();
        if (!discountText.isEmpty()) {
            try {
                discount = new BigDecimal(discountText);
                if (discount.compareTo(BigDecimal.ZERO) < 0) {
                    lblStatus.setText("Discount cannot be negative.");
                    btnSave.setEnabled(false);
                    return;
                }
                if (discount.scale() > 2) {
                    lblStatus.setText("Discount cannot have more than 2 decimal places.");
                    btnSave.setEnabled(false);
                    return;
                }
            } catch (NumberFormatException e) {
                lblStatus.setText("Invalid discount amount.");
                btnSave.setEnabled(false);
                return;
            }
        }

        // Perform calculation using service layer rules
        String custName = tfCustomerName.getText().trim();
        String custPhone = tfCustomerPhone.getText().trim();
        try {
            CalculationResult result = billingService.calculateBill(
                    new ArrayList<>(cartItems),
                    discount,
                    custName.isEmpty() ? null : custName,
                    custPhone.isEmpty() ? null : custPhone);

            if (result.isSuccess()) {
                Bill bill = result.getBill();
                lblSubtotal.setText("Subtotal: " + CurrencyUtil.formatCurrency(bill.getSubtotal().doubleValue()));
                lblDiscount.setText("Discount: " + CurrencyUtil.formatCurrency(bill.getDiscount().doubleValue()));

                String cgstRateStr = result.getCgstRate().stripTrailingZeros().toPlainString();
                String sgstRateStr = result.getSgstRate().stripTrailingZeros().toPlainString();
                lblCgst.setText(String.format("CGST (%s%%): %s", cgstRateStr,
                        CurrencyUtil.formatCurrency(bill.getCgstAmount().doubleValue())));
                lblSgst.setText(String.format("SGST (%s%%): %s", sgstRateStr,
                        CurrencyUtil.formatCurrency(bill.getSgstAmount().doubleValue())));

                lblGrandTotal.setText("Grand Total: " + CurrencyUtil.formatCurrency(bill.getTotalAmount().doubleValue()));
                lblStatus.setText("");
                btnSave.setEnabled(true);
            } else {
                lblStatus.setText(result.getErrorMessage());
                btnSave.setEnabled(false);
                if (result.getCgstRate() == null) {
                    lblCgst.setText("CGST: Not Configured");
                }
                if (result.getSgstRate() == null) {
                    lblSgst.setText("SGST: Not Configured");
                }
            }
        } catch (BillingDatabaseException e) {
            lblStatus.setText(e.getMessage());
            btnSave.setEnabled(false);
        }
    }

    /** Schedules safe asynchronous UI recalculation from document events. */
    private void scheduleRecalc() {
        SwingUtilities.invokeLater(this::recalcTotals);
    }

    /** Attach UI listeners. */
    private void attachListeners() {
        // Live search
        tfSearch.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) { filterMedicines(tfSearch.getText()); }
        });

        // Discount changes trigger live recalculation
        tfDiscount.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { scheduleRecalc(); }
            @Override public void removeUpdate(DocumentEvent e) { scheduleRecalc(); }
            @Override public void changedUpdate(DocumentEvent e) { scheduleRecalc(); }
        });

        // Add item to cart
        btnAddItem.addActionListener(e -> {
            Medicine selected = (Medicine) cbMedicines.getSelectedItem();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "No medicine selected.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            int qty = (int) spQuantity.getValue();
            if (qty <= 0) {
                JOptionPane.showMessageDialog(this, "Quantity must be positive.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Use the quantity already loaded in the selected Medicine object
            Integer qtyObj = selected.getQuantity();
            int stock = (qtyObj != null) ? qtyObj : 0;

            // Merge duplicate medicines
            for (BillItem bi : cartItems) {
                if (bi.getMedicineId() == selected.getMedicineId()) {
                    int newQty = bi.getQuantity() + qty;
                    if (newQty > stock) {
                        JOptionPane.showMessageDialog(this,
                                "Combined quantity exceeds stock (" + stock + ").",
                                "Stock error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    bi.setQuantity(newQty);
                    BigDecimal newAmt = selected.getSellingPrice()
                            .multiply(BigDecimal.valueOf(newQty)).setScale(2, RoundingMode.HALF_UP);
                    bi.setAmount(newAmt);
                    refreshCartTable();
                    recalcTotals();
                    return;
                }
            }

            // For new line item, validate requested quantity against available stock
            if (qty > stock) {
                JOptionPane.showMessageDialog(this,
                        "Requested quantity exceeds available stock (" + stock + ").",
                        "Stock error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // New line item
            BillItem item = new BillItem();
            item.setMedicineId(selected.getMedicineId());
            item.setMedicineName(selected.getMedicineName());
            item.setQuantity(qty);
            item.setPrice(selected.getSellingPrice());
            BigDecimal amt = selected.getSellingPrice()
                    .multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            item.setAmount(amt);
            cartItems.add(item);
            addItemToTable(item);
            recalcTotals();
        });

        // Clear cart
        btnClear.addActionListener(e -> {
            cartItems.clear();
            cartModel.setRowCount(0);
            tfCustomerName.setText("");
            tfCustomerPhone.setText("");
            tfDiscount.setText("0.00");
            recalcTotals();
        });

        // Save bill
        btnSave.addActionListener(e -> {
            if (cartItems.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Cart is empty.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            BigDecimal discount = BigDecimal.ZERO;
            String discountStr = tfDiscount.getText().trim();
            if (!discountStr.isEmpty()) {
                try {
                    discount = new BigDecimal(discountStr);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid discount amount.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            String custName = tfCustomerName.getText().trim();
            String custPhone = tfCustomerPhone.getText().trim();
            try {
                CalculationResult result = billingService.calculateBill(
                        new ArrayList<>(cartItems), // defensive copy
                        discount,
                        custName.isEmpty() ? null : custName,
                        custPhone.isEmpty() ? null : custPhone);

                if (!result.isSuccess()) {
                    JOptionPane.showMessageDialog(this,
                            "Bill calculation failed: " + result.getErrorMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                boolean saved = billingService.processBill(result);
                if (saved) {
                    int generatedId = result.getBill().getBillId();
                    JOptionPane.showMessageDialog(this,
                            "Bill saved successfully! Bill ID: " + generatedId,
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    // Clear cart after successful save
                    btnClear.doClick();
                    // Reload medicines so updated stock appears immediately
                    reloadMedicines();
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Failed to save the bill. Please try again.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Unexpected error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });
    }

    /** Add a row to the cart table. */
    private void addItemToTable(BillItem item) {
        cartModel.addRow(new Object[]{
                item.getMedicineId(),
                item.getMedicineName(),
                item.getPrice(),
                item.getQuantity(),
                item.getAmount(),
                "Remove"
        });
    }

    /** Refresh the entire cart table after changes. */
    private void refreshCartTable() {
        cartModel.setRowCount(0);
        for (BillItem bi : cartItems) {
            addItemToTable(bi);
        }
    }

    // ── Helper classes ─────────────────────────────────────────────────────

    /** Renderer for medicine names in the combo box. */
    private static class MedicineRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                                                              int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Medicine) {
                setText(((Medicine) value).getMedicineName());
            }
            return this;
        }
    }

    /** Simple button renderer for the Remove column. */
    private static class ButtonRenderer extends JButton implements TableCellRenderer {
        private static final long serialVersionUID = 1L;

        public ButtonRenderer() {
            setOpaque(true);
            setBackground(BUTTON_COLOR);
            setForeground(Color.WHITE);
            setFocusPainted(false);
        }
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                                                                boolean isSelected, boolean hasFocus,
                                                                int row, int column) {
            setText(value == null ? "" : value.toString());
            return this;
        }
    }

    /** Editor for the Remove button – deletes the row from cart & model. */
    private class ButtonEditor extends DefaultCellEditor {
        private static final long serialVersionUID = 1L;
        private final JButton button = new JButton("Remove");
        private int viewRow;

        public ButtonEditor(JCheckBox checkBox) {
            super(checkBox);
            button.setOpaque(true);
            button.setBackground(BUTTON_COLOR);
            button.setForeground(Color.WHITE);
            button.setFocusPainted(false);
            button.addActionListener(e -> stopCellEditing());
        }

        @Override public Component getTableCellEditorComponent(JTable table, Object value,
                                                               boolean isSelected, int row, int column) {
            viewRow = row;
            button.setText(value == null ? "" : value.toString());
            return button;
        }

        @Override public Object getCellEditorValue() { return "Remove"; }

        @Override public boolean stopCellEditing() {
            int modelRow = tblCart.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < cartItems.size()) {
                cartItems.remove(modelRow);
                cartModel.removeRow(modelRow);
                recalcTotals();
            }
            return super.stopCellEditing();
        }
    }
}
