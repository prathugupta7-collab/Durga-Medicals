package com.durgamedicals.ui;

import com.durgamedicals.dao.SupplierDAO;
import com.durgamedicals.model.Supplier;
import com.durgamedicals.util.FontUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * SuppliersPanel – UI for managing supplier records.
 * Provides table view, search, add, edit, delete and refresh actions.
 */
public class SuppliersPanel extends JPanel {
    private final SupplierDAO dao = new SupplierDAO();
    private final JTextField searchField = new JTextField(20);
    private final JButton searchBtn = new JButton("Search");
    private final DefaultTableModel tableModel;
    private final JTable table;

    private static final String[] COLUMNS = {"ID", "Name", "Phone", "Address"};

    public SuppliersPanel() {
        setLayout(new BorderLayout());
        setBackground(DashboardPanel.BACKGROUND_COLOR);

        // Search bar (north)
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setBackground(DashboardPanel.BACKGROUND_COLOR);
        searchPanel.add(new JLabel("Search:"));
        searchPanel.add(searchField);
        searchPanel.add(searchBtn);
        add(searchPanel, BorderLayout.NORTH);

        // Table (center)
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
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Action buttons (south)
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(DashboardPanel.BACKGROUND_COLOR);
        JButton addBtn = new JButton("Add");
        JButton editBtn = new JButton("Edit");
        JButton deleteBtn = new JButton("Delete");
        JButton refreshBtn = new JButton("Refresh");
        // style buttons with brand colours
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
        loadAllSuppliers();

        // Listeners
        searchBtn.addActionListener(this::performSearch);
        refreshBtn.addActionListener(e -> loadAllSuppliers());
        addBtn.addActionListener(e -> openSupplierForm(null));
        editBtn.addActionListener(e -> editSelectedSupplier());
        deleteBtn.addActionListener(e -> deleteSelectedSupplier());
    }

    private void performSearch(ActionEvent e) {
        String keyword = searchField.getText();
        List<Supplier> list = dao.searchSuppliers(keyword);
        populateTable(list);
        if (list.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No suppliers found.", "Search", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void loadAllSuppliers() {
        List<Supplier> list = dao.getAllSuppliers();
        populateTable(list);
    }

    private void populateTable(List<Supplier> suppliers) {
        tableModel.setRowCount(0);
        for (Supplier s : suppliers) {
            tableModel.addRow(new Object[]{
                    s.getSupplierId(),
                    s.getSupplierName(),
                    s.getPhone(),
                    s.getAddress()
            });
        }
    }

    private void openSupplierForm(Supplier existing) {
        SupplierFormDialog dialog = new SupplierFormDialog(SwingUtilities.getWindowAncestor(this), existing);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        Supplier result = dialog.getSupplier();
        if (result != null) {
            boolean success;
            if (existing == null) {
                success = dao.addSupplier(result);
            } else {
                result.setSupplierId(existing.getSupplierId());
                success = dao.updateSupplier(result);
            }
            if (success) {
                JOptionPane.showMessageDialog(this, existing == null ? "Supplier added successfully." : "Supplier updated successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                loadAllSuppliers();
            } else {
                JOptionPane.showMessageDialog(this, "Operation failed. See console for details.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void editSelectedSupplier() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a supplier to edit.", "Edit", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        Supplier sup = dao.getSupplierById(id);
        if (sup == null) {
            JOptionPane.showMessageDialog(this, "Selected supplier could not be loaded.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        openSupplierForm(sup);
    }

    private void deleteSelectedSupplier() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a supplier to delete.", "Delete", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Delete selected supplier?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        boolean success = dao.deleteSupplier(id);
        if (success) {
            JOptionPane.showMessageDialog(this, "Supplier deleted.", "Success", JOptionPane.INFORMATION_MESSAGE);
            loadAllSuppliers();
        } else {
            JOptionPane.showMessageDialog(this, "Delete failed. See console for details.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Modal dialog for adding or editing a supplier */
    private static class SupplierFormDialog extends JDialog {
        private Supplier supplierResult = null;
        private final JTextField nameField = new JTextField(20);
        private final JTextField phoneField = new JTextField(15);
        private final JTextField addressField = new JTextField(30);

        SupplierFormDialog(Window owner, Supplier existing) {
            super(owner, existing == null ? "Add Supplier" : "Edit Supplier", ModalityType.APPLICATION_MODAL);
            setLayout(new BorderLayout());
            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5,5,5,5);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.gridx = 0; gbc.gridy = 0;
            addLabel(form, gbc, "Name*");
            gbc.gridx = 1; form.add(nameField, gbc);
            gbc.gridx = 0; gbc.gridy++;
            addLabel(form, gbc, "Phone");
            gbc.gridx = 1; form.add(phoneField, gbc);
            gbc.gridx = 0; gbc.gridy++;
            addLabel(form, gbc, "Address");
            gbc.gridx = 1; form.add(addressField, gbc);
            add(form, BorderLayout.CENTER);

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton okBtn = new JButton("OK");
            JButton cancelBtn = new JButton("Cancel");
            btnPanel.add(okBtn);
            btnPanel.add(cancelBtn);
            add(btnPanel, BorderLayout.SOUTH);

            if (existing != null) {
                nameField.setText(existing.getSupplierName());
                phoneField.setText(existing.getPhone() != null ? existing.getPhone() : "");
                addressField.setText(existing.getAddress() != null ? existing.getAddress() : "");
            }

            okBtn.addActionListener(e -> onOk());
            cancelBtn.addActionListener(e -> dispose());

            pack();
        }

        private void addLabel(JPanel p, GridBagConstraints gbc, String text) {
            gbc.anchor = GridBagConstraints.EAST;
            p.add(new JLabel(text), gbc);
            gbc.anchor = GridBagConstraints.WEST;
        }

        private void onOk() {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Name is required.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Supplier s = new Supplier();
            s.setSupplierName(name);
            s.setPhone(phoneField.getText().trim().isEmpty() ? null : phoneField.getText().trim());
            s.setAddress(addressField.getText().trim().isEmpty() ? null : addressField.getText().trim());
            this.supplierResult = s;
            dispose();
        }

        public Supplier getSupplier() {
            return supplierResult;
        }
    }
}
