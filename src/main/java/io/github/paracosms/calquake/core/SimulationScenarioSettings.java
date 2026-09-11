package io.github.paracosms.calquake.core;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable scenario settings for CalQuake simulation mode.
 */
public record SimulationScenarioSettings(
        String scenarioId,
        String displayName,
        Instant createdUtc,
        GeoPoint epicenter,
        double magnitude,
        double depthKm,
        IntensityDisplayMode intensityDisplayMode,
        String assumptionSetId
) {
    public SimulationScenarioSettings {
        Objects.requireNonNull(scenarioId, "scenarioId cannot be null");
        if (scenarioId.isBlank()) throw new IllegalArgumentException("scenarioId cannot be blank");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        if (displayName.isBlank()) throw new IllegalArgumentException("displayName cannot be blank");
        Objects.requireNonNull(createdUtc, "createdUtc cannot be null");
        Objects.requireNonNull(epicenter, "epicenter cannot be null");
        if (!Double.isFinite(magnitude)) throw new IllegalArgumentException("magnitude must be finite");
        if (!Double.isFinite(depthKm) || depthKm < 0.0) {
            throw new IllegalArgumentException("depthKm must be finite and non-negative");
        }
        Objects.requireNonNull(intensityDisplayMode, "intensityDisplayMode cannot be null");
        Objects.requireNonNull(assumptionSetId, "assumptionSetId cannot be null");
        if (assumptionSetId.isBlank()) throw new IllegalArgumentException("assumptionSetId cannot be blank");
    }

    public SimulationScenarioSettings withIntensityDisplayMode(IntensityDisplayMode newMode) {
        return new SimulationScenarioSettings(
                scenarioId, displayName, createdUtc, epicenter, magnitude, depthKm,
                Objects.requireNonNull(newMode, "newMode cannot be null"), assumptionSetId);
    }

    public static SimulationScenarioSettings createDefault() {
        return new SimulationScenarioSettings(
                "custom-california-scenario-v1",
                "Custom California Scenario",
                Instant.parse("2026-09-10T00:00:00Z"),
                new GeoPoint(35.5, -118.5),
                6.5,
                10.0,
                IntensityDisplayMode.MAXIMUM_REACHED,
                SimulationAssumptionSet.DEFAULT_ID
        );
    }
}
