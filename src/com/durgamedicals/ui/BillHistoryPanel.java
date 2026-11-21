package com.durgamedicals.ui;

import com.durgamedicals.dao.BillDAO;
import com.durgamedicals.model.Bill;
import com.durgamedicals.model.BillItem;
import com.durgamedicals.util.CurrencyUtil;
import com.durgamedicals.util.DBConnection;
import com.durgamedicals.util.FontUtil;
import com.durgamedicals.ui.DashboardPanel;
import com.durgamedicals.service.BillPrintService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * BillHistoryPanel.java
 *
 * Full functional UI panel for browsing, searching, and inspecting past pharmacy bills.
 * Integrates directly with BillDAO without duplicating database queries or modifying schema.
 *
 * Features:
 *   • Background asynchronous loading using SwingWorker (prevents EDT freezing).
 *   • Search bills by Bill ID, Customer Name, or Customer Phone using BillDAO.searchBills().
 *   • JTable display with columns: Bill ID, Customer Name, Customer Phone, Bill Date,
 *     Subtotal, Tax, Discount, Grand Total.
 *   • View Details modal dialog displaying purchased medicines, quantities, prices, amounts,
 *     and complete tax breakdown.
 *   • Double-click table row shortcut to immediately open Bill Details.
 *   • Responsive layout matching Durga Medicals theme colours and Raleway typography.
 *   • Graceful database error handling and user notifications without silently swallowing exceptions.
 */
