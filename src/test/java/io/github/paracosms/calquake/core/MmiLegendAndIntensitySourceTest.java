package io.github.paracosms.calquake.core;

import io.github.paracosms.calquake.data.ScenarioLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying Stage 4 MMI Legend, display rounding, IntensitySource,
 * and the frozen 5-location peak intensity reference values.
 */
class MmiLegendAndIntensitySourceTest {

    private static Scenario scenario;
    private static IntensitySource intensitySource;
    private static ReplayEngine engine;

    @BeforeAll
    static void setUp() {
        ScenarioLoader loader = new ScenarioLoader();
        scenario = loader.loadDefaultScenario();
        intensitySource = IntensitySource.scenarioPeak();
        HadleyKanamoriTauPModel model = HadleyKanamoriTauPModel.create();
        engine = ReplayEngine.create(scenario, model, intensitySource);
    }

    @ParameterizedTest
    @CsvSource({
            "7.200, 7.2",
            "6.900, 6.9",
            "3.840, 3.8",
            "3.850, 3.9",
            "3.100, 3.1",
            "1.000, 1.0",
            "9.990, 10.0"
    })
    @DisplayName("Verify documented 1-decimal display rounding")
    void testDisplayRounding(double source, double expectedRounded) {
        assertEquals(expectedRounded, MmiLegend.roundToDisplay(source), 1e-9);
    }

    @ParameterizedTest
    @CsvSource({
            "1.0, I, #fbfcff",
            "1.49, I, #fbfcff",
            "1.5, II-III, #acdbff",
            "3.1, II-III, #acdbff",
            "3.49, II-III, #acdbff",
            "3.5, IV, #7ffffa",
            "3.8, IV, #7ffffa",
            "3.9, IV, #7ffffa",
            "4.49, IV, #7ffffa",
            "4.5, V, #81ff8a",
            "5.49, V, #81ff8a",
            "5.5, VI, #fffa00",
            "6.49, VI, #fffa00",
            "6.5, VII, #ffc400",
            "6.9, VII, #ffc400",
            "7.2, VII, #ffc400",
            "7.49, VII, #ffc400",
            "7.5, VIII, #ff8500",
            "8.49, VIII, #ff8500",
            "8.5, IX, #fb0000",
            "9.49, IX, #fb0000",
            "9.5, X+, #c80000",
            "10.0, X+, #c80000"
    })
    @DisplayName("Verify Worden et al. legend bin classification and colors")
    void testLegendBinsAndBoundaries(double mmi, String expectedRoman, String expectedColor) {
        MmiLegend.MmiBin bin = MmiLegend.findBin(mmi);
        assertEquals(expectedRoman, bin.roman());
        assertEquals(expectedColor, bin.colorHex());
    }

    @Test
    @DisplayName("Verify N/A bin classification for null, NaN, and out-of-coverage")
    void testNaBin() {
        MmiLegend.MmiBin binNull = MmiLegend.findBin(null);
        assertEquals("N/A", binNull.roman());
        assertEquals("#808080", binNull.colorHex());
        assertEquals("Outside coverage", binNull.shakingDescriptor());

        MmiLegend.MmiBin binNan = MmiLegend.findBin(Double.NaN);
        assertEquals("N/A", binNan.roman());

        MmiLegend.MmiBin binNegative = MmiLegend.findBin(-0.5);
        assertEquals("N/A", binNegative.roman());
    }

    @Test
    @DisplayName("Verify intensity values are time-invariant across replay frames")
    void testIntensityTimeInvariance() {
        double[] testTimes = {0.0, 1.0, 5.0, 10.0, 30.0, 60.0, 120.0};
        for (double t : testTimes) {
            FrameState frame = engine.frameAt(t);
            assertEquals(5, frame.locationIntensities().size());

            LocationIntensityState ridgecrest = frame.locationIntensities().get(0);
            assertEquals("Ridgecrest", ridgecrest.city());
            assertEquals(7.2, ridgecrest.mmiSourceDecimal(), 1e-9);
            assertEquals("VII", ridgecrest.mmiRoman());
            assertEquals("#ffc400", ridgecrest.colorHex());
        }
    }

