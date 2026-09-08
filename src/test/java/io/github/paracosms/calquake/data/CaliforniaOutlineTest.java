package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.AzimuthalEquidistantProjection;
import io.github.paracosms.calquake.core.GeoPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying CaliforniaOutline loading, structural fidelity, and projection.
 */
class CaliforniaOutlineTest {

    // Frozen Ridgecrest CI epicenter
    private static final GeoPoint EPICENTER = new GeoPoint(35.7695, -117.5993333);

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
    @DisplayName("Verify projected rings and bounding box centered at Ridgecrest epicenter")
    void testProjectRingsAndBoundingBox() {
        CaliforniaOutline outline = CaliforniaOutline.loadDefault();
        AzimuthalEquidistantProjection proj = AzimuthalEquidistantProjection.centeredAt(EPICENTER);

        List<List<AzimuthalEquidistantProjection.ProjectedPoint>> projectedRings = outline.projectRings(proj);
        assertEquals(6, projectedRings.size());

        AzimuthalEquidistantProjection.BoundingBox bbox = outline.computeProjectedBoundingBox(proj);
        assertNotNull(bbox);

        // Expected bounds derived from haversine projection of vertices:
        // minX ≈ -576.48 km, maxX ≈ 317.98 km
        // minY ≈ -712.03 km, maxY ≈ 359.65 km
        assertEquals(-576.48, bbox.minXKm(), 0.5);
        assertEquals(317.98, bbox.maxXKm(), 0.5);
        assertEquals(-712.03, bbox.minYKm(), 0.5);
        assertEquals(359.65, bbox.maxYKm(), 0.5);

        assertTrue(bbox.widthKm() > 890.0 && bbox.widthKm() < 900.0);
        assertTrue(bbox.heightKm() > 1065.0 && bbox.heightKm() < 1075.0);
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
