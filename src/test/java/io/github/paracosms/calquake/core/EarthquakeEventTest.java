package io.github.paracosms.calquake.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EarthquakeEventTest {

    @Test
    void testValidEventCreation() {
        Instant origin = Instant.parse("2019-07-06T03:19:53.040Z");
        GeoPoint epicenter = new GeoPoint(35.7695, -117.5993333);
        EarthquakeEvent event = new EarthquakeEvent(
                "ci38457511", "ci", "M 7.1 - Ridgecrest Earthquake Sequence",
                origin, epicenter, 8.0, 7.1, "mw",
                "https://earthquake.usgs.gov/earthquakes/eventpage/ci38457511"
        );

        assertEquals("ci38457511", event.id());
        assertEquals("ci", event.network());
        assertEquals("M 7.1 - Ridgecrest Earthquake Sequence", event.title());
        assertEquals(origin, event.originUtc());
        assertEquals(epicenter, event.epicenter());
        assertEquals(8.0, event.depthKm(), 1e-9);
        assertEquals(7.1, event.magnitude(), 1e-9);
        assertEquals("mw", event.magnitudeType());
        assertEquals("https://earthquake.usgs.gov/earthquakes/eventpage/ci38457511", event.url());
    }

    @Test
    void testRejectsNullOrBlankId() {
        Instant origin = Instant.parse("2019-07-06T03:19:53.040Z");
        GeoPoint epicenter = new GeoPoint(35.7695, -117.5993333);

        assertThrows(IllegalArgumentException.class, () ->
                new EarthquakeEvent(null, "ci", "Title", origin, epicenter, 8.0, 7.1, "mw", ""));
        assertThrows(IllegalArgumentException.class, () ->
                new EarthquakeEvent("   ", "ci", "Title", origin, epicenter, 8.0, 7.1, "mw", ""));
    }

    @Test
    void testRejectsNullOriginUtc() {
        GeoPoint epicenter = new GeoPoint(35.7695, -117.5993333);
        assertThrows(NullPointerException.class, () ->
                new EarthquakeEvent("ci38457511", "ci", "Title", null, epicenter, 8.0, 7.1, "mw", ""));
    }

    @Test
    void testRejectsNullEpicenter() {
        Instant origin = Instant.parse("2019-07-06T03:19:53.040Z");
        assertThrows(NullPointerException.class, () ->
                new EarthquakeEvent("ci38457511", "ci", "Title", origin, null, 8.0, 7.1, "mw", ""));
    }

    @Test
    void testRejectsNegativeOrNonFiniteDepth() {
        Instant origin = Instant.parse("2019-07-06T03:19:53.040Z");
        GeoPoint epicenter = new GeoPoint(35.7695, -117.5993333);

        assertThrows(IllegalArgumentException.class, () ->
                new EarthquakeEvent("ci38457511", "ci", "Title", origin, epicenter, -0.1, 7.1, "mw", ""));
        assertThrows(IllegalArgumentException.class, () ->
                new EarthquakeEvent("ci38457511", "ci", "Title", origin, epicenter, Double.NaN, 7.1, "mw", ""));
        assertThrows(IllegalArgumentException.class, () ->
                new EarthquakeEvent("ci38457511", "ci", "Title", origin, epicenter, Double.POSITIVE_INFINITY, 7.1, "mw", ""));
    }

    @Test
    void testRejectsNonFiniteMagnitude() {
        Instant origin = Instant.parse("2019-07-06T03:19:53.040Z");
        GeoPoint epicenter = new GeoPoint(35.7695, -117.5993333);

        assertThrows(IllegalArgumentException.class, () ->
                new EarthquakeEvent("ci38457511", "ci", "Title", origin, epicenter, 8.0, Double.NaN, "mw", ""));
        assertThrows(IllegalArgumentException.class, () ->
                new EarthquakeEvent("ci38457511", "ci", "Title", origin, epicenter, 8.0, Double.NEGATIVE_INFINITY, "mw", ""));
    }
}
