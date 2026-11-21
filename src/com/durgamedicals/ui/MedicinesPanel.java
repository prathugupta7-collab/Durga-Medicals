package com.durgamedicals.ui;

import com.durgamedicals.dao.MedicineDAO;
import com.durgamedicals.model.Medicine;
import com.durgamedicals.util.FontUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Panel for managing medicines: list, search, add, edit, delete.
 * Uses the existing MedicineDAO for persistence.
 */
public class MedicinesPanel extends JPanel {
    private final MedicineDAO dao = new MedicineDAO();
    private final JTextField searchField = new JTextField(20);
    private final JButton searchBtn = new JButton("Search");
    private final DefaultTableModel tableModel;
    private final JTable table;

    private static final String[] COLUMNS = {
            "ID", "Name", "Category", "Purchase Price", "Selling Price",
            "Quantity", "Expiry Date", "Supplier ID", "Min Qty"
    };

    public MedicinesPanel() {
        setLayout(new BorderLayout());
        setBackground(DashboardPanel.BACKGROUND_COLOR);
        // Top search bar
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setBackground(DashboardPanel.BACKGROUND_COLOR);
        searchPanel.add(new JLabel("Search:"));
        searchPanel.add(searchField);
        searchPanel.add(searchBtn);
        add(searchPanel, BorderLayout.NORTH);

        // Table
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // read‑only
            }
        };
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.setFont(FontUtil.getRalewayRegular(12f));
        table.getTableHeader().setFont(FontUtil.getRalewayBold(12f));
        // Custom renderer for stock/expiry highlights
        table.setDefaultRenderer(Object.class, new MedicineRowRenderer());
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Bottom action buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(DashboardPanel.BACKGROUND_COLOR);
        JButton addBtn = new JButton("Add");
        JButton editBtn = new JButton("Edit");
        JButton deleteBtn = new JButton("Delete");
        JButton refreshBtn = new JButton("Refresh");
        // style buttons using theme colours
        for (JButton b : new JButton[]{addBtn, editBtn, deleteBtn, refreshBtn, searchBtn}) {
            b.setBackground(DashboardPanel.BUTTON_COLOR);
            b.setForeground(Color.WHITE);
            b.setFocusPainted(false);
            b.setFont(FontUtil.getRalewayBold(12f));
        }
        btnPanel.add(addBtn);
        btnPanel.add(editBtn);
        btnPanel.add(deleteBtn);
        btnPanel.add(refreshBtn);
        add(btnPanel, BorderLayout.SOUTH);

        // Load initial data
        loadAllMedicines();

        // Action listeners
        searchBtn.addActionListener(this::performSearch);
        refreshBtn.addActionListener(e -> loadAllMedicines());
        addBtn.addActionListener(e -> openMedicineForm(null));
        editBtn.addActionListener(e -> editSelectedMedicine());
        deleteBtn.addActionListener(e -> deleteSelectedMedicine());
    }

    private void performSearch(ActionEvent e) {
        String keyword = searchField.getText();
        List<Medicine> list = dao.searchMedicines(keyword);
        populateTable(list);
        if (list.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No medicines found.", "Search", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public void loadAllMedicines() {
        List<Medicine> list = dao.getAllMedicines();
        populateTable(list);
    }

    private void populateTable(List<Medicine> medicines) {
        tableModel.setRowCount(0);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Medicine m : medicines) {
            tableModel.addRow(new Object[]{
                    m.getMedicineId(),
                    m.getMedicineName(),
                    m.getCategory(),
                    m.getPurchasePrice(),
                    m.getSellingPrice(),
                    m.getQuantity(),
                    m.getExpiryDate() != null ? m.getExpiryDate().format(fmt) : null,
                    m.getSupplierId(),
                    m.getMinQuantity()
            });
        }
    }

    private void openMedicineForm(Medicine existing) {
        MedicineFormDialog dialog = new MedicineFormDialog(SwingUtilities.getWindowAncestor(this), existing);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        Medicine result = dialog.getMedicine();
        if (result != null) {
            boolean success;
            if (existing == null) {
                success = dao.addMedicine(result);
            } else {
                result.setMedicineId(existing.getMedicineId());
                success = dao.updateMedicine(result);
            }
            if (success) {
                JOptionPane.showMessageDialog(this, existing == null ? "Medicine added successfully." : "Medicine updated successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                loadAllMedicines();
            } else {
                JOptionPane.showMessageDialog(this, "Operation failed. See console for details.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void editSelectedMedicine() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a medicine to edit.", "Edit", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        Medicine med = dao.getMedicineById(id);
        if (med == null) {
            JOptionPane.showMessageDialog(this, "Selected medicine could not be loaded.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        openMedicineForm(med);
    }

    private void deleteSelectedMedicine() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a medicine to delete.", "Delete", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Delete selected medicine?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        boolean success = dao.deleteMedicine(id);
        if (success) {
            JOptionPane.showMessageDialog(this, "Medicine deleted.", "Success", JOptionPane.INFORMATION_MESSAGE);
            loadAllMedicines();
        } else {
            JOptionPane.showMessageDialog(this, "Delete failed. See console for details.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Renderer to colour rows for low stock or expired medicines */
    private class MedicineRowRenderer extends JLabel implements TableCellRenderer {
        public MedicineRowRenderer() {
            setOpaque(true);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText(value == null ? "" : value.toString());
            // Reset colours
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            // Apply custom highlights when not selected
            if (!isSelected) {
                Integer qty = (Integer) table.getModel().getValueAt(row, 5);
                Integer minQty = (Integer) table.getModel().getValueAt(row, 8);
                String expiryStr = (String) table.getModel().getValueAt(row, 6);
                boolean low = qty != null && minQty != null && qty <= minQty;
                boolean expired = expiryStr != null && expiryStr.compareTo(java.time.LocalDate.now().toString()) < 0;
                if (expired) {
                    setBackground(DashboardPanel.EXPIRED_HIGHLIGHT);
                } else if (low) {
                    setBackground(DashboardPanel.LOW_STOCK_HIGHLIGHT);
                }
            }
            return this;
        }
    }

    /** Simple dialog for adding/editing a medicine */
    private static class MedicineFormDialog extends JDialog {
        private Medicine medicineResult = null;
        private final JTextField nameField = new JTextField(20);
        private final JTextField categoryField = new JTextField(20);
        private final JTextField purchaseField = new JTextField(10);
        private final JTextField sellingField = new JTextField(10);
        private final JTextField qtyField = new JTextField(5);
        private final JTextField expiryField = new JTextField(10); // yyyy-MM-dd
        private final JTextField supplierField = new JTextField(5);
        private final JTextField minQtyField = new JTextField(5);

        MedicineFormDialog(Window owner, Medicine existing) {
            super(owner, existing == null ? "Add Medicine" : "Edit Medicine", ModalityType.APPLICATION_MODAL);
            setLayout(new BorderLayout());
            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5,5,5,5);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.gridx = 0; gbc.gridy = 0;
            addLabel(form, gbc, "Name*");
            gbc.gridx = 1; form.add(nameField, gbc);
            gbc.gridx = 0; gbc.gridy++;
            addLabel(form, gbc, "Category");
            gbc.gridx = 1; form.add(categoryField, gbc);
            gbc.gridx = 0; gbc.gridy++;
            addLabel(form, gbc, "Purchase Price");
            gbc.gridx = 1; form.add(purchaseField, gbc);
            gbc.gridx = 0; gbc.gridy++;
            addLabel(form, gbc, "Selling Price");
            gbc.gridx = 1; form.add(sellingField, gbc);
            gbc.gridx = 0; gbc.gridy++;
            addLabel(form, gbc, "Quantity");
            gbc.gridx = 1; form.add(qtyField, gbc);
            gbc.gridx = 0; gbc.gridy++;
            addLabel(form, gbc, "Expiry (yyyy-MM-dd)");
            gbc.gridx = 1; form.add(expiryField, gbc);
            gbc.gridx = 0; gbc.gridy++;
            addLabel(form, gbc, "Supplier ID");
            gbc.gridx = 1; form.add(supplierField, gbc);
            gbc.gridx = 0; gbc.gridy++;
            addLabel(form, gbc, "Min Qty*");
            gbc.gridx = 1; form.add(minQtyField, gbc);
            add(form, BorderLayout.CENTER);

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton okBtn = new JButton("OK");
            JButton cancelBtn = new JButton("Cancel");
            btnPanel.add(okBtn);
            btnPanel.add(cancelBtn);
            add(btnPanel, BorderLayout.SOUTH);

            // Pre‑fill if editing
            if (existing != null) {
                nameField.setText(existing.getMedicineName());
                categoryField.setText(existing.getCategory());
                purchaseField.setText(existing.getPurchasePrice() != null ? existing.getPurchasePrice().toString() : "");
                sellingField.setText(existing.getSellingPrice() != null ? existing.getSellingPrice().toString() : "");
                qtyField.setText(existing.getQuantity() != null ? existing.getQuantity().toString() : "");
                expiryField.setText(existing.getExpiryDate() != null ? existing.getExpiryDate().toString() : "");
                supplierField.setText(existing.getSupplierId() != null ? existing.getSupplierId().toString() : "");
                minQtyField.setText(String.valueOf(existing.getMinQuantity()));
            }

            okBtn.addActionListener(e -> onOk(existing));
            cancelBtn.addActionListener(e -> dispose());

            pack();
        }

        private void addLabel(JPanel p, GridBagConstraints gbc, String text) {
            gbc.anchor = GridBagConstraints.EAST;
            p.add(new JLabel(text), gbc);
            gbc.anchor = GridBagConstraints.WEST;
        }

        private void onOk(Medicine existing) {
            // Basic validation (required fields: name, minQty)
            String name = nameField.getText().trim();
            if (name.isEmpty()) { showError("Name is required."); return; }
            int minQty;
            try { minQty = Integer.parseInt(minQtyField.getText().trim()); } catch (Exception ex) { showError("Minimum quantity must be an integer."); return; }
            // Build Medicine object (optional fields can stay null)
            Medicine m = new Medicine();
            m.setMedicineName(name);
            m.setCategory(categoryField.getText().trim().isEmpty() ? null : categoryField.getText().trim());
            try { m.setPurchasePrice(nameField.getText().trim().isEmpty() ? null : new java.math.BigDecimal(purchaseField.getText().trim())); } catch (Exception ignored) {}
            try { m.setSellingPrice(sellingField.getText().trim().isEmpty() ? null : new java.math.BigDecimal(sellingField.getText().trim())); } catch (Exception ignored) {}
            try { m.setQuantity(qtyField.getText().trim().isEmpty() ? null : Integer.parseInt(qtyField.getText().trim())); } catch (Exception ignored) {}
            try { m.setExpiryDate(expiryField.getText().trim().isEmpty() ? null : java.time.LocalDate.parse(expiryField.getText().trim())); } catch (Exception ignored) {}
            try { m.setSupplierId(supplierField.getText().trim().isEmpty() ? null : Integer.parseInt(supplierField.getText().trim())); } catch (Exception ignored) {}
            m.setMinQuantity(minQty);
            // If editing, keep the original ID (set by caller)
            this.medicineResult = m;
            dispose();
        }

        private void showError(String msg) {
            JOptionPane.showMessageDialog(this, msg, "Input Error", JOptionPane.ERROR_MESSAGE);
        }

        public Medicine getMedicine() {
            return medicineResult;
        }
    }
}
