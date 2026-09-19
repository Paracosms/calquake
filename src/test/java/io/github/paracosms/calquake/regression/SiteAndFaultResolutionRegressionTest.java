package io.github.paracosms.calquake.regression;

import io.github.paracosms.calquake.core.*;
import io.github.paracosms.calquake.data.CaliforniaFaultCatalog;
import io.github.paracosms.calquake.data.CaliforniaVs30Grid;
import io.github.paracosms.calquake.data.FaultSectionCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SiteAndFaultResolutionRegressionTest {

    private static final double EXPECTED_SF_VS30 = 239.7;

    private static FaultSection createVerticalSection(long id, String name) {
        return new FaultSection(
                id, name,
                List.of(new GeoPoint(35.0, -118.0), new GeoPoint(35.2, -118.0)),
                90.0, "Vertical", 0.0, 0.0, 15.0
        );
    }

    private static FaultSection createDippingSection(long id, String name, double dip, String dipDir, double upper, double lower) {
        return new FaultSection(
                id, name,
                List.of(new GeoPoint(35.0, -118.0), new GeoPoint(35.2, -118.0)),
                dip, dipDir, 90.0, upper, lower
        );
    }

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
        assertEquals(EXPECTED_SF_VS30, resolvedMissing.vs30MetersPerSecond(), 2.0);

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

    @ParameterizedTest
    @CsvSource({
            "35.1, -118.0, 7.5, 0.0, true",       // On fault plane and within depth range
            "35.1, -118.0, 20.0, 5.0, true",      // On trace horizontally, 5 km below lower depth (15 km)
            "35.3, -118.0, 7.5, 11.12, false",    // Beyond north endpoint (~11.1 km past endpoint -> exceeds 10 km)
            "35.5, -118.0, 7.5, 33.3, false"      // Far away
    })
    void testBoundedPatchDistancesAndDepthMismatch(double lat, double lon, double depthKm, double expectedDistApprox, boolean isEligible) {
        FaultSection section = createVerticalSection(10, "Vertical-10");
        FaultSectionCatalog catalog = new FaultSectionCatalog(List.of(section), "test-sha");
        AutomaticFaultResolver resolver = new AutomaticFaultResolver(catalog);

        FaultResolution res = resolver.resolve(new GeoPoint(lat, lon), depthKm, 6.5);
        assertNotNull(res);
        if (isEligible) {
            assertEquals(FaultResolution.Mode.FAULT_INFORMED, res.mode());
            assertEquals(expectedDistApprox, res.nearestDistanceKm(), 0.5);
            assertEquals(10L, res.candidateSectionId().orElseThrow());
        } else {
            assertEquals(FaultResolution.Mode.GENERIC, res.mode());
            assertTrue(res.candidateSectionId().isEmpty());
        }
    }

    @Test
    void testCandidateSelectionAmbiguityAndTieBreaking() {
        // Section 100 at lon -118.02 (~1.8 km from -118.0)
        // Section 50 at lon -118.03 (~2.7 km from -118.0)
        // Distance difference is < 1.0 km, so comparable set contains both.
        // Lowest sectionId (50) must be selected, and isAmbiguous must be true.
        FaultSection sec100 = new FaultSection(100, "Fault-100",
                List.of(new GeoPoint(35.0, -118.02), new GeoPoint(35.2, -118.02)),
                90.0, "Vertical", 0.0, 0.0, 15.0);
        FaultSection sec50 = new FaultSection(50, "Fault-50",
                List.of(new GeoPoint(35.0, -118.03), new GeoPoint(35.2, -118.03)),
                90.0, "Vertical", 0.0, 0.0, 15.0);

        // Catalog order 1: [sec100, sec50]
        FaultSectionCatalog cat1 = new FaultSectionCatalog(List.of(sec100, sec50), "sha1");
        AutomaticFaultResolver res1 = new AutomaticFaultResolver(cat1);
        FaultResolution r1 = res1.resolve(new GeoPoint(35.1, -118.0), 5.0, 6.0);

        assertEquals(FaultResolution.Mode.FAULT_INFORMED, r1.mode());
        assertEquals(50L, r1.candidateSectionId().orElseThrow(), "Lowest sectionId within 1 km tolerance must win");
        assertTrue(r1.isAmbiguous(), "Candidate within 1 km must trigger ambiguity");

        // Catalog order 2: [sec50, sec100] - order must not change selection
        FaultSectionCatalog cat2 = new FaultSectionCatalog(List.of(sec50, sec100), "sha2");
        AutomaticFaultResolver res2 = new AutomaticFaultResolver(cat2);
        FaultResolution r2 = res2.resolve(new GeoPoint(35.1, -118.0), 5.0, 6.0);
        assertEquals(50L, r2.candidateSectionId().orElseThrow());
        assertTrue(r2.isAmbiguous());
        assertEquals(r1.nearestDistanceKm(), r2.nearestDistanceKm(), 1.0e-6);

        // Clear winner: Section 100 at lon -118.01 (~0.9 km), Section 50 at lon -118.05 (~4.5 km)
        FaultSection secWinner = new FaultSection(100, "Fault-100",
                List.of(new GeoPoint(35.0, -118.01), new GeoPoint(35.2, -118.01)),
                90.0, "Vertical", 0.0, 0.0, 15.0);
        FaultSectionCatalog catClear = new FaultSectionCatalog(List.of(secWinner, sec50), "sha3");
        FaultResolution rClear = new AutomaticFaultResolver(catClear).resolve(new GeoPoint(35.1, -118.0), 5.0, 6.0);
        assertEquals(100L, rClear.candidateSectionId().orElseThrow());
        assertFalse(rClear.isAmbiguous(), "Clear winner > 1 km closer must not be ambiguous");
    }

    @Test
    void testDeriveLocalStrikeAndDipSideOrientation() {
        // Trace going South-to-North: azimuth 0 degrees.
        // If dip is East: strike should be ~0 degrees (0 + 90 = 90 points East).
        FaultSection eastDip = createDippingSection(1, "EastDip", 60.0, "East", 0.0, 15.0);
        FaultSectionCatalog catEast = new FaultSectionCatalog(List.of(eastDip), "sha");
        FaultResolution resEast = new AutomaticFaultResolver(catEast).resolve(new GeoPoint(35.1, -118.0), 5.0, 6.0);
        assertEquals(0.0, resEast.mechanism().strikeDegrees(), 2.0);

        // If dip is West: strike should flip to ~180 degrees (180 + 90 = 270 points West).
        FaultSection westDip = createDippingSection(2, "WestDip", 60.0, "West", 0.0, 15.0);
        FaultSectionCatalog catWest = new FaultSectionCatalog(List.of(westDip), "sha");
        FaultResolution resWest = new AutomaticFaultResolver(catWest).resolve(new GeoPoint(35.1, -118.0), 5.0, 6.0);
        assertEquals(180.0, resWest.mechanism().strikeDegrees(), 2.0);
    }

    @Test
    void testExplicitInputsPrecedenceThroughCustomCompletion() {
        TravelTimeModel model = new HadleyKanamoriTauPModel();
        List<SimulationSite> sites = List.of(new SimulationSite("s1", "Site 1", new GeoPoint(35.0, -118.0)));
        GeoPoint epicenter = new GeoPoint(35.5, -118.5);

        // Case 1: Both mechanism and rupture supplied
        Mechanism customMech = new Mechanism(45.0, 120.0, 55.0, "REVERSE", "custom");
        RuptureGeometry customRup = new RuptureGeometry(
                List.of(List.of(new GeoPoint(35.4, -118.6), new GeoPoint(35.6, -118.4))),
                2.0, 12.0, 120.0, 55.0, "user-custom", "sha", false);
        EventSource sourceBoth = new EventSource(
                "ev1", "net", "Both Supplied", java.time.Instant.EPOCH, 6.5, "mw",
                epicenter, 8.0, Optional.of(customRup), Optional.of(customMech), java.util.Map.of());

        ScenarioInputs inBoth = ScenarioInputs.forCustomScenario(sourceBoth, sites, model);
        assertEquals(FaultResolution.Mode.SUPPLIED.name(), inBoth.event().metadata().get("resolver.mode"));
        assertEquals(120.0, inBoth.event().mechanism().orElseThrow().strikeDegrees(), 1e-9);
        assertEquals(customRup, inBoth.event().ruptureGeometry().orElseThrow());

        // Case 2: Only mechanism supplied -> generates planar with supplied mechanism
        EventSource sourceMechOnly = new EventSource(
                "ev2", "net", "Mech Only", java.time.Instant.EPOCH, 6.5, "mw",
                epicenter, 8.0, Optional.empty(), Optional.of(customMech), java.util.Map.of());
        ScenarioInputs inMechOnly = ScenarioInputs.forCustomScenario(sourceMechOnly, sites, model);
        assertEquals(FaultResolution.Mode.SUPPLIED.name(), inMechOnly.event().metadata().get("resolver.mode"));
        assertEquals(120.0, inMechOnly.event().mechanism().orElseThrow().strikeDegrees(), 1e-9);
        assertTrue(inMechOnly.event().ruptureGeometry().isPresent());
        assertEquals(55.0, inMechOnly.event().ruptureGeometry().orElseThrow().dipDegrees(), 1e-9);

        // Case 3: Neither supplied -> resolved automatically
        EventSource sourceNeither = new EventSource(
                "ev3", "net", "Neither Supplied", java.time.Instant.EPOCH, 6.5, "mw",
                epicenter, 8.0, Optional.empty(), Optional.empty(), java.util.Map.of());
        ScenarioInputs inNeither = ScenarioInputs.forCustomScenario(sourceNeither, sites, model);
        assertNotNull(inNeither.event().metadata().get("resolver.mode"));
        assertTrue(inNeither.event().mechanism().isPresent());
        assertTrue(inNeither.event().ruptureGeometry().isPresent());
        assertTrue(inNeither.scientificConfiguration().versionIds().containsKey("resolverId"));
    }

    @Test
    void testLoadDefaultGrid() {
        CaliforniaVs30Grid grid = CaliforniaVs30Grid.loadDefault();
        assertNotNull(grid);
        assertEquals(1320, grid.width());
        assertEquals(1260, grid.height());
        assertEquals(-125.0, grid.west(), 1e-6);
        assertEquals(42.5, grid.north(), 1e-6);
        assertNotNull(grid.sha256());
        assertFalse(grid.sha256().isBlank());
        assertEquals("usgs-global-vs30-mosaic-2025", grid.datasetId());

        // Golden coordinates from manifest
        // Ridgecrest: (35.7695, -117.5993)
        Optional<Vs30Sample> ridgecrest = grid.sample(new GeoPoint(35.7695, -117.5993));
        assertTrue(ridgecrest.isPresent(), "Ridgecrest sample should be present");
        assertEquals(277.0, ridgecrest.get().vs30MetersPerSecond(), 2.0);

        // Los Angeles: (34.0522, -118.2437)
        Optional<Vs30Sample> la = grid.sample(new GeoPoint(34.0522, -118.2437));
        assertTrue(la.isPresent(), "LA sample should be present");
        assertEquals(304.5, la.get().vs30MetersPerSecond(), 2.0);

        // San Francisco: (37.7946, -122.3999)
        Optional<Vs30Sample> sf = grid.sample(new GeoPoint(37.7946, -122.3999));
        assertTrue(sf.isPresent(), "SF sample should be present");
        assertEquals(EXPECTED_SF_VS30, sf.get().vs30MetersPerSecond(), 2.0);

        // Bakersfield: (35.3733, -119.0187)
        Optional<Vs30Sample> bakersfield = grid.sample(new GeoPoint(35.3733, -119.0187));
        assertTrue(bakersfield.isPresent(), "Bakersfield sample should be present");
        assertEquals(246.0, bakersfield.get().vs30MetersPerSecond(), 2.0);

        // Pacific ocean offshore: (35.0, -123.0)
        Optional<Vs30Sample> ocean = grid.sample(new GeoPoint(35.0, -123.0));
        assertFalse(ocean.isPresent(), "Offshore ocean point should return empty sample");

        // Far outside grid bounds: (50.0, -130.0)
        Optional<Vs30Sample> outside = grid.sample(new GeoPoint(50.0, -130.0));
        assertFalse(outside.isPresent(), "Outside bounds should return empty sample");
    }

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
        }

        assertTrue(foundSanAndreas, "San Andreas fault must be present in catalog");
        assertTrue(foundHayward, "Hayward fault must be present in catalog");
        assertTrue(foundGarlock, "Garlock fault must be present in catalog");

        // Bounded sample: check first and last vertex of the first 25 faults
        List<MappedFault> faults = catalog.faults();
        int sampleLimit = Math.min(25, faults.size());
        for (int i = 0; i < sampleLimit; i++) {
            MappedFault fault = faults.get(i);
            for (List<GeoPoint> polyline : fault.polylines()) {
                assertTrue(polyline.size() >= 2, "Polyline must have at least 2 points");
                GeoPoint first = polyline.getFirst();
                GeoPoint last = polyline.getLast();
                for (GeoPoint pt : List.of(first, last)) {
                    assertTrue(Double.isFinite(pt.latitude()));
                    assertTrue(Double.isFinite(pt.longitude()));
                    // California bounding envelope roughly 30 to 45 N, -126 to -113 W
                    assertTrue(pt.latitude() >= 30.0 && pt.latitude() <= 45.0, "Latitude out of California bounds: " + pt.latitude());
                    assertTrue(pt.longitude() >= -126.0 && pt.longitude() <= -113.0, "Longitude out of California bounds: " + pt.longitude());
                }
            }
        }
    }
}
