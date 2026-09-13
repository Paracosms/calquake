package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.FaultSection;
import tools.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaultSectionCatalogTest {

    @Test
    void testBundledCatalogLoadsAndValidatesIntegrity() {
        FaultSectionCatalog catalog = FaultSectionCatalog.loadDefault();
        assertNotNull(catalog);
        assertEquals(664, catalog.sections().size());
        assertEquals(FaultSectionCatalog.CATALOG_ID, catalog.datasetId());
        assertEquals("80e695cc85ad4341df208f90c4e7e534a11f17aac28af6c6133b0cdfa9e8453b", catalog.sha256());

        for (FaultSection section : catalog.sections()) {
            assertNotNull(section.sectionName());
            assertFalse(section.sectionName().isBlank());
            assertTrue(section.trace().size() >= 2);
            assertTrue(section.dipDegrees() > 0.0 && section.dipDegrees() <= 90.0);
            assertNotNull(section.dipDirection());
            assertFalse(section.dipDirection().isBlank());
            assertTrue(section.rakeDegrees() >= -180.0 && section.rakeDegrees() <= 180.0);
            assertTrue(section.upperDepthKm() >= 0.0);
            assertTrue(section.lowerDepthKm() > section.upperDepthKm());
        }
    }

    @Test
    void testCatalogRejectsCorruptOrMissingResource() {
        assertThrows(IllegalStateException.class,
                () -> FaultSectionCatalog.loadFromResource("/nonexistent-resource.geojson"));

        assertThrows(IllegalArgumentException.class,
                () -> FaultSectionCatalog.fromJsonNode(JsonNodeFactory.instance.objectNode(), "hash"));

        assertThrows(IllegalArgumentException.class,
                () -> new FaultSectionCatalog(List.of(), "hash"));
    }
}
