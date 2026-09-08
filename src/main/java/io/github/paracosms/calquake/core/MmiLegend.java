package io.github.paracosms.calquake.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Represents the USGS ShakeMap Modified Mercalli Intensity (MMI) scale (Worden et al., 2012)
 * and provides display decimal rounding and frozen legend bin classifications.
 */
public final class MmiLegend {

    public record MmiBin(
            String roman,
            Double minMmi,
            Double maxMmi,
            String shakingDescriptor,
            String damageDescriptor,
            String colorHex,
            String textColorHex
    ) {
        public boolean contains(double mmi) {
            if (minMmi != null && mmi < minMmi) return false;
            if (maxMmi != null && mmi >= maxMmi) return false;
            return true;
        }
    }

    public static final MmiBin BIN_I = new MmiBin("I", 1.0, 1.5, "Not felt", "None", "#fbfcff", "#000000");
    public static final MmiBin BIN_II_III = new MmiBin("II-III", 1.5, 3.5, "Weak", "None", "#acdbff", "#000000");
    public static final MmiBin BIN_IV = new MmiBin("IV", 3.5, 4.5, "Light", "None", "#7ffffa", "#000000");
    public static final MmiBin BIN_V = new MmiBin("V", 4.5, 5.5, "Moderate", "Very light", "#81ff8a", "#000000");
    public static final MmiBin BIN_VI = new MmiBin("VI", 5.5, 6.5, "Strong", "Light", "#fffa00", "#000000");
    public static final MmiBin BIN_VII = new MmiBin("VII", 6.5, 7.5, "Very strong", "Moderate", "#ffc400", "#000000");
    public static final MmiBin BIN_VIII = new MmiBin("VIII", 7.5, 8.5, "Severe", "Moderate/heavy", "#ff8500", "#ffffff");
    public static final MmiBin BIN_IX = new MmiBin("IX", 8.5, 9.5, "Violent", "Heavy", "#fb0000", "#ffffff");
    public static final MmiBin BIN_X_PLUS = new MmiBin("X+", 9.5, null, "Extreme", "Very heavy", "#c80000", "#ffffff");
    public static final MmiBin BIN_NA = new MmiBin("N/A", null, null, "Outside coverage", "N/A", "#808080", "#ffffff");

    public static final List<MmiBin> ALL_BINS = List.of(
            BIN_I, BIN_II_III, BIN_IV, BIN_V, BIN_VI,
            BIN_VII, BIN_VIII, BIN_IX, BIN_X_PLUS, BIN_NA
    );

    private MmiLegend() {}

    /**
     * Rounds a source MMI decimal to standard display precision (1 decimal place, HALF_UP).
     *
     * @param mmiSourceDecimal raw MMI decimal
     * @return 1-decimal rounded MMI
     */
    public static double roundToDisplay(double mmiSourceDecimal) {
        if (Double.isNaN(mmiSourceDecimal) || Double.isInfinite(mmiSourceDecimal)) {
            return Double.NaN;
        }
        return BigDecimal.valueOf(mmiSourceDecimal)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Resolves the appropriate frozen legend bin for an MMI value.
     * Values on bin boundaries follow standard ShakeMap half-open intervals [min, max).
     *
     * @param mmi MMI numeric value, or {@code null}
     * @return corresponding {@link MmiBin}
     */
    public static MmiBin findBin(Double mmi) {
        if (mmi == null || Double.isNaN(mmi) || Double.isInfinite(mmi) || mmi < 1.0) {
            return BIN_NA;
        }
        if (mmi < 1.5) return BIN_I;
        if (mmi < 3.5) return BIN_II_III;
        if (mmi < 4.5) return BIN_IV;
        if (mmi < 5.5) return BIN_V;
        if (mmi < 6.5) return BIN_VI;
        if (mmi < 7.5) return BIN_VII;
        if (mmi < 8.5) return BIN_VIII;
        if (mmi < 9.5) return BIN_IX;
        return BIN_X_PLUS;
    }
}
