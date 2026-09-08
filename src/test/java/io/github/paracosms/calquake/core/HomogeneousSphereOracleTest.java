package io.github.paracosms.calquake.core;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.DistanceRay;
import edu.sc.seis.TauP.SeismicPhase;
import edu.sc.seis.TauP.SeismicPhaseFactory;
import edu.sc.seis.TauP.TauModel;
import edu.sc.seis.TauP.TauModelLoader;
import edu.sc.seis.TauP.VelocityModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies TauP against the homogeneous-sphere analytic oracle to <= 0.001 s error,
 * zero distance, and vertical delay.
 * <p>
 * Oracle formula: T = sqrt(h^2 + 4*R*(R-h)*sin^2(Delta / 2)) / v
 * where Delta = d / R (radians), R = 6,371 km, h = source depth, v = constant velocity.
 */
class HomogeneousSphereOracleTest {

    private static final double R = 6371.0;
    private static final double V = 6.0; // km/s
    private static final double H = 8.0; // km

    private static TauModel createHomogeneousModel(double velocity, double radius) throws Exception {
        String nd = String.format("""
                0.0 %.6f %.6f 2.7
                %.1f %.6f %.6f 2.7
                """, velocity, velocity / 1.73, radius, velocity, velocity / 1.73);
        VelocityModel vMod = VelocityModel.readNDFile(new StringReader(nd), "homogeneous");
        vMod.setRadiusOfEarth(radius);
        return TauModelLoader.createTauModel(vMod);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0, 5.0, 10.0, 25.0, 50.0, 100.0, 150.0, 200.0, 250.0, 300.0})
    @DisplayName("Verify TauP agrees with homogeneous oracle to <= 0.001 s across 0-300 km")
    void testHomogeneousSphereOracleAccuracy(double distKm) throws Exception {
        TauModel tMod = createHomogeneousModel(V, R);
        SeismicPhase pPhase = SeismicPhaseFactory.createPhase("p", tMod, H, 0.0);

        double delta = distKm / R; // central angle in radians
        double chord = Math.sqrt(H * H + 4.0 * R * (R - H) * Math.sin(delta / 2.0) * Math.sin(delta / 2.0));
        double tOracle = chord / V;

        double deg = Math.toDegrees(delta);
        List<Arrival> arrivals = DistanceRay.ofDegrees(deg).calculate(pPhase);
        assertFalse(arrivals.isEmpty(), "Arrival list should not be empty for dist=" + distKm);

        double tTauP = arrivals.get(0).getTime();
        double absError = Math.abs(tTauP - tOracle);

        // Stage 3 exit criterion: <= 0.001 s
        assertTrue(absError <= 0.001,
                String.format("TauP error %.6f s exceeds 0.001 s at dist=%.1f km (Oracle=%.6f, TauP=%.6f)",
                        absError, distKm, tOracle, tTauP));
    }

    @Test
    @DisplayName("Verify zero distance vertical delay exactly equals h / v")
    void testZeroDistanceVerticalDelay() throws Exception {
        TauModel tMod = createHomogeneousModel(V, R);
        SeismicPhase pPhase = SeismicPhaseFactory.createPhase("p", tMod, H, 0.0);

        List<Arrival> arrivals = DistanceRay.ofDegrees(0.0).calculate(pPhase);
        assertFalse(arrivals.isEmpty(), "Zero distance arrival should exist");

        double tTauP = arrivals.get(0).getTime();
        double tExpected = H / V; // 8.0 / 6.0 = 1.333333... s

        assertEquals(tExpected, tTauP, 1e-4, "Vertical travel time must match h / v");
    }

    @Test
    @DisplayName("Verify oracle consistency with varied depth and velocity parameters")
    void testVariedDepthAndVelocity() throws Exception {
        double[] depths = {2.0, 15.0, 30.0};
        double[] velocities = {4.5, 6.5, 8.0};

        for (double h : depths) {
            for (double v : velocities) {
                TauModel tMod = createHomogeneousModel(v, R);
                SeismicPhase pPhase = SeismicPhaseFactory.createPhase("p", tMod, h, 0.0);

                double distKm = 75.0;
                double delta = distKm / R;
                double chord = Math.sqrt(h * h + 4.0 * R * (R - h) * Math.sin(delta / 2.0) * Math.sin(delta / 2.0));
                double tOracle = chord / v;

                List<Arrival> arrivals = DistanceRay.ofDegrees(Math.toDegrees(delta)).calculate(pPhase);
                assertFalse(arrivals.isEmpty());
                double tTauP = arrivals.get(0).getTime();

                assertEquals(tOracle, tTauP, 0.001,
                        String.format("Failed for h=%.1f, v=%.1f at d=%.1f km", h, v, distKm));
            }
        }
    }
}
