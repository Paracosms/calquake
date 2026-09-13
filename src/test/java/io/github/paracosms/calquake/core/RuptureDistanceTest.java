package io.github.paracosms.calquake.core;

import io.github.paracosms.calquake.data.ScenarioLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void correctedPlanarRupturePreservesDimensionsAndContainsHypocenter() {
        GeoPoint epicenter = new GeoPoint(35.0, -118.0);
        double depthKm = 10.0;
        double mag = 6.5;
        Mechanism mech60 = new Mechanism(0.0, 45.0, 60.0, "STRIKE_SLIP", "test");

        RuptureGeometry rup60 = RuptureGeometryProvider.generatePlanar(epicenter, depthKm, mag, mech60);
        assertNotNull(rup60);

        // Wells & Coppersmith: W = 10^(-1.01 + 0.32 * 6.5) = 10^1.07
        double expectedW = Math.pow(10.0, -1.01 + 0.32 * mag);
        double deltaRad = Math.toRadians(60.0);
        double expectedVertical = expectedW * Math.sin(deltaRad);

        assertEquals(expectedVertical, rup60.bottomDepthKm() - rup60.topDepthKm(), 1.0e-6);
        assertTrue(rup60.topDepthKm() >= 0.0);
        assertTrue(depthKm >= rup60.topDepthKm() && depthKm <= rup60.bottomDepthKm(),
                "Hypocenter depth must be contained in [topDepthKm, bottomDepthKm]");

        // Dipping rupture projection is a polygon enclosing epicenter
        assertEquals(0.0, rup60.rjbKm(epicenter), 1.0e-6, "Epicenter must lie within dipping surface projection");

        // Exterior point has positive Rjb
        GeoPoint exterior = new GeoPoint(36.0, -118.0);
        double exteriorRjb = rup60.rjbKm(exterior);
        assertTrue(exteriorRjb > 50.0, "Exterior site ~111 km away must have positive Rjb");

        // Shallow vertical fault test (h < W/2 so top clamps to 0)
        Mechanism mech90 = new Mechanism(0.0, 0.0, 90.0, "STRIKE_SLIP", "test");
        RuptureGeometry rup90 = RuptureGeometryProvider.generatePlanar(epicenter, 3.0, mag, mech90);
        assertEquals(0.0, rup90.topDepthKm(), 1.0e-9, "Top depth must clamp to 0 without going negative");
        assertEquals(expectedW, rup90.bottomDepthKm(), 1.0e-6, "Bottom depth must preserve full vertical extent W");
        assertEquals(90.0, rup90.dipDegrees(), 1.0e-9);
        assertEquals(2, rup90.surfaceProjectionParts().getFirst().size(), "Vertical surface projection is a line segment");
        assertEquals(0.0, rup90.rjbKm(epicenter), 1.0e-6);
    }
}
