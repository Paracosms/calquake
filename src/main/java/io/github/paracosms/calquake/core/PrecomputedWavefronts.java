package io.github.paracosms.calquake.core;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Encapsulates precomputed P and S travel-time curves for a given scenario or hypocentral depth.
 * Precomputed once upon load, allowing constant-time deterministic radius queries
 * during animation frames.
 */
public final class PrecomputedWavefronts {

    private final TravelTimeCurve pCurve;
    private final TravelTimeCurve sCurve;

    public PrecomputedWavefronts(TravelTimeCurve pCurve, TravelTimeCurve sCurve) {
        this.pCurve = Objects.requireNonNull(pCurve, "pCurve cannot be null");
        this.sCurve = Objects.requireNonNull(sCurve, "sCurve cannot be null");
    }

    /**
     * Precomputes P and S curves for the given model and depth covering up to maxTimeSeconds.
     */
    public static PrecomputedWavefronts precompute(TravelTimeModel model, double depthKm, double maxTimeSeconds) {
        TravelTimeCurve p = TravelTimeCurve.precompute("P", model, depthKm, maxTimeSeconds);
        TravelTimeCurve s = TravelTimeCurve.precompute("S", model, depthKm, maxTimeSeconds);
        return new PrecomputedWavefronts(p, s);
    }

    /**
     * Precomputes P and S curves for the given scenario and model covering default 120s replay.
     */
    public static PrecomputedWavefronts forScenario(Scenario scenario, TravelTimeModel model) {
        Objects.requireNonNull(scenario, "scenario cannot be null");
        return precompute(model, scenario.event().depthKm(), TravelTimeCurve.DEFAULT_MAX_TIME_SECONDS);
    }

    /**
     * Invert both P and S curves for surface wavefront radii at the specified elapsed time.
     *
     * @param elapsedSeconds elapsed time in seconds from origin
     * @return immutable {@link WavefrontRadii} containing available radii
     */
    public WavefrontRadii radiiAt(double elapsedSeconds) {
        OptionalDouble pRad = pCurve.invertRadiusKm(elapsedSeconds);
        OptionalDouble sRad = sCurve.invertRadiusKm(elapsedSeconds);

        Double p = pRad.isPresent() ? pRad.getAsDouble() : null;
        Double s = sRad.isPresent() ? sRad.getAsDouble() : null;
        return new WavefrontRadii(p, s);
    }

    public TravelTimeCurve pCurve() {
        return pCurve;
    }

    public TravelTimeCurve sCurve() {
        return sCurve;
    }
}
