package com.durgamedicals.ui;

import com.durgamedicals.dao.MedicineDAO;
import com.durgamedicals.dao.ReportDAO;
import com.durgamedicals.dao.ReportDAO.DailySalesPoint;
import com.durgamedicals.dao.ReportDAO.MonthlySalesPoint;
import com.durgamedicals.dao.ReportDAO.SalesSummary;
import com.durgamedicals.dao.ReportDAO.TopMedicineSale;
import com.durgamedicals.model.Medicine;
import com.durgamedicals.util.CSVExporter;
import com.durgamedicals.util.CurrencyUtil;
import com.durgamedicals.util.DBConnection;
import com.durgamedicals.util.FontUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ReportsPanel.java
 *
 * Full functional UI panel for business analytics and pharmacy reports.
 * Features:
 *   • Sales Overview (Total Sales, Total Bills, Total Discounts, Total Combined Tax).
 *   • Date Presets (Today, Yesterday, Last 7 Days, This Month, Custom Date Range).
 *   • Exclusive End-Boundary Date Filtering: bill_date >= start AND bill_date < endExclusive.
 *   • Interactive pure-Java2D Sales Chart (supports Daily and Monthly views with readable labels).
 *   • Top-Selling Medicines ranking table.
 *   • Real-time Low Stock report (independent of sales date range).
 *   • Tab-sensitive CSV Export with full UTF-8 encoding and Excel compatibility.
 *   • Asynchronous SwingWorker execution (prevents EDT freezes).
 */
