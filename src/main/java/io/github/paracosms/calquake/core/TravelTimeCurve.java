package io.github.paracosms.calquake.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Precomputed travel-time curve for a specific seismic phase family ("P" or "S")
 * at a given hypocentral depth, supporting fast monotonic inversion from elapsed time
 * to epicentral surface radius.
 * <p>
 * Ensures the precomputed domain covers the full replay duration (120 s) and handles
 * pre-surface arrival states explicitly (returning empty before vertical travel time).
 */
public final class TravelTimeCurve {

    public static final double DEFAULT_DISTANCE_STEP_KM = 0.5;
    public static final double DEFAULT_MAX_TIME_SECONDS = 120.0;
    public static final double TIME_BUFFER_SECONDS = 5.0;
    public static final double ABSOLUTE_MAX_DISTANCE_KM = 1200.0;

    private final String phaseFamily;
    private final double depthKm;
    private final double verticalTimeSeconds;
    private final double[] distancesKm;
    private final double[] timesSeconds;

    public TravelTimeCurve(String phaseFamily, double depthKm, double verticalTimeSeconds,
                           double[] distancesKm, double[] timesSeconds) {
        this.phaseFamily = Objects.requireNonNull(phaseFamily, "phaseFamily cannot be null");
        if (depthKm < 0.0) {
            throw new IllegalArgumentException("Depth must be non-negative: " + depthKm);
        }
        if (verticalTimeSeconds < 0.0) {
            throw new IllegalArgumentException("Vertical time must be non-negative: " + verticalTimeSeconds);
        }
        Objects.requireNonNull(distancesKm, "distancesKm cannot be null");
        Objects.requireNonNull(timesSeconds, "timesSeconds cannot be null");
        if (distancesKm.length != timesSeconds.length || distancesKm.length < 2) {
            throw new IllegalArgumentException("Distances and times arrays must have equal length >= 2");
        }
        this.depthKm = depthKm;
        this.verticalTimeSeconds = verticalTimeSeconds;
        this.distancesKm = distancesKm.clone();
        this.timesSeconds = timesSeconds.clone();
    }

    /**
     * Precomputes travel-time curve for the specified phase, model, and depth.
     *
     * @param phaseFamily    "P" or "S"
     * @param model          travel time model
     * @param depthKm        hypocentral depth in km
     * @param maxTimeSeconds maximum replay time in seconds to cover (e.g. 120.0)
     * @param distanceStepKm distance discretization step in km (e.g. 0.5)
     * @return precomputed TravelTimeCurve
     */
    public static TravelTimeCurve precompute(String phaseFamily, TravelTimeModel model,
                                            double depthKm, double maxTimeSeconds, double distanceStepKm) {
        Objects.requireNonNull(phaseFamily, "phaseFamily cannot be null");
        Objects.requireNonNull(model, "model cannot be null");
        if (depthKm < 0.0) {
            throw new IllegalArgumentException("Depth must be non-negative: " + depthKm);
        }
        if (maxTimeSeconds <= 0.0) {
            throw new IllegalArgumentException("maxTimeSeconds must be positive: " + maxTimeSeconds);
        }
        if (distanceStepKm <= 0.0) {
            throw new IllegalArgumentException("distanceStepKm must be positive: " + distanceStepKm);
        }

        double verticalTime = model.verticalTravelTimeSeconds(phaseFamily, depthKm);
        double targetMaxTime = maxTimeSeconds + TIME_BUFFER_SECONDS;

        List<Double> distList = new ArrayList<>();
        List<Double> timeList = new ArrayList<>();

        // d = 0.0 is vertical arrival
        distList.add(0.0);
        timeList.add(verticalTime);

        double currentDist = distanceStepKm;
        double lastTime = verticalTime;

        while (lastTime < targetMaxTime && currentDist <= ABSOLUTE_MAX_DISTANCE_KM) {
            double time = model.travelTimeSeconds(phaseFamily, currentDist, depthKm);
            distList.add(currentDist);
            timeList.add(time);
            lastTime = time;
            currentDist += distanceStepKm;
        }

        double[] dArr = new double[distList.size()];
        double[] tArr = new double[timeList.size()];
        for (int i = 0; i < distList.size(); i++) {
            dArr[i] = distList.get(i);
            tArr[i] = timeList.get(i);
        }

        return new TravelTimeCurve(phaseFamily, depthKm, verticalTime, dArr, tArr);
    }

    /**
     * Precomputes travel-time curve using standard 0.5 km step and 120.0 s target replay time.
     */
    public static TravelTimeCurve precompute(String phaseFamily, TravelTimeModel model, double depthKm, double maxTimeSeconds) {
        return precompute(phaseFamily, model, depthKm, maxTimeSeconds, DEFAULT_DISTANCE_STEP_KM);
    }

    /**
     * Invert the travel-time curve to obtain the surface wavefront radius in kilometers
     * at a given elapsed time from the origin.
     *
     * @param elapsedSeconds elapsed time in seconds from event origin
     * @return surface radius in kilometers, or {@link OptionalDouble#empty()} if wave has not yet reached surface
     */
    public OptionalDouble invertRadiusKm(double elapsedSeconds) {
        if (Double.isNaN(elapsedSeconds) || elapsedSeconds < verticalTimeSeconds) {
            return OptionalDouble.empty();
        }
        if (elapsedSeconds == verticalTimeSeconds) {
            return OptionalDouble.of(0.0);
        }

        int index = Arrays.binarySearch(timesSeconds, elapsedSeconds);
        if (index >= 0) {
            return OptionalDouble.of(distancesKm[index]);
        }

        int insertionPoint = -(index + 1);
        if (insertionPoint <= 0) {
            return OptionalDouble.of(0.0);
        }
        if (insertionPoint >= timesSeconds.length) {
            // Beyond precomputed range; extrapolate using the final segment
            int n = timesSeconds.length;
            double dt = timesSeconds[n - 1] - timesSeconds[n - 2];
            double dd = distancesKm[n - 1] - distancesKm[n - 2];
            double frac = (elapsedSeconds - timesSeconds[n - 1]) / dt;
            return OptionalDouble.of(distancesKm[n - 1] + frac * dd);
        }

        int i0 = insertionPoint - 1;
        int i1 = insertionPoint;

        double t0 = timesSeconds[i0];
        double t1 = timesSeconds[i1];
        double d0 = distancesKm[i0];
        double d1 = distancesKm[i1];

        double frac = (elapsedSeconds - t0) / (t1 - t0);
        double radius = d0 + frac * (d1 - d0);
        return OptionalDouble.of(radius);
    }

    public String phaseFamily() {
        return phaseFamily;
    }

    public double depthKm() {
        return depthKm;
    }

    public double verticalTimeSeconds() {
        return verticalTimeSeconds;
    }

    public double maxDistanceKm() {
        return distancesKm[distancesKm.length - 1];
    }

    public double maxTimeSeconds() {
        return timesSeconds[timesSeconds.length - 1];
    }

    public int sampleCount() {
        return distancesKm.length;
    }

    public double[] getDistancesKm() {
        return distancesKm.clone();
    }

    public double[] getTimesSeconds() {
        return timesSeconds.clone();
    }
}
