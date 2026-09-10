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
}
