package io.github.paracosms.calquake.core;

import java.util.List;
import java.util.Objects;

/**
 * Immutable normalized fault-section modeling record derived from authoritative NSHM fault sections.
 * Used exclusively for deterministic scenario fault association.
 */
public record FaultSection(
        long sectionId,
        String sectionName,
        String state,
        List<GeoPoint> trace,
        double dipDegrees,
        String dipDirection,
        double rakeDegrees,
        double upperDepthKm,
        double lowerDepthKm
) {
    public FaultSection {
        Objects.requireNonNull(sectionName, "sectionName cannot be null");
        if (sectionName.isBlank()) {
            throw new IllegalArgumentException("sectionName cannot be blank");
        }
        state = state == null ? "" : state.trim();
        Objects.requireNonNull(trace, "trace cannot be null");
        if (trace.size() < 2) {
            throw new IllegalArgumentException("Fault section trace requires at least two points");
        }
        for (GeoPoint p : trace) {
            Objects.requireNonNull(p, "trace point cannot be null");
        }
        trace = List.copyOf(trace);
        if (!Double.isFinite(dipDegrees) || dipDegrees <= 0.0 || dipDegrees > 90.0) {
            throw new IllegalArgumentException("dipDegrees must be in (0, 90]: " + dipDegrees);
        }
        Objects.requireNonNull(dipDirection, "dipDirection cannot be null");
        dipDirection = dipDirection.trim();
        if (dipDirection.isBlank()) {
            throw new IllegalArgumentException("dipDirection cannot be blank");
        }
        if (!Double.isFinite(rakeDegrees) || rakeDegrees < -180.0 || rakeDegrees > 180.0) {
            throw new IllegalArgumentException("rakeDegrees must be in [-180, 180]: " + rakeDegrees);
        }
        if (!Double.isFinite(upperDepthKm) || upperDepthKm < 0.0) {
            throw new IllegalArgumentException("upperDepthKm must be non-negative and finite: " + upperDepthKm);
        }
        if (!Double.isFinite(lowerDepthKm) || lowerDepthKm <= upperDepthKm) {
            throw new IllegalArgumentException("lowerDepthKm must be greater than upperDepthKm: " + lowerDepthKm);
        }
    }

    public FaultSection(
            long sectionId,
            String sectionName,
            List<GeoPoint> trace,
            double dipDegrees,
            String dipDirection,
            double rakeDegrees,
            double upperDepthKm,
            double lowerDepthKm
    ) {
        this(sectionId, sectionName, "CA", trace, dipDegrees, dipDirection, rakeDegrees, upperDepthKm, lowerDepthKm);
    }

    /**
     * Down-dip planar width in kilometers.
     */
    public double downDipWidthKm() {
        return (lowerDepthKm - upperDepthKm) / Math.sin(Math.toRadians(dipDegrees));
    }

    /**
     * Horizontal width of the dipping projection in kilometers.
     */
    public double horizontalDipWidthKm() {
        return downDipWidthKm() * Math.cos(Math.toRadians(dipDegrees));
    }
}
