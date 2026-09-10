package io.github.paracosms.calquake.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceLocationTest {

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
