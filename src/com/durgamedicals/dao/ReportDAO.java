package com.durgamedicals.dao;

import com.durgamedicals.util.DBConnection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ReportDAO.java
 *
 * Data Access Object providing aggregation and analytic queries for the Reports module.
 * Queries existing 'bills' and 'bill_items' tables without modifying any schema or records.
 *
 * Calculations adhere strictly to:
 *   • bill_date >= start AND bill_date < endExclusive (exclusive end boundary).
 *   • Total Sales = SUM(total_amount)
 *   • Total Bills = COUNT(bill_id)
 *   • Total Discounts = SUM(discount)
 *   • Total Tax = SUM(tax_amount) (combined CGST + SGST tax)
 */
public class ReportDAO {

    // ── Supporting Record Types ───────────────────────────────────────────

    public record SalesSummary(
            BigDecimal totalSales,
            int billCount,
            BigDecimal totalDiscounts,
            BigDecimal totalTax,
            BigDecimal totalSubtotal
    ) {}

    public record TopMedicineSale(
            int medicineId,
            String medicineName,
            int totalUnitsSold,
            BigDecimal totalRevenue
    ) {}

    public record DailySalesPoint(
            LocalDate date,
            BigDecimal totalAmount
    ) {}

    public record MonthlySalesPoint(
            int year,
            int month,
            String label,
            BigDecimal totalAmount
    ) {}

    // ── Aggregation Methods ───────────────────────────────────────────────

    /**
     * Calculates overall sales metrics within the specified date window.
     * Uses exclusive end boundary: bill_date >= start AND bill_date < endExclusive.
     *
     * @param start        Inclusive start timestamp.
     * @param endExclusive Exclusive end timestamp.
     * @return SalesSummary containing aggregated figures.
     */
    public SalesSummary getSalesSummary(LocalDateTime start, LocalDateTime endExclusive) {
        String sql = """
            SELECT
                COUNT(bill_id) AS bill_count,
                COALESCE(SUM(subtotal), 0) AS total_subtotal,
                COALESCE(SUM(discount), 0) AS total_discount,
                COALESCE(SUM(tax_amount), 0) AS total_tax,
                COALESCE(SUM(total_amount), 0) AS total_sales
            FROM bills
            WHERE bill_date >= ? AND bill_date < ?
            """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(endExclusive));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("bill_count");
                    BigDecimal subtotal = rs.getBigDecimal("total_subtotal").setScale(2, RoundingMode.HALF_UP);
                    BigDecimal discount = rs.getBigDecimal("total_discount").setScale(2, RoundingMode.HALF_UP);
                    BigDecimal tax = rs.getBigDecimal("total_tax").setScale(2, RoundingMode.HALF_UP);
                    BigDecimal sales = rs.getBigDecimal("total_sales").setScale(2, RoundingMode.HALF_UP);

                    return new SalesSummary(sales, count, discount, tax, subtotal);
                }
            }
        } catch (SQLException e) {
            System.err.println("Database Error [getSalesSummary]: " + e.getMessage());
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [getSalesSummary]: " + e.getMessage());
        }

        return new SalesSummary(BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Retrieves medicines ranked by total quantity sold within the date window.
     *
     * @param start        Inclusive start timestamp.
     * @param endExclusive Exclusive end timestamp.
     * @param limit        Maximum number of records to return.
     * @return List of TopMedicineSale records ordered by total units sold descending.
     */
    public List<TopMedicineSale> getTopSellingMedicines(LocalDateTime start, LocalDateTime endExclusive, int limit) {
        List<TopMedicineSale> list = new ArrayList<>();
        String sql = """
            SELECT
                bi.medicine_id,
                COALESCE(bi.medicine_name, 'Unknown Medicine') AS med_name,
                COALESCE(SUM(bi.quantity), 0) AS units_sold,
                COALESCE(SUM(bi.amount), 0) AS revenue
            FROM bill_items bi
            JOIN bills b ON bi.bill_id = b.bill_id
            WHERE b.bill_date >= ? AND b.bill_date < ?
            GROUP BY bi.medicine_id, bi.medicine_name
            ORDER BY units_sold DESC, revenue DESC
            LIMIT ?
            """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(endExclusive));
            stmt.setInt(3, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int medId = rs.getInt("medicine_id");
                    String name = rs.getString("med_name");
                    int units = rs.getInt("units_sold");
                    BigDecimal revenue = rs.getBigDecimal("revenue").setScale(2, RoundingMode.HALF_UP);

                    list.add(new TopMedicineSale(medId, name, units, revenue));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database Error [getTopSellingMedicines]: " + e.getMessage());
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [getTopSellingMedicines]: " + e.getMessage());
        }

        return list;
    }

    /**
     * Retrieves daily sales breakdown within the date window for trend charting.
     *
     * @param start        Inclusive start timestamp.
     * @param endExclusive Exclusive end timestamp.
     * @return List of DailySalesPoint ordered chronologically.
     */
    public List<DailySalesPoint> getDailySales(LocalDateTime start, LocalDateTime endExclusive) {
        List<DailySalesPoint> points = new ArrayList<>();
        String sql = """
            SELECT
                DATE(bill_date) AS sale_date,
                COALESCE(SUM(total_amount), 0) AS daily_total
            FROM bills
            WHERE bill_date >= ? AND bill_date < ?
            GROUP BY DATE(bill_date)
            ORDER BY sale_date ASC
            """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(endExclusive));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Date d = rs.getDate("sale_date");
                    if (d != null) {
                        LocalDate date = d.toLocalDate();
                        BigDecimal total = rs.getBigDecimal("daily_total").setScale(2, RoundingMode.HALF_UP);
                        points.add(new DailySalesPoint(date, total));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Database Error [getDailySales]: " + e.getMessage());
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [getDailySales]: " + e.getMessage());
        }

        return points;
    }

    /**
     * Retrieves monthly sales breakdown respecting the selected date range.
     *
     * @param start        Inclusive start timestamp.
     * @param endExclusive Exclusive end timestamp.
     * @return List of MonthlySalesPoint ordered chronologically.
     */
    public List<MonthlySalesPoint> getMonthlySales(LocalDateTime start, LocalDateTime endExclusive) {
        List<MonthlySalesPoint> points = new ArrayList<>();
        String sql = """
            SELECT
                YEAR(bill_date) AS sale_year,
                MONTH(bill_date) AS sale_month,
                COALESCE(SUM(total_amount), 0) AS monthly_total
            FROM bills
            WHERE bill_date >= ? AND bill_date < ?
            GROUP BY YEAR(bill_date), MONTH(bill_date)
            ORDER BY sale_year ASC, sale_month ASC
            """;

        DateTimeFormatter monthNameFmt = DateTimeFormatter.ofPattern("MMM yyyy");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(endExclusive));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int yr = rs.getInt("sale_year");
                    int mo = rs.getInt("sale_month");
                    BigDecimal total = rs.getBigDecimal("monthly_total").setScale(2, RoundingMode.HALF_UP);

                    LocalDate sampleDate = LocalDate.of(yr, mo, 1);
                    String label = sampleDate.format(monthNameFmt);

                    points.add(new MonthlySalesPoint(yr, mo, label, total));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database Error [getMonthlySales]: " + e.getMessage());
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error [getMonthlySales]: " + e.getMessage());
        }

        return points;
    }
}
