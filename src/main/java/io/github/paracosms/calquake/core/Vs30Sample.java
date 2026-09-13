package io.github.paracosms.calquake.core;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Deterministic sample extracted from the USGS Global Vs30 Mosaic raster.
 */
public record Vs30Sample(
        double vs30MetersPerSecond,
        OptionalDouble logarithmicStandardDeviation,
        int colIndex,
        int rowIndex,
        String sourceId
) {
    public Vs30Sample {
        if (!Double.isFinite(vs30MetersPerSecond) || vs30MetersPerSecond <= 0.0) {
            throw new IllegalArgumentException("Vs30 must be positive and finite: " + vs30MetersPerSecond);
        }
        Objects.requireNonNull(logarithmicStandardDeviation, "logarithmicStandardDeviation cannot be null");
        sourceId = sourceId == null ? "" : sourceId.trim();
    }
}
