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
    private final PrecomputedWavefronts wavefronts;
    private final IntensitySource intensitySource;
    private final List<LocationIntensityState> fixedIntensities;

    public ReplayEngine(Scenario scenario, TravelTimeModel model, IntensitySource intensitySource) {
        this.scenario = Objects.requireNonNull(scenario, "scenario cannot be null");
        Objects.requireNonNull(model, "model cannot be null");
        this.intensitySource = Objects.requireNonNull(intensitySource, "intensitySource cannot be null");

        // Precompute curves once for scenario's hypocentral depth
        this.wavefronts = PrecomputedWavefronts.forScenario(scenario, model);

        // Pre-resolve immutable fixed location intensities
        List<LocationIntensityState> states = new ArrayList<>();
        for (ReferenceLocation loc : scenario.locations()) {
            ReferenceLocation.PeakIntensity intensity = intensitySource.getPeakIntensity(loc);
            states.add(new LocationIntensityState(loc, intensity));
        }
        this.fixedIntensities = List.copyOf(states);
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

        List<LocationIntensityState> intensities;
        if (targetScenario.equals(this.scenario)) {
            intensities = this.fixedIntensities;
        } else {
            List<LocationIntensityState> custom = new ArrayList<>();
            for (ReferenceLocation loc : targetScenario.locations()) {
                custom.add(new LocationIntensityState(loc, intensitySource.getPeakIntensity(loc)));
            }
            intensities = List.copyOf(custom);
        }

        return new FrameState(
                elapsedSeconds,
                targetScenario.event().epicenter(),
                radii,
                intensities
        );
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
