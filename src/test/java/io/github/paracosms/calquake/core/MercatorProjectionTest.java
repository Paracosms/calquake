package io.github.paracosms.calquake.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying standard conformal Mercator projection:
 * <ul>
 *   <li>California origin (37.0°N, -119.5°W) projects to (0, 0).</li>
 *   <li>Screen-axis direction: East is +x, North is -y (screen upwards), South is +y, West is -x.</li>
 *   <li>Round-trip inversion preserves original coordinates within 1e-6 degrees.</li>
 *   <li>Viewport mapping strictly preserves 1:1 uniform aspect ratio.</li>
 *   <li>Geodesic circle points match spherical great-circle radius exactly.</li>
 * </ul>
 */
class MercatorProjectionTest {

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
}
