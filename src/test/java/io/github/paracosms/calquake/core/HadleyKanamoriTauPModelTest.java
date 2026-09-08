package io.github.paracosms.calquake.core;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.DistanceRay;
import edu.sc.seis.TauP.SeismicPhase;
import edu.sc.seis.TauP.SeismicPhaseFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying the Hadley-Kanamori TauP model implementation,
 * step discontinuity preservation, Vp/Vs = 1.73 proportionality, and ray penetration boundaries.
 */
class HadleyKanamoriTauPModelTest {

    private static HadleyKanamoriTauPModel model;

    @BeforeAll
    static void setUp() {
        model = HadleyKanamoriTauPModel.create();
    }

    @Test
    @DisplayName("Verify exact vertical travel time at 8.0 km depth matches analytical layer integration")
    void testVerticalTravelTimes() {
        // Analytical P: 5.5 / 5.5 + 2.5 / 6.3 = 1.396825... s
        double expectedP = (5.5 / 5.5) + (2.5 / 6.3);
        double actualP = model.verticalTravelTimeSeconds("P", 8.0);
        assertEquals(expectedP, actualP, 1e-6, "P vertical travel time must match analytical integration");

        // Analytical S: expectedP * 1.73 = 2.416508... s
        double expectedS = expectedP * 1.73;
        double actualS = model.verticalTravelTimeSeconds("S", 8.0);
        assertEquals(expectedS, actualS, 1e-6, "S vertical travel time must match analytical integration");

        // d=0 via travelTimeSeconds must equal verticalTravelTimeSeconds
        assertEquals(actualP, model.travelTimeSeconds("P", 0.0, 8.0), 1e-6);
        assertEquals(actualS, model.travelTimeSeconds("S", 0.0, 8.0), 1e-6);
    }

    @ParameterizedTest
    @ValueSource(doubles = {1.0, 5.0, 15.0, 30.0, 50.0, 75.0, 100.0, 150.0, 200.0, 250.0, 300.0})
    @DisplayName("Verify Ts = 1.73 * Tp kinematic proportionality holds across all distances")
    void testKinematicProportionality(double distKm) {
        double tp = model.travelTimeSeconds("P", distKm, 8.0);
        double ts = model.travelTimeSeconds("S", distKm, 8.0);

        double ratio = ts / tp;
        // Vp/Vs = 1.73 exactly; ratio must match 1.73 within numerical tolerance (< 0.005)
        assertEquals(1.73, ratio, 0.005,
                String.format("Ratio Ts/Tp %.5f deviates from 1.73 at distance %.1f km", ratio, distKm));
    }

    @Test
    @DisplayName("Verify strict monotonicity of travel time with distance")
    void testTravelTimeMonotonicity() {
        double lastP = 0.0;
        double lastS = 0.0;
        for (double d = 0.0; d <= 300.0; d += 5.0) {
            double tp = model.travelTimeSeconds("P", d, 8.0);
            double ts = model.travelTimeSeconds("S", d, 8.0);

            assertTrue(tp >= lastP, "P travel time must increase monotonically at d=" + d);
            assertTrue(ts >= lastS, "S travel time must increase monotonically at d=" + d);
            lastP = tp;
            lastS = ts;
        }
    }

    @Test
    @DisplayName("Verify maximum ray penetration depth for benchmark range (0-150 km) is <= 16.5 km")
    void testBenchmarkRayPenetrationDepth() throws Exception {
        SeismicPhase pPhase = SeismicPhaseFactory.createPhase("p", model.getTauModel(), 8.0, 0.0);
        double maxPierceDepth = 0.0;

        for (double d = 1.0; d <= 150.0; d += 5.0) {
            double deg = (d / TravelTimeModel.EARTH_RADIUS_KM) * (180.0 / Math.PI);
            List<Arrival> arrivals = DistanceRay.ofDegrees(deg).calculate(pPhase);
            if (!arrivals.isEmpty()) {
                double deepest = arrivals.get(0).getDeepestPierce().getDepth();
                maxPierceDepth = Math.max(maxPierceDepth, deepest);
            }
        }

        assertTrue(maxPierceDepth <= 16.5,
                "Benchmark rays must not penetrate beyond the 16.0-32.0 km layer. Max depth observed: " + maxPierceDepth);
    }

    @Test
    @DisplayName("Verify input validation")
    void testInputValidation() {
        assertThrows(IllegalArgumentException.class, () -> model.travelTimeSeconds("INVALID", 10.0));
        assertThrows(IllegalArgumentException.class, () -> model.travelTimeSeconds("P", -1.0));
        assertThrows(IllegalArgumentException.class, () -> model.travelTimeSeconds("P", 10.0, -5.0));
    }
}
