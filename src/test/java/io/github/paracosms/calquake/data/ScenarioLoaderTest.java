package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.EarthquakeEvent;
import io.github.paracosms.calquake.core.ReferenceLocation;
import io.github.paracosms.calquake.core.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioLoaderTest {

    private ScenarioLoader loader;

    @BeforeEach
    void setUp() {
        loader = new ScenarioLoader();
    }

    @Test
    void testLoadDefaultScenarioFidelity() {
        Scenario scenario = loader.loadDefaultScenario();
        assertNotNull(scenario);

        // 1. Verify Event Source Fidelity
        EarthquakeEvent event = scenario.event();
        assertEquals("ci38457511", event.id());
        assertEquals("ci", event.network());
        assertEquals("M 7.1 - Ridgecrest Earthquake Sequence", event.title());
        assertEquals(Instant.parse("2019-07-06T03:19:53.040Z"), event.originUtc());
        assertEquals(35.7695, event.epicenter().latitude(), 1e-9);
        assertEquals(-117.5993333, event.epicenter().longitude(), 1e-9);
        assertEquals(8.0, event.depthKm(), 1e-9);
        assertEquals(7.1, event.magnitude(), 1e-9);
        assertEquals("mw", event.magnitudeType());

        // 2. Verify Five Reference Locations
        List<ReferenceLocation> locations = scenario.locations();
        assertEquals(5, locations.size());

        // Ridgecrest
        ReferenceLocation ridgecrest = scenario.findLocationByCity("Ridgecrest").orElseThrow();
        assertEquals("0660704", ridgecrest.geoid());
        assertEquals(35.628542, ridgecrest.internalPoint().latitude(), 1e-6);
        assertEquals(-117.663992, ridgecrest.internalPoint().longitude(), 1e-6);
        assertEquals(35.6333, ridgecrest.sampledGridNode().point().latitude(), 1e-4);
        assertEquals(-117.6667, ridgecrest.sampledGridNode().point().longitude(), 1e-4);
        assertEquals(0.5829, ridgecrest.sampledGridNode().offsetKm(), 1e-4);
        assertEquals(7.2, ridgecrest.peakIntensity().mmiSourceDecimal(), 1e-9);
        assertEquals(7.2, ridgecrest.peakIntensity().mmiDisplayRounded(), 1e-9);
        assertEquals("VII", ridgecrest.peakIntensity().mmiRoman());
        assertEquals("Very strong", ridgecrest.peakIntensity().shakingDescription());
        assertEquals("#ffc400", ridgecrest.peakIntensity().colorHex());
        assertNotNull(ridgecrest.groundMotion());
        assertEquals(41.53, ridgecrest.groundMotion().pgaPctG(), 1e-2);

        // Trona
        ReferenceLocation trona = scenario.findLocationByCity("Trona").orElseThrow();
        assertEquals("0680515", trona.geoid());
        assertEquals(35.815821, trona.internalPoint().latitude(), 1e-6);
        assertEquals(-117.347348, trona.internalPoint().longitude(), 1e-6);
        assertEquals(35.8167, trona.sampledGridNode().point().latitude(), 1e-4);
        assertEquals(-117.35, trona.sampledGridNode().point().longitude(), 1e-4);
        assertEquals(0.2583, trona.sampledGridNode().offsetKm(), 1e-4);
        assertEquals(6.9, trona.peakIntensity().mmiSourceDecimal(), 1e-9);
        assertEquals(6.9, trona.peakIntensity().mmiDisplayRounded(), 1e-9);
        assertEquals("VII", trona.peakIntensity().mmiRoman());
        assertEquals("#ffc400", trona.peakIntensity().colorHex());

        // Bakersfield
        ReferenceLocation bakersfield = scenario.findLocationByCity("Bakersfield").orElseThrow();
        assertEquals("0603526", bakersfield.geoid());
        assertEquals(35.353593, bakersfield.internalPoint().latitude(), 1e-6);
        assertEquals(-119.036921, bakersfield.internalPoint().longitude(), 1e-6);
        assertEquals(35.35, bakersfield.sampledGridNode().point().latitude(), 1e-4);
        assertEquals(-119.0333, bakersfield.sampledGridNode().point().longitude(), 1e-4);
        assertEquals(0.5172, bakersfield.sampledGridNode().offsetKm(), 1e-4);
        assertEquals(3.9, bakersfield.peakIntensity().mmiSourceDecimal(), 1e-9);
        assertEquals(3.9, bakersfield.peakIntensity().mmiDisplayRounded(), 1e-9);
        assertEquals("IV", bakersfield.peakIntensity().mmiRoman());
        assertEquals("#7ffffa", bakersfield.peakIntensity().colorHex());

        // Los Angeles
        ReferenceLocation losAngeles = scenario.findLocationByCity("Los Angeles").orElseThrow();
        assertEquals("0644000", losAngeles.geoid());
        assertEquals(34.019394, losAngeles.internalPoint().latitude(), 1e-6);
        assertEquals(-118.410825, losAngeles.internalPoint().longitude(), 1e-6);
        assertEquals(34.0167, losAngeles.sampledGridNode().point().latitude(), 1e-4);
        assertEquals(-118.4167, losAngeles.sampledGridNode().point().longitude(), 1e-4);
        assertEquals(0.6188, losAngeles.sampledGridNode().offsetKm(), 1e-4);
        assertEquals(3.8, losAngeles.peakIntensity().mmiSourceDecimal(), 1e-9);
        assertEquals(3.8, losAngeles.peakIntensity().mmiDisplayRounded(), 1e-9);
        assertEquals("IV", losAngeles.peakIntensity().mmiRoman());
        assertEquals("#7ffffa", losAngeles.peakIntensity().colorHex());

        // Fresno
        ReferenceLocation fresno = scenario.findLocationByCity("Fresno").orElseThrow();
        assertEquals("0627000", fresno.geoid());
        assertEquals(36.782684, fresno.internalPoint().latitude(), 1e-6);
        assertEquals(-119.793359, fresno.internalPoint().longitude(), 1e-6);
        assertEquals(36.7833, fresno.sampledGridNode().point().latitude(), 1e-4);
        assertEquals(-119.8, fresno.sampledGridNode().point().longitude(), 1e-4);
        assertEquals(0.5954, fresno.sampledGridNode().offsetKm(), 1e-4);
        assertEquals(3.1, fresno.peakIntensity().mmiSourceDecimal(), 1e-9);
        assertEquals(3.1, fresno.peakIntensity().mmiDisplayRounded(), 1e-9);
        assertEquals("II-III", fresno.peakIntensity().mmiRoman());
        assertEquals("#acdbff", fresno.peakIntensity().colorHex());
    }

    @Test
    void testDerivativeFixturesMatchProvenanceManifest() throws Exception {
        JsonNode manifest;
        try (InputStream input = getClass().getResourceAsStream("/data/provenance_manifest.json")) {
            assertNotNull(input, "provenance manifest must be present");
            manifest = new ObjectMapper().readTree(input);
        }

        JsonNode derivatives = manifest.get("derivative_fixtures");
        assertNotNull(derivatives, "provenance manifest must identify the derived fixtures");
        assertResourceHash("/data/five_reference_locations.json", derivatives, "five_reference_locations.json");
        assertResourceHash("/data/mmi_legend.json", derivatives, "mmi_legend.json");
        assertResourceHash("/data/california_outline.json", derivatives, "california_outline.json");
        assertResourceHash("/fixtures/observed_picks_ci38457511.json", derivatives,
                "observed_picks_ci38457511.json");
    }

    private void assertResourceHash(String resourcePath, JsonNode derivatives, String manifestKey) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(input, "Missing resource: " + resourcePath);
            String normalized = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
            String expected = derivatives.get(manifestKey).get("sha256_hex").asText();
            assertEquals(expected.toLowerCase(), actual.toLowerCase(), "SHA-256 mismatch for " + resourcePath);
        }
    }

    @Test
    void testLoadEventFromProvenanceManifestFormat() {
        String manifestSnippet = """
                {
                  "manifest_version": "1.0",
                  "event": {
                    "id": "ci38457511",
                    "network": "ci",
                    "title": "M 7.1 - Ridgecrest Earthquake Sequence",
                    "origin_utc": "2019-07-06T03:19:53.040Z",
                    "latitude": 35.7695,
                    "longitude": -117.5993333,
                    "depth_km": 8.0,
                    "magnitude": 7.1,
                    "magnitude_type": "mw"
                  }
                }
                """;
        EarthquakeEvent event = loader.loadEvent(manifestSnippet);
        assertEquals("ci38457511", event.id());
        assertEquals(35.7695, event.epicenter().latitude(), 1e-9);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{ invalid json }",
            "\"not an object\"",
            "[]",
            // missing id
            """
            {"origin_utc":"2019-07-06T03:19:53.040Z","latitude":35.0,"longitude":-117.0,"depth_km":8.0,"magnitude":7.1}
            """,
            // blank id
            """
            {"id":"  ","origin_utc":"2019-07-06T03:19:53.040Z","latitude":35.0,"longitude":-117.0,"depth_km":8.0,"magnitude":7.1}
            """,
            // missing origin_utc
            """
            {"id":"e1","latitude":35.0,"longitude":-117.0,"depth_km":8.0,"magnitude":7.1}
            """,
            // invalid ISO timestamp
            """
            {"id":"e1","origin_utc":"invalid-timestamp","latitude":35.0,"longitude":-117.0,"depth_km":8.0,"magnitude":7.1}
            """,
            // missing latitude
            """
            {"id":"e1","origin_utc":"2019-07-06T03:19:53.040Z","longitude":-117.0,"depth_km":8.0,"magnitude":7.1}
            """,
            // invalid latitude (> 90)
            """
            {"id":"e1","origin_utc":"2019-07-06T03:19:53.040Z","latitude":95.0,"longitude":-117.0,"depth_km":8.0,"magnitude":7.1}
            """,
            // invalid longitude (< -180)
            """
            {"id":"e1","origin_utc":"2019-07-06T03:19:53.040Z","latitude":35.0,"longitude":-190.0,"depth_km":8.0,"magnitude":7.1}
            """,
            // non-numeric latitude
            """
            {"id":"e1","origin_utc":"2019-07-06T03:19:53.040Z","latitude":"north","longitude":-117.0,"depth_km":8.0,"magnitude":7.1}
            """,
            // negative depth
            """
            {"id":"e1","origin_utc":"2019-07-06T03:19:53.040Z","latitude":35.0,"longitude":-117.0,"depth_km":-1.0,"magnitude":7.1}
            """,
            // non-numeric magnitude
            """
            {"id":"e1","origin_utc":"2019-07-06T03:19:53.040Z","latitude":35.0,"longitude":-117.0,"depth_km":8.0,"magnitude":"huge"}
            """
    })
    void testRejectsMalformedEventJson(String malformedJson) {
        assertThrows(IllegalArgumentException.class, () -> loader.loadEvent(malformedJson));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{ invalid json }",
            "\"not an object\"",
            "{}",
            // empty locations array
            "{\"locations\":[]}",
            // missing city
            """
            {"locations":[{"geoid":"01","internal_point":{"latitude":35.0,"longitude":-117.0},"sampled_grid_node":{"latitude":35.0,"longitude":-117.0,"offset_km":0.1},"peak_intensity":{"mmi_source_decimal":5.0,"mmi_display_rounded":5.0}}]}
            """,
            // missing geoid
            """
            {"locations":[{"city":"City","internal_point":{"latitude":35.0,"longitude":-117.0},"sampled_grid_node":{"latitude":35.0,"longitude":-117.0,"offset_km":0.1},"peak_intensity":{"mmi_source_decimal":5.0,"mmi_display_rounded":5.0}}]}
            """,
            // missing internal_point
            """
            {"locations":[{"city":"City","geoid":"01","sampled_grid_node":{"latitude":35.0,"longitude":-117.0,"offset_km":0.1},"peak_intensity":{"mmi_source_decimal":5.0,"mmi_display_rounded":5.0}}]}
            """,
            // missing sampled_grid_node
            """
            {"locations":[{"city":"City","geoid":"01","internal_point":{"latitude":35.0,"longitude":-117.0},"peak_intensity":{"mmi_source_decimal":5.0,"mmi_display_rounded":5.0}}]}
            """,
            // missing peak_intensity
            """
            {"locations":[{"city":"City","geoid":"01","internal_point":{"latitude":35.0,"longitude":-117.0},"sampled_grid_node":{"latitude":35.0,"longitude":-117.0,"offset_km":0.1}}]}
            """,
            // invalid MMI (> 10)
            """
            {"locations":[{"city":"City","geoid":"01","internal_point":{"latitude":35.0,"longitude":-117.0},"sampled_grid_node":{"latitude":35.0,"longitude":-117.0,"offset_km":0.1},"peak_intensity":{"mmi_source_decimal":11.0,"mmi_display_rounded":11.0}}]}
            """,
            // invalid offset (< 0)
            """
            {"locations":[{"city":"City","geoid":"01","internal_point":{"latitude":35.0,"longitude":-117.0},"sampled_grid_node":{"latitude":35.0,"longitude":-117.0,"offset_km":-0.5},"peak_intensity":{"mmi_source_decimal":5.0,"mmi_display_rounded":5.0}}]}
            """
    })
    void testRejectsMalformedLocationsJson(String malformedJson) {
        assertThrows(IllegalArgumentException.class, () -> loader.loadReferenceLocations(malformedJson));
    }

    @Test
    void testLoadNorthridgeScenarioFidelity() {
        Scenario scenario = loader.loadNorthridgeScenario();
        assertNotNull(scenario);

        // 1. Verify Event Source Fidelity
        EarthquakeEvent event = scenario.event();
        assertEquals("ci3144585", event.id());
        assertEquals("ci", event.network());
        assertEquals("M 6.7 - Northridge, California, earthquake", event.title());
        assertEquals(Instant.parse("1994-01-17T12:30:55.388Z"), event.originUtc());
        assertEquals(34.213, event.epicenter().latitude(), 1e-9);
        assertEquals(-118.537, event.epicenter().longitude(), 1e-9);
        assertEquals(18.2, event.depthKm(), 1e-9);
        assertEquals(6.7, event.magnitude(), 1e-9);
        assertEquals("mw", event.magnitudeType());

        // 2. Verify Five Reference Locations
        List<ReferenceLocation> locations = scenario.locations();
        assertEquals(5, locations.size());

        // Los Angeles (closest, MMI 7.2)
        ReferenceLocation losAngeles = scenario.findLocationByCity("Los Angeles").orElseThrow();
        assertEquals("0644000", losAngeles.geoid());
        assertEquals(7.2, losAngeles.peakIntensity().mmiSourceDecimal(), 1e-9);
        assertEquals(7.2, losAngeles.peakIntensity().mmiDisplayRounded(), 1e-9);
        assertEquals("VII", losAngeles.peakIntensity().mmiRoman());
        assertEquals("Very strong", losAngeles.peakIntensity().shakingDescription());
        assertEquals("#ffc400", losAngeles.peakIntensity().colorHex());
        assertNotNull(losAngeles.groundMotion());
        assertEquals(35.91, losAngeles.groundMotion().pgaPctG(), 1e-2);

        // Bakersfield (MMI 4.5)
        ReferenceLocation bakersfield = scenario.findLocationByCity("Bakersfield").orElseThrow();
        assertEquals("0603526", bakersfield.geoid());
        assertEquals(4.5, bakersfield.peakIntensity().mmiSourceDecimal(), 1e-9);
        assertEquals("V", bakersfield.peakIntensity().mmiRoman());
        assertEquals("#81ff8a", bakersfield.peakIntensity().colorHex());
        assertEquals(4.821, bakersfield.groundMotion().pgaPctG(), 1e-3);

        // Ridgecrest (MMI 5.3)
        ReferenceLocation ridgecrest = scenario.findLocationByCity("Ridgecrest").orElseThrow();
        assertEquals("0660704", ridgecrest.geoid());
        assertEquals(5.3, ridgecrest.peakIntensity().mmiSourceDecimal(), 1e-9);
        assertEquals("V", ridgecrest.peakIntensity().mmiRoman());
        assertEquals("#81ff8a", ridgecrest.peakIntensity().colorHex());
        assertEquals(4.202, ridgecrest.groundMotion().pgaPctG(), 1e-3);

        // Trona (MMI 4.3)
        ReferenceLocation trona = scenario.findLocationByCity("Trona").orElseThrow();
        assertEquals("0680515", trona.geoid());
        assertEquals(4.3, trona.peakIntensity().mmiSourceDecimal(), 1e-9);
        assertEquals("IV", trona.peakIntensity().mmiRoman());
        assertEquals("#7ffffa", trona.peakIntensity().colorHex());
        assertEquals(2.064, trona.groundMotion().pgaPctG(), 1e-3);

        // Fresno (MMI 3.0)
        ReferenceLocation fresno = scenario.findLocationByCity("Fresno").orElseThrow();
        assertEquals("0627000", fresno.geoid());
        assertEquals(3.0, fresno.peakIntensity().mmiSourceDecimal(), 1e-9);
        assertEquals("II-III", fresno.peakIntensity().mmiRoman());
        assertEquals("#acdbff", fresno.peakIntensity().colorHex());
        assertEquals(0.7956, fresno.groundMotion().pgaPctG(), 1e-4);
    }

    @Test
    void testLoadScenarioByEventName() {
        Scenario northridge = loader.loadScenario("Northridge");
        assertEquals("ci3144585", northridge.event().id());

        Scenario northridgeById = loader.loadScenario("ci3144585");
        assertEquals("ci3144585", northridgeById.event().id());

        Scenario ridgecrest = loader.loadScenario("Ridgecrest");
        assertEquals("ci38457511", ridgecrest.event().id());

        Scenario defaultScenario = loader.loadScenario(null);
        assertEquals("ci38457511", defaultScenario.event().id());

        Scenario unknownScenario = loader.loadScenario("unknown");
        assertEquals("ci38457511", unknownScenario.event().id());
    }
}
