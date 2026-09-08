package io.github.paracosms.calquake.core;

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
    void testScenarioCreationAndLookup() {
        EarthquakeEvent event = createTestEvent();
        List<ReferenceLocation> locations = List.of(
                createTestLocation("Ridgecrest", "0660704"),
                createTestLocation("Trona", "0680515")
        );

        Scenario scenario = new Scenario(event, locations);

        assertEquals(event, scenario.event());
        assertEquals(2, scenario.locations().size());

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
}
