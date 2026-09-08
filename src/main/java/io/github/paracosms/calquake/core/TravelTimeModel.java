package io.github.paracosms.calquake.core;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure domain interface for seismic travel-time calculations.
 * Encapsulates earth velocity modeling without presentation, UI, or file-system dependencies.
 */
public interface TravelTimeModel {

    double DEFAULT_SOURCE_DEPTH_KM = 8.0;
    double EARTH_RADIUS_KM = 6371.0;

    /**
     * Compute the earliest arrival travel time in seconds for the given phase family
     * ("P" or "S") at the specified epicentral distance and source hypocentral depth.
     *
     * @param phaseFamily phase family ("P" or "S")
     * @param distanceKm  epicentral surface distance in kilometers
     * @param depthKm     hypocentral source depth in kilometers
     * @return earliest arrival travel time in seconds
     */
    double travelTimeSeconds(String phaseFamily, double distanceKm, double depthKm);

    /**
     * Compute the earliest arrival travel time in seconds for the given phase family
     * at the default Ridgecrest hypocentral depth (8.0 km).
     *
     * @param phaseFamily phase family ("P" or "S")
     * @param distanceKm  epicentral surface distance in kilometers
     * @return earliest arrival travel time in seconds
     */
    default double travelTimeSeconds(String phaseFamily, double distanceKm) {
        return travelTimeSeconds(phaseFamily, distanceKm, DEFAULT_SOURCE_DEPTH_KM);
    }

    /**
     * Compute the vertical travel time in seconds (epicentral distance = 0)
     * from hypocentral depth to surface receiver.
     *
     * @param phaseFamily phase family ("P" or "S")
     * @param depthKm     hypocentral source depth in kilometers
     * @return vertical travel time in seconds
     */
    double verticalTravelTimeSeconds(String phaseFamily, double depthKm);

    /**
     * Compute the vertical travel time at the default depth of 8.0 km.
     *
     * @param phaseFamily phase family ("P" or "S")
     * @return vertical travel time in seconds
     */
    default double verticalTravelTimeSeconds(String phaseFamily) {
        return verticalTravelTimeSeconds(phaseFamily, DEFAULT_SOURCE_DEPTH_KM);
    }

    /**
     * Query detailed arrival information for the earliest eligible arrival.
     *
     * @param phaseFamily phase family ("P" or "S")
     * @param distanceKm  epicentral surface distance in kilometers
     * @param depthKm     hypocentral source depth in kilometers
     * @return Optional containing PhaseArrival if an arrival exists
     */
    Optional<PhaseArrival> earliestArrival(String phaseFamily, double distanceKm, double depthKm);

    /**
     * Immutable representation of a calculated seismic arrival.
     *
     * @param phaseName   specific phase branch name (e.g. "p", "P", "Pn", "s", "S", "Sn")
     * @param timeSeconds travel time from event origin in seconds
     * @param rayParam    ray parameter in s/radian
     * @param distanceKm  epicentral distance in kilometers
     * @param depthKm     source depth in kilometers
     */
    record PhaseArrival(
            String phaseName,
            double timeSeconds,
            double rayParam,
            double distanceKm,
            double depthKm
    ) {
        public PhaseArrival {
            Objects.requireNonNull(phaseName, "phaseName cannot be null");
            if (Double.isNaN(timeSeconds) || timeSeconds < 0.0) {
                throw new IllegalArgumentException("Invalid arrival time: " + timeSeconds);
            }
            if (Double.isNaN(distanceKm) || distanceKm < 0.0) {
                throw new IllegalArgumentException("Invalid distance: " + distanceKm);
            }
            if (Double.isNaN(depthKm) || depthKm < 0.0) {
                throw new IllegalArgumentException("Invalid depth: " + depthKm);
            }
        }
    }
}
