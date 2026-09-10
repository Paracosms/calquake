package io.github.paracosms.calquake.core;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of the earthquake replay state at a given elapsed time.
 *
 * @param elapsedSeconds      elapsed time in seconds from event origin
 * @param epicenter           geographic coordinates of the epicenter
 * @param frontRadii          available surface wavefront radii (P and S in km)
 * @param locationIntensities immutable list of prepared, frame-local location intensities
 */
public record FrameState(
        double elapsedSeconds,
        GeoPoint epicenter,
        WavefrontRadii frontRadii,
        List<LocationIntensityState> locationIntensities
) {
    public FrameState {
        if (Double.isNaN(elapsedSeconds) || Double.isInfinite(elapsedSeconds) || elapsedSeconds < 0.0) {
            throw new IllegalArgumentException("Elapsed seconds must be a non-negative finite number: " + elapsedSeconds);
        }
        Objects.requireNonNull(epicenter, "epicenter cannot be null");
        Objects.requireNonNull(frontRadii, "frontRadii cannot be null");
        Objects.requireNonNull(locationIntensities, "locationIntensities cannot be null");
        locationIntensities = List.copyOf(locationIntensities);
    }
}
