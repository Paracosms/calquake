package io.github.paracosms.calquake.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Bssa14GroundMotionTest {
    private final Bssa14GroundMotion model = new Bssa14GroundMotion();

    @ParameterizedTest
    @CsvSource({
            "6.5, 0.0, 10.0, 760.0, 2.8166654798165385",
            "7.1, 0.0, 0.0, 260.0, 4.342563367678909",
            "6.7, 90.0, 50.0, 320.0, 2.058040847445018"
    })
    void matchesPinnedIndependentNaturalLogPgvFixtures(
            double magnitude, double rake, double rjb, double vs30, double expectedLnPgv) {
        Bssa14GroundMotion.Prediction prediction = model.predictPgv(magnitude, rake, rjb, vs30);
        assertEquals(expectedLnPgv, prediction.naturalLogPgvCmPerSecond(), 1.0e-12);
        assertEquals(Math.exp(expectedLnPgv), prediction.pgvCmPerSecond(), 1.0e-12);
    }
}
