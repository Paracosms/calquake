package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.MappedFault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaliforniaFaultCatalogTest {

    @Test
    void testLoadDefaultCatalog() {
        CaliforniaFaultCatalog catalog = CaliforniaFaultCatalog.loadDefault();
        assertNotNull(catalog);
        assertFalse(catalog.faults().isEmpty(), "Fault catalog should not be empty");
        assertTrue(catalog.size() > 1000, "Catalog should have thousands of mapped fault features, found: " + catalog.size());

        boolean foundSanAndreas = false;
        boolean foundHayward = false;
        boolean foundGarlock = false;

        for (MappedFault fault : catalog.faults()) {
            assertNotNull(fault.faultName());
            assertNotNull(fault.polylines());
            assertFalse(fault.polylines().isEmpty(), "Fault must have at least one polyline");

            String nameLower = fault.faultName().toLowerCase();
            if (nameLower.contains("san andreas")) foundSanAndreas = true;
            if (nameLower.contains("hayward")) foundHayward = true;
            if (nameLower.contains("garlock")) foundGarlock = true;

            for (List<GeoPoint> polyline : fault.polylines()) {
                assertTrue(polyline.size() >= 2, "Polyline must have at least 2 points");
                for (GeoPoint pt : polyline) {
                    assertTrue(Double.isFinite(pt.latitude()));
                    assertTrue(Double.isFinite(pt.longitude()));
                    // California bounding envelope roughly 30 to 45 N, -126 to -113 W
                    assertTrue(pt.latitude() >= 30.0 && pt.latitude() <= 45.0, "Latitude out of California bounds: " + pt.latitude());
                    assertTrue(pt.longitude() >= -126.0 && pt.longitude() <= -113.0, "Longitude out of California bounds: " + pt.longitude());
                }
            }
        }

        assertTrue(foundSanAndreas, "San Andreas fault must be present in catalog");
        assertTrue(foundHayward, "Hayward fault must be present in catalog");
        assertTrue(foundGarlock, "Garlock fault must be present in catalog");
    }
}
