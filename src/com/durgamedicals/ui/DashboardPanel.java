package com.durgamedicals.ui;

import com.durgamedicals.dao.*;
import com.durgamedicals.model.*;
import com.durgamedicals.util.DBConnection;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dashboard panel for DurgaMedicals.
 *
 * <p>All UI styling, navigation, logout and CardLayout integration are
 * preserved. The panel loads data automatically, provides a manual
 * Refresh button, and guarantees that the loading state (button disabled
 * and {@code loading} flag) is managed in a single place.</p>
 */
public class DashboardPanel extends JPanel {

    /* --------------------------------------------------------------------- *
     *  Theme colours – private, used internally by the dashboard             *
     * --------------------------------------------------------------------- */
    private static final Color COLOR_BLACK       = new Color(0x17_17_17);
    private static final Color COLOR_HAZEL_GREEN = new Color(0xA3_B1_8A);
    private static final Color COLOR_HAZEL       = new Color(0xB7_A5_8A);
    private static final Color COLOR_WHITE       = Color.WHITE;

    /* --------------------------------------------------------------------- *
     *  Public colour constants – referenced by MedicinesPanel,               *
     *  SuppliersPanel and other UI classes                                   *
     * --------------------------------------------------------------------- */
    public static final Color BACKGROUND_COLOR    = Color.WHITE;                  // #FFFFFF
    public static final Color BUTTON_COLOR        = new Color(0xA3_B1_8A);        // Hazel Green #A3B18A
    public static final Color EXPIRED_HIGHLIGHT   = new Color(0xFF, 0xCC, 0xCC);  // subtle pale red
    public static final Color LOW_STOCK_HIGHLIGHT = new Color(0xFF, 0xE0, 0xB2); // subtle pale orange

    /* --------------------------------------------------------------------- *
     *  UI components                                                         *
     * --------------------------------------------------------------------- */
    private final CardLayout cardLayout;
    private final JPanel     cardsPanel;
    private final MedicinesPanel medicinesPanel = new MedicinesPanel();
    private final BillingPanel billingPanel = new BillingPanel();
    private final BillHistoryPanel billHistoryPanel = new BillHistoryPanel();
    private final ReportsPanel reportsPanel = new ReportsPanel();

    private JLabel   loadingLabel;       // "Loading…", error messages, etc.
    private JButton  refreshButton;      // manual refresh
    private JLabel   shopNameLabel;      // set by loadShopName()
    private JLabel   headingLabel;       // "Dashboard" title

    private JLabel totalMedsLabel;
    private JLabel lowStockLabel;
    private JLabel suppliersLabel;
    private JLabel todaySalesLabel;

    private JTable recentBillsTable;
    private JTable lowStockTable;
    private JTable expiredTable;

    /* --------------------------------------------------------------------- *
     *  DAO instances – singletons for the whole panel                         *
     * --------------------------------------------------------------------- */
    private final MedicineDAO medicineDAO   = new MedicineDAO();
    private final SupplierDAO supplierDAO   = new SupplierDAO();
    private final BillDAO     billDAO       = new BillDAO();
    private final SettingsDAO settingsDAO   = new SettingsDAO();

    /* --------------------------------------------------------------------- *
     *  Loading‑state flag – prevents concurrent refreshes                     *
     * --------------------------------------------------------------------- */
    private volatile boolean loading = false;

    /* --------------------------------------------------------------------- *
     *  Formatters used throughout the UI                                      *
     * --------------------------------------------------------------------- */
    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final NumberFormat CURRENCY_FMT = NumberFormat.getCurrencyInstance(
            new Locale("en", "IN"));

    /* --------------------------------------------------------------------- *
     *  Constructor – builds UI, loads shop name, and auto‑loads data          *
     * --------------------------------------------------------------------- */
    public DashboardPanel(User user, Runnable onLogout) {
        setLayout(new BorderLayout());
        setBackground(COLOR_BLACK);

        /* -------- Sidebar (navigation) -------- */
        JPanel sideBar = createSidebar(user, onLogout);
        add(sideBar, BorderLayout.WEST);

        /* -------- Main card area (CardLayout) -------- */
        cardLayout = new CardLayout();
        cardsPanel = new JPanel(cardLayout);
        cardsPanel.setBackground(COLOR_WHITE);
        cardsPanel.add(createDashboardCard(), "dashboard");
        // existing panels – no-arg constructors as defined in the project
        cardsPanel.add(medicinesPanel, "medicines");
        cardsPanel.add(new SuppliersPanel(), "suppliers");
        cardsPanel.add(billingPanel, "billing");
        cardsPanel.add(new SettingsPanel(), "settings");
        cardsPanel.add(billHistoryPanel, "bill_history");
        cardsPanel.add(reportsPanel, "reports");
        add(cardsPanel, BorderLayout.CENTER);

        /* -------- Dynamic shop‑name loading (original SettingsDAO logic) -------- */
        loadShopName();

        /* -------- Auto‑load dashboard data when the panel appears -------- */
        loadDashboardData();          // loading flag handled inside the method
    }

