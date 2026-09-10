package io.github.paracosms.calquake.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Core deterministic replay engine.
 * Precomputes P and S travel-time curves once upon scenario construction,
 * allowing fast, pure-function evaluation of {@link FrameState} at any elapsed time.
 */
public final class ReplayEngine {

    private final Scenario scenario;
    private final TravelTimeModel model;
    private final PrecomputedWavefronts wavefronts;
    private final IntensitySource intensitySource;
    private final List<LocationMetadata> scenarioLocationMetadata;
    private final List<LocationIntensityState> fixedIntensities;

    private record LocationMetadata(
            ReferenceLocation location,
            ReferenceLocation.PeakIntensity peakIntensity,
            double distanceKm,
            double pArrivalTimeSeconds,
            double sArrivalTimeSeconds
    ) {}

    public ReplayEngine(Scenario scenario, TravelTimeModel model, IntensitySource intensitySource) {
        this.scenario = Objects.requireNonNull(scenario, "scenario cannot be null");
        this.model = Objects.requireNonNull(model, "model cannot be null");
        this.intensitySource = Objects.requireNonNull(intensitySource, "intensitySource cannot be null");

        // Precompute curves once for scenario's hypocentral depth
        this.wavefronts = PrecomputedWavefronts.forScenario(scenario, model);

        this.scenarioLocationMetadata = computeMetadata(scenario, model, intensitySource);

        // Pre-resolve immutable fixed location intensities for backward compatibility
        List<LocationIntensityState> states = new ArrayList<>();
        for (LocationMetadata meta : scenarioLocationMetadata) {
            states.add(new LocationIntensityState(meta.location(), meta.peakIntensity()));
        }
        this.fixedIntensities = List.copyOf(states);
    }

    private static List<LocationMetadata> computeMetadata(Scenario sc, TravelTimeModel m, IntensitySource src) {
        GeoPoint epicenter = sc.event().epicenter();
        double depthKm = sc.event().depthKm();
        List<LocationMetadata> list = new ArrayList<>();
        for (ReferenceLocation loc : sc.locations()) {
            double distKm = loc.internalPoint().distanceKmTo(epicenter);
            double pTime = m.travelTimeSeconds("P", distKm, depthKm);
            double sTime = m.travelTimeSeconds("S", distKm, depthKm);
            ReferenceLocation.PeakIntensity intensity = src.getPeakIntensity(loc);
            list.add(new LocationMetadata(loc, intensity, distKm, pTime, sTime));
        }
        return List.copyOf(list);
    }

    public static ReplayEngine create(Scenario scenario, TravelTimeModel model) {
        return new ReplayEngine(scenario, model, IntensitySource.scenarioPeak());
    }

    public static ReplayEngine create(Scenario scenario, TravelTimeModel model, IntensitySource intensitySource) {
        return new ReplayEngine(scenario, model, intensitySource);
    }

    /**
     * Deterministically computes the {@link FrameState} at the given elapsed time
     * for the bound scenario.
     *
     * @param elapsedSeconds elapsed time in seconds
     * @return immutable FrameState
     */
    public FrameState frameAt(double elapsedSeconds) {
        return frameAt(this.scenario, elapsedSeconds);
    }

    /**
     * Deterministically computes the {@link FrameState} for a scenario at the given elapsed time.
     *
     * @param targetScenario scenario to evaluate
     * @param elapsedSeconds elapsed time in seconds
     * @return immutable FrameState
     */
    public FrameState frameAt(Scenario targetScenario, double elapsedSeconds) {
        Objects.requireNonNull(targetScenario, "targetScenario cannot be null");
        if (Double.isNaN(elapsedSeconds) || Double.isInfinite(elapsedSeconds) || elapsedSeconds < 0.0) {
            throw new IllegalArgumentException("Elapsed seconds must be a non-negative finite number: " + elapsedSeconds);
        }

        WavefrontRadii radii = wavefronts.radiiAt(elapsedSeconds);

        List<LocationMetadata> metadataList;
        if (targetScenario.equals(this.scenario)) {
            metadataList = this.scenarioLocationMetadata;
        } else {
            metadataList = computeMetadata(targetScenario, this.model, this.intensitySource);
        }

        List<LocationIntensityState> intensities = new ArrayList<>(metadataList.size());
        for (LocationMetadata meta : metadataList) {
            boolean sArrived = elapsedSeconds >= meta.sArrivalTimeSeconds();
            intensities.add(new LocationIntensityState(
                    meta.location(),
                    meta.peakIntensity(),
                    sArrived,
                    meta.sArrivalTimeSeconds(),
                    meta.pArrivalTimeSeconds(),
                    meta.distanceKm()
            ));
        }

        return new FrameState(
                elapsedSeconds,
                targetScenario.event().epicenter(),
                radii,
                intensities
        );
    }

    public TravelTimeModel model() {
        return model;
    }

    public Scenario scenario() {
        return scenario;
    }

    public PrecomputedWavefronts wavefronts() {
        return wavefronts;
    }

    public IntensitySource intensitySource() {
        return intensitySource;
    }

    public List<LocationIntensityState> fixedIntensities() {
        return fixedIntensities;
    }
}
