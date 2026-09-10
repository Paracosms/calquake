package io.github.paracosms.calquake.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Worden2012GmiceTest {
    private final Worden2012Gmice gmice = new Worden2012Gmice();

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
}
