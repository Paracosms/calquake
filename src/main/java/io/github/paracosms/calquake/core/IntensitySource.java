package io.github.paracosms.calquake.core;

import java.util.Objects;

/**
 * Pure domain interface supplying peak ground motion intensities for reference locations.
 * In Demo 0, time changes never alter peak MMI values (historical peak intensity replay).
 */
public interface IntensitySource {

    /**
     * Obtains the peak intensity for the specified reference location.
     *
     * @param location reference location
     * @return peak intensity parameters
     */
    ReferenceLocation.PeakIntensity getPeakIntensity(ReferenceLocation location);

    /**
     * Creates an IntensitySource that extracts peak intensity directly from the scenario's reference locations.
     */
    static IntensitySource scenarioPeak() {
        return new ScenarioPeakIntensitySource();
    }

    final class ScenarioPeakIntensitySource implements IntensitySource {
        @Override
        public ReferenceLocation.PeakIntensity getPeakIntensity(ReferenceLocation location) {
            Objects.requireNonNull(location, "location cannot be null");
            return location.peakIntensity();
        }
    }
}
