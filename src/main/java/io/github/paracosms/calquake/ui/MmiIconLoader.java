package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.MmiLegend;
import javafx.scene.image.Image;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches MMI icon images for high-performance canvas rendering.
 */
public final class MmiIconLoader {

    private static final String RESOURCE_BASE_PATH = "/icons/mmi/";
    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();

    private MmiIconLoader() {}

    /**
     * Resolves the canonical file base name for a given MMI Roman numeral.
     *
     * @param roman Roman numeral string (e.g. "I", "II-III", "VII", "X+", "N/A")
     * @return canonical base name (e.g. "mmi_vii")
     */
    public static String resolveBaseName(String roman) {
        if (roman == null) {
            return "mmi_na";
        }
        String normalized = roman.trim()
                .replace("-", "_")
                .replace("+", "_plus")
                .replace("/", "_")
                .toLowerCase();

        return switch (normalized) {
            case "i" -> "mmi_i";
            case "ii" -> "mmi_ii";
            case "iii" -> "mmi_iii";
            case "ii_iii" -> "mmi_ii_iii";
            case "iv" -> "mmi_iv";
            case "v" -> "mmi_v";
            case "vi" -> "mmi_vi";
            case "vii" -> "mmi_vii";
            case "viii" -> "mmi_viii";
            case "ix" -> "mmi_ix";
            case "x" -> "mmi_x";
            case "x_plus" -> "mmi_x_plus";
            default -> "mmi_na";
        };
    }

    /**
     * Retrieves the resource path for a given MMI Roman numeral.
     *
     * @param roman Roman numeral
     * @param svg true for SVG, false for PNG
     * @return resource path string
     */
    public static String getResourcePath(String roman, boolean svg) {
        String base = resolveBaseName(roman);
        return RESOURCE_BASE_PATH + base + (svg ? ".svg" : ".png");
    }

    /**
     * Retrieves the cached JavaFX Image for an MMI Roman numeral.
     *
     * @param roman Roman numeral (e.g. "VII", "IV", "II-III")
     * @return JavaFX Image instance
     */
    public static Image getIcon(String roman) {
        String base = resolveBaseName(roman);
        return IMAGE_CACHE.computeIfAbsent(base, key -> {
            String path = RESOURCE_BASE_PATH + key + ".png";
            InputStream stream = MmiIconLoader.class.getResourceAsStream(path);
            if (stream == null) {
                // Fallback to NA
                String naPath = RESOURCE_BASE_PATH + "mmi_na.png";
                stream = MmiIconLoader.class.getResourceAsStream(naPath);
            }
            if (stream == null) {
                throw new IllegalStateException("MMI icon resource not found: " + path);
            }
            return new Image(stream);
        });
    }

    /**
     * Retrieves the cached JavaFX Image for a given MMI bin.
     *
     * @param bin MmiLegend.MmiBin
     * @return JavaFX Image instance
     */
    public static Image getIcon(MmiLegend.MmiBin bin) {
        Objects.requireNonNull(bin, "bin cannot be null");
        return getIcon(bin.roman());
    }

    /**
     * Clears the image cache. Primarily used in testing.
     */
    public static void clearCache() {
        IMAGE_CACHE.clear();
    }
}
