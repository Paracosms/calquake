package io.github.paracosms.calquake.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class GeoPointTest {

    @Test
    void testValidCoordinates() {
        GeoPoint point = new GeoPoint(35.7695, -117.5993333);
        assertEquals(35.7695, point.latitude(), 1e-9);
        assertEquals(-117.5993333, point.longitude(), 1e-9);
    }

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

    @Test
    void testEqualityAndToString() {
        GeoPoint p1 = new GeoPoint(35.7695, -117.5993333);
        GeoPoint p2 = new GeoPoint(35.7695, -117.5993333);
        GeoPoint p3 = new GeoPoint(35.7696, -117.5993333);

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
        assertNotEquals(p1, p3);
        assertTrue(p1.toString().contains("35.7695"));
    }
}
