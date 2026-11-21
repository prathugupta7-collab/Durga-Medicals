package com.durgamedicals.util;

import java.sql.*;

public class MedicineTableInfoTest {
    public static void main(String[] args) {
        try (Connection conn = DBConnection.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, "medicines", null)) {
                System.out.println("Columns in 'medicines' table:");
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    String type = rs.getString("TYPE_NAME");
                    int size = rs.getInt("COLUMN_SIZE");
                    System.out.printf("- %s: %s(%d)%n", name, type, size);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
