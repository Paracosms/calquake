package io.github.paracosms.calquake.core;

import io.github.paracosms.calquake.data.ScenarioLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying Stage 4 Exit Gate criteria:
 * <ul>
 *   <li>Determinism: identical inputs and times produce identical {@link FrameState}.</li>
 *   <li>Second tiny synthetic scenario: alters event ID, origin time, coordinates, depth (15 km),
 *       and reference locations through domain contracts, confirming zero event-specific hardcoding.</li>
 * </ul>
 */
class ReplayDeterminismAndSyntheticScenarioTest {

    @Test
    @DisplayName("Verify determinism: identical inputs and time yield identical FrameState")
    void testFrameStateDeterminism() {
        ScenarioLoader loader = new ScenarioLoader();
        Scenario scenario = loader.loadDefaultScenario();
        HadleyKanamoriTauPModel model = HadleyKanamoriTauPModel.create();
        ReplayEngine engine = ReplayEngine.create(scenario, model);

        double[] times = {0.0, 1.0, 1.396825, 2.0, 2.4165, 5.0, 15.0, 30.0, 60.0, 120.0};

        for (double t : times) {
            FrameState f1 = engine.frameAt(scenario, t);
            FrameState f2 = engine.frameAt(scenario, t);

            assertEquals(f1, f2, "FrameState must be strictly equal for identical input at t=" + t);
        }
    }

    @Test
    @DisplayName("Verify second tiny synthetic scenario with distinct location, depth, and time")
    void testSyntheticScenario() {
        // Synthetic M6.5 earthquake in Northern California / East Bay
        String syntheticId = "nc73999999";
        Instant syntheticOrigin = Instant.parse("2026-06-01T12:00:00.000Z");
        GeoPoint syntheticEpicenter = new GeoPoint(37.8000, -122.2500); // Oakland / Hayward Fault
        double syntheticDepthKm = 15.0; // 15 km depth vs Ridgecrest's 8 km
        double syntheticMag = 6.5;

        EarthquakeEvent syntheticEvent = new EarthquakeEvent(
                syntheticId, "nc", "Synthetic Hayward Fault M6.5",
                syntheticOrigin, syntheticEpicenter, syntheticDepthKm, syntheticMag, "mw", "https://example.org/synthetic"
        );

        // Synthetic reference locations
        GeoPoint sfPoint = new GeoPoint(37.7749, -122.4194);
        ReferenceLocation sf = new ReferenceLocation(
                "San Francisco", "San Francisco city", "0667000", "02411786", "25",
                sfPoint,
                new ReferenceLocation.SampledGridNode(sfPoint, 0.1),
                new ReferenceLocation.PeakIntensity(8.1, 8.1, "VIII", "Severe", "Moderate/heavy", "#ff8500"),
                null
        );

        GeoPoint sjPoint = new GeoPoint(37.3382, -121.8863);
        ReferenceLocation sj = new ReferenceLocation(
                "San Jose", "San Jose city", "0668000", "02411788", "25",
                sjPoint,
                new ReferenceLocation.SampledGridNode(sjPoint, 0.2),
                new ReferenceLocation.PeakIntensity(5.8, 5.8, "VI", "Strong", "Light", "#fffa00"),
                null
        );

        GeoPoint sacPoint = new GeoPoint(38.5816, -121.4944);
        ReferenceLocation sac = new ReferenceLocation(
                "Sacramento", "Sacramento city", "0664000", "02411780", "25",
                sacPoint,
                new ReferenceLocation.SampledGridNode(sacPoint, 0.3),
                new ReferenceLocation.PeakIntensity(4.2, 4.2, "IV", "Light", "None", "#7ffffa"),
                null
        );

        Scenario syntheticScenario = new Scenario(syntheticEvent, List.of(sf, sj, sac));

        // Create an engine using only the generic scenario contract.
        HadleyKanamoriTauPModel model = HadleyKanamoriTauPModel.create();
        ReplayEngine engine = ReplayEngine.create(syntheticScenario, model);

        FrameState initFrame = engine.frameAt(syntheticScenario, 0.0);
        assertEquals(0.0, initFrame.elapsedSeconds(), 1e-9);
        assertEquals(syntheticEpicenter, initFrame.epicenter());
        assertEquals(3, initFrame.locationIntensities().size());
        assertEquals("San Francisco", initFrame.locationIntensities().get(0).city());
        assertEquals(8.1, initFrame.locationIntensities().get(0).mmiSourceDecimal(), 1e-9);
        assertEquals("VIII", initFrame.locationIntensities().get(0).mmiRoman());

        // Verify vertical travel time for 15.0 km depth in Hadley-Kanamori model:
        // Layer 1 (0 - 5.5 km): 5.5 / 5.5 = 1.000 s
        // Layer 2 (5.5 - 15.0 km): 9.5 / 6.3 = 1.5079365 s
        // Expected vertical P: ~2.5079 s
        // Expected vertical S: ~2.5079 * 1.73 = ~4.3387 s
        double expectedVertP = (5.5 / 5.5) + (9.5 / 6.3);
        double expectedVertS = expectedVertP * 1.73;

        TravelTimeCurve pCurve = engine.wavefronts().pCurve();
        TravelTimeCurve sCurve = engine.wavefronts().sCurve();
        assertEquals(expectedVertP, pCurve.verticalTimeSeconds(), 1e-4);
        assertEquals(expectedVertS, sCurve.verticalTimeSeconds(), 1e-4);

        // At t = 2.0 s (before synthetic vertical P arrival), no P or S front
        WavefrontRadii radii2s = engine.wavefronts().radiiAt(2.0);
        assertFalse(radii2s.hasP());
        assertFalse(radii2s.hasS());

        // At t = 3.5 s (after P vertical arrival but before S vertical arrival)
        WavefrontRadii radii35s = engine.wavefronts().radiiAt(3.5);
        assertTrue(radii35s.hasP());
        assertFalse(radii35s.hasS());
        assertTrue(radii35s.pRadiusKm() > 0.0);

        // At t = 6.0 s (both P and S have arrived)
        WavefrontRadii radii6s = engine.wavefronts().radiiAt(6.0);
        assertTrue(radii6s.hasP());
        assertTrue(radii6s.hasS());
        assertTrue(radii6s.pRadiusKm() > radii6s.sRadiusKm());
    }
}
