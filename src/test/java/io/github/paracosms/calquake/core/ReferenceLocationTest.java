package io.github.paracosms.calquake.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceLocationTest {

    @Test
    void testValidReferenceLocationCreation() {
        GeoPoint internal = new GeoPoint(35.628542, -117.663992);
        ReferenceLocation.SampledGridNode node = new ReferenceLocation.SampledGridNode(
                new GeoPoint(35.6333, -117.6667), 0.5829
        );
        ReferenceLocation.PeakIntensity intensity = new ReferenceLocation.PeakIntensity(
                7.2, 7.2, "VII", "Very strong", "Moderate", "#ffc400"
        );
        ReferenceLocation.GroundMotion groundMotion = new ReferenceLocation.GroundMotion(
                41.53, 33.57, 71.46, 37.29, 8.595, 261.0
        );

        ReferenceLocation loc = new ReferenceLocation(
                "Ridgecrest", "Ridgecrest city", "0660704", "02410944", "25",
                internal, node, intensity, groundMotion
        );

        assertEquals("Ridgecrest", loc.city());
        assertEquals("0660704", loc.geoid());
        assertEquals(7.2, loc.peakIntensity().mmiSourceDecimal(), 1e-9);
        assertEquals("#ffc400", loc.peakIntensity().colorHex());
        assertEquals(0.5829, loc.sampledGridNode().offsetKm(), 1e-9);
        assertNotNull(loc.groundMotion());
        assertEquals(41.53, loc.groundMotion().pgaPctG(), 1e-9);
    }

    @Test
    void testRejectsInvalidLocationFields() {
        GeoPoint internal = new GeoPoint(35.628542, -117.663992);
        ReferenceLocation.SampledGridNode node = new ReferenceLocation.SampledGridNode(
                new GeoPoint(35.6333, -117.6667), 0.5829
        );
        ReferenceLocation.PeakIntensity intensity = new ReferenceLocation.PeakIntensity(
                7.2, 7.2, "VII", "Very strong", "Moderate", "#ffc400"
        );

        assertThrows(IllegalArgumentException.class, () ->
                new ReferenceLocation(null, "city", "0660704", "02410944", "25", internal, node, intensity, null));
        assertThrows(IllegalArgumentException.class, () ->
                new ReferenceLocation("  ", "city", "0660704", "02410944", "25", internal, node, intensity, null));
        assertThrows(IllegalArgumentException.class, () ->
                new ReferenceLocation("Ridgecrest", "city", null, "02410944", "25", internal, node, intensity, null));
        assertThrows(NullPointerException.class, () ->
                new ReferenceLocation("Ridgecrest", "city", "0660704", "02410944", "25", null, node, intensity, null));
        assertThrows(NullPointerException.class, () ->
                new ReferenceLocation("Ridgecrest", "city", "0660704", "02410944", "25", internal, null, intensity, null));
        assertThrows(NullPointerException.class, () ->
                new ReferenceLocation("Ridgecrest", "city", "0660704", "02410944", "25", internal, node, null, null));
    }

    @Test
    void testRejectsInvalidOffsetKm() {
        GeoPoint pt = new GeoPoint(35.6333, -117.6667);
        assertThrows(IllegalArgumentException.class, () -> new ReferenceLocation.SampledGridNode(pt, -0.01));
        assertThrows(IllegalArgumentException.class, () -> new ReferenceLocation.SampledGridNode(pt, Double.NaN));
    }

    @Test
    void testRejectsInvalidMmi() {
        assertThrows(IllegalArgumentException.class, () ->
                new ReferenceLocation.PeakIntensity(0.9, 0.9, "I", "None", "None", "#ffffff"));
        assertThrows(IllegalArgumentException.class, () ->
                new ReferenceLocation.PeakIntensity(10.1, 10.1, "X", "Extreme", "Heavy", "#ff0000"));
        assertThrows(IllegalArgumentException.class, () ->
                new ReferenceLocation.PeakIntensity(Double.NaN, 5.0, "V", "Moderate", "Very light", "#ffffff"));
    }
}
