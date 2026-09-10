package io.github.paracosms.calquake.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class GeoPointTest {

    @ParameterizedTest
    @CsvSource({
            "90.0, 180.0",
            "-90.0, -180.0",
            "0.0, 0.0",
            "35.628542, -117.663992"
    })
    void testBoundaryValidCoordinates(double lat, double lon) {
        assertDoesNotThrow(() -> new GeoPoint(lat, lon));
    }

    @ParameterizedTest
    @CsvSource({
            "90.0001, 0.0",
            "-90.0001, 0.0",
            "0.0, 180.0001",
            "0.0, -180.0001",
            "100.0, 50.0",
            "-120.0, 0.0"
    })
    void testOutOfBoundsCoordinates(double lat, double lon) {
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(lat, lon));
    }

    @Test
    void testNanAndInfinityCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0.0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(Double.POSITIVE_INFINITY, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0.0, Double.NEGATIVE_INFINITY));
    }

}
