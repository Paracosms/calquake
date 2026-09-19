package io.github.paracosms.calquake.regression;

import io.github.paracosms.calquake.core.Bssa14GroundMotion;
import io.github.paracosms.calquake.core.DomainStatus;
import io.github.paracosms.calquake.core.EmpiricalEnvelopeModel;
import io.github.paracosms.calquake.core.MmiLegend;
import io.github.paracosms.calquake.core.Worden2012Gmice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class GroundMotionAndIntensityRegressionTest {

    private final Bssa14GroundMotion bssa14 = new Bssa14GroundMotion();
    private final Worden2012Gmice gmice = new Worden2012Gmice();
    private final EmpiricalEnvelopeModel envelopeModel = new EmpiricalEnvelopeModel();

    @ParameterizedTest
    @CsvSource({
            "6.5, 0.0, 10.0, 760.0, 2.8166654798165385",
            "7.1, 0.0, 0.0, 260.0, 4.342563367678909",
            "6.7, 90.0, 50.0, 320.0, 2.058040847445018"
    })
    void matchesPinnedIndependentNaturalLogPgvFixtures(
            double magnitude, double rake, double rjb, double vs30, double expectedLnPgv) {
        Bssa14GroundMotion.Prediction prediction = bssa14.predictPgv(magnitude, rake, rjb, vs30);
        assertEquals(expectedLnPgv, prediction.naturalLogPgvCmPerSecond(), 1.0e-12);
        assertEquals(Math.exp(expectedLnPgv), prediction.pgvCmPerSecond(), 1.0e-12);
    }

    @Test
    void usesPgvCentimetersPerSecondAndPinnedBreakpoint() {
        assertEquals(3.78, gmice.fromPgvCmPerSecond(1.0), 1.0e-12);
        double breakpointPgv = Math.pow(10.0, 0.53);
        assertEquals(3.78 + 1.47 * 0.53, gmice.fromPgvCmPerSecond(breakpointPgv), 1.0e-12);
        assertEquals(2.89 + 3.16, gmice.fromPgvCmPerSecond(10.0), 1.0e-12);
    }

    @Test
    void zeroAndInvalidAmplitudeNeverEvaluateALogarithm() {
        assertTrue(gmice.tryFromPgvCmPerSecond(0.0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> gmice.fromPgvCmPerSecond(0.0));
        assertThrows(IllegalArgumentException.class, () -> gmice.fromPgvCmPerSecond(Double.NaN));
    }

    @Test
    void envelopeHasOnsetRisePeakDecayAndNonnegativeCombination() {
        EmpiricalEnvelopeModel.Parameters parameters = envelopeModel.parameters(6.5, 50.0, 760.0);
        double p = 10.0;
        double s = 18.0;
        assertEquals(0.0, envelopeModel.rawEnvelope(p, p, s, parameters), 0.0);
        double pRise = envelopeModel.rawEnvelope(p + parameters.p().riseSeconds(), p, s, parameters);
        assertTrue(pRise > 0.0);
        double atS = envelopeModel.rawEnvelope(s, p, s, parameters);
        double afterSRise = envelopeModel.rawEnvelope(s + parameters.s().riseSeconds(), p, s, parameters);
        assertTrue(afterSRise > atS);
        assertTrue(envelopeModel.rawEnvelope(envelopeModel.supportEnd(p, s, parameters), p, s, parameters) >= 0.0);
    }

    @Test
    void retainsOutOfCalibrationDomainMetadataWithoutSuppressingValues() {
        EmpiricalEnvelopeModel.Parameters parameters = envelopeModel.parameters(7.1, 250.0, 300.0);
        assertEquals(DomainStatus.OUT_OF_DOMAIN, parameters.domainStatus());
        assertTrue(envelopeModel.rawEnvelope(50.0, 10.0, 40.0, parameters) > 0.0);
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
}
