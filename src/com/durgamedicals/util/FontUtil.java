package com.durgamedicals.util;

import java.awt.Font;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for loading bundled TrueType fonts.
 *
 * Font files are expected to be present in the project directory under
 * {@code resources/fonts/}. The class first attempts to load the font as a
 * classpath resource (e.g., when the {@code resources} folder is packaged into
 * the runtime classpath). If that fails, it falls back to loading the font file
 * directly from the file system relative to the project root.
 *
 * If a font cannot be loaded, a {@code SansSerif} fallback is returned and a
 * warning is printed to {@code System.err}.
 */
public class FontUtil {
    private static final Map<String, Font> cache = new HashMap<>();

    // Font file names (located in resources/fonts/)
    private static final String MILKER_FILE = "Milker-Regular.ttf"; // brand name (if available)
    private static final String RALeway_BOLD_FILE = "Raleway-Bold.ttf";
    private static final String RALeway_EXTRABOLD_FILE = "Raleway-ExtraBold.ttf";
    private static final String RALeway_REGULAR_FILE = "Raleway-Regular.ttf";

    private static Font loadFont(String fileName, float size, int style) {
        // Use cache key based on file name
        String cacheKey = fileName + "-" + size + "-" + style;
        Font cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        // 1. Try classpath resource (fonts packaged inside the jar or class output)
        String resourcePath = "/fonts/" + fileName; // leading '/' for absolute classpath resource
        try (InputStream is = FontUtil.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, is).deriveFont(style, size);
                cache.put(cacheKey, font);
                return font;
            }
        } catch (IOException | java.awt.FontFormatException e) {
            System.err.println("[WARNING] Failed to load font as resource: " + resourcePath + " - " + e.getMessage());
        }
        // 2. Fallback to file system path relative to project root
        File file = new File("resources/fonts/" + fileName);
        if (file.exists()) {
            try (InputStream fis = new FileInputStream(file)) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, fis).deriveFont(style, size);
                cache.put(cacheKey, font);
                System.out.println("[INFO] Loaded font from file: " + file.getPath());
                return font;
            } catch (IOException | java.awt.FontFormatException e) {
                System.err.println("[WARNING] Failed to load font from file: " + file.getPath() + " - " + e.getMessage());
            }
        } else {
            System.err.println("[WARNING] Font file not found: " + file.getPath());
        }
        // Final fallback to system font
        Font fallback = new Font("SansSerif", style, (int) size);
        cache.put(cacheKey, fallback);
        return fallback;
    }

    /** Milker font – primarily for brand name. */
    public static Font getMilker(float size) {
        return loadFont(MILKER_FILE, size, Font.PLAIN);
    }

    /** Raleway Bold – for titles, headings, buttons. */
    public static Font getRalewayBold(float size) {
        return loadFont(RALeway_BOLD_FILE, size, Font.BOLD);
    }

    /** Raleway ExtraBold – for major headings and branding. */
    public static Font getRalewayExtraBold(float size) {
        return loadFont(RALeway_EXTRABOLD_FILE, size, Font.BOLD);
    }

    /** Raleway Regular – for body text, labels, etc. */
    public static Font getRalewayRegular(float size) {
        return loadFont(RALeway_REGULAR_FILE, size, Font.PLAIN);
    }
}
