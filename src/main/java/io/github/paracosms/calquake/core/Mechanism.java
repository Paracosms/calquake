package io.github.paracosms.calquake.core;

/** Frozen earthquake faulting mechanism used by ground-motion prediction. */
public record Mechanism(
        double rakeDegrees,
        double strikeDegrees,
        double dipDegrees,
        String style,
        String provenance
) {
    public Mechanism {
        if (!Double.isFinite(rakeDegrees) || rakeDegrees < -180.0 || rakeDegrees > 180.0) {
            throw new IllegalArgumentException("Rake must be within [-180, 180] degrees");
        }
        if (!Double.isFinite(strikeDegrees) || strikeDegrees < 0.0 || strikeDegrees >= 360.0) {
            throw new IllegalArgumentException("Strike must be within [0, 360) degrees");
        }
        if (!Double.isFinite(dipDegrees) || dipDegrees <= 0.0 || dipDegrees > 90.0) {
            throw new IllegalArgumentException("Dip must be within (0, 90] degrees");
        }
        style = style == null ? "" : style.trim();
        provenance = provenance == null ? "" : provenance.trim();
    }
}
