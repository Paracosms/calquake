package io.github.paracosms.calquake.core;

import io.github.paracosms.calquake.data.CaliforniaOutline;
import io.github.paracosms.calquake.data.SimulationSiteCatalog;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioTest {

    private EarthquakeEvent createTestEvent() {
        return new EarthquakeEvent(
                "ci38457511", "ci", "Ridgecrest",
                Instant.parse("2019-07-06T03:19:53.040Z"),
                new GeoPoint(35.7695, -117.5993333),
                8.0, 7.1, "mw", ""
        );
    }

    private ReferenceLocation createTestLocation(String city, String geoid) {
        return new ReferenceLocation(
                city, city + " city", geoid, "00000000", "25",
                new GeoPoint(35.0, -117.0),
                new ReferenceLocation.SampledGridNode(new GeoPoint(35.01, -117.01), 0.5),
                new ReferenceLocation.PeakIntensity(7.0, 7.0, "VII", "Very strong", "Moderate", "#ffc400"),
                null
        );
    }

    @Test
    void testCaseInsensitiveLocationLookup() {
        EarthquakeEvent event = createTestEvent();
        List<ReferenceLocation> locations = List.of(
                createTestLocation("Ridgecrest", "0660704"),
                createTestLocation("Trona", "0680515")
        );

        Scenario scenario = new Scenario(event, locations);

        assertTrue(scenario.findLocationByCity("ridgecrest").isPresent());
        assertEquals("0660704", scenario.findLocationByCity("Ridgecrest").get().geoid());
        assertTrue(scenario.findLocationByGeoid("0680515").isPresent());
        assertEquals("Trona", scenario.findLocationByGeoid("0680515").get().city());

        assertFalse(scenario.findLocationByCity("Unknown").isPresent());
        assertFalse(scenario.findLocationByGeoid("9999999").isPresent());
    }

    @Test
    void testImmutabilityOfLocations() {
        EarthquakeEvent event = createTestEvent();
        List<ReferenceLocation> mutableList = new ArrayList<>();
        mutableList.add(createTestLocation("Ridgecrest", "0660704"));

        Scenario scenario = new Scenario(event, mutableList);
        mutableList.add(createTestLocation("Trona", "0680515"));

        // Scenario's list should not be modified
        assertEquals(1, scenario.locations().size());
        assertThrows(UnsupportedOperationException.class, () -> scenario.locations().add(createTestLocation("Fresno", "0627000")));
    }

    @Test
    void testRejectsNullEventOrEmptyLocations() {
        EarthquakeEvent event = createTestEvent();
        assertThrows(NullPointerException.class, () -> new Scenario(null, List.of(createTestLocation("Ridgecrest", "0660704"))));
        assertThrows(NullPointerException.class, () -> new Scenario(event, null));
        assertThrows(IllegalArgumentException.class, () -> new Scenario(event, List.of()));
    }

    @Test
    void testSimulationAssumptionSetResolutionAndDefaults() {
        SimulationAssumptionSet set = SimulationAssumptionSet.resolve("calquake-custom-v1");
        assertNotNull(set);
        assertEquals("calquake-custom-v1", set.id());
        assertEquals(0.0, set.mechanism().strikeDegrees(), 1e-9);
        assertEquals(0.0, set.mechanism().rakeDegrees(), 1e-9);
        assertEquals(90.0, set.mechanism().dipDegrees(), 1e-9);
        assertEquals("wells-coppersmith-1994-all-slip", set.ruptureScalingModel());
        assertEquals(760.0, set.defaultVs30(), 1e-9);
        assertEquals(SiteConditionProvenance.DEFAULT, set.siteProvenance());

        assertThrows(IllegalArgumentException.class, () -> SimulationAssumptionSet.resolve("unknown-set"));
    }

    @Test
    void testSimulationScenarioSettingsCreation() {
        SimulationScenarioSettings settings = SimulationScenarioSettings.createDefault();
        assertEquals("custom-california-scenario-v1", settings.scenarioId());
        assertEquals("Custom California Scenario", settings.displayName());
        assertEquals(35.5, settings.epicenter().latitude(), 1e-9);
        assertEquals(-118.5, settings.epicenter().longitude(), 1e-9);
        assertEquals(6.5, settings.magnitude(), 1e-9);
        assertEquals(10.0, settings.depthKm(), 1e-9);
        assertEquals(IntensityDisplayMode.MAXIMUM_REACHED, settings.intensityDisplayMode());
        assertEquals(SimulationAssumptionSet.DEFAULT_ID, settings.assumptionSetId());
    }

    @Test
    void testSimulationValidatorRejectsHardErrorsAndFlagsWarnings() {
        List<SimulationSite> sites = SimulationSiteCatalog.loadDefault().sites();
        CaliforniaOutline outline = CaliforniaOutline.loadDefault();

        // 1. Hard validation: blanks
        var blankRes = SimulationValidator.validateRaw("", "", "", "", sites, outline);
        assertFalse(blankRes.isValid());
        assertEquals(4, blankRes.errors().size());

        // 2. Hard validation: non-numeric
        var nonNumRes = SimulationValidator.validateRaw("abc", "-118.5", "6.5", "10.0", sites, outline);
        assertFalse(nonNumRes.isValid());
        assertTrue(nonNumRes.errors().stream().anyMatch(e -> e.contains("Latitude must be a valid number")));

        // 3. Hard validation: lat/lon out of bounds
        var latRes = SimulationValidator.validateRaw("95.0", "-118.5", "6.5", "10.0", sites, outline);
        assertFalse(latRes.isValid());
        assertTrue(latRes.errors().stream().anyMatch(e -> e.contains("between -90 and 90")));

        // 4. Hard validation: negative depth
        var depthRes = SimulationValidator.validateRaw("35.5", "-118.5", "6.5", "-5.0", sites, outline);
        assertFalse(depthRes.isValid());
        assertTrue(depthRes.errors().stream().anyMatch(e -> e.contains("non-negative")));

        // 5. Hard validation: NaN/infinite
        var nanRes = SimulationValidator.validate(Double.NaN, -118.5, 6.5, 10.0, sites, outline);
        assertFalse(nanRes.isValid());

        // 6. Plausible California scenario passes cleanly with no warnings
        var cleanRes = SimulationValidator.validate(35.5, -118.5, 6.5, 10.0, sites, outline);
        assertTrue(cleanRes.isValid());
        assertFalse(cleanRes.hasWarnings());
        assertTrue(cleanRes.warnings().isEmpty());

        // 7. Warned scenario: mag 8.6 (exceeds BSSA14 8.5) and depth 5.0 km (outside 8.0-18.2 benchmark range)
        var warnedRes = SimulationValidator.validate(35.5, -118.5, 8.6, 5.0, sites, outline);
        assertTrue(warnedRes.isValid(), "Warned scenario must still be valid for preparation");
        assertTrue(warnedRes.hasWarnings());
        assertTrue(warnedRes.warnings().stream().anyMatch(w -> w.contains("BSSA14")));
        assertTrue(warnedRes.warnings().stream().anyMatch(w -> w.toLowerCase().contains("depth")));
        assertTrue(warnedRes.warningSummary().contains("toy simulation may be wildly inaccurate"));
    }
}
