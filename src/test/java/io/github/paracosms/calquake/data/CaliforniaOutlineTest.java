package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.MercatorProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying CaliforniaOutline loading, structural fidelity, and projection.
 */
class CaliforniaOutlineTest {

    @Test
    @DisplayName("Verify default California outline loads with exactly 6 rings and 468 vertices")
    void testLoadDefaultOutline() {
        CaliforniaOutline outline = CaliforniaOutline.loadDefault();
        assertNotNull(outline);
        assertEquals(CaliforniaOutline.EXPECTED_RINGS, outline.ringCount());
        assertEquals(CaliforniaOutline.EXPECTED_VERTICES, outline.totalVertices());

        // Verify Geographic Bounding Box matches metadata
        CaliforniaOutline.GeographicBoundingBox bounds = outline.geographicBounds();
        assertEquals(-124.409591, bounds.minLongitude(), 1e-5);
        assertEquals(32.534156, bounds.minLatitude(), 1e-5);
        assertEquals(-114.139055, bounds.maxLongitude(), 1e-5);
        assertEquals(42.009247, bounds.maxLatitude(), 1e-5);
    }

    @Test
    @DisplayName("Verify projected rings and bounding box in California default Mercator projection")
    void testProjectRingsAndBoundingBox() {
        CaliforniaOutline outline = CaliforniaOutline.loadDefault();
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

    @Test
    @DisplayName("Verify outline rejects invalid ring counts or malformed inputs")
    void testRejectsInvalidOutline() {
        assertThrows(NullPointerException.class, () -> new CaliforniaOutline(null));
        assertThrows(IllegalArgumentException.class, () -> new CaliforniaOutline(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CaliforniaOutline(List.of(
                List.of(new GeoPoint(32, -118), new GeoPoint(33, -118), new GeoPoint(33, -119), new GeoPoint(32, -118))
        )));
    }
}
