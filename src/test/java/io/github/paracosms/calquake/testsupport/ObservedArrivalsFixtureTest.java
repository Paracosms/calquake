package io.github.paracosms.calquake.testsupport;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObservedArrivalsFixtureTest {

    @Test
    void testLoadObservedArrivalsFixtureCounts() {
        List<ObservedArrival> pPicks = ObservedArrivalsFixture.loadPPicks();
        List<ObservedArrival> sPicks = ObservedArrivalsFixture.loadSPicks();
        List<ObservedArrival> allPicks = ObservedArrivalsFixture.loadAllPicks();

        // Stage 1 frozen count requirements: >= 40 P and >= 15 S
        assertEquals(78, pPicks.size(), "Retained P picks count must be 78");
        assertEquals(16, sPicks.size(), "Retained S picks count must be 16");
        assertEquals(94, allPicks.size(), "Total retained picks count must be 94");
    }

    @Test
    void testFirstPickFidelity() {
        List<ObservedArrival> pPicks = ObservedArrivalsFixture.loadPPicks();
        ObservedArrival first = pPicks.get(0);

        assertEquals("CI", first.network());
        assertEquals("CLC", first.station());
        assertEquals("HHZ", first.channel());
        assertEquals("P", first.phase());
        assertEquals(0.8, first.quality(), 1e-6);
        assertEquals(5.14, first.distanceKm(), 1e-2);
        assertEquals(0.628, first.observedTimeSec(), 1e-3);
        assertEquals(35.8157, first.phaseCoordinates().latitude(), 1e-4);
        assertEquals(-117.5975, first.phaseCoordinates().longitude(), 1e-4);
        assertEquals(775.0, first.phaseElevationM(), 1e-1);
    }

    @Test
    void testAllPicksSatisfySelectionCriteria() {
        List<ObservedArrival> allPicks = ObservedArrivalsFixture.loadAllPicks();

        for (ObservedArrival pick : allPicks) {
            assertTrue(pick.quality() >= 0.5, "Quality must be >= 0.5 for " + pick.station());
            assertTrue(pick.distanceKm() >= 0.0 && pick.distanceKm() <= 300.0,
                    "Distance must be within 0-300 km for " + pick.station() + ": " + pick.distanceKm());
            assertNotNull(pick.absoluteTimeUtc());
            assertNotNull(pick.phaseCoordinates());
            assertTrue(pick.observedTimeSec() > 0.0, "Observed time must be positive for " + pick.station());
        }
    }
}
