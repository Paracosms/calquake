package io.github.paracosms.calquake.core;

import io.github.paracosms.calquake.data.ScenarioLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuptureDistanceTest {
    @Test
    void rjbIsZeroInsideProjectionAndUsesNearestEdgeOutside() {
        RuptureGeometry square = new RuptureGeometry(List.of(List.of(
                new GeoPoint(0.0, 0.0), new GeoPoint(0.0, 1.0),
                new GeoPoint(1.0, 1.0), new GeoPoint(1.0, 0.0), new GeoPoint(0.0, 0.0))),
                0.0, 10.0, 0.0, 45.0, "analytic", "", false);
        assertEquals(0.0, square.rjbKm(new GeoPoint(0.5, 0.5)), 1.0e-9);
        assertEquals(GeoPoint.EARTH_RADIUS_KM * Math.PI / 180.0,
                square.rjbKm(new GeoPoint(2.0, 0.5)), 0.02);
    }

    @Test
    void bundledRupturesProduceFiniteDistinctRjb() {
        ScenarioLoader loader = new ScenarioLoader();
        ScenarioInputs ridgecrest = loader.loadScenarioInputs("Ridgecrest");
        ScenarioInputs northridge = loader.loadScenarioInputs("Northridge");
        double ridgecrestRjb = ridgecrest.event().ruptureGeometry().orElseThrow()
                .rjbKm(ridgecrest.sites().getFirst().coordinates());
        double northridgeRjb = northridge.event().ruptureGeometry().orElseThrow()
                .rjbKm(northridge.sites().getFirst().coordinates());
        assertTrue(Double.isFinite(ridgecrestRjb) && ridgecrestRjb >= 0.0);
        assertTrue(Double.isFinite(northridgeRjb) && northridgeRjb >= 0.0);
        assertTrue(Math.abs(ridgecrestRjb - northridgeRjb) > 1.0);
    }
}
