package com.durgamedicals.service;

import com.durgamedicals.model.Bill;
import com.durgamedicals.model.BillItem;
import com.durgamedicals.util.CurrencyUtil;
import com.durgamedicals.util.FontUtil;

import java.awt.*;
import java.awt.print.*;
import java.util.List;

/**
 * Service responsible for rendering a printable invoice for a Bill.
 * It receives a fully populated Bill object and its immutable line items.
 * No database access is performed – the service only prints the data that was
 * already saved. This guarantees that printing never creates a new bill, never
 * deducts stock, and never recalculates financial values.
 */
public class BillPrintService implements Printable {

    /** Enum describing the outcome of a print request. */
    public enum PrintStatus { SUCCESS, CANCELLED, FAILURE }

    private static final int PAGE_MARGIN = 36; // points (0.5 inch)
    private static final Font FONT_TITLE = FontUtil.getRalewayBold(18f);
    private static final Font FONT_HEADER = FontUtil.getRalewayBold(14f);
    private static final Font FONT_NORMAL = FontUtil.getRalewayRegular(12f);
    private static final Color HAZEL_GREEN = new Color(0xA3_B1_8A);

    private final Bill bill;
    private final List<BillItem> items;

    public BillPrintService(Bill bill, List<BillItem> items) {
        this.bill = bill;
        this.items = items;
    }

    /** Launches the standard printer dialog and prints the invoice. */
    public PrintStatus print() {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("DurgaMedicals Invoice #" + bill.getBillId());
        job.setPrintable(this);
        // If the user cancels the print dialog, treat it as CANCELLED
        if (!job.printDialog()) {
            return PrintStatus.CANCELLED;
        }
        try {
            job.print();
            return PrintStatus.SUCCESS;
        } catch (PrinterException e) {
            e.printStackTrace();
            return PrintStatus.FAILURE;
        }
    }

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        // Prepare graphics
        Graphics2D g2 = (Graphics2D) graphics;
        g2.translate(pageFormat.getImageableX() + PAGE_MARGIN,
                pageFormat.getImageableY() + PAGE_MARGIN);
        g2.setPaint(Color.BLACK);

        int lineHeight   = g2.getFontMetrics(FONT_NORMAL).getHeight();
        int usableWidth  = (int) pageFormat.getImageableWidth()  - 2 * PAGE_MARGIN;
        int usableHeight = (int) pageFormat.getImageableHeight() - 2 * PAGE_MARGIN;

        // Reserve space for summary/footer on the final page (5 totals + spacing + footer)
        int footerReserve = lineHeight * 8; // generous safety margin

        // ----- Header heights -----
        // Simulate first-page header to find used height before items start
        int y = 0;
        // First page full header
        g2.setFont(FONT_TITLE);
        g2.setColor(HAZEL_GREEN);
        // title line
        y += lineHeight + 4; // title + small gap
        // subtitle line
        y += lineHeight * 2;
        // Bill meta (4 lines) and spacing after
        y += lineHeight * 4;
        y += lineHeight * 2; // extra spacing before table
        int headerHeightFirst = y;

        // Simulate continuation header (shorter)
        y = 0;
        g2.setFont(FONT_TITLE);
        g2.setColor(HAZEL_GREEN);
        // title line (cont.)
        y += lineHeight + 4;
        // subtitle line (cont.)
        y += lineHeight * 2;
        int headerHeightCont = y;

        // Column header appears on every page
        int columnHeaderHeight = lineHeight + 4; // header line + separator line

        // Determine how many items fit on first page and on later pages
        int itemsPerFirst = Math.max(1, (usableHeight - headerHeightFirst - columnHeaderHeight - footerReserve) / lineHeight);
        int itemsPerOther = Math.max(1, (usableHeight - headerHeightCont - columnHeaderHeight - footerReserve) / lineHeight);

        // Compute start index for the requested page based on capacities of previous pages
        int startIdx = 0;
        int capacityThisPage;
        if (pageIndex == 0) {
            capacityThisPage = itemsPerFirst;
        } else {
            // items on page 0
            startIdx = itemsPerFirst;
            // add capacities of intermediate pages (1 .. pageIndex-1)
            for (int i = 1; i < pageIndex; i++) {
                startIdx += itemsPerOther;
            }
            capacityThisPage = itemsPerOther;
        }

        // No more items to show?
        if (startIdx >= items.size()) {
            return NO_SUCH_PAGE;
        }
        int endIdx = Math.min(startIdx + capacityThisPage, items.size());

