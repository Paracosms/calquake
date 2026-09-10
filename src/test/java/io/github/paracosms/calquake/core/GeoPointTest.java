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

    @Test
    void testDistanceKmTo() {
        GeoPoint p1 = new GeoPoint(35.7695, -117.5993333); // Ridgecrest epicenter
        assertEquals(0.0, p1.distanceKmTo(p1), 1e-9);

        // Same coordinate slightly different float precision
        GeoPoint p1Copy = new GeoPoint(35.7695, -117.5993333);
        assertEquals(0.0, p1.distanceKmTo(p1Copy), 1e-9);

        // North pole to South pole: pi * R = 20015.087 km
        GeoPoint northPole = new GeoPoint(90.0, 0.0);
        GeoPoint southPole = new GeoPoint(-90.0, 0.0);
        assertEquals(Math.PI * GeoPoint.EARTH_RADIUS_KM, northPole.distanceKmTo(southPole), 1e-4);

        // Equator quarter circle: (pi / 2) * R = 10007.543 km
        GeoPoint eq1 = new GeoPoint(0.0, 0.0);
        GeoPoint eq2 = new GeoPoint(0.0, 90.0);
        assertEquals((Math.PI / 2.0) * GeoPoint.EARTH_RADIUS_KM, eq1.distanceKmTo(eq2), 1e-4);

        // Ridgecrest to Los Angeles (~208 km)
        GeoPoint la = new GeoPoint(34.019394, -118.410825);
        double distLa = p1.distanceKmTo(la);
        assertTrue(distLa > 200.0 && distLa < 220.0, "Distance to LA should be ~208 km, got: " + distLa);

        assertThrows(NullPointerException.class, () -> p1.distanceKmTo(null));
    }
}
