package io.github.paracosms.calquake.regression;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.DistanceRay;
import edu.sc.seis.TauP.SeismicPhase;
import edu.sc.seis.TauP.SeismicPhaseFactory;
import edu.sc.seis.TauP.TauModel;
import edu.sc.seis.TauP.TauModelLoader;
import edu.sc.seis.TauP.VelocityModel;
import io.github.paracosms.calquake.core.HadleyKanamoriTauPModel;
import io.github.paracosms.calquake.core.PrecomputedWavefronts;
import io.github.paracosms.calquake.core.TravelTimeCurve;
import io.github.paracosms.calquake.core.TravelTimeModel;
import io.github.paracosms.calquake.core.WavefrontRadii;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class TravelTimeRegressionTest {

    private static HadleyKanamoriTauPModel model;
    private static TravelTimeCurve pCurve;
    private static TravelTimeCurve sCurve;
    private static PrecomputedWavefronts wavefronts;

    private static final double DEPTH_KM = 8.0;
    private static final double R = 6371.0;
    private static final double V = 6.0; // km/s
    private static final double H = 8.0; // km

    @BeforeAll
    static void setUp() {
        model = HadleyKanamoriTauPModel.create();
        pCurve = TravelTimeCurve.precompute("P", model, DEPTH_KM, 120.0);
        sCurve = TravelTimeCurve.precompute("S", model, DEPTH_KM, 120.0);
        wavefronts = new PrecomputedWavefronts(pCurve, sCurve);
    }

    private static TauModel createHomogeneousModel(double velocity, double radius) throws Exception {
        String nd = String.format("""
                0.0 %.6f %.6f 2.7
                %.1f %.6f %.6f 2.7
                """, velocity, velocity / 1.73, radius, velocity, velocity / 1.73);
        VelocityModel vMod = VelocityModel.readNDFile(new StringReader(nd), "homogeneous");
        vMod.setRadiusOfEarth(radius);
        return TauModelLoader.createTauModel(vMod);
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
    @DisplayName("Verify precomputed curves cover full 120-second replay duration")
    void testCurveCoverage() {
        assertTrue(pCurve.maxTimeSeconds() >= 125.0,
                "P curve must cover at least 125 s (covers: " + pCurve.maxTimeSeconds() + " s)");
        assertTrue(sCurve.maxTimeSeconds() >= 125.0,
                "S curve must cover at least 125 s (covers: " + sCurve.maxTimeSeconds() + " s)");

        // P front travels > 900 km; S front travels > 500 km in 120 s
        assertTrue(pCurve.maxDistanceKm() >= 900.0,
                "P distance domain must exceed 900 km (reaches: " + pCurve.maxDistanceKm() + " km)");
        assertTrue(sCurve.maxDistanceKm() >= 500.0,
                "S distance domain must exceed 500 km (reaches: " + sCurve.maxDistanceKm() + " km)");

        double[][] frozenRadii = {
                {10.0, 59.92, 33.24},
                {30.0, 196.70, 106.25}
        };
        for (double[] control : frozenRadii) {
            WavefrontRadii radii = wavefronts.radiiAt(control[0]);
            assertEquals(control[1], radii.pRadiusKm(), 0.1,
                    "P radius must match the frozen propagation control at t=" + control[0]);
            assertEquals(control[2], radii.sRadiusKm(), 0.1,
                    "S radius must match the frozen propagation control at t=" + control[0]);
        }
    }

    @Test
    @DisplayName("Verify pre-surface arrival absence: no front exists before vertical arrival")
    void testPreVerticalArrivalAbsence() {
        double vertP = pCurve.verticalTimeSeconds();
        double vertS = sCurve.verticalTimeSeconds();

        // Analytical vertical delay at 8 km:
        // P: 5.5/5.5 + 2.5/6.3 = 1.396825 s
        assertEquals(1.3968, vertP, 0.001);
        // S: 1.396825 * 1.73 = 2.416508 s
        assertEquals(2.4165, vertS, 0.001);

        // Before vertical arrival, invertRadiusKm must return empty
        assertTrue(pCurve.invertRadiusKm(0.0).isEmpty());
        assertTrue(pCurve.invertRadiusKm(0.5).isEmpty());
        assertTrue(pCurve.invertRadiusKm(1.0).isEmpty());
        assertTrue(pCurve.invertRadiusKm(vertP - 0.001).isEmpty());

        assertTrue(sCurve.invertRadiusKm(0.0).isEmpty());
        assertTrue(sCurve.invertRadiusKm(1.5).isEmpty());
        assertTrue(sCurve.invertRadiusKm(vertS - 0.001).isEmpty());

        // At vertical arrival, radius is exactly 0.0 km
        OptionalDouble atVertP = pCurve.invertRadiusKm(vertP);
        assertTrue(atVertP.isPresent());
        assertEquals(0.0, atVertP.getAsDouble(), 1e-6);

        OptionalDouble atVertS = sCurve.invertRadiusKm(vertS);
        assertTrue(atVertS.isPresent());
        assertEquals(0.0, atVertS.getAsDouble(), 1e-6);

        // Radii helper via PrecomputedWavefronts
        WavefrontRadii radiiBeforeP = wavefronts.radiiAt(1.0);
        assertFalse(radiiBeforeP.hasP());
        assertFalse(radiiBeforeP.hasS());

        WavefrontRadii radiiBetween = wavefronts.radiiAt(2.0);
        assertTrue(radiiBetween.hasP());
        assertFalse(radiiBetween.hasS());
        assertTrue(radiiBetween.pRadiusKm() > 0.0);

        WavefrontRadii radiiAfterS = wavefronts.radiiAt(5.0);
        assertTrue(radiiAfterS.hasP());
        assertTrue(radiiAfterS.hasS());
        assertTrue(radiiAfterS.pRadiusKm() > radiiAfterS.sRadiusKm());
    }

    @ParameterizedTest
    @ValueSource(doubles = {
            1.40, 1.50, 2.0, 3.0, 5.0, 10.0, 15.0, 18.0, 18.1, 18.5, 20.0,
            25.0, 30.0, 45.0, 60.0, 75.0, 90.0, 105.0, 120.0
    })
    @DisplayName("Verify P inversion accuracy against direct TauP is <= 0.01 s")
    void testPInversionAccuracy(double elapsedSec) {
        OptionalDouble radiusOpt = pCurve.invertRadiusKm(elapsedSec);
        assertTrue(radiusOpt.isPresent());
        double radiusKm = radiusOpt.getAsDouble();

        double directTauPTime = model.travelTimeSeconds("P", radiusKm, DEPTH_KM);
        double errorSec = Math.abs(directTauPTime - elapsedSec);

        assertTrue(errorSec <= 0.01,
                String.format("P inversion error %.5f s exceeds 0.01 s at t=%.2f s (radius=%.2f km, directTauP=%.5f s)",
                        errorSec, elapsedSec, radiusKm, directTauPTime));
    }

    @ParameterizedTest
    @ValueSource(doubles = {
            2.42, 2.50, 3.0, 5.0, 10.0, 15.0, 20.0, 30.0, 31.3, 31.5, 32.0,
            40.0, 50.0, 60.0, 75.0, 90.0, 105.0, 120.0
    })
    @DisplayName("Verify S inversion accuracy against direct TauP is <= 0.01 s")
    void testSInversionAccuracy(double elapsedSec) {
        OptionalDouble radiusOpt = sCurve.invertRadiusKm(elapsedSec);
        assertTrue(radiusOpt.isPresent());
        double radiusKm = radiusOpt.getAsDouble();

        double directTauPTime = model.travelTimeSeconds("S", radiusKm, DEPTH_KM);
        double errorSec = Math.abs(directTauPTime - elapsedSec);

        assertTrue(errorSec <= 0.01,
                String.format("S inversion error %.5f s exceeds 0.01 s at t=%.2f s (radius=%.2f km, directTauP=%.5f s)",
                        errorSec, elapsedSec, radiusKm, directTauPTime));
    }

    @ParameterizedTest
    @ValueSource(doubles = {1.0, 100.0, 300.0})
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
}