    /* --------------------------------------------------------------------- *
     *  Sidebar construction helpers                                           *
     * --------------------------------------------------------------------- */
    private JPanel createSidebar(User user, Runnable onLogout) {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(COLOR_BLACK);
        side.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("DurgaMedicals");
        title.setForeground(COLOR_WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(title);
        side.add(Box.createVerticalStrut(20));

        side.add(makeNavButton("Dashboard", () -> showCard("dashboard")));
        side.add(makeNavButton("Medicines", () -> {
            showCard("medicines");
            medicinesPanel.loadAllMedicines();
        }));
        side.add(makeNavButton("Suppliers", () -> showCard("suppliers")));
        side.add(makeNavButton("Billing",   () -> {
            showCard("billing");
            billingPanel.reloadMedicines();
        }));
        side.add(makeNavButton("Settings",  () -> showCard("settings")));

        side.add(makeNavButton("Bill History", () -> {
            showCard("bill_history");
            billHistoryPanel.reloadBills();
        }));
        side.add(makeNavButton("Reports", () -> {
            showCard("reports");
            reportsPanel.refreshReport();
        }));

        side.add(Box.createVerticalGlue());

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoutBtn.addActionListener(e -> onLogout.run());
        side.add(logoutBtn);

        return side;
    }

    private JButton makeNavButton(String text, Runnable action) {
        JButton btn = new JButton(text);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, btn.getPreferredSize().height));
        btn.addActionListener(e -> action.run());
        return btn;
    }

    private void showCard(String name) {
        cardLayout.show(cardsPanel, name);
    }

    /* --------------------------------------------------------------------- *
     *  Dashboard UI construction                                            *
     * --------------------------------------------------------------------- */
    private JPanel createDashboardCard() {
        JPanel dash = new JPanel(new BorderLayout(0, 12));
        dash.setBackground(COLOR_WHITE);
        dash.setBorder(new EmptyBorder(16, 20, 16, 20));

        /* ================================================================= *
         *  TOP SECTION (North): Header + Stat Cards + Refresh Bar           *
         * ================================================================= */
        JPanel topContainer = new JPanel(new BorderLayout(0, 12));
        topContainer.setOpaque(false);

        /* ----- 1. Header (title + shop name) ----- */
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        headingLabel = new JLabel("Dashboard");
        headingLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        headingLabel.setForeground(COLOR_BLACK);
        header.add(headingLabel, BorderLayout.WEST);

        shopNameLabel = new JLabel("");                 // filled asynchronously
        shopNameLabel.setFont(new Font("SansSerif", Font.PLAIN, 18));
        shopNameLabel.setForeground(COLOR_BLACK);
        header.add(shopNameLabel, BorderLayout.EAST);
        topContainer.add(header, BorderLayout.NORTH);

        /* ----- 2. Middle of top: Stat cards + Refresh Bar ----- */
        JPanel statsAndRefresh = new JPanel(new BorderLayout(0, 10));
        statsAndRefresh.setOpaque(false);

        // 4 statistic cards in 1 row
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 14, 0));
        statsPanel.setOpaque(false);
        statsPanel.setPreferredSize(new Dimension(0, 105));
        totalMedsLabel  = makeStatCard(statsPanel, "Total Medicines", "0");
        lowStockLabel   = makeStatCard(statsPanel, "Low Stock", "0");
        suppliersLabel  = makeStatCard(statsPanel, "Suppliers", "0");
        todaySalesLabel = makeStatCard(statsPanel, "Today's Sales", CURRENCY_FMT.format(BigDecimal.ZERO));
        statsAndRefresh.add(statsPanel, BorderLayout.CENTER);

        // Refresh button + loading/error indicator
        JPanel refreshPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        refreshPanel.setOpaque(false);
        refreshButton = new JButton("Refresh");
        refreshButton.setFont(new Font("SansSerif", Font.BOLD, 12));
        refreshButton.setBackground(COLOR_HAZEL_GREEN);
        refreshButton.setForeground(COLOR_BLACK);
        refreshButton.setFocusPainted(false);
        refreshButton.addActionListener(this::onRefreshClicked);

        loadingLabel = new JLabel("");
        loadingLabel.setForeground(Color.RED);
        loadingLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        refreshPanel.add(refreshButton);
        refreshPanel.add(loadingLabel);
        statsAndRefresh.add(refreshPanel, BorderLayout.SOUTH);

        topContainer.add(statsAndRefresh, BorderLayout.CENTER);
        dash.add(topContainer, BorderLayout.NORTH);

        /* ================================================================= *
         *  TABLES SECTION (Center): Expands to fill all remaining space     *
         * ================================================================= */
        recentBillsTable = createTable(new String[]{"ID", "Customer", "Date", "Amount"});
        lowStockTable    = createTable(new String[]{"ID", "Name", "Stock", "Expiry"});
        expiredTable     = createTable(new String[]{"ID", "Name", "Stock", "Expiry"});

        // Upper section: Recent Bills + Low Stock side-by-side
        JPanel upperTables = new JPanel(new GridLayout(1, 2, 14, 0));
        upperTables.setOpaque(false);
        upperTables.add(createTableSection("Recent Bills", recentBillsTable));
        upperTables.add(createTableSection("Low Stock Medicines", lowStockTable));

        // Lower section: Expired Medicines
        JPanel lowerTable = createTableSection("Expired Medicines", expiredTable);

        // Split pane to allow flexible vertical distribution
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, upperTables, lowerTable);
        splitPane.setResizeWeight(0.5);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);
        upperTables.setMinimumSize(new Dimension(100, 100));
        lowerTable.setMinimumSize(new Dimension(100, 80));

        // Ensure divider position is properly set when dashboard is laid out
        splitPane.addAncestorListener(new javax.swing.event.AncestorListener() {
            @Override
            public void ancestorAdded(javax.swing.event.AncestorEvent event) {
                SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(0.5));
            }
            @Override
            public void ancestorRemoved(javax.swing.event.AncestorEvent event) {}
            @Override
            public void ancestorMoved(javax.swing.event.AncestorEvent event) {}
        });

        dash.add(splitPane, BorderLayout.CENTER);
        return dash;
    }

    /**
     * Creates a section panel with a clear title label above a scrollable table.
     */
    private JPanel createTableSection(String title, JTable table) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);

        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        lbl.setForeground(COLOR_BLACK);
        panel.add(lbl, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(COLOR_WHITE);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates a stat card panel, adds it to the parent grid, and returns the value label
     * so the caller can update it during refresh.
     */
    private JLabel makeStatCard(JPanel parent, String title, String value) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(COLOR_HAZEL_GREEN);
        card.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        card.setPreferredSize(new Dimension(160, 105));
        card.setMinimumSize(new Dimension(120, 85));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setForeground(COLOR_BLACK);
        titleLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JLabel valueLbl = new JLabel(value);
        valueLbl.setForeground(COLOR_BLACK);
        valueLbl.setFont(new Font("SansSerif", Font.BOLD, 22));

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valueLbl, BorderLayout.CENTER);
        parent.add(card);
        return valueLbl;
    }

    private JTable createTable(String[] columnNames) {
        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        return table;
    }

    /* --------------------------------------------------------------------- *
     *  Refresh button handling – delegates state handling to loadDashboardData()
     * --------------------------------------------------------------------- */
    private void onRefreshClicked(ActionEvent e) {
        loadDashboardData();
    }

    /* --------------------------------------------------------------------- *
     *  Dashboard data loading (SwingWorker) – unified loading‑state handling *
     * --------------------------------------------------------------------- */
    private void loadDashboardData() {
        // Guard against re‑entrancy if a load is already in progress
        if (loading) {
            return;
        }

        // ---- Centralised loading‑state update ---------------------------------
        loading = true;
        loadingLabel.setText("Loading\u2026");
        refreshButton.setEnabled(false);
        // -----------------------------------------------------------------------

        SwingWorker<Void, Void> worker = new SwingWorker<>() {

            // Results gathered here
            private List<Medicine> allMeds;
            private List<Medicine> lowStockMeds;
            private List<Medicine> expiredMeds;
            private List<Bill> recentBills;
            private int totalSuppliers;
            private BigDecimal todaySales;

            @Override
            protected Void doInBackground() {
                /* ---- Quick DB health‑check (uses existing configuration) ---- */
                if (!DBConnection.testConnection()) {
                    // Health‑check failed – abort further work
                    return null;
                }

                try {
                    allMeds       = medicineDAO.getAllMedicines();
                    lowStockMeds  = medicineDAO.getLowStockMedicines();
                    expiredMeds   = medicineDAO.getExpiredMedicines();
                    recentBills   = billDAO.getAllBills();
                    totalSuppliers = supplierDAO.getAllSuppliers().size();

                    todaySales = computeTodaySales(recentBills);
                } catch (Exception ex) {
                    // Unexpected exception (DAO methods swallow SQLExceptions)
                    ex.printStackTrace();
                    // Signal failure by nullifying the results
                    allMeds = lowStockMeds = expiredMeds = null;
                    recentBills = null;
                }
                return null;
            }

            @Override
            protected void done() {
                // ---- Always reset loading state, even on cancel / failure ----------
                loading = false;
                refreshButton.setEnabled(true);
                // ---------------------------------------------------------------------

                if (isCancelled()) {
                    loadingLabel.setText("Refresh cancelled.");
                    return;
                }

                if (allMeds == null) {
                    // Either health‑check failed or an unexpected exception occurred
                    loadingLabel.setText("Unable to load dashboard data \u2013 verify the DB connection.");
                    return;
                }

                /* --------- Populate UI components ---------- */
                totalMedsLabel.setText(String.valueOf(allMeds.size()));
                lowStockLabel.setText(String.valueOf(lowStockMeds.size()));
                suppliersLabel.setText(String.valueOf(totalSuppliers));
                todaySalesLabel.setText(CURRENCY_FMT.format(todaySales));

                populateBillsTable(recentBills);
                populateMedicineTable(lowStockTable, lowStockMeds);
                populateMedicineTable(expiredTable, expiredMeds);

                // clear any previous error message
                loadingLabel.setText("");
            }
        };

        worker.execute();
    }

    /** Compute today's sales from the list of bills that have already been fetched. */
    private BigDecimal computeTodaySales(List<Bill> bills) {
        if (bills == null) {
            return BigDecimal.ZERO;
        }
        LocalDate today = LocalDate.now();
        BigDecimal sum = BigDecimal.ZERO;
        for (Bill b : bills) {
            if (b.getBillDate() != null && b.getBillDate().toLocalDate().equals(today)) {
                sum = sum.add(b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO);
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /* --------------------------------------------------------------------- *
     *  Table population helpers                                              *
     * --------------------------------------------------------------------- */
    private void populateBillsTable(List<Bill> bills) {
        DefaultTableModel model = (DefaultTableModel) recentBillsTable.getModel();
        model.setRowCount(0);

        if (bills == null || bills.isEmpty()) {
            return;
        }

        // Defensive copy – we do not want to modify the original DAO list
        List<Bill> sortable = new ArrayList<>(bills);

        // Safely sort – treat null dates as the oldest possible value
        sortable.sort((b1, b2) -> {
            if (b1.getBillDate() == null && b2.getBillDate() == null) return 0;
            if (b1.getBillDate() == null) return 1;
            if (b2.getBillDate() == null) return -1;
            return b2.getBillDate().compareTo(b1.getBillDate()); // descending
        });

        int limit = Math.min(5, sortable.size());
        for (int i = 0; i < limit; i++) {
            Bill b = sortable.get(i);
            String amountStr = b.getTotalAmount() != null
                    ? CURRENCY_FMT.format(b.getTotalAmount())
                    : CURRENCY_FMT.format(BigDecimal.ZERO);
            String custName = (b.getCustomerName() != null && !b.getCustomerName().trim().isEmpty())
                    ? b.getCustomerName().trim()
                    : "Walk-in Customer";
            model.addRow(new Object[]{
                    b.getBillId(),
                    custName,
                    b.getBillDate() != null
                            ? b.getBillDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            : "",
                    amountStr
            });
        }
    }

    private void populateMedicineTable(JTable table, List<Medicine> meds) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);

        if (meds == null || meds.isEmpty()) {
            return;
        }

        for (Medicine m : meds) {
            model.addRow(new Object[]{
                    m.getMedicineId(),
                    m.getMedicineName(),
                    m.getQuantity(),
                    m.getExpiryDate() != null
                            ? m.getExpiryDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            : ""
            });
        }
    }

    /* --------------------------------------------------------------------- *
     *  Dynamic shop‑name loading (original SettingsDAO logic)                *
     * --------------------------------------------------------------------- */
    private void loadShopName() {
        // Run in a background thread to avoid blocking the UI.
        new Thread(() -> {
            try {
                // The key used by the original application is assumed to be "shop_name".
                // Adjust this constant if a different key is used in your database.
                String fetched = settingsDAO.getSetting("shop_name");
                final String shopName = fetched != null ? fetched : "";
                SwingUtilities.invokeLater(() -> shopNameLabel.setText(shopName));
            } catch (SQLException | IllegalStateException e) {
                // DAO already logs the SQLException; we just show a user‑friendly outcome.
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> shopNameLabel.setText(""));
            }
        }, "ShopNameLoader").start();
    }
}