    @Test
    @DisplayName("Verify city MMI rating reveal occurs strictly upon S-wave arrival")
    void testSWaveArrivalMmiRevealTiming() {
        // At t = 0.0 s, no location has received S wave
        FrameState f0 = engine.frameAt(0.0);
        for (LocationIntensityState loc : f0.locationIntensities()) {
            assertFalse(loc.sWaveArrived(), loc.city() + " must not have S-wave arrived at t=0");
            assertFalse(loc.isRevealed(), loc.city() + " must not be revealed at t=0");
        }

        // Check arrival times are ordered by distance from Ridgecrest epicenter:
        // Ridgecrest (~16.8 km, ~5.8s), Trona (~23 km, ~7.7s), Bakersfield (~138 km, ~40s),
        // Los Angeles (~207 km, ~59s), Fresno (~228 km, ~64s)
        LocationIntensityState rc = f0.locationIntensities().get(0);
        LocationIntensityState trona = f0.locationIntensities().get(1);
        LocationIntensityState bakersfield = f0.locationIntensities().get(2);
        LocationIntensityState la = f0.locationIntensities().get(3);
        LocationIntensityState fresno = f0.locationIntensities().get(4);

        double tRc = rc.sArrivalTimeSeconds();
        double tTrona = trona.sArrivalTimeSeconds();
        double tBakersfield = bakersfield.sArrivalTimeSeconds();
        double tLa = la.sArrivalTimeSeconds();
        double tFresno = fresno.sArrivalTimeSeconds();

        assertTrue(tRc < tTrona);
        assertTrue(tTrona < tBakersfield);
        assertTrue(tBakersfield < tLa);
        assertTrue(tLa < tFresno);

        // Before Ridgecrest S arrival: none revealed
        FrameState fBeforeRc = engine.frameAt(tRc - 0.1);
        assertFalse(fBeforeRc.locationIntensities().get(0).isRevealed());

        // After Ridgecrest S arrival: only Ridgecrest revealed
        FrameState fAfterRc = engine.frameAt(tRc + 0.1);
        assertTrue(fAfterRc.locationIntensities().get(0).isRevealed(), "Ridgecrest revealed");
        assertFalse(fAfterRc.locationIntensities().get(1).isRevealed(), "Trona not revealed");

        // After Trona S arrival: Ridgecrest & Trona revealed
        FrameState fAfterTrona = engine.frameAt(tTrona + 0.1);
        assertTrue(fAfterTrona.locationIntensities().get(0).isRevealed());
        assertTrue(fAfterTrona.locationIntensities().get(1).isRevealed());
        assertFalse(fAfterTrona.locationIntensities().get(2).isRevealed());

        // After Bakersfield S arrival: Ridgecrest, Trona, and Bakersfield revealed
        FrameState fAfterBakersfield = engine.frameAt(tBakersfield + 0.1);
        assertTrue(fAfterBakersfield.locationIntensities().get(2).isRevealed());
        assertFalse(fAfterBakersfield.locationIntensities().get(3).isRevealed());

        // Before Fresno S arrival: Fresno not yet revealed
        FrameState fBeforeFresno = engine.frameAt(tFresno - 0.1);
        assertFalse(fBeforeFresno.locationIntensities().get(4).isRevealed(), "Fresno not revealed before its arrival");

        // After Fresno S arrival: all 5 locations revealed
        FrameState fAfterFresno = engine.frameAt(tFresno + 0.1);
        for (LocationIntensityState loc : fAfterFresno.locationIntensities()) {
            assertTrue(loc.isRevealed(), loc.city() + " must be revealed after its arrival");
        }
    }
}
