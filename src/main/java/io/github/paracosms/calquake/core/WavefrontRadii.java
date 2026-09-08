package io.github.paracosms.calquake.core;

import java.util.OptionalDouble;

/**
 * Immutable record representing available P and S surface wavefront radii in kilometers.
 * <p>
 * Before a seismic wave reaches the surface receiver at the epicenter (t &lt; vertical travel time),
 * no surface wavefront exists, represented explicitly as {@code null} or an empty {@link OptionalDouble}.
 *
 * @param pRadiusKm P-wave surface radius in km, or {@code null} if not yet arrived at surface
 * @param sRadiusKm S-wave surface radius in km, or {@code null} if not yet arrived at surface
 */
public record WavefrontRadii(Double pRadiusKm, Double sRadiusKm) {

    public WavefrontRadii {
        if (pRadiusKm != null && (Double.isNaN(pRadiusKm) || pRadiusKm < 0.0)) {
            throw new IllegalArgumentException("P radius must be non-negative: " + pRadiusKm);
        }
        if (sRadiusKm != null && (Double.isNaN(sRadiusKm) || sRadiusKm < 0.0)) {
            throw new IllegalArgumentException("S radius must be non-negative: " + sRadiusKm);
        }
    }

    public static WavefrontRadii empty() {
        return new WavefrontRadii(null, null);
    }

    public static WavefrontRadii of(Double pRadiusKm, Double sRadiusKm) {
        return new WavefrontRadii(pRadiusKm, sRadiusKm);
    }

    public boolean hasP() {
        return pRadiusKm != null;
    }

    public boolean hasS() {
        return sRadiusKm != null;
    }

    public OptionalDouble getPRadiusKm() {
        return hasP() ? OptionalDouble.of(pRadiusKm) : OptionalDouble.empty();
    }

    public OptionalDouble getSRadiusKm() {
        return hasS() ? OptionalDouble.of(sRadiusKm) : OptionalDouble.empty();
    }
}
