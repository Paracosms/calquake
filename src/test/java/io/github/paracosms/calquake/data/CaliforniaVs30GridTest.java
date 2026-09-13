package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.Vs30Sample;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaliforniaVs30GridTest {

    @Test
    void testLoadDefaultGrid() {
        CaliforniaVs30Grid grid = CaliforniaVs30Grid.loadDefault();
        assertNotNull(grid);
        assertEquals(1320, grid.width());
        assertEquals(1260, grid.height());
        assertEquals(-125.0, grid.west(), 1e-6);
        assertEquals(42.5, grid.north(), 1e-6);
        assertNotNull(grid.sha256());
        assertFalse(grid.sha256().isBlank());
        assertEquals("usgs-global-vs30-mosaic-2025", grid.datasetId());

        // Golden coordinates from manifest
        // Ridgecrest: (35.7695, -117.5993)
        Optional<Vs30Sample> ridgecrest = grid.sample(new GeoPoint(35.7695, -117.5993));
        assertTrue(ridgecrest.isPresent(), "Ridgecrest sample should be present");
        assertEquals(277.0, ridgecrest.get().vs30MetersPerSecond(), 2.0);

        // Los Angeles: (34.0522, -118.2437)
        Optional<Vs30Sample> la = grid.sample(new GeoPoint(34.0522, -118.2437));
        assertTrue(la.isPresent(), "LA sample should be present");
        assertEquals(304.5, la.get().vs30MetersPerSecond(), 2.0);

        // San Francisco: (37.7946, -122.3999)
        Optional<Vs30Sample> sf = grid.sample(new GeoPoint(37.7946, -122.3999));
        assertTrue(sf.isPresent(), "SF sample should be present");
        assertEquals(239.7, sf.get().vs30MetersPerSecond(), 2.0);

        // Bakersfield: (35.3733, -119.0187)
        Optional<Vs30Sample> bakersfield = grid.sample(new GeoPoint(35.3733, -119.0187));
        assertTrue(bakersfield.isPresent(), "Bakersfield sample should be present");
        assertEquals(246.0, bakersfield.get().vs30MetersPerSecond(), 2.0);

        // Pacific ocean offshore: (35.0, -123.0)
        Optional<Vs30Sample> ocean = grid.sample(new GeoPoint(35.0, -123.0));
        assertFalse(ocean.isPresent(), "Offshore ocean point should return empty sample");

        // Far outside grid bounds: (50.0, -130.0)
        Optional<Vs30Sample> outside = grid.sample(new GeoPoint(50.0, -130.0));
        assertFalse(outside.isPresent(), "Outside bounds should return empty sample");
    }
}
