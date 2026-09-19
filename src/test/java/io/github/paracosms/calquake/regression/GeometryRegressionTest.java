package io.github.paracosms.calquake.regression;

import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.Mechanism;
import io.github.paracosms.calquake.core.MercatorProjection;
import io.github.paracosms.calquake.core.RuptureGeometry;
import io.github.paracosms.calquake.core.RuptureGeometryProvider;
import io.github.paracosms.calquake.core.ScenarioInputs;
import io.github.paracosms.calquake.data.CaliforniaOutline;
import io.github.paracosms.calquake.data.ScenarioLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeometryRegressionTest {

    private static MercatorProjection proj;

    @BeforeAll
    static void setUp() {
        proj = MercatorProjection.californiaDefault();
    }

    @Test
    @DisplayName("Verify California default origin projects to (0.0, 0.0)")
    void testOriginProjectsToZeroZero() {
        GeoPoint origin = proj.origin();
        assertEquals(37.0, origin.latitude());
        assertEquals(-119.5, origin.longitude());

        MercatorProjection.ProjectedPoint p = proj.project(origin);
        assertEquals(0.0, p.xKm(), 1e-9);
        assertEquals(0.0, p.yKm(), 1e-9);
        assertEquals(0.0, p.distanceFromOriginKm(), 1e-9);

        GeoPoint unprojected = proj.unproject(p);
        assertEquals(origin.latitude(), unprojected.latitude(), 1e-6);
        assertEquals(origin.longitude(), unprojected.longitude(), 1e-6);
    }

    @Test
    @DisplayName("Verify cardinal directions and screen-axis orientation")
    void testCardinalDirectionsAndScreenAxes() {
        GeoPoint origin = proj.origin();

        // Due North: lat + 1 deg, lon = lon0
        GeoPoint northPt = new GeoPoint(origin.latitude() + 1.0, origin.longitude());
        MercatorProjection.ProjectedPoint pNorth = proj.project(northPt);
        assertEquals(0.0, pNorth.xKm(), 1e-9, "North should have x = 0");
        assertTrue(pNorth.yKm() < 0.0, "North should have y < 0 (screen upwards)");

        // Due South: lat - 1 deg, lon = lon0
        GeoPoint southPt = new GeoPoint(origin.latitude() - 1.0, origin.longitude());
        MercatorProjection.ProjectedPoint pSouth = proj.project(southPt);
        assertEquals(0.0, pSouth.xKm(), 1e-9, "South should have x = 0");
        assertTrue(pSouth.yKm() > 0.0, "South should have y > 0 (screen downwards)");

        // Due East: lat = lat0, lon + 1 deg
        GeoPoint eastPt = new GeoPoint(origin.latitude(), origin.longitude() + 1.0);
        MercatorProjection.ProjectedPoint pEast = proj.project(eastPt);
        assertTrue(pEast.xKm() > 0.0, "East should have x > 0");
        assertEquals(0.0, pEast.yKm(), 1e-9, "East along parallel should have y = 0");

        // Due West: lat = lat0, lon - 1 deg
        GeoPoint westPt = new GeoPoint(origin.latitude(), origin.longitude() - 1.0);
        MercatorProjection.ProjectedPoint pWest = proj.project(westPt);
        assertTrue(pWest.xKm() < 0.0, "West should have x < 0");
        assertEquals(0.0, pWest.yKm(), 1e-9, "West along parallel should have y = 0");
    }

    @ParameterizedTest
    @CsvSource({
            "Ridgecrest, 35.628542, -117.663992",
            "Trona, 35.815821, -117.347348",
            "Bakersfield, 35.353593, -119.036921",
            "Los Angeles, 34.019394, -118.410825",
            "Fresno, 36.782684, -119.793359",
            "San Francisco, 37.7749, -122.4194",
            "Sacramento, 38.5816, -121.4944"
    })
    @DisplayName("Verify reference locations project accurately and invert round-trip")
    void testReferenceLocationsRoundTrip(String city, double lat, double lon) {
        GeoPoint original = new GeoPoint(lat, lon);
        MercatorProjection.ProjectedPoint projected = proj.project(original);

        GeoPoint inverted = proj.unproject(projected);
        assertEquals(lat, inverted.latitude(), 1e-6, city + " latitude round-trip error");
        assertEquals(lon, inverted.longitude(), 1e-6, city + " longitude round-trip error");
    }

    @Test
    @DisplayName("Verify geodesic circle points match physical spherical radius")
    void testGeodesicCircleGeneration() {
        GeoPoint epicenter = new GeoPoint(35.7695, -117.5993333); // Ridgecrest
        double radiusKm = 150.0;
        int numPoints = 48;

        List<MercatorProjection.ProjectedPoint> circle =
                proj.geodesicCirclePoints(epicenter, radiusKm, numPoints);
        assertEquals(numPoints, circle.size());

        for (MercatorProjection.ProjectedPoint pt : circle) {
            GeoPoint unprojected = proj.unproject(pt);
            double actualDistKm = epicenter.distanceKmTo(unprojected);
            assertEquals(radiusKm, actualDistKm, 0.05,
                    "Geodesic perimeter point must equal spherical distance to epicenter");
        }

        assertTrue(proj.geodesicCirclePoints(epicenter, 0.0, 48).isEmpty());
        assertTrue(proj.geodesicCirclePoints(epicenter, -10.0, 48).isEmpty());
    }

    @Test
    @DisplayName("Verify viewport transform strictly preserves 1:1 aspect ratio")
    void testViewportTransformPreservesAspectRatio() {
        MercatorProjection.BoundingBox bounds =
                new MercatorProjection.BoundingBox(-500.0, -700.0, 500.0, 700.0);

        double screenW = 890.0;
        double screenH = 719.0;
        double margin = 24.0;

        MercatorProjection.ViewportTransform vt =
                proj.createViewportTransform(bounds, screenW, screenH, margin);

        MercatorProjection.ScreenPoint pt0 = vt.toScreen(0.0, 0.0);
        MercatorProjection.ScreenPoint ptX = vt.toScreen(100.0, 0.0);
        MercatorProjection.ScreenPoint ptY = vt.toScreen(0.0, 100.0);

        double deltaPxX = Math.abs(ptX.xPx() - pt0.xPx());
        double deltaPxY = Math.abs(ptY.yPx() - pt0.yPx());

        assertEquals(deltaPxX, deltaPxY, 1e-9,
                "Horizontal and vertical pixel scales must be identical to preserve 1:1 aspect ratio");

        MercatorProjection.ScreenPoint boundsCenter =
                vt.toScreen(bounds.centerXKm(), bounds.centerYKm());
        assertEquals(screenW / 2.0, boundsCenter.xPx(), 1e-9,
                "Projected bounds must remain horizontally centered");
        assertEquals(screenH / 2.0, boundsCenter.yPx(), 1e-9,
                "Projected bounds must remain vertically centered");
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

    @Test
    @DisplayName("Verify projected rings and bounding box in California default Mercator projection")
    void testProjectRingsAndBoundingBox() {
        CaliforniaOutline outline = CaliforniaOutline.loadDefault();
        assertNotNull(outline);
        assertEquals(CaliforniaOutline.EXPECTED_RINGS, outline.ringCount());
        assertEquals(CaliforniaOutline.EXPECTED_VERTICES, outline.totalVertices());

        MercatorProjection proj = MercatorProjection.californiaDefault();

        List<List<MercatorProjection.ProjectedPoint>> projectedRings = outline.projectRings(proj);
        assertEquals(6, projectedRings.size());

        MercatorProjection.BoundingBox bbox = outline.computeProjectedBoundingBox(proj);
        assertNotNull(bbox);

        // Expected bounds derived from Mercator projection of vertices:
        // minX ≈ -545.92 km, maxX ≈ 596.11 km
        // minY ≈ -722.45 km, maxY ≈ 604.80 km
        assertEquals(-545.92, bbox.minXKm(), 0.5);
        assertEquals(596.11, bbox.maxXKm(), 0.5);
        assertEquals(-722.45, bbox.minYKm(), 0.5);
        assertEquals(604.80, bbox.maxYKm(), 0.5);

        assertTrue(bbox.widthKm() > 1140.0 && bbox.widthKm() < 1145.0);
        assertTrue(bbox.heightKm() > 1325.0 && bbox.heightKm() < 1330.0);
    }
}
