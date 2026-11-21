package com.durgamedicals.util;

public class FontLoadTest {
    public static void main(String[] args) {
        java.awt.Font extraBold = FontUtil.getRalewayExtraBold(12f);
        java.awt.Font bold = FontUtil.getRalewayBold(12f);
        java.awt.Font regular = FontUtil.getRalewayRegular(12f);
        System.out.println("ExtraBold family: " + extraBold.getFamily());
        System.out.println("Bold family: " + bold.getFamily());
        System.out.println("Regular family: " + regular.getFamily());
    }
}
