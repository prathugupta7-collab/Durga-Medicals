package com.durgamedicals.util;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSVExporter.java
 *
 * Utility class for exporting tabular reports to CSV files.
 * Ensures UTF-8 encoding (including UTF-8 BOM for Microsoft Excel compatibility)
 * and conforms to RFC 4180 escaping rules for quotes, commas, and newlines.
 */
public class CSVExporter {

    /**
     * Escapes a single CSV cell value according to RFC 4180 rules.
     */
    public static String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        if (needsQuotes) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Converts a row of cell values into a single comma-separated line.
     */
    public static String formatRow(String[] columns) {
        if (columns == null || columns.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(escapeCSV(columns[i]));
        }
        return sb.toString();
    }

    /**
     * Prompts the user with a JFileChooser to save the CSV data.
     *
     * @param parent          Parent Swing component for dialog positioning.
     * @param defaultFileName Default suggested filename (e.g., "sales_report.csv").
     * @param rows            List of string arrays representing rows (first row typically headers).
     * @return true if successfully saved, false if cancelled or failed.
     */
    public static boolean exportWithFileChooser(Component parent, String defaultFileName, List<String[]> rows) {
        if (rows == null || rows.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "No data available to export.",
                    "Export Empty", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Report to CSV");
        chooser.setSelectedFile(new File(defaultFileName));
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));

        int userChoice = chooser.showSaveDialog(parent);
        if (userChoice != JFileChooser.APPROVE_OPTION) {
            return false;
        }

        File targetFile = chooser.getSelectedFile();
        if (!targetFile.getName().toLowerCase().endsWith(".csv")) {
            targetFile = new File(targetFile.getParentFile(), targetFile.getName() + ".csv");
        }

        if (targetFile.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(parent,
                    "File '" + targetFile.getName() + "' already exists. Do you want to replace it?",
                    "Confirm Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (overwrite != JOptionPane.YES_OPTION) {
                return false;
            }
        }

        try (FileOutputStream fos = new FileOutputStream(targetFile);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             PrintWriter writer = new PrintWriter(osw)) {

            // Write UTF-8 Byte Order Mark (BOM) for full Excel and character encoding compatibility
            fos.write(0xEF);
            fos.write(0xBB);
            fos.write(0xBF);

            for (String[] row : rows) {
                writer.println(formatRow(row));
            }
            writer.flush();

            JOptionPane.showMessageDialog(parent,
                    "Report exported successfully!\nSaved to: " + targetFile.getAbsolutePath(),
                    "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            return true;

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Failed to export CSV: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            return false;
        }
    }
}
