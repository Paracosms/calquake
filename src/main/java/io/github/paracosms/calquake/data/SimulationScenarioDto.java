package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.IntensityDisplayMode;
import io.github.paracosms.calquake.core.SimulationAssumptionSet;
import io.github.paracosms.calquake.core.SimulationScenarioSettings;

import java.time.Instant;
import java.util.Objects;

/**
 * Versioned Data Transfer Object for CalQuake simulation scenarios.
 * Matches the UTF-8 JSON schema defined in Section 8.1 of the simulation mode specification.
 */
public record SimulationScenarioDto(
        int schemaVersion,
        String type,
        String scenarioId,
        String name,
        String createdUtc,
        EpicenterDto epicenter,
        double magnitude,
        double depthKm,
        String intensityDisplayMode,
        String assumptionSet
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String SCENARIO_TYPE = "calquake-simulation-scenario";

    public record EpicenterDto(double latitude, double longitude) {
        public EpicenterDto {
            if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
                throw new IllegalArgumentException("Latitude must be a finite number between -90 and 90: " + latitude);
            }
            if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
                throw new IllegalArgumentException("Longitude must be a finite number between -180 and 180: " + longitude);
            }
        }
    }

    public SimulationScenarioDto {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(scenarioId, "scenarioId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(createdUtc, "createdUtc cannot be null");
        Objects.requireNonNull(epicenter, "epicenter cannot be null");
        Objects.requireNonNull(intensityDisplayMode, "intensityDisplayMode cannot be null");
        Objects.requireNonNull(assumptionSet, "assumptionSet cannot be null");
    }

    public static SimulationScenarioDto fromSettings(SimulationScenarioSettings settings) {
        Objects.requireNonNull(settings, "settings cannot be null");
        return new SimulationScenarioDto(
                CURRENT_SCHEMA_VERSION,
                SCENARIO_TYPE,
                settings.scenarioId(),
                settings.displayName(),
                settings.createdUtc().toString(),
                new EpicenterDto(settings.epicenter().latitude(), settings.epicenter().longitude()),
                settings.magnitude(),
                settings.depthKm(),
                settings.intensityDisplayMode().name(),
                settings.assumptionSetId()
        );
    }

    public SimulationScenarioSettings toSettings() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported schema version: " + schemaVersion + ". Expected version " + CURRENT_SCHEMA_VERSION);
        }
        if (!SCENARIO_TYPE.equals(type)) {
            throw new IllegalArgumentException("Unknown scenario type: '" + type + "'. Expected '" + SCENARIO_TYPE + "'");
        }
        if (scenarioId.isBlank()) {
            throw new IllegalArgumentException("scenarioId cannot be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        Instant instant = Instant.parse(createdUtc);
        GeoPoint geoPoint = new GeoPoint(epicenter.latitude(), epicenter.longitude());
        if (!Double.isFinite(magnitude)) {
            throw new IllegalArgumentException("magnitude must be finite");
        }
        if (!Double.isFinite(depthKm) || depthKm < 0.0) {
            throw new IllegalArgumentException("depthKm must be finite and non-negative");
        }

        IntensityDisplayMode mode;
        try {
            mode = IntensityDisplayMode.valueOf(intensityDisplayMode);
        } catch (IllegalArgumentException e) {
            mode = IntensityDisplayMode.fromLabel(intensityDisplayMode);
        }

        // Explicitly resolve assumption set; fails safely if unknown
        SimulationAssumptionSet.resolve(assumptionSet);

        return new SimulationScenarioSettings(
                scenarioId,
                name,
                instant,
                geoPoint,
                magnitude,
                depthKm,
                mode,
                assumptionSet
        );
    }
}
