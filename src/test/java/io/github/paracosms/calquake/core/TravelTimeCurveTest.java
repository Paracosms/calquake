package io.github.paracosms.calquake.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying Stage 4 travel-time curve precomputation and inversion:
 * <ul>
 *   <li>Computed domain covers the full 120-second replay.</li>
 *   <li>Pre-surface arrival absence is represented explicitly.</li>
 *   <li>Inversion accuracy against direct TauP is &le; 0.01 s, including across branch transitions.</li>
 *   <li>Strict monotonicity of inverted radius with elapsed time.</li>
 * </ul>
 */
class TravelTimeCurveTest {

    private static HadleyKanamoriTauPModel model;
    private static TravelTimeCurve pCurve;
    private static TravelTimeCurve sCurve;
    private static PrecomputedWavefronts wavefronts;

    private static final double DEPTH_KM = 8.0;

    @BeforeAll
    static void setUp() {
        model = HadleyKanamoriTauPModel.create();
        pCurve = TravelTimeCurve.precompute("P", model, DEPTH_KM, 120.0);
        sCurve = TravelTimeCurve.precompute("S", model, DEPTH_KM, 120.0);
        wavefronts = new PrecomputedWavefronts(pCurve, sCurve);
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

    @Test
    @DisplayName("Verify dense scan of P inversion accuracy across all 120 seconds <= 0.01 s")
    void testDensePInversionScan() {
        double vertP = pCurve.verticalTimeSeconds();
        for (double t = vertP + 0.1; t <= 120.0; t += 0.5) {
            OptionalDouble rOpt = pCurve.invertRadiusKm(t);
            assertTrue(rOpt.isPresent());
            double r = rOpt.getAsDouble();

            double directTime = model.travelTimeSeconds("P", r, DEPTH_KM);
            double error = Math.abs(directTime - t);
            assertTrue(error <= 0.01,
                    String.format("Dense P error %.5f s > 0.01 s at t=%.2f s (r=%.2f km)", error, t, r));
        }
    }

    @Test
    @DisplayName("Verify dense scan of S inversion accuracy across all 120 seconds <= 0.01 s")
    void testDenseSInversionScan() {
        double vertS = sCurve.verticalTimeSeconds();
        for (double t = vertS + 0.1; t <= 120.0; t += 0.5) {
            OptionalDouble rOpt = sCurve.invertRadiusKm(t);
            assertTrue(rOpt.isPresent());
            double r = rOpt.getAsDouble();

            double directTime = model.travelTimeSeconds("S", r, DEPTH_KM);
            double error = Math.abs(directTime - t);
            assertTrue(error <= 0.01,
                    String.format("Dense S error %.5f s > 0.01 s at t=%.2f s (r=%.2f km)", error, t, r));
        }
    }

    @Test
    @DisplayName("Verify strict monotonicity of inverted radius with elapsed time")
    void testRadiusMonotonicity() {
        double lastP = -1.0;
        double lastS = -1.0;

        for (double t = 0.0; t <= 120.0; t += 0.1) {
            OptionalDouble pRad = pCurve.invertRadiusKm(t);
            if (pRad.isPresent()) {
                double r = pRad.getAsDouble();
                assertTrue(r >= lastP, "P radius must strictly increase with time at t=" + t);
                lastP = r;
            }

            OptionalDouble sRad = sCurve.invertRadiusKm(t);
            if (sRad.isPresent()) {
                double r = sRad.getAsDouble();
                assertTrue(r >= lastS, "S radius must strictly increase with time at t=" + t);
                lastS = r;
            }
        }
    }

    @Test
    @DisplayName("Verify invalid inputs rejected")
    void testInvalidInputs() {
        assertTrue(pCurve.invertRadiusKm(Double.NaN).isEmpty());
        assertTrue(pCurve.invertRadiusKm(-1.0).isEmpty());

        assertThrows(NullPointerException.class, () ->
                TravelTimeCurve.precompute(null, model, 8.0, 120.0));
        assertThrows(NullPointerException.class, () ->
                TravelTimeCurve.precompute("P", null, 8.0, 120.0));
        assertThrows(IllegalArgumentException.class, () ->
                TravelTimeCurve.precompute("P", model, -1.0, 120.0));
        assertThrows(IllegalArgumentException.class, () ->
                TravelTimeCurve.precompute("P", model, 8.0, 0.0));
    }
}