public class BillHistoryPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    // ── Theme colours ───────────────────────────────────────────────────────
    public static final Color HEADING_COLOR = Color.decode("#171717");
    public static final Color ERROR_COLOR   = Color.decode("#D9534F");

    // ── DAO & Formatters ───────────────────────────────────────────────────
    private final BillDAO billDAO = new BillDAO();
    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ── UI Components ──────────────────────────────────────────────────────
    private final JTextField searchField = new JTextField(22);
    private final JButton searchBtn = new JButton("Search");
    private final JButton clearSearchBtn = new JButton("Clear");
    private final JButton refreshBtn = new JButton("Refresh");
    private final JButton viewDetailsBtn = new JButton("View Details");
    private final JLabel recordCountLabel = new JLabel("Total Bills: 0");
    private final JLabel statusLabel = new JLabel("");

    private final DefaultTableModel tableModel;
    private final JTable table;

    // Ordered columns per project specifications
    private static final String[] COLUMNS = {
            "Bill ID", "Customer Name", "Customer Phone", "Bill Date",
            "Subtotal", "Tax", "Discount", "Grand Total"
    };

    // State flag to prevent concurrent SwingWorker executions
    private volatile boolean loading = false;

    public BillHistoryPanel() {
        setLayout(new BorderLayout(0, 10));
        setBackground(DashboardPanel.BACKGROUND_COLOR);

        // =====================================================================
        // Top Container: Heading + Search Toolbar
        // =====================================================================
        JPanel topContainer = new JPanel(new BorderLayout(0, 12));
        topContainer.setBackground(DashboardPanel.BACKGROUND_COLOR);
        topContainer.setBorder(new EmptyBorder(16, 20, 10, 20));

        // Heading
        JLabel heading = new JLabel("Bill History");
        heading.setFont(FontUtil.getRalewayBold(24f));
        heading.setForeground(HEADING_COLOR);
        topContainer.add(heading, BorderLayout.NORTH);

        // Search bar
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        searchBar.setBackground(DashboardPanel.BACKGROUND_COLOR);

        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setFont(FontUtil.getRalewayBold(13f));
        searchField.setFont(FontUtil.getRalewayRegular(13f));
        searchField.setToolTipText("Search by Bill ID, Customer Name, or Phone");

        styleButton(searchBtn);
        styleButton(clearSearchBtn);
        styleButton(refreshBtn);

        searchBar.add(searchLabel);
        searchBar.add(searchField);
        searchBar.add(searchBtn);
        searchBar.add(clearSearchBtn);
        searchBar.add(refreshBtn);

        statusLabel.setFont(FontUtil.getRalewayBold(12f));
        statusLabel.setForeground(Color.DARK_GRAY);
        searchBar.add(statusLabel);

        topContainer.add(searchBar, BorderLayout.SOUTH);
        add(topContainer, BorderLayout.NORTH);

        // =====================================================================
        // Center: Bills Table inside JScrollPane
        // =====================================================================
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // read-only table
            }
        };

        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        table.setFont(FontUtil.getRalewayRegular(12f));
        table.getTableHeader().setFont(FontUtil.getRalewayBold(12f));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        setupColumnFormatting();

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        scrollPane.getViewport().setBackground(DashboardPanel.BACKGROUND_COLOR);
        add(scrollPane, BorderLayout.CENTER);

        // =====================================================================
        // Bottom: Status Count & Action Buttons
        // =====================================================================
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setBackground(DashboardPanel.BACKGROUND_COLOR);
        bottomPanel.setBorder(new EmptyBorder(10, 20, 16, 20));

        recordCountLabel.setFont(FontUtil.getRalewayBold(13f));
        recordCountLabel.setForeground(Color.DARK_GRAY);
        bottomPanel.add(recordCountLabel, BorderLayout.WEST);

        JPanel actionBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionBtns.setBackground(DashboardPanel.BACKGROUND_COLOR);

        styleButton(viewDetailsBtn);
        viewDetailsBtn.setFont(FontUtil.getRalewayBold(13f));
        actionBtns.add(viewDetailsBtn);

        bottomPanel.add(actionBtns, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // =====================================================================
        // Listeners & Initial Data Load
        // =====================================================================
        attachListeners();
        reloadBills();
    }

    /** Styles standard buttons with the application theme. */
    private void styleButton(JButton btn) {
        btn.setBackground(DashboardPanel.BUTTON_COLOR);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(FontUtil.getRalewayBold(12f));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /** Configures table column widths and text alignments. */
    private void setupColumnFormatting() {
        if (table.getColumnModel().getColumnCount() < COLUMNS.length) {
            return;
        }

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(70);  // Bill ID
        table.getColumnModel().getColumn(0).setMaxWidth(90);
        table.getColumnModel().getColumn(1).setPreferredWidth(160); // Customer Name
        table.getColumnModel().getColumn(2).setPreferredWidth(120); // Customer Phone
        table.getColumnModel().getColumn(3).setPreferredWidth(130); // Bill Date
        table.getColumnModel().getColumn(4).setPreferredWidth(100); // Subtotal
        table.getColumnModel().getColumn(5).setPreferredWidth(90);  // Tax
        table.getColumnModel().getColumn(6).setPreferredWidth(90);  // Discount
        table.getColumnModel().getColumn(7).setPreferredWidth(110); // Grand Total

        // Alignments
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);

        table.getColumnModel().getColumn(0).setCellRenderer(centerRenderer); // Bill ID
        table.getColumnModel().getColumn(2).setCellRenderer(centerRenderer); // Phone
        table.getColumnModel().getColumn(3).setCellRenderer(centerRenderer); // Bill Date
        table.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);  // Subtotal
        table.getColumnModel().getColumn(5).setCellRenderer(rightRenderer);  // Tax
        table.getColumnModel().getColumn(6).setCellRenderer(rightRenderer);  // Discount
        table.getColumnModel().getColumn(7).setCellRenderer(rightRenderer);  // Grand Total
    }

    /** Attaches action listeners to buttons, text fields, and table mouse events. */
    private void attachListeners() {
        searchBtn.addActionListener(e -> performSearch());
        searchField.addActionListener(e -> performSearch());

        clearSearchBtn.addActionListener(e -> {
            searchField.setText("");
            reloadBills();
        });

        refreshBtn.addActionListener(e -> reloadBills());

        viewDetailsBtn.addActionListener(e -> viewSelectedBillDetails());

        // Double-click row shortcut to view details
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() != -1) {
                    viewSelectedBillDetails();
                }
            }
        });
    }

    /** Reloads all bills from the database asynchronously. */
    public void reloadBills() {
        searchField.setText("");
        executeLoad(null);
    }

    /** Performs a keyword search across bill ID, customer name, and customer phone asynchronously. */
    private void performSearch() {
        String keyword = searchField.getText().trim();
        executeLoad(keyword);
    }

    /**
     * Executes asynchronous database loading via SwingWorker to avoid freezing the EDT.
     *
     * @param keyword Optional search keyword (null or empty loads all bills).
     */
    private void executeLoad(String keyword) {
        if (loading) {
            return;
        }

        loading = true;
        setLoadingState(true, (keyword != null && !keyword.isEmpty()) ? "Searching bills..." : "Loading bills...");

        new SwingWorker<List<Bill>, Void>() {
            private String loadError = null;

            @Override
            protected List<Bill> doInBackground() {
                try {
                    if (!DBConnection.testConnection()) {
                        loadError = "Unable to connect to the database. Please verify your connection.";
                        return null;
                    }
                    if (keyword == null || keyword.isEmpty()) {
                        return billDAO.getAllBills();
                    } else {
                        return billDAO.searchBills(keyword);
                    }
                } catch (Exception e) {
                    loadError = "Error loading bills: " + e.getMessage();
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void done() {
                loading = false;
                setLoadingState(false, null);

                if (isCancelled()) {
                    statusLabel.setText("Operation cancelled.");
                    return;
                }

                try {
                    List<Bill> bills = get();
                    if (loadError != null || bills == null) {
                        statusLabel.setText(loadError != null ? loadError : "Failed to load bills.");
                        statusLabel.setForeground(ERROR_COLOR);
                        tableModel.setRowCount(0);
                        recordCountLabel.setText("Total Bills: 0");
                        return;
                    }

                    populateTable(bills);

                    if (keyword != null && !keyword.isEmpty() && bills.isEmpty()) {
                        JOptionPane.showMessageDialog(BillHistoryPanel.this,
                                "No bills found matching: \"" + keyword + "\"",
                                "Search Results", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    statusLabel.setText("Error retrieving results: " + e.getMessage());
                    statusLabel.setForeground(ERROR_COLOR);
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    /** Controls UI button enablement and status label during asynchronous loading. */
    private void setLoadingState(boolean isLoading, String message) {
        refreshBtn.setEnabled(!isLoading);
        searchBtn.setEnabled(!isLoading);
        clearSearchBtn.setEnabled(!isLoading);
        viewDetailsBtn.setEnabled(!isLoading);

        if (isLoading) {
            statusLabel.setText(message != null ? message : "Loading...");
            statusLabel.setForeground(Color.DARK_GRAY);
        } else {
            statusLabel.setText("");
        }
    }

    /** Populates the JTable rows from the provided list of Bill model objects. */
    private void populateTable(List<Bill> bills) {
        tableModel.setRowCount(0);

        if (bills == null || bills.isEmpty()) {
            recordCountLabel.setText("Total Bills: 0");
            recordCountLabel.setForeground(Color.DARK_GRAY);
            return;
        }

        for (Bill b : bills) {
            String dateStr = b.getBillDate() != null
                    ? b.getBillDate().format(DATE_TIME_FMT)
                    : "-";
            String custName = (b.getCustomerName() != null && !b.getCustomerName().trim().isEmpty())
                    ? b.getCustomerName().trim()
                    : "Walk-in Customer";
            String custPhone = (b.getCustomerPhone() != null && !b.getCustomerPhone().trim().isEmpty())
                    ? b.getCustomerPhone().trim()
                    : "-";

            String subtotalStr = b.getSubtotal() != null
                    ? CurrencyUtil.formatCurrency(b.getSubtotal().doubleValue())
                    : CurrencyUtil.formatCurrency(0.0);
            String taxStr = b.getTaxAmount() != null
                    ? CurrencyUtil.formatCurrency(b.getTaxAmount().doubleValue())
                    : CurrencyUtil.formatCurrency(0.0);
            String discountStr = b.getDiscount() != null
                    ? CurrencyUtil.formatCurrency(b.getDiscount().doubleValue())
                    : CurrencyUtil.formatCurrency(0.0);
            String totalStr = b.getTotalAmount() != null
                    ? CurrencyUtil.formatCurrency(b.getTotalAmount().doubleValue())
                    : CurrencyUtil.formatCurrency(0.0);

            // Populated strictly in the order: Bill ID, Customer Name, Customer Phone, Bill Date, Subtotal, Tax, Discount, Grand Total
            tableModel.addRow(new Object[]{
                    b.getBillId(),
                    custName,
                    custPhone,
                    dateStr,
                    subtotalStr,
                    taxStr,
                    discountStr,
                    totalStr
            });
        }

        recordCountLabel.setText("Total Bills: " + bills.size());
        recordCountLabel.setForeground(Color.DARK_GRAY);
    }

    /** Opens the BillDetailsDialog for the currently selected table row asynchronously. */
    private void viewSelectedBillDetails() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a bill from the table to view its details.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);
        Object idVal = tableModel.getValueAt(modelRow, 0);
        if (!(idVal instanceof Integer)) {
            return;
        }
        int billId = (Integer) idVal;

        viewDetailsBtn.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // Load details and items in background worker to prevent EDT freezing
        new SwingWorker<BillDetailsPayload, Void>() {
            private String workerError = null;

            @Override
            protected BillDetailsPayload doInBackground() {
                try {
                    Bill bill = billDAO.getBillById(billId);
                    List<BillItem> items = billDAO.getItemsByBillId(billId);
                    return new BillDetailsPayload(bill, items);
                } catch (Exception e) {
                    workerError = e.getMessage();
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void done() {
                viewDetailsBtn.setEnabled(true);
                setCursor(Cursor.getDefaultCursor());

                if (workerError != null) {
                    JOptionPane.showMessageDialog(BillHistoryPanel.this,
                            "Error loading bill details: " + workerError,
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                try {
                    BillDetailsPayload payload = get();
                    if (payload == null || payload.bill == null) {
                        JOptionPane.showMessageDialog(BillHistoryPanel.this,
                                "Unable to load details for Bill #" + billId + ". Please check database connection.",
                                "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    Window parentWindow = SwingUtilities.getWindowAncestor(BillHistoryPanel.this);
                    BillDetailsDialog dialog = new BillDetailsDialog(parentWindow, payload.bill, payload.items);
                    dialog.setLocationRelativeTo(BillHistoryPanel.this);
                    dialog.setVisible(true);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(BillHistoryPanel.this,
                            "Error opening bill details: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        }.execute();
    }

    /** Simple data carrier for asynchronous bill detail fetching. */
    private record BillDetailsPayload(Bill bill, List<BillItem> items) {}

    // ── Supporting Modal Details Dialog ───────────────────────────────────

    /**
     * Modal dialog displaying comprehensive details and itemized lines of a bill.
     */
    private static class BillDetailsDialog extends JDialog {

        private static final long serialVersionUID = 1L;

        public BillDetailsDialog(Window owner, Bill bill, List<BillItem> items) {
            super(owner, "Bill Details — Bill #" + bill.getBillId(), ModalityType.APPLICATION_MODAL);
            setLayout(new BorderLayout(0, 12));
            getContentPane().setBackground(DashboardPanel.BACKGROUND_COLOR);
            setSize(680, 520);
            setMinimumSize(new Dimension(550, 400));

            // =================================================================
            // Top: Header & Customer Information
            // =================================================================
            JPanel topPanel = new JPanel(new BorderLayout(0, 8));
            topPanel.setBackground(DashboardPanel.BACKGROUND_COLOR);
            topPanel.setBorder(new EmptyBorder(14, 18, 6, 18));

            JLabel titleLabel = new JLabel("Invoice Details — Bill #" + bill.getBillId());
            titleLabel.setFont(FontUtil.getRalewayBold(18f));
            titleLabel.setForeground(HEADING_COLOR);
            topPanel.add(titleLabel, BorderLayout.NORTH);

            // Information Grid: 2 columns (Meta on left, Financials on right)
            JPanel infoGrid = new JPanel(new GridLayout(1, 2, 20, 0));
            infoGrid.setBackground(Color.decode("#F8F9FA"));
            infoGrid.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.decode("#E0E0E0")),
                    new EmptyBorder(10, 14, 10, 14)
            ));

            // Left Meta Panel
            JPanel metaPanel = new JPanel(new GridLayout(4, 1, 0, 4));
            metaPanel.setOpaque(false);
            String dateFormatted = bill.getBillDate() != null
                    ? bill.getBillDate().format(DATE_TIME_FMT)
                    : "-";
            String custName = (bill.getCustomerName() != null && !bill.getCustomerName().trim().isEmpty())
                    ? bill.getCustomerName().trim()
                    : "Walk-in Customer";
            String custPhone = (bill.getCustomerPhone() != null && !bill.getCustomerPhone().trim().isEmpty())
                    ? bill.getCustomerPhone().trim()
                    : "-";

            metaPanel.add(createInfoLabel("Bill ID: ", String.valueOf(bill.getBillId())));
            metaPanel.add(createInfoLabel("Date: ", dateFormatted));
            metaPanel.add(createInfoLabel("Customer: ", custName));
            metaPanel.add(createInfoLabel("Phone: ", custPhone));
            infoGrid.add(metaPanel);

            // Right Financial Panel
            JPanel financialPanel = new JPanel(new GridLayout(5, 1, 0, 4));
            financialPanel.setOpaque(false);

            String subtotalStr = bill.getSubtotal() != null
                    ? CurrencyUtil.formatCurrency(bill.getSubtotal().doubleValue()) : "₹0.00";
            String discountStr = bill.getDiscount() != null
                    ? CurrencyUtil.formatCurrency(bill.getDiscount().doubleValue()) : "₹0.00";
            String cgstStr = bill.getCgstAmount() != null
                    ? CurrencyUtil.formatCurrency(bill.getCgstAmount().doubleValue()) : "₹0.00";
            String sgstStr = bill.getSgstAmount() != null
                    ? CurrencyUtil.formatCurrency(bill.getSgstAmount().doubleValue()) : "₹0.00";
            String totalStr = bill.getTotalAmount() != null
                    ? CurrencyUtil.formatCurrency(bill.getTotalAmount().doubleValue()) : "₹0.00";

            financialPanel.add(createInfoLabel("Subtotal: ", subtotalStr));
            financialPanel.add(createInfoLabel("Discount: ", discountStr));
            financialPanel.add(createInfoLabel("CGST / SGST: ", cgstStr + " / " + sgstStr));
            financialPanel.add(createInfoLabel("Combined Tax: ",
                    bill.getTaxAmount() != null ? CurrencyUtil.formatCurrency(bill.getTaxAmount().doubleValue()) : "₹0.00"));

            JLabel grandTotalLbl = new JLabel("Grand Total: " + totalStr);
            grandTotalLbl.setFont(FontUtil.getRalewayBold(14f));
            grandTotalLbl.setForeground(HEADING_COLOR);
            financialPanel.add(grandTotalLbl);

            infoGrid.add(financialPanel);
            topPanel.add(infoGrid, BorderLayout.CENTER);
            add(topPanel, BorderLayout.NORTH);

            // =================================================================
            // Center: Purchased Items Table
            // =================================================================
            String[] itemColumns = {"#", "Medicine Name", "Quantity", "Price", "Amount"};
            DefaultTableModel itemModel = new DefaultTableModel(itemColumns, 0) {
                private static final long serialVersionUID = 1L;
                @Override public boolean isCellEditable(int row, int col) { return false; }
            };

            JTable itemTable = new JTable(itemModel);
            itemTable.setRowHeight(24);
            itemTable.setFont(FontUtil.getRalewayRegular(12f));
            itemTable.getTableHeader().setFont(FontUtil.getRalewayBold(12f));
            itemTable.setFillsViewportHeight(true);

            // Set column widths & alignments
            if (itemTable.getColumnModel().getColumnCount() >= 5) {
                itemTable.getColumnModel().getColumn(0).setPreferredWidth(40);  // #
                itemTable.getColumnModel().getColumn(0).setMaxWidth(50);
                itemTable.getColumnModel().getColumn(1).setPreferredWidth(260); // Medicine Name
                itemTable.getColumnModel().getColumn(2).setPreferredWidth(70);  // Quantity
                itemTable.getColumnModel().getColumn(3).setPreferredWidth(100); // Price
                itemTable.getColumnModel().getColumn(4).setPreferredWidth(110); // Amount

                DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
                centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

                DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
                rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);

                itemTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
                itemTable.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
                itemTable.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);
                itemTable.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);
            }

            if (items != null && !items.isEmpty()) {
                int rowIdx = 1;
                for (BillItem itm : items) {
                    String priceStr = itm.getPrice() != null
                            ? CurrencyUtil.formatCurrency(itm.getPrice().doubleValue()) : "₹0.00";
                    String amountStr = itm.getAmount() != null
                            ? CurrencyUtil.formatCurrency(itm.getAmount().doubleValue()) : "₹0.00";

                    itemModel.addRow(new Object[]{
                            rowIdx++,
                            itm.getMedicineName() != null ? itm.getMedicineName() : "Unknown",
                            itm.getQuantity(),
                            priceStr,
                            amountStr
                    });
                }
            }

            JScrollPane itemScroll = new JScrollPane(itemTable);
            itemScroll.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 18));
            itemScroll.getViewport().setBackground(DashboardPanel.BACKGROUND_COLOR);
            add(itemScroll, BorderLayout.CENTER);

            // =================================================================
            // Bottom: Print and Close Buttons
            // =================================================================
            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 12));
            bottomPanel.setBackground(DashboardPanel.BACKGROUND_COLOR);

            // Print Bill button
            JButton printBtn = new JButton("Print Bill");
            printBtn.setBackground(DashboardPanel.BUTTON_COLOR);
            printBtn.setForeground(Color.WHITE);
            printBtn.setFocusPainted(false);
            printBtn.setFont(FontUtil.getRalewayBold(12f));
            printBtn.addActionListener(e -> {
                try {
                    BillPrintService printer = new BillPrintService(bill, items);
                    BillPrintService.PrintStatus status = printer.print();
                    if (status == BillPrintService.PrintStatus.FAILURE) {
                        JOptionPane.showMessageDialog(this,
                                "Failed to print the bill. See console for details.",
                                "Print Error", JOptionPane.ERROR_MESSAGE);
                    }
                    // SUCCESS – no notification needed; CANCELLED – silent.
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                            "Unexpected error during printing: " + ex.getMessage(),
                            "Print Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            bottomPanel.add(printBtn);

            // Close button
            JButton closeBtn = new JButton("Close");
            closeBtn.setBackground(DashboardPanel.BUTTON_COLOR);
            closeBtn.setForeground(Color.WHITE);
            closeBtn.setFocusPainted(false);
            closeBtn.setFont(FontUtil.getRalewayBold(12f));
            closeBtn.addActionListener(e -> dispose());
            bottomPanel.add(closeBtn);

            add(bottomPanel, BorderLayout.SOUTH);

            // Escape key dismisses dialog
            getRootPane().registerKeyboardAction(
                    e -> dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_IN_FOCUSED_WINDOW
            );
        }

        private JLabel createInfoLabel(String label, String value) {
            JLabel lbl = new JLabel("<html><b>" + label + "</b>" + value + "</html>");
            lbl.setFont(FontUtil.getRalewayRegular(12f));
            lbl.setForeground(COLOR_DARK_TEXT);
            return lbl;
        }

        private static final Color COLOR_DARK_TEXT = Color.decode("#212529");
    }
}
