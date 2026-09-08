package io.github.paracosms.calquake.core;

import java.util.Objects;

/**
 * Immutable representation of a reference location and its peak historical intensity
 * in a replay frame.
 */
public record LocationIntensityState(
        ReferenceLocation location,
        ReferenceLocation.PeakIntensity intensity
) {
    public LocationIntensityState {
        Objects.requireNonNull(location, "location cannot be null");
        Objects.requireNonNull(intensity, "intensity cannot be null");
    }

    public String city() {
        return location.city();
    }

    public String geoid() {
        return location.geoid();
    }

    public GeoPoint internalPoint() {
        return location.internalPoint();
    }

    public double mmiSourceDecimal() {
        return intensity.mmiSourceDecimal();
    }

    public double mmiDisplayRounded() {
        return intensity.mmiDisplayRounded();
    }

    public String mmiRoman() {
        return intensity.mmiRoman();
    }

    public String shakingDescription() {
        return intensity.shakingDescription();
    }

    public String damageDescription() {
        return intensity.damageDescription();
    }

    public String colorHex() {
        return intensity.colorHex();
    }
}