public class ReportsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    // ── Theme Colours ───────────────────────────────────────────────────────
    public static final Color HEADING_COLOR       = Color.decode("#171717");
    public static final Color STAT_CARD_BG        = Color.decode("#A3B18A"); // Hazel Green
    public static final Color STAT_TEXT_COLOR     = Color.decode("#171717");
    public static final Color ERROR_COLOR         = Color.decode("#D9534F");

    // ── DAOs & Formatters ───────────────────────────────────────────────────
    private final ReportDAO   reportDAO   = new ReportDAO();
    private final MedicineDAO medicineDAO = new MedicineDAO();

    private static final DateTimeFormatter INPUT_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ── UI Components ──────────────────────────────────────────────────────
    private final JComboBox<String> cbDatePresets = new JComboBox<>(new String[]{
            "Today", "Yesterday", "Last 7 Days", "This Month", "Custom Date Range"
    });
    private final JTextField tfStartDate = new JTextField(9);
    private final JTextField tfEndDate   = new JTextField(9);
    private final JButton btnGenerate    = new JButton("Generate Report");
    private final JButton btnExportCSV   = new JButton("Export to CSV");
    private final JLabel lblStatus       = new JLabel("");

    // Stat Cards
    private final JLabel lblTotalSales     = new JLabel("₹0.00");
    private final JLabel lblTotalBills     = new JLabel("0");
    private final JLabel lblTotalDiscounts = new JLabel("₹0.00");
    private final JLabel lblTotalTax       = new JLabel("₹0.00");

    // Tabs
    private final JTabbedPane tabbedPane = new JTabbedPane();

    // Tab 1: Sales Chart
    private final SalesChartComponent chartComponent = new SalesChartComponent();
    private final JComboBox<String> cbChartMode = new JComboBox<>(new String[]{"Auto", "Daily", "Monthly"});

    // Tab 2: Top Selling Table
    private final DefaultTableModel topMedsModel;
    private final JTable tblTopMeds;

    // Tab 3: Low Stock Table
    private final DefaultTableModel lowStockModel;
    private final JTable tblLowStock;

    // Tab 4: Stock Quantity Table (NEW)
    private final DefaultTableModel stockQuantityModel;
    private final JTable tblStockQuantity;
    private final JButton btnRefreshStock = new JButton("Refresh Stock");
    private final JLabel lblStockCount = new JLabel("");
    private final JLabel lblStockStatus = new JLabel("");
    private volatile boolean loadingStock = false;

    // Cached results for export and chart toggling
    private SalesSummary currentSummary = null;
    private List<DailySalesPoint> currentDailyPoints = new ArrayList<>();
    private List<MonthlySalesPoint> currentMonthlyPoints = new ArrayList<>();
    private List<TopMedicineSale> currentTopMeds = new ArrayList<>();
    private List<Medicine> currentLowStock = new ArrayList<>();
    private List<Medicine> currentStockQuantity = new ArrayList<>();
    private boolean isMonthlyChart = false;

    private volatile boolean loading = false;

    public ReportsPanel() {
        setLayout(new BorderLayout(0, 10));
        setBackground(DashboardPanel.BACKGROUND_COLOR);

        // =====================================================================
        // TOP: Heading + Date Filters Bar
        // =====================================================================
        JPanel topContainer = new JPanel(new BorderLayout(0, 12));
        topContainer.setBackground(DashboardPanel.BACKGROUND_COLOR);
        topContainer.setBorder(new EmptyBorder(14, 18, 6, 18));

        JLabel heading = new JLabel("Reports & Analytics");
        heading.setFont(FontUtil.getRalewayBold(24f));
        heading.setForeground(HEADING_COLOR);
        topContainer.add(heading, BorderLayout.NORTH);

        // Filter Controls Panel
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        filterBar.setBackground(DashboardPanel.BACKGROUND_COLOR);

        filterBar.add(new JLabel("Date Range:"));
        filterBar.add(cbDatePresets);

        filterBar.add(new JLabel("From:"));
        filterBar.add(tfStartDate);
        filterBar.add(new JLabel("To:"));
        filterBar.add(tfEndDate);

        styleButton(btnGenerate);
        styleButton(btnExportCSV);
        filterBar.add(btnGenerate);
        filterBar.add(btnExportCSV);

        lblStatus.setFont(FontUtil.getRalewayBold(12f));
        lblStatus.setForeground(Color.DARK_GRAY);
        filterBar.add(lblStatus);

        topContainer.add(filterBar, BorderLayout.SOUTH);
        add(topContainer, BorderLayout.NORTH);

        // =====================================================================
        // CENTER: Stat Cards + Tabbed Content
        // =====================================================================
        JPanel centerContainer = new JPanel(new BorderLayout(0, 12));
        centerContainer.setBackground(DashboardPanel.BACKGROUND_COLOR);
        centerContainer.setBorder(new EmptyBorder(0, 18, 14, 18));

        // Stat Cards Section (1 row of 4 cards)
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 14, 0));
        statsPanel.setOpaque(false);
        statsPanel.setPreferredSize(new Dimension(0, 95));

        makeStatCard(statsPanel, "Total Sales", lblTotalSales);
        makeStatCard(statsPanel, "Total Bills", lblTotalBills);
        makeStatCard(statsPanel, "Total Discounts", lblTotalDiscounts);
        makeStatCard(statsPanel, "Total Tax (CGST+SGST)", lblTotalTax);

        centerContainer.add(statsPanel, BorderLayout.NORTH);

        // Tabbed Panel
        tabbedPane.setFont(FontUtil.getRalewayBold(13f));
        tabbedPane.setBackground(DashboardPanel.BACKGROUND_COLOR);

        // --- Tab 1: Sales Analysis Chart ---
        JPanel chartTabPanel = new JPanel(new BorderLayout(0, 8));
        chartTabPanel.setBackground(DashboardPanel.BACKGROUND_COLOR);
        chartTabPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel chartControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        chartControls.setBackground(DashboardPanel.BACKGROUND_COLOR);
        chartControls.add(new JLabel("Chart View:"));
        chartControls.add(cbChartMode);
        chartTabPanel.add(chartControls, BorderLayout.NORTH);
        chartTabPanel.add(chartComponent, BorderLayout.CENTER);
        tabbedPane.addTab("Sales Trend (Chart)", chartTabPanel);

        // --- Tab 2: Top-Selling Medicines Table ---
        String[] topColumns = {"Rank", "Medicine Name", "Units Sold", "Total Revenue"};
        topMedsModel = new DefaultTableModel(topColumns, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tblTopMeds = createStyledTable(topMedsModel);
        setupTopMedsAlignments();
        JScrollPane topMedsScroll = new JScrollPane(tblTopMeds);
        topMedsScroll.getViewport().setBackground(DashboardPanel.BACKGROUND_COLOR);
        tabbedPane.addTab("Top-Selling Medicines", topMedsScroll);

        // --- Tab 3: Low Stock Report Table ---
        String[] lowStockColumns = {"ID", "Medicine Name", "Category", "Current Stock", "Min Threshold", "Selling Price"};
        lowStockModel = new DefaultTableModel(lowStockColumns, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tblLowStock = createStyledTable(lowStockModel);
        setupLowStockAlignments();
        tblLowStock.setDefaultRenderer(Object.class, new LowStockRowRenderer());
        JScrollPane lowStockScroll = new JScrollPane(tblLowStock);
        lowStockScroll.getViewport().setBackground(DashboardPanel.BACKGROUND_COLOR);
        tabbedPane.addTab("Low Stock Alerts", lowStockScroll);

        // --- Tab 4: Stock Quantity Report Table (NEW) ---
        JPanel stockTabPanel = new JPanel(new BorderLayout(0, 8));
        stockTabPanel.setBackground(DashboardPanel.BACKGROUND_COLOR);
        stockTabPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel stockHeaderBar = new JPanel(new BorderLayout(8, 0));
        stockHeaderBar.setBackground(DashboardPanel.BACKGROUND_COLOR);

        lblStockCount.setFont(FontUtil.getRalewayBold(12f));
        lblStockCount.setForeground(HEADING_COLOR);
        stockHeaderBar.add(lblStockCount, BorderLayout.WEST);

        JPanel stockActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        stockActions.setBackground(DashboardPanel.BACKGROUND_COLOR);

        lblStockStatus.setFont(FontUtil.getRalewayRegular(12f));
        lblStockStatus.setForeground(Color.DARK_GRAY);
        stockActions.add(lblStockStatus);

        styleButton(btnRefreshStock);
        stockActions.add(btnRefreshStock);

        stockHeaderBar.add(stockActions, BorderLayout.EAST);
        stockTabPanel.add(stockHeaderBar, BorderLayout.NORTH);

        String[] stockColumns = {"Medicine ID", "Medicine Name", "Category", "Quantity Left", "Minimum Quantity", "Stock Status"};
        stockQuantityModel = new DefaultTableModel(stockColumns, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tblStockQuantity = createStyledTable(stockQuantityModel);
        setupStockQuantityColumns();
        JScrollPane stockScroll = new JScrollPane(tblStockQuantity);
        stockScroll.getViewport().setBackground(DashboardPanel.BACKGROUND_COLOR);
        stockTabPanel.add(stockScroll, BorderLayout.CENTER);

        tabbedPane.addTab("Stock Quantity", stockTabPanel);

        centerContainer.add(tabbedPane, BorderLayout.CENTER);
        add(centerContainer, BorderLayout.CENTER);

        // =====================================================================
        // Initialize Listeners & Initial Load
        // =====================================================================
        attachListeners();
        applyPresetDate("Today");
        refreshReport();
    }

    /** Styles standard buttons with the application theme. */
    private void styleButton(JButton btn) {
        btn.setBackground(DashboardPanel.BUTTON_COLOR);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(FontUtil.getRalewayBold(12f));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /** Creates a consistent stat card panel matching Durga Medicals theme. */
    private void makeStatCard(JPanel parent, String title, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(STAT_CARD_BG);
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setForeground(STAT_TEXT_COLOR);
        titleLbl.setFont(FontUtil.getRalewayBold(12f));

        valueLabel.setForeground(STAT_TEXT_COLOR);
        valueLabel.setFont(FontUtil.getRalewayBold(20f));

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        parent.add(card);
    }

    /** Creates a standardized, read-only JTable matching project typography. */
    private JTable createStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowHeight(25);
        table.setFont(FontUtil.getRalewayRegular(12f));
        table.getTableHeader().setFont(FontUtil.getRalewayBold(12f));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        return table;
    }

    private void setupTopMedsAlignments() {
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);

        tblTopMeds.getColumnModel().getColumn(0).setCellRenderer(center); // Rank
        tblTopMeds.getColumnModel().getColumn(2).setCellRenderer(center); // Units
        tblTopMeds.getColumnModel().getColumn(3).setCellRenderer(right);  // Revenue
    }

    private void setupLowStockAlignments() {
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);

        tblLowStock.getColumnModel().getColumn(0).setCellRenderer(center); // ID
        tblLowStock.getColumnModel().getColumn(3).setCellRenderer(center); // Current Stock
        tblLowStock.getColumnModel().getColumn(4).setCellRenderer(center); // Min Threshold
        tblLowStock.getColumnModel().getColumn(5).setCellRenderer(right);  // Selling Price
    }

    private void setupStockQuantityColumns() {
        StockQuantityRowRenderer centerRenderer = new StockQuantityRowRenderer(SwingConstants.CENTER, false);
        StockQuantityRowRenderer leftRenderer   = new StockQuantityRowRenderer(SwingConstants.LEFT, false);
        StockQuantityRowRenderer statusRenderer = new StockQuantityRowRenderer(SwingConstants.CENTER, true);

        tblStockQuantity.getColumnModel().getColumn(0).setCellRenderer(centerRenderer); // Medicine ID
        tblStockQuantity.getColumnModel().getColumn(1).setCellRenderer(leftRenderer);   // Medicine Name
        tblStockQuantity.getColumnModel().getColumn(2).setCellRenderer(leftRenderer);   // Category
        tblStockQuantity.getColumnModel().getColumn(3).setCellRenderer(centerRenderer); // Quantity Left
        tblStockQuantity.getColumnModel().getColumn(4).setCellRenderer(centerRenderer); // Minimum Quantity
        tblStockQuantity.getColumnModel().getColumn(5).setCellRenderer(statusRenderer); // Stock Status

        tblStockQuantity.getColumnModel().getColumn(0).setPreferredWidth(85);
        tblStockQuantity.getColumnModel().getColumn(1).setPreferredWidth(230);
        tblStockQuantity.getColumnModel().getColumn(2).setPreferredWidth(140);
        tblStockQuantity.getColumnModel().getColumn(3).setPreferredWidth(100);
        tblStockQuantity.getColumnModel().getColumn(4).setPreferredWidth(115);
        tblStockQuantity.getColumnModel().getColumn(5).setPreferredWidth(130);
    }

    /**
     * Determines stock status according to business logic:
     *   • Out of Stock: Quantity is 0.
     *   • Critical Stock: Quantity is below minimum quantity.
     *   • Low Stock: Quantity is equal to minimum quantity.
     *   • In Stock: Quantity is greater than minimum quantity.
     */
    public static String calculateStockStatus(Integer quantity, int minQuantity) {
        int qty = (quantity != null) ? quantity : 0;
        if (qty <= 0) {
            return "Out of Stock";
        } else if (qty < minQuantity) {
            return "Critical Stock";
        } else if (qty == minQuantity) {
            return "Low Stock";
        } else {
            return "In Stock";
        }
    }

    /** Attaches action listeners for filters, buttons, and mode switches. */
    private void attachListeners() {
        cbDatePresets.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                String selected = (String) cbDatePresets.getSelectedItem();
                applyPresetDate(selected);
            }
        });

        btnGenerate.addActionListener(e -> refreshReport());
        btnExportCSV.addActionListener(e -> exportActiveTabToCSV());
        btnRefreshStock.addActionListener(e -> refreshStockQuantityOnly());

        cbChartMode.addActionListener(e -> updateChartDisplay());
    }

    /** Applies preset dates to input fields and locks them unless 'Custom' is chosen. */
    private void applyPresetDate(String preset) {
        LocalDate today = LocalDate.now();
        boolean isCustom = "Custom Date Range".equalsIgnoreCase(preset);

        tfStartDate.setEditable(isCustom);
        tfEndDate.setEditable(isCustom);

        switch (preset) {
            case "Today" -> {
                tfStartDate.setText(today.format(INPUT_DATE_FMT));
                tfEndDate.setText(today.format(INPUT_DATE_FMT));
            }
            case "Yesterday" -> {
                LocalDate yesterday = today.minusDays(1);
                tfStartDate.setText(yesterday.format(INPUT_DATE_FMT));
                tfEndDate.setText(yesterday.format(INPUT_DATE_FMT));
            }
            case "Last 7 Days" -> {
                tfStartDate.setText(today.minusDays(6).format(INPUT_DATE_FMT));
                tfEndDate.setText(today.format(INPUT_DATE_FMT));
            }
            case "This Month" -> {
                LocalDate firstDay = today.withDayOfMonth(1);
                tfStartDate.setText(firstDay.format(INPUT_DATE_FMT));
                tfEndDate.setText(today.format(INPUT_DATE_FMT));
            }
            case "Custom Date Range" -> {
                // Keep existing text or default to this month
                if (tfStartDate.getText().trim().isEmpty()) {
                    tfStartDate.setText(today.withDayOfMonth(1).format(INPUT_DATE_FMT));
                }
                if (tfEndDate.getText().trim().isEmpty()) {
                    tfEndDate.setText(today.format(INPUT_DATE_FMT));
                }
            }
        }
    }

    /**
     * Executes the report queries asynchronously via SwingWorker to avoid freezing the EDT.
     */
    public void refreshReport() {
        if (loading) {
            return;
        }

        // Parse user-specified date boundaries
        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = LocalDate.parse(tfStartDate.getText().trim(), INPUT_DATE_FMT);
            endDate   = LocalDate.parse(tfEndDate.getText().trim(), INPUT_DATE_FMT);
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this,
                    "Invalid date format. Please use YYYY-MM-DD.",
                    "Date Format Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (startDate.isAfter(endDate)) {
            JOptionPane.showMessageDialog(this,
                    "Start Date cannot be after End Date.",
                    "Date Range Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Exclusive end boundary: startDate 00:00:00 up to endDate + 1 day 00:00:00
        LocalDateTime startInclusive = startDate.atStartOfDay();
        LocalDateTime endExclusive   = endDate.plusDays(1).atStartOfDay();

        loading = true;
        setLoadingState(true, "Generating report...");

        new SwingWorker<ReportDataPayload, Void>() {
            private String workerError = null;

            @Override
            protected ReportDataPayload doInBackground() {
                try {
                    if (!DBConnection.testConnection()) {
                        workerError = "Unable to connect to the database. Please verify configuration.";
                        return null;
                    }

                    // 1. Sales summary (Total Sales, Total Bills, Total Discounts, Total Tax)
                    SalesSummary summary = reportDAO.getSalesSummary(startInclusive, endExclusive);

                    // 2. Top-selling medicines within range
                    List<TopMedicineSale> topMeds = reportDAO.getTopSellingMedicines(startInclusive, endExclusive, 20);

                    // 3. Sales trend points (both daily and monthly fetched to allow instant toggle)
                    List<DailySalesPoint> dailyPoints = reportDAO.getDailySales(startInclusive, endExclusive);
                    List<MonthlySalesPoint> monthlyPoints = reportDAO.getMonthlySales(startInclusive, endExclusive);

                    // 4. Low stock report (independent of sales date range)
                    List<Medicine> lowStock = medicineDAO.getLowStockMedicines();

                    // 5. Stock quantity report (all medicines sorted by quantity left ascending)
                    List<Medicine> stockQuantity = medicineDAO.getAllMedicines();
                    if (stockQuantity != null) {
                        stockQuantity.sort(Comparator.comparingInt((Medicine m) -> m.getQuantity() != null ? m.getQuantity() : 0)
                                                     .thenComparing(Medicine::getMedicineName, String.CASE_INSENSITIVE_ORDER));
                    }

                    return new ReportDataPayload(summary, topMeds, dailyPoints, monthlyPoints, lowStock, stockQuantity);

                } catch (Exception ex) {
                    workerError = ex.getMessage();
                    ex.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void done() {
                loading = false;
                setLoadingState(false, null);

                if (workerError != null) {
                    lblStatus.setText(workerError);
                    lblStatus.setForeground(ERROR_COLOR);
                    return;
                }

                try {
                    ReportDataPayload payload = get();
                    if (payload == null) {
                        return;
                    }

                    // Cache payload references
                    currentSummary = payload.summary;
                    currentTopMeds = payload.topMeds;
                    currentDailyPoints = payload.dailyPoints;
                    currentMonthlyPoints = payload.monthlyPoints;
                    currentLowStock = payload.lowStock;
                    currentStockQuantity = payload.stockQuantity;

                    // Update UI components
                    displaySalesSummary(currentSummary);
                    displayTopSellingMedicines(currentTopMeds);
                    displayLowStock(currentLowStock);
                    displayStockQuantity(currentStockQuantity);

                    // Determine chart mode automatically if "Auto" is selected
                    long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
                    isMonthlyChart = daysBetween > 31;
                    updateChartDisplay();

                } catch (Exception ex) {
                    lblStatus.setText("Error rendering report: " + ex.getMessage());
                    lblStatus.setForeground(ERROR_COLOR);
                    ex.printStackTrace();
                }
            }
        }.execute();
    }

    /** Updates the overview stat cards. */
    private void displaySalesSummary(SalesSummary summary) {
        if (summary == null) {
            lblTotalSales.setText("₹0.00");
            lblTotalBills.setText("0");
            lblTotalDiscounts.setText("₹0.00");
            lblTotalTax.setText("₹0.00");
            return;
        }

        lblTotalSales.setText(CurrencyUtil.formatCurrency(summary.totalSales().doubleValue()));
        lblTotalBills.setText(String.valueOf(summary.billCount()));
        lblTotalDiscounts.setText(CurrencyUtil.formatCurrency(summary.totalDiscounts().doubleValue()));
        lblTotalTax.setText(CurrencyUtil.formatCurrency(summary.totalTax().doubleValue()));
    }

    /** Populates the Top-Selling Medicines table. */
    private void displayTopSellingMedicines(List<TopMedicineSale> topMeds) {
        topMedsModel.setRowCount(0);
        if (topMeds == null || topMeds.isEmpty()) {
            return;
        }

        int rank = 1;
        for (TopMedicineSale t : topMeds) {
            String revenueStr = CurrencyUtil.formatCurrency(t.totalRevenue().doubleValue());
            topMedsModel.addRow(new Object[]{
                    "#" + rank++,
                    t.medicineName(),
                    t.totalUnitsSold(),
                    revenueStr
            });
        }
    }

    /** Populates the Low Stock Alert table (independent of date range). */
    private void displayLowStock(List<Medicine> lowStock) {
        lowStockModel.setRowCount(0);
        if (lowStock == null || lowStock.isEmpty()) {
            return;
        }

        for (Medicine m : lowStock) {
            String priceStr = m.getSellingPrice() != null
                    ? CurrencyUtil.formatCurrency(m.getSellingPrice().doubleValue()) : "₹0.00";
            lowStockModel.addRow(new Object[]{
                    m.getMedicineId(),
                    m.getMedicineName(),
                    m.getCategory() != null ? m.getCategory() : "-",
                    m.getQuantity() != null ? m.getQuantity() : 0,
                    m.getMinQuantity(),
                    priceStr
            });
        }
    }

    /** Populates the Stock Quantity table and updates summary count. */
    private void displayStockQuantity(List<Medicine> stockList) {
        stockQuantityModel.setRowCount(0);
        if (stockList == null || stockList.isEmpty()) {
            lblStockCount.setText("No medicines found in inventory.");
            return;
        }

        int outOfStock = 0;
        int critical = 0;
        int lowStock = 0;
        int inStock = 0;

        for (Medicine m : stockList) {
            int qty = (m.getQuantity() != null) ? m.getQuantity() : 0;
            int minQty = m.getMinQuantity();
            String status = calculateStockStatus(qty, minQty);

            switch (status) {
                case "Out of Stock" -> outOfStock++;
                case "Critical Stock" -> critical++;
                case "Low Stock" -> lowStock++;
                case "In Stock" -> inStock++;
            }

            stockQuantityModel.addRow(new Object[]{
                    m.getMedicineId(),
                    m.getMedicineName(),
                    m.getCategory() != null ? m.getCategory() : "-",
                    qty,
                    minQty,
                    status
            });
        }

        lblStockCount.setText(String.format("Total: %d medicines  |  In Stock: %d  |  Low Stock: %d  |  Critical: %d  |  Out of Stock: %d",
                stockList.size(), inStock, lowStock, critical, outOfStock));
    }

    /** Asynchronously reloads only the Stock Quantity table via SwingWorker. */
    public void refreshStockQuantityOnly() {
        if (loadingStock) {
            return;
        }
        loadingStock = true;
        btnRefreshStock.setEnabled(false);
        lblStockStatus.setText("Refreshing stock...");

        new SwingWorker<List<Medicine>, Void>() {
            private String workerError = null;

            @Override
            protected List<Medicine> doInBackground() {
                try {
                    List<Medicine> list = medicineDAO.getAllMedicines();
                    if (list != null) {
                        list.sort(Comparator.comparingInt((Medicine m) -> m.getQuantity() != null ? m.getQuantity() : 0)
                                            .thenComparing(Medicine::getMedicineName, String.CASE_INSENSITIVE_ORDER));
                    }
                    return list;
                } catch (Exception ex) {
                    workerError = ex.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                loadingStock = false;
                btnRefreshStock.setEnabled(true);
                lblStockStatus.setText("");

                if (workerError != null) {
                    lblStockStatus.setText("Error: " + workerError);
                    lblStockStatus.setForeground(ERROR_COLOR);
                    return;
                }

                try {
                    List<Medicine> list = get();
                    if (list != null) {
                        currentStockQuantity = list;
                        displayStockQuantity(currentStockQuantity);
                    }
                } catch (Exception ex) {
                    lblStockStatus.setText("Error: " + ex.getMessage());
                    lblStockStatus.setForeground(ERROR_COLOR);
                }
            }
        }.execute();
    }

    /** Updates chart data and switches between Daily and Monthly views. */
    private void updateChartDisplay() {
        String mode = (String) cbChartMode.getSelectedItem();
        boolean useMonthly = "Monthly".equalsIgnoreCase(mode) || ("Auto".equalsIgnoreCase(mode) && isMonthlyChart);

        if (useMonthly) {
            chartComponent.setMonthlyData(currentMonthlyPoints);
        } else {
            chartComponent.setDailyData(currentDailyPoints);
        }
    }

    /** Exports the currently active report tab to CSV. */
    private void exportActiveTabToCSV() {
        int selectedTab = tabbedPane.getSelectedIndex();
        String preset = (String) cbDatePresets.getSelectedItem();

        switch (selectedTab) {
            case 0 -> {
                // Export Sales Summary + Chart breakdown
                List<String[]> rows = new ArrayList<>();
                rows.add(new String[]{"Durga Medicals - Sales Analysis Report"});
                rows.add(new String[]{"Date Preset", preset});
                rows.add(new String[]{"From Date", tfStartDate.getText().trim()});
                rows.add(new String[]{"To Date", tfEndDate.getText().trim()});
                rows.add(new String[]{});
                rows.add(new String[]{"Metric", "Value"});
                rows.add(new String[]{"Total Sales", lblTotalSales.getText()});
                rows.add(new String[]{"Total Invoices", lblTotalBills.getText()});
                rows.add(new String[]{"Total Discounts", lblTotalDiscounts.getText()});
                rows.add(new String[]{"Total Tax (CGST+SGST)", lblTotalTax.getText()});
                rows.add(new String[]{});

                boolean useMonthly = chartComponent.isShowingMonthly();
                if (useMonthly) {
                    rows.add(new String[]{"Month", "Monthly Sales (₹)"});
                    for (MonthlySalesPoint p : currentMonthlyPoints) {
                        rows.add(new String[]{p.label(), p.totalAmount().toPlainString()});
                    }
                } else {
                    rows.add(new String[]{"Date", "Daily Sales (₹)"});
                    for (DailySalesPoint p : currentDailyPoints) {
                        rows.add(new String[]{p.date().toString(), p.totalAmount().toPlainString()});
                    }
                }

                CSVExporter.exportWithFileChooser(this, "sales_report_" + LocalDate.now() + ".csv", rows);
            }
            case 1 -> {
                // Export Top-Selling Medicines
                List<String[]> rows = new ArrayList<>();
                rows.add(new String[]{"Durga Medicals - Top-Selling Medicines"});
                rows.add(new String[]{"From Date", tfStartDate.getText().trim()});
                rows.add(new String[]{"To Date", tfEndDate.getText().trim()});
                rows.add(new String[]{});
                rows.add(new String[]{"Rank", "Medicine ID", "Medicine Name", "Units Sold", "Total Revenue (₹)"});

                int rank = 1;
                for (TopMedicineSale t : currentTopMeds) {
                    rows.add(new String[]{
                            String.valueOf(rank++),
                            String.valueOf(t.medicineId()),
                            t.medicineName(),
                            String.valueOf(t.totalUnitsSold()),
                            t.totalRevenue().toPlainString()
                    });
                }

                CSVExporter.exportWithFileChooser(this, "top_medicines_" + LocalDate.now() + ".csv", rows);
            }
            case 2 -> {
                // Export Low Stock Alerts
                List<String[]> rows = new ArrayList<>();
                rows.add(new String[]{"Durga Medicals - Low Stock Alert Report"});
                rows.add(new String[]{"Generated On", LocalDate.now().toString()});
                rows.add(new String[]{});
                rows.add(new String[]{"Medicine ID", "Medicine Name", "Category", "Current Quantity", "Min Threshold", "Selling Price (₹)"});

                for (Medicine m : currentLowStock) {
                    rows.add(new String[]{
                            String.valueOf(m.getMedicineId()),
                            m.getMedicineName(),
                            m.getCategory() != null ? m.getCategory() : "",
                            String.valueOf(m.getQuantity() != null ? m.getQuantity() : 0),
                            String.valueOf(m.getMinQuantity()),
                            m.getSellingPrice() != null ? m.getSellingPrice().toPlainString() : "0.00"
                    });
                }

                CSVExporter.exportWithFileChooser(this, "low_stock_report_" + LocalDate.now() + ".csv", rows);
            }
            case 3 -> {
                // Export Stock Quantity Report
                List<String[]> rows = new ArrayList<>();
                rows.add(new String[]{"Durga Medicals - Stock Quantity Report"});
                rows.add(new String[]{"Generated On", LocalDate.now().toString()});
                rows.add(new String[]{});
                rows.add(new String[]{"Medicine ID", "Medicine Name", "Category", "Quantity Left", "Minimum Quantity", "Stock Status"});

                for (Medicine m : currentStockQuantity) {
                    int qty = (m.getQuantity() != null) ? m.getQuantity() : 0;
                    int minQty = m.getMinQuantity();
                    String status = calculateStockStatus(qty, minQty);
                    rows.add(new String[]{
                            String.valueOf(m.getMedicineId()),
                            m.getMedicineName(),
                            m.getCategory() != null ? m.getCategory() : "",
                            String.valueOf(qty),
                            String.valueOf(minQty),
                            status
                    });
                }

                CSVExporter.exportWithFileChooser(this, "stock_quantity_report_" + LocalDate.now() + ".csv", rows);
            }
        }
    }

    private void setLoadingState(boolean isLoading, String message) {
        btnGenerate.setEnabled(!isLoading);
        btnExportCSV.setEnabled(!isLoading);
        cbDatePresets.setEnabled(!isLoading);
        btnRefreshStock.setEnabled(!isLoading);

        if (isLoading) {
            lblStatus.setText(message != null ? message : "Loading...");
            lblStatus.setForeground(Color.DARK_GRAY);
        } else {
            lblStatus.setText("");
        }
    }

    // ── Supporting Data Carrier & Renderer ────────────────────────────────

    private record ReportDataPayload(
            SalesSummary summary,
            List<TopMedicineSale> topMeds,
            List<DailySalesPoint> dailyPoints,
            List<MonthlySalesPoint> monthlyPoints,
            List<Medicine> lowStock,
            List<Medicine> stockQuantity
    ) {}

    /** Row renderer applying subtle pale orange background to low stock entries. */
    private static class LowStockRowRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            if (!isSelected) {
                setBackground(DashboardPanel.LOW_STOCK_HIGHLIGHT);
            }
            return this;
        }
    }

    /** Row renderer applying subtle status highlights harmonizing with Hazel Green theme. */
    private static class StockQuantityRowRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        // Subtle tints harmonious with the Hazel Green (#A3B18A) theme
        private static final Color COLOR_OUT_OF_STOCK = new Color(0xFF, 0xD6, 0xD6); // subtle pastel red
        private static final Color COLOR_CRITICAL     = new Color(0xFF, 0xE5, 0xD0); // subtle pastel peach
        private static final Color COLOR_LOW_STOCK    = new Color(0xFF, 0xEE, 0xB8); // subtle warm amber
        private static final Color COLOR_IN_STOCK     = new Color(0xEE, 0xF3, 0xEA); // subtle sage/mint green

        private final int alignment;
        private final boolean bold;

        public StockQuantityRowRenderer(int alignment, boolean bold) {
            this.alignment = alignment;
            this.bold = bold;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            setHorizontalAlignment(alignment);

            if (bold) {
                setFont(FontUtil.getRalewayBold(12f));
            } else {
                setFont(FontUtil.getRalewayRegular(12f));
            }

            if (!isSelected) {
                int modelRow = table.convertRowIndexToModel(row);
                Object statusObj = table.getModel().getValueAt(modelRow, 5); // Stock Status column
                String status = (statusObj != null) ? statusObj.toString() : "";

                switch (status) {
                    case "Out of Stock" -> setBackground(COLOR_OUT_OF_STOCK);
                    case "Critical Stock" -> setBackground(COLOR_CRITICAL);
                    case "Low Stock" -> setBackground(COLOR_LOW_STOCK);
                    case "In Stock" -> setBackground(COLOR_IN_STOCK);
                    default -> setBackground(Color.WHITE);
                }
                setForeground(Color.decode("#171717"));
            }
            return this;
        }
    }

    // ── Interactive Java 2D Sales Chart Component ─────────────────────────

    /**
     * SalesChartComponent.java
     *
     * Pure Java 2D vector chart rendering daily or monthly sales bars.
     * Includes currency labels, date axes, background grid lines, and adaptive scale.
     */
    private static class SalesChartComponent extends JComponent {
        private static final long serialVersionUID = 1L;

        private List<DailySalesPoint> dailyList = new ArrayList<>();
        private List<MonthlySalesPoint> monthlyList = new ArrayList<>();
        private boolean monthlyMode = false;

        private static final DateTimeFormatter DAY_LABEL_FMT = DateTimeFormatter.ofPattern("dd MMM");
        private static final Color BAR_COLOR       = Color.decode("#A3B18A"); // Hazel Green
        private static final Color BAR_BORDER      = Color.decode("#588157"); // Darker Hazel Green
        private static final Color GRID_COLOR      = Color.decode("#E9ECEF");
        private static final Color TEXT_MUTED      = Color.decode("#495057");

        public SalesChartComponent() {
            setBackground(Color.WHITE);
            setOpaque(true);
            setMinimumSize(new Dimension(300, 220));
            setPreferredSize(new Dimension(650, 320));
        }

        public void setDailyData(List<DailySalesPoint> data) {
            this.dailyList = (data != null) ? new ArrayList<>(data) : new ArrayList<>();
            this.monthlyMode = false;
            repaint();
        }

        public void setMonthlyData(List<MonthlySalesPoint> data) {
            this.monthlyList = (data != null) ? new ArrayList<>(data) : new ArrayList<>();
            this.monthlyMode = true;
            repaint();
        }

        public boolean isShowingMonthly() {
            return monthlyMode;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            // Background
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, width, height);

            int count = monthlyMode ? monthlyList.size() : dailyList.size();
            if (count == 0) {
                g2.setColor(TEXT_MUTED);
                g2.setFont(FontUtil.getRalewayBold(14f));
                String emptyMsg = "No sales recorded for the selected period.";
                FontMetrics fm = g2.getFontMetrics();
                int msgX = (width - fm.stringWidth(emptyMsg)) / 2;
                int msgY = (height / 2);
                g2.drawString(emptyMsg, msgX, msgY);
                g2.dispose();
                return;
            }

            // Margins
            int leftMargin = 65;
            int rightMargin = 25;
            int topMargin = 35;
            int bottomMargin = 45;

            int plotWidth = width - leftMargin - rightMargin;
            int plotHeight = height - topMargin - bottomMargin;

            if (plotWidth <= 0 || plotHeight <= 0) {
                g2.dispose();
                return;
            }

            // Find Maximum Value
            BigDecimal maxVal = BigDecimal.ZERO;
            if (monthlyMode) {
                for (MonthlySalesPoint p : monthlyList) {
                    if (p.totalAmount().compareTo(maxVal) > 0) {
                        maxVal = p.totalAmount();
                    }
                }
            } else {
                for (DailySalesPoint p : dailyList) {
                    if (p.totalAmount().compareTo(maxVal) > 0) {
                        maxVal = p.totalAmount();
                    }
                }
            }

            if (maxVal.compareTo(BigDecimal.ZERO) == 0) {
                maxVal = BigDecimal.valueOf(100); // fallback scale
            }

            // Draw Y-Axis Horizontal Grid Lines and Labels
            int gridSteps = 4;
            g2.setFont(FontUtil.getRalewayRegular(10f));
            FontMetrics fontMetrics = g2.getFontMetrics();

            for (int i = 0; i <= gridSteps; i++) {
                int y = topMargin + plotHeight - (int) ((double) i / gridSteps * plotHeight);
                double stepVal = maxVal.doubleValue() * ((double) i / gridSteps);

                g2.setColor(GRID_COLOR);
                g2.drawLine(leftMargin, y, leftMargin + plotWidth, y);

                g2.setColor(TEXT_MUTED);
                String yLabel = "₹" + String.format("%.0f", stepVal);
                g2.drawString(yLabel, leftMargin - fontMetrics.stringWidth(yLabel) - 6, y + 4);
            }

            // Draw Bars
            int totalSlotWidth = plotWidth / count;
            int barWidth = Math.max(14, Math.min(50, (int) (totalSlotWidth * 0.65)));

            for (int i = 0; i < count; i++) {
                BigDecimal val = monthlyMode ? monthlyList.get(i).totalAmount() : dailyList.get(i).totalAmount();
                String xLabel = monthlyMode
                        ? monthlyList.get(i).label()
                        : dailyList.get(i).date().format(DAY_LABEL_FMT);

                int slotCenterX = leftMargin + (i * totalSlotWidth) + (totalSlotWidth / 2);
                int barX = slotCenterX - (barWidth / 2);

                double ratio = val.doubleValue() / maxVal.doubleValue();
                int barHeight = (int) (ratio * plotHeight);
                int barY = topMargin + plotHeight - barHeight;

                // Bar Fill
                g2.setColor(BAR_COLOR);
                g2.fillRoundRect(barX, barY, barWidth, barHeight, 4, 4);

                // Bar Outline
                g2.setColor(BAR_BORDER);
                g2.drawRoundRect(barX, barY, barWidth, barHeight, 4, 4);

                // Value Label above bar
                if (val.compareTo(BigDecimal.ZERO) > 0) {
                    g2.setColor(HEADING_COLOR);
                    g2.setFont(FontUtil.getRalewayBold(9f));
                    String valText = "₹" + String.format("%.0f", val.doubleValue());
                    int valW = g2.getFontMetrics().stringWidth(valText);
                    g2.drawString(valText, slotCenterX - (valW / 2), Math.max(topMargin + 12, barY - 5));
                }

                // X-Axis Date/Month Label below bar
                g2.setColor(TEXT_MUTED);
                g2.setFont(FontUtil.getRalewayRegular(10f));
                int labelW = g2.getFontMetrics().stringWidth(xLabel);
                g2.drawString(xLabel, slotCenterX - (labelW / 2), topMargin + plotHeight + 18);
            }

            // Base X-axis line
            g2.setColor(Color.decode("#ADB5BD"));
            g2.drawLine(leftMargin, topMargin + plotHeight, leftMargin + plotWidth, topMargin + plotHeight);

            g2.dispose();
        }
    }
}
