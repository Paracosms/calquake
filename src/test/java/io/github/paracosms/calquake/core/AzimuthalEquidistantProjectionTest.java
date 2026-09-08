package io.github.paracosms.calquake.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying Stage 4 Azimuthal Equidistant Projection:
 * <ul>
 *   <li>Epicenter projects to (0, 0).</li>
 *   <li>Screen-axis direction: East is +x, North is -y, South is +y, West is -x.</li>
 *   <li>Radial distance is preserved exactly: hypot(x, y) = d.</li>
 *   <li>Round-trip spherical inversion preserves original coordinates.</li>
 *   <li>Viewport mapping preserves 1:1 uniform aspect ratio for circular wavefronts.</li>
 * </ul>
 */
class AzimuthalEquidistantProjectionTest {

    // Frozen Ridgecrest CI epicenter
    private static final GeoPoint EPICENTER = new GeoPoint(35.7695, -117.5993333);
    private static AzimuthalEquidistantProjection proj;

    @BeforeAll
    static void setUp() {
        proj = AzimuthalEquidistantProjection.centeredAt(EPICENTER);
    }

    @Test
    @DisplayName("Verify epicenter projects to (0.0, 0.0)")
    void testEpicenterProjectsToOrigin() {
        AzimuthalEquidistantProjection.ProjectedPoint p = proj.project(EPICENTER);
        assertEquals(0.0, p.xKm(), 1e-9);
        assertEquals(0.0, p.yKm(), 1e-9);
        assertEquals(0.0, p.distanceFromOriginKm(), 1e-9);

        GeoPoint unprojected = proj.unproject(p);
        assertEquals(EPICENTER.latitude(), unprojected.latitude(), 1e-6);
        assertEquals(EPICENTER.longitude(), unprojected.longitude(), 1e-6);
    }

    @Test
    @DisplayName("Verify cardinal directions and screen-axis orientation")
    void testCardinalDirectionsAndScreenAxes() {
        double dKm = 100.0;
        double r = AzimuthalEquidistantProjection.EARTH_RADIUS_KM;
        double deltaRad = dKm / r;
        double deltaDeg = Math.toDegrees(deltaRad);

        // Due North: lat + deltaDeg, lon = lon0
        GeoPoint northPt = new GeoPoint(EPICENTER.latitude() + deltaDeg, EPICENTER.longitude());
        AzimuthalEquidistantProjection.ProjectedPoint pNorth = proj.project(northPt);
        assertEquals(0.0, pNorth.xKm(), 0.05, "North should have x = 0");
        assertEquals(-dKm, pNorth.yKm(), 0.05, "North should have y = -d (screen upwards)");
        assertEquals(dKm, pNorth.distanceFromOriginKm(), 0.01);

        // Due South: lat - deltaDeg, lon = lon0
        GeoPoint southPt = new GeoPoint(EPICENTER.latitude() - deltaDeg, EPICENTER.longitude());
        AzimuthalEquidistantProjection.ProjectedPoint pSouth = proj.project(southPt);
        assertEquals(0.0, pSouth.xKm(), 0.05, "South should have x = 0");
        assertEquals(dKm, pSouth.yKm(), 0.05, "South should have y = +d (screen downwards)");
        assertEquals(dKm, pSouth.distanceFromOriginKm(), 0.01);

        // Due East: azimuth = 90 deg
        // Using unproject to find exact east point at 100 km: (x = 100, y = 0)
        GeoPoint eastPt = proj.unproject(dKm, 0.0);
        AzimuthalEquidistantProjection.ProjectedPoint pEast = proj.project(eastPt);
        assertEquals(dKm, pEast.xKm(), 0.01, "East should have x = +d");
        assertEquals(0.0, pEast.yKm(), 0.01, "East should have y = 0");

        // Due West: azimuth = 270 deg (x = -100, y = 0)
        GeoPoint westPt = proj.unproject(-dKm, 0.0);
        AzimuthalEquidistantProjection.ProjectedPoint pWest = proj.project(westPt);
        assertEquals(-dKm, pWest.xKm(), 0.01, "West should have x = -d");
        assertEquals(0.0, pWest.yKm(), 0.01, "West should have y = 0");
    }

    @ParameterizedTest
    @CsvSource({
            "Ridgecrest, 35.628542, -117.663992",
            "Trona, 35.815821, -117.347348",
            "Bakersfield, 35.353593, -119.036921",
            "Los Angeles, 34.019394, -118.410825",
            "Fresno, 36.782684, -119.793359"
    })
    @DisplayName("Verify reference locations project accurately and invert round-trip")
    void testReferenceLocationsRoundTrip(String city, double lat, double lon) {
        GeoPoint original = new GeoPoint(lat, lon);
        AzimuthalEquidistantProjection.ProjectedPoint projected = proj.project(original);

        // Distance from origin must match great-circle distance on 6,371 km sphere
        double phi1 = Math.toRadians(EPICENTER.latitude());
        double lam1 = Math.toRadians(EPICENTER.longitude());
        double phi2 = Math.toRadians(lat);
        double lam2 = Math.toRadians(lon);
        double cosDelta = Math.sin(phi1) * Math.sin(phi2) + Math.cos(phi1) * Math.cos(phi2) * Math.cos(lam2 - lam1);
        double expectedDistKm = AzimuthalEquidistantProjection.EARTH_RADIUS_KM * Math.acos(Math.max(-1.0, Math.min(1.0, cosDelta)));

        assertEquals(expectedDistKm, projected.distanceFromOriginKm(), 1e-4,
                city + " projected radial distance must equal spherical great-circle distance");

        // Invert and check round-trip coordinates
        GeoPoint inverted = proj.unproject(projected);
        assertEquals(lat, inverted.latitude(), 1e-5, city + " latitude round-trip error");
        assertEquals(lon, inverted.longitude(), 1e-5, city + " longitude round-trip error");
    }

    @Test
    @DisplayName("Verify viewport transform strictly preserves 1:1 aspect ratio")
    void testViewportTransformPreservesAspectRatio() {
        AzimuthalEquidistantProjection.BoundingBox bounds =
                new AzimuthalEquidistantProjection.BoundingBox(-400.0, -500.0, 400.0, 500.0);

        double screenW = 1280.0;
        double screenH = 800.0;
        double margin = 20.0;

        AzimuthalEquidistantProjection.ViewportTransform vt =
                proj.createViewportTransform(bounds, screenW, screenH, margin);

        // Check scaling of horizontal and vertical distances
        AzimuthalEquidistantProjection.ScreenPoint pt0 = vt.toScreen(0.0, 0.0);
        AzimuthalEquidistantProjection.ScreenPoint ptX = vt.toScreen(100.0, 0.0);
        AzimuthalEquidistantProjection.ScreenPoint ptY = vt.toScreen(0.0, 100.0);

        double deltaPxX = Math.abs(ptX.xPx() - pt0.xPx());
        double deltaPxY = Math.abs(ptY.yPx() - pt0.yPx());

        assertEquals(deltaPxX, deltaPxY, 1e-9,
                "Horizontal and vertical pixel scales must be identical to preserve 1:1 aspect ratio");

        // Radius scaling
        double rKm = 75.0;
        double rPx = vt.toScreenRadius(rKm);
        assertEquals(rKm * vt.scalePxPerKm(), rPx, 1e-9);
    }
}
