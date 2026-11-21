package com.durgamedicals.ui;

import com.durgamedicals.dao.SettingsDAO;
import com.durgamedicals.util.FontUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;

/**
 * SettingsPanel.java
 *
 * UI panel for configuring GST rates and shop details.
 * Uses the existing SettingsDAO for persistence.
 *
 * Theme colours (Durga Medicals):
 *   - Background: #171717 (black)
 *   - Accent: #A3B18A (hazel green)
 *   - Secondary: #B7A58A (hazel)
 *   - Text: #FFFFFF (white)
 */
public class SettingsPanel extends JPanel {
    // DAO instance
    private final SettingsDAO settingsDAO = new SettingsDAO();

    // UI components for GST
    private final JTextField txtCgst = new JTextField(10);
    private final JTextField txtSgst = new JTextField(10);
    private final JButton btnSaveGst = new JButton("Save GST Settings");

    // UI components for shop details
    private final JTextField txtShopName = new JTextField(20);
    private final JTextField txtShopAddress = new JTextField(30);
    private final JTextField txtShopPhone = new JTextField(15);
    private final JButton btnSaveShop = new JButton("Save Shop Details");

    public SettingsPanel() {
        setLayout(new BorderLayout());
        setBackground(Color.decode("#171717"));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // Title label
        JLabel lblTitle = new JLabel("Settings");
        lblTitle.setForeground(Color.decode("#FFFFFF"));
        lblTitle.setFont(FontUtil.getRalewayBold(24));
        lblTitle.setHorizontalAlignment(SwingConstants.CENTER);
        add(lblTitle, BorderLayout.NORTH);

        // Main content panel
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        // GST Section Header
        JLabel lblGstHeader = new JLabel("GST Configuration");
        lblGstHeader.setFont(FontUtil.getRalewayBold(18));
        lblGstHeader.setForeground(Color.decode("#A3B18A"));
        content.add(lblGstHeader, gbc);
        gbc.gridy++;

        // CGST
        content.add(new JLabel("CGST Rate (%):"), gbc);
        gbc.gridx = 1;
        content.add(txtCgst, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // SGST
        content.add(new JLabel("SGST Rate (%):"), gbc);
        gbc.gridx = 1;
        content.add(txtSgst, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // Save GST button spanning two columns
        gbc.gridwidth = 2;
        btnSaveGst.setBackground(Color.decode("#A3B18A"));
        btnSaveGst.setForeground(Color.decode("#FFFFFF"));
        btnSaveGst.setFont(FontUtil.getRalewayBold(14));
        content.add(btnSaveGst, gbc);
        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.gridx = 0;

        // Separator
        JSeparator sep = new JSeparator();
        sep.setForeground(Color.decode("#B7A58A"));
        gbc.gridwidth = 2;
        content.add(sep, gbc);
        gbc.gridwidth = 1;
        gbc.gridy++;

        // Shop Details Header
        JLabel lblShopHeader = new JLabel("Shop Details");
        lblShopHeader.setFont(FontUtil.getRalewayBold(18));
        lblShopHeader.setForeground(Color.decode("#A3B18A"));
        content.add(lblShopHeader, gbc);
        gbc.gridy++;

        // Shop Name
        content.add(new JLabel("Shop Name:"), gbc);
        gbc.gridx = 1;
        content.add(txtShopName, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // Shop Address
        content.add(new JLabel("Shop Address:"), gbc);
        gbc.gridx = 1;
        content.add(txtShopAddress, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // Shop Phone
        content.add(new JLabel("Shop Phone:"), gbc);
        gbc.gridx = 1;
        content.add(txtShopPhone, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // Save Shop button spanning two columns
        gbc.gridwidth = 2;
        btnSaveShop.setBackground(Color.decode("#A3B18A"));
        btnSaveShop.setForeground(Color.decode("#FFFFFF"));
        btnSaveShop.setFont(FontUtil.getRalewayBold(14));
        content.add(btnSaveShop, gbc);
        gbc.gridwidth = 1;

        add(content, BorderLayout.CENTER);

        // Load persisted settings
        loadSettings();

        // Attach listeners
        btnSaveGst.addActionListener(this::saveGstSettings);
        btnSaveShop.addActionListener(this::saveShopDetails);
    }

    /** Loads existing settings from the database and populates the fields. */
    private void loadSettings() {
        try {
            String cgst = settingsDAO.getSetting("cgst_rate");
            String sgst = settingsDAO.getSetting("sgst_rate");
            String name = settingsDAO.getSetting("shop_name");
            String address = settingsDAO.getSetting("shop_address");
            String phone = settingsDAO.getSetting("shop_phone");

            txtCgst.setText(cgst != null ? cgst : "");
            txtSgst.setText(sgst != null ? sgst : "");
            txtShopName.setText(name != null ? name : "");
            txtShopAddress.setText(address != null ? address : "");
            txtShopPhone.setText(phone != null ? phone : "");
        } catch (SQLException e) {
            showError("Failed to load settings: " + e.getMessage());
        }
    }

    /** Validates and saves GST settings. */
    private void saveGstSettings(ActionEvent e) {
        String cgstStr = txtCgst.getText().trim();
        String sgstStr = txtSgst.getText().trim();
        if (cgstStr.isEmpty() || sgstStr.isEmpty()) {
            showError("GST fields cannot be empty.");
            return;
        }
        try {
            double cgst = Double.parseDouble(cgstStr);
            double sgst = Double.parseDouble(sgstStr);
            if (cgst < 0 || cgst > 100 || sgst < 0 || sgst > 100) {
                showError("GST rates must be between 0 and 100.");
                return;
            }
            settingsDAO.setSetting("cgst_rate", cgstStr);
            settingsDAO.setSetting("sgst_rate", sgstStr);
            showInfo("GST settings saved successfully.");
        } catch (NumberFormatException ex) {
            showError("GST rates must be numeric.");
        } catch (SQLException ex) {
            showError("Failed to save GST settings: " + ex.getMessage());
        }
    }

    /** Saves shop detail settings. */
    private void saveShopDetails(ActionEvent e) {
        String name = txtShopName.getText().trim();
        String address = txtShopAddress.getText().trim();
        String phone = txtShopPhone.getText().trim();
        if (name.isEmpty() || address.isEmpty() || phone.isEmpty()) {
            showError("All shop detail fields must be filled.");
            return;
        }
        try {
            settingsDAO.setSetting("shop_name", name);
            settingsDAO.setSetting("shop_address", address);
            settingsDAO.setSetting("shop_phone", phone);
            showInfo("Shop details saved successfully.");
        } catch (SQLException ex) {
            showError("Failed to save shop details: " + ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "Info", JOptionPane.INFORMATION_MESSAGE);
    }
}
