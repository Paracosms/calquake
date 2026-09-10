package io.github.paracosms.calquake.core;

import io.github.paracosms.calquake.data.ScenarioLoader;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrecomputedWavefrontsTest {

    private static final TravelTimeModel LINEAR_MODEL = new TravelTimeModel() {
        @Override
        public double travelTimeSeconds(String phaseFamily, double distanceKm, double depthKm) {
            return verticalTravelTimeSeconds(phaseFamily, depthKm)
                    + distanceKm / velocityKmPerSecond(phaseFamily);
        }

        @Override
        public double verticalTravelTimeSeconds(String phaseFamily, double depthKm) {
            return depthKm / velocityKmPerSecond(phaseFamily);
        }

        @Override
        public Optional<PhaseArrival> earliestArrival(
                String phaseFamily,
                double distanceKm,
                double depthKm
        ) {
            return Optional.of(new PhaseArrival(
                    phaseFamily,
                    travelTimeSeconds(phaseFamily, distanceKm, depthKm),
                    0.0,
                    distanceKm,
                    depthKm));
        }

        private double velocityKmPerSecond(String phaseFamily) {
            return "P".equalsIgnoreCase(phaseFamily) ? 4.0 : 2.0;
        }
    };

    @Test
    void scenarioFactoryPrecomputesThroughExplicitNonDefaultDuration() {
        Scenario scenario = new ScenarioLoader().loadDefaultScenario();

        PrecomputedWavefronts wavefronts =
                PrecomputedWavefronts.forScenario(scenario, LINEAR_MODEL, 180.0);

        assertTrue(wavefronts.pCurve().maxTimeSeconds() >= 185.0);
        assertTrue(wavefronts.sCurve().maxTimeSeconds() >= 185.0);
        assertTrue(wavefronts.radiiAt(180.0).hasP());
        assertTrue(wavefronts.radiiAt(180.0).hasS());
    }

    @Test
    void legacyScenarioFactoryRetainsDefaultDuration() {
        Scenario scenario = new ScenarioLoader().loadDefaultScenario();

        PrecomputedWavefronts legacy = PrecomputedWavefronts.forScenario(scenario, LINEAR_MODEL);
        PrecomputedWavefronts explicit = PrecomputedWavefronts.forScenario(
                scenario,
                LINEAR_MODEL,
                TravelTimeCurve.DEFAULT_MAX_TIME_SECONDS);

        assertEquals(explicit.pCurve().sampleCount(), legacy.pCurve().sampleCount());
        assertEquals(explicit.sCurve().sampleCount(), legacy.sCurve().sampleCount());
        assertEquals(explicit.radiiAt(120.0), legacy.radiiAt(120.0));
    }

    @Test
    void durationAwareFactoryRejectsNonPositiveOrNonFiniteDuration() {
        Scenario scenario = new ScenarioLoader().loadDefaultScenario();

        assertThrows(IllegalArgumentException.class,
                () -> PrecomputedWavefronts.forScenario(scenario, LINEAR_MODEL, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> PrecomputedWavefronts.forScenario(scenario, LINEAR_MODEL, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> PrecomputedWavefronts.forScenario(
                        scenario,
                        LINEAR_MODEL,
                        Double.POSITIVE_INFINITY));
    }
}