        // Reset y and draw actual header for the current page
        y = 0;
        if (pageIndex == 0) {
            // Full heading on first page
            g2.setFont(FONT_TITLE);
            g2.setColor(HAZEL_GREEN);
            g2.drawString("DURGA MEDICALS", 0, y + lineHeight);
            y += lineHeight + 4;
            g2.setColor(Color.BLACK);
            g2.drawString("Pharmacy Billing Invoice", 0, y + lineHeight);
            y += lineHeight * 2;

            // Bill meta information
            g2.setFont(FONT_NORMAL);
            g2.drawString("Invoice #: " + bill.getBillId(), 0, y + lineHeight);
            y += lineHeight;
            g2.drawString("Date: " + (bill.getBillDate() != null ? bill.getBillDate().toString() : "-"), 0, y + lineHeight);
            y += lineHeight;
            g2.drawString("Customer: " + (bill.getCustomerName() != null ? bill.getCustomerName() : "Walk‑in Customer"), 0, y + lineHeight);
            y += lineHeight;
            g2.drawString("Phone: " + (bill.getCustomerPhone() != null ? bill.getCustomerPhone() : "-"), 0, y + lineHeight);
            y += lineHeight * 2;
        } else {
            // Continuation heading on subsequent pages
            g2.setFont(FONT_TITLE);
            g2.setColor(HAZEL_GREEN);
            g2.drawString("DURGA MEDICALS (cont.)", 0, y + lineHeight);
            y += lineHeight + 4;
            g2.setColor(Color.BLACK);
            g2.drawString("Pharmacy Billing Invoice (cont.)", 0, y + lineHeight);
            y += lineHeight * 2;
        }

        // ----- Column header (appears on every page) -----
        g2.setFont(FONT_HEADER);
        g2.drawString("#", 0, y + lineHeight);
        g2.drawString("Medicine", 30, y + lineHeight);
        g2.drawString("Qty", 250, y + lineHeight);
        g2.drawString("Price", 300, y + lineHeight);
        g2.drawString("Amount", 380, y + lineHeight);
        y += lineHeight;
        g2.drawLine(0, y, usableWidth, y);
        y += 4;

        // ----- Render the items belonging to this page -----
        g2.setFont(FONT_NORMAL);
        int index = startIdx + 1; // global numbering continues across pages
        for (int i = startIdx; i < endIdx; i++) {
            BillItem item = items.get(i);
            g2.drawString(String.valueOf(index), 0, y + lineHeight);
            g2.drawString(item.getMedicineName() != null ? item.getMedicineName() : "-", 30, y + lineHeight);
            g2.drawString(String.valueOf(item.getQuantity()), 250, y + lineHeight);
            g2.drawString(CurrencyUtil.formatCurrency(item.getPrice().doubleValue()), 300, y + lineHeight);
            g2.drawString(CurrencyUtil.formatCurrency(item.getAmount().doubleValue()), 380, y + lineHeight);
            y += lineHeight;
            index++;
        }

        // ----- If this is the final page, render totals and footer -----
        if (endIdx == items.size()) {
            y += lineHeight; // space before summary
            g2.drawLine(0, y, usableWidth, y);
            y += lineHeight;
            g2.drawString("Subtotal: " + CurrencyUtil.formatCurrency(bill.getSubtotal().doubleValue()), 300, y + lineHeight);
            y += lineHeight;
            g2.drawString("Discount: " + CurrencyUtil.formatCurrency(bill.getDiscount().doubleValue()), 300, y + lineHeight);
            y += lineHeight;
            g2.drawString("CGST: " + CurrencyUtil.formatCurrency(bill.getCgstAmount().doubleValue()), 300, y + lineHeight);
            y += lineHeight;
            g2.drawString("SGST: " + CurrencyUtil.formatCurrency(bill.getSgstAmount().doubleValue()), 300, y + lineHeight);
            y += lineHeight;
            g2.drawString("Grand Total: " + CurrencyUtil.formatCurrency(bill.getTotalAmount().doubleValue()), 300, y + lineHeight);
            y += lineHeight * 2;

            // Footer
            g2.setColor(HAZEL_GREEN);
            g2.setFont(FONT_NORMAL);
            g2.drawString("Thank you for choosing Durga Medicals!", 0, y + lineHeight);
        }

        return PAGE_EXISTS;
    }
}
