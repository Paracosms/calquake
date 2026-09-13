package io.github.paracosms.calquake.core;

import io.github.paracosms.calquake.data.FaultSectionCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticFaultResolverTest {

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
}
