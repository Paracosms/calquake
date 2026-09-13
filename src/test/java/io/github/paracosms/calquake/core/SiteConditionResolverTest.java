package io.github.paracosms.calquake.core;

import io.github.paracosms.calquake.data.CaliforniaVs30Grid;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteConditionResolverTest {

    @Test
    void testResolutionPrecedence() {
        SiteConditionResolver resolver = SiteConditionResolver.defaultResolver();

        // 1. Explicit MEASURED site condition must be preserved
        SiteCondition explicitMeasured = new SiteCondition(450.0, SiteConditionProvenance.MEASURED, "borehole-101");
        SiteCondition resolvedMeasured = resolver.resolve(new GeoPoint(34.0522, -118.2437), Optional.of(explicitMeasured));
        assertEquals(450.0, resolvedMeasured.vs30MetersPerSecond());
        assertEquals(SiteConditionProvenance.MEASURED, resolvedMeasured.provenance());
        assertEquals("borehole-101", resolvedMeasured.sourceId());

        // 2. Explicit MAPPED_PROXY site condition must be preserved
        SiteCondition explicitProxy = new SiteCondition(520.0, SiteConditionProvenance.MAPPED_PROXY, "imported-cgs");
        SiteCondition resolvedProxy = resolver.resolve(new GeoPoint(34.0522, -118.2437), Optional.of(explicitProxy));
        assertEquals(520.0, resolvedProxy.vs30MetersPerSecond());
        assertEquals(SiteConditionProvenance.MAPPED_PROXY, resolvedProxy.provenance());
        assertEquals("imported-cgs", resolvedProxy.sourceId());

        // 3. DEFAULT condition is overridden by raster sample
        SiteCondition defaultCondition = SiteCondition.defaultRock();
        SiteCondition resolvedRaster = resolver.resolve(new GeoPoint(34.0522, -118.2437), Optional.of(defaultCondition));
        assertNotEquals(760.0, resolvedRaster.vs30MetersPerSecond());
        assertEquals(SiteConditionProvenance.MAPPED_PROXY, resolvedRaster.provenance());
        assertEquals(CaliforniaVs30Grid.DATASET_ID, resolvedRaster.sourceId());

        // 4. Missing condition is resolved to raster sample
        SiteCondition resolvedMissing = resolver.resolve(new GeoPoint(37.7946, -122.3999), Optional.empty());
        assertEquals(SiteConditionProvenance.MAPPED_PROXY, resolvedMissing.provenance());
        assertEquals(239.7, resolvedMissing.vs30MetersPerSecond(), 2.0);

        // 5. Offshore point falls back to default rock 760 m/s with DEFAULT provenance
        SiteCondition resolvedOcean = resolver.resolve(new GeoPoint(35.0, -123.0), Optional.empty());
        assertEquals(760.0, resolvedOcean.vs30MetersPerSecond());
        assertEquals(SiteConditionProvenance.DEFAULT, resolvedOcean.provenance());
    }

    @Test
    void testCustomScenarioResolvesRealisticVs30() {
        EventSource event = new EventSource(
                "custom-ev-1", "calquake", "Test Custom Event",
                java.time.Instant.parse("2026-09-10T00:00:00Z"),
                6.5, "mw", new GeoPoint(35.5, -118.5), 10.0,
                Optional.empty(), Optional.empty(), java.util.Map.of()
        );

        List<SimulationSite> requestedSites = List.of(
                new SimulationSite("la", "Los Angeles", new GeoPoint(34.0522, -118.2437)),
                new SimulationSite("sf", "San Francisco", new GeoPoint(37.7946, -122.3999)),
                new SimulationSite("ridgecrest", "Ridgecrest", new GeoPoint(35.7695, -117.5993))
        );

        ScenarioInputs inputs = ScenarioInputs.forCustomScenario(
                event, requestedSites, new HadleyKanamoriTauPModel());

        // Verify scientific configuration contains vs30 dataset and checksum
        assertNotNull(inputs.scientificConfiguration().versionIds().get("vs30DatasetId"));
        assertNotNull(inputs.scientificConfiguration().versionIds().get("vs30Checksum"));

        // Verify sites have distinct realistic Vs30 values, not all 760 m/s
        SimulationSite laSite = inputs.requireSite("la");
        assertTrue(laSite.siteCondition().isPresent());
        assertEquals(SiteConditionProvenance.MAPPED_PROXY, laSite.siteCondition().get().provenance());
        assertNotEquals(760.0, laSite.siteCondition().get().vs30MetersPerSecond());

        SimulationSite sfSite = inputs.requireSite("sf");
        assertTrue(sfSite.siteCondition().isPresent());
        assertEquals(SiteConditionProvenance.MAPPED_PROXY, sfSite.siteCondition().get().provenance());
        assertNotEquals(760.0, sfSite.siteCondition().get().vs30MetersPerSecond());

        // LA and SF should have different Vs30 values
        assertNotEquals(laSite.siteCondition().get().vs30MetersPerSecond(),
                sfSite.siteCondition().get().vs30MetersPerSecond());
    }
}
