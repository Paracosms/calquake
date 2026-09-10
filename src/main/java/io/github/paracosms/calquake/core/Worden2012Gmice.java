package io.github.paracosms.calquake.core;

import java.util.OptionalDouble;

/** Worden et al. (2012) California PGV-to-MMI conversion, without M/R correction. */
public final class Worden2012Gmice {
    public static final String MODEL_ID = "WordenEtAl2012-PGV-California-no-MR-correction";
    public static final String VERSION = "WGRW12-equation-1-PGV";
    /** SHA-256 of the documented canonical piecewise-equation serialization. */
    public static final String COEFFICIENT_SHA256 =
            "b31b0c7e22b66931ab38aa1a9b21742aaff4d37a2199d0337267530d49a3cb95";
    public static final double LOG10_PGV_BREAKPOINT = 0.53;

    public double fromPgvCmPerSecond(double pgvCmPerSecond) {
        if (!Double.isFinite(pgvCmPerSecond) || pgvCmPerSecond <= 0.0) {
            throw new IllegalArgumentException("PGV must be finite and positive in cm/s");
        }
        double x = Math.log10(pgvCmPerSecond);
        return x <= LOG10_PGV_BREAKPOINT ? 3.78 + 1.47 * x : 2.89 + 3.16 * x;
    }

    public OptionalDouble tryFromPgvCmPerSecond(double pgvCmPerSecond) {
        return Double.isFinite(pgvCmPerSecond) && pgvCmPerSecond > 0.0
                ? OptionalDouble.of(fromPgvCmPerSecond(pgvCmPerSecond))
                : OptionalDouble.empty();
    }
}
