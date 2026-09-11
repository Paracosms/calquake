package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.EarthquakeEvent;
import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.HadleyKanamoriTauPModel;
import io.github.paracosms.calquake.core.ReferenceLocation;
import io.github.paracosms.calquake.core.Scenario;
import io.github.paracosms.calquake.core.ScenarioInputs;
import io.github.paracosms.calquake.core.SimulationScenarioSettings;
import io.github.paracosms.calquake.core.SimulationSite;
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

    @Test
    void testSimulationSiteCatalogLoadDefault() {
        SimulationSiteCatalog catalog = SimulationSiteCatalog.loadDefault();
        assertNotNull(catalog);
        List<SimulationSite> sites = catalog.sites();
        assertEquals(5, sites.size(), "Default catalog must initially contain the 5 California cities");

        SimulationSite ridgecrest = catalog.requireById("ridgecrest");
        assertEquals("Ridgecrest", ridgecrest.displayName());
        assertEquals(35.628542, ridgecrest.coordinates().latitude(), 1e-6);
        assertEquals(-117.663992, ridgecrest.coordinates().longitude(), 1e-6);

        assertTrue(catalog.findById("trona").isPresent());
        assertTrue(catalog.findById("bakersfield").isPresent());
        assertTrue(catalog.findById("los-angeles").isPresent());
        assertTrue(catalog.findById("fresno").isPresent());

        // Display name lookup
        assertEquals("los-angeles", catalog.requireByDisplayName("Los Angeles").id());
        assertTrue(catalog.findByDisplayName("NonExistent").isEmpty());
    }

    @Test
    void testSimulationSiteCatalogCustomAndValidation() {
        // Valid custom catalog
        String validJson = """
                {
                  "schema_version": 1,
                  "sites": [
                    { "id": "city-1", "display_name": "City One", "latitude": 37.5, "longitude": -122.0 },
                    { "id": "city-2", "display_name": "City Two", "latitude": 38.0, "longitude": -121.5 }
                  ]
                }
                """;
        SimulationSiteCatalog catalog = SimulationSiteCatalog.loadFromJsonString(validJson);
        assertEquals(2, catalog.sites().size());
        assertEquals("City One", catalog.requireById("city-1").displayName());

        // Duplicate ID rejection
        String duplicateIdJson = """
                {
                  "sites": [
                    { "id": "dup", "display_name": "First", "latitude": 37.0, "longitude": -122.0 },
                    { "id": "dup", "display_name": "Second", "latitude": 38.0, "longitude": -121.0 }
                  ]
                }
                """;
        assertThrows(IllegalArgumentException.class, () -> SimulationSiteCatalog.loadFromJsonString(duplicateIdJson));

        // Invalid latitude
        String badLatJson = """
                {
                  "sites": [
                    { "id": "bad", "display_name": "Bad", "latitude": 95.0, "longitude": -120.0 }
                  ]
                }
                """;
        assertThrows(IllegalArgumentException.class, () -> SimulationSiteCatalog.loadFromJsonString(badLatJson));
    }

    @Test
    void testLoadStarterSimulationBundleWithCatalog() {
        HadleyKanamoriTauPModel model = new HadleyKanamoriTauPModel();

        // Default catalog
        var bundle = loader.loadStarterSimulationBundle(model);
        assertNotNull(bundle);
        assertEquals(5, bundle.inputs().sites().size());
        assertTrue(bundle.references().bySiteId().isEmpty(), "Custom simulation bundle must have no historical references");

        // Custom catalog with 2 sites
        SimulationSiteCatalog customCatalog = SimulationSiteCatalog.of(List.of(
                new SimulationSite("c1", "City 1", new GeoPoint(35.0, -118.0)),
                new SimulationSite("c2", "City 2", new GeoPoint(36.0, -119.0))
        ));
        var customBundle = loader.loadStarterSimulationBundle(model, customCatalog);
        assertEquals(2, customBundle.inputs().sites().size());
        assertEquals("c1", customBundle.inputs().sites().get(0).id());
        assertEquals("c2", customBundle.inputs().sites().get(1).id());
    }

    @Test
    void testLoadStarterSimulationSettings() {
        SimulationScenarioSettings settings = loader.loadStarterSimulationSettings();
        assertNotNull(settings);
        assertEquals("custom-california-scenario-v1", settings.scenarioId());
        assertEquals("Custom California Scenario", settings.displayName());
        assertEquals(35.5, settings.epicenter().latitude(), 1e-6);
        assertEquals(-118.5, settings.epicenter().longitude(), 1e-6);
        assertEquals(6.5, settings.magnitude(), 1e-6);
        assertEquals(10.0, settings.depthKm(), 1e-6);
        assertEquals("calquake-custom-v1", settings.assumptionSetId());
    }

    @Test
    void testSimulationScenarioSerializerRoundTripAndAtomicWrite() throws Exception {
        SimulationScenarioSettings original = new SimulationScenarioSettings(
                "custom-test-scenario-1",
                "Custom Test Scenario",
                Instant.parse("2026-09-11T10:15:30Z"),
                new GeoPoint(34.0522, -118.2437),
                7.2,
                12.5,
                io.github.paracosms.calquake.core.IntensityDisplayMode.CURRENT_SHAKING,
                "calquake-custom-v1"
        );

        String json = SimulationScenarioSerializer.toJson(original);
        assertNotNull(json);
        assertTrue(json.contains("\"schema_version\" : 1"));
        assertTrue(json.contains("\"type\" : \"calquake-simulation-scenario\""));
        assertTrue(json.contains("\"CURRENT_SHAKING\""));

        SimulationScenarioSettings deserialized = SimulationScenarioSerializer.fromJson(json);
        assertEquals(original.scenarioId(), deserialized.scenarioId());
        assertEquals(original.displayName(), deserialized.displayName());
        assertEquals(original.createdUtc(), deserialized.createdUtc());
        assertEquals(original.epicenter().latitude(), deserialized.epicenter().latitude(), 1e-6);
        assertEquals(original.epicenter().longitude(), deserialized.epicenter().longitude(), 1e-6);
        assertEquals(original.magnitude(), deserialized.magnitude(), 1e-6);
        assertEquals(original.depthKm(), deserialized.depthKm(), 1e-6);
        assertEquals(original.intensityDisplayMode(), deserialized.intensityDisplayMode());
        assertEquals(original.assumptionSetId(), deserialized.assumptionSetId());

        // Test atomic file write and read
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("calquake-test-", ".calquake.json");
        try {
            SimulationScenarioSerializer.writeToFile(original, tempFile);
            assertTrue(java.nio.file.Files.size(tempFile) > 0);

            SimulationScenarioSettings fromFile = SimulationScenarioSerializer.readFromFile(tempFile);
            assertEquals(original, fromFile);

            // Verify input signature reproducibility
            HadleyKanamoriTauPModel model = new HadleyKanamoriTauPModel();
            var sites = SimulationSiteCatalog.loadDefault().sites();
            var inputs1 = ScenarioInputs.forCustomScenario(original, sites, model);
            var inputs2 = ScenarioInputs.forCustomScenario(fromFile, sites, model);
            assertEquals(io.github.paracosms.calquake.core.InputSignature.compute(inputs1, io.github.paracosms.calquake.core.MmiMode.SIMULATED),
                    io.github.paracosms.calquake.core.InputSignature.compute(inputs2, io.github.paracosms.calquake.core.MmiMode.SIMULATED));
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testSimulationScenarioSerializerValidationErrors() {
        // Unknown type
        String badType = """
                {
                  "schema_version": 1,
                  "type": "unknown-type",
                  "scenario_id": "test",
                  "name": "Test",
                  "created_utc": "2026-09-10T00:00:00Z",
                  "epicenter": { "latitude": 35.0, "longitude": -118.0 },
                  "magnitude": 6.0,
                  "depth_km": 10.0,
                  "intensity_display_mode": "MAXIMUM_REACHED",
                  "assumption_set": "calquake-custom-v1"
                }
                """;
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> SimulationScenarioSerializer.fromJson(badType));
        assertTrue(e1.getMessage().contains("Unknown scenario type"));

        // Unsupported schema version
        String badVersion = """
                {
                  "schema_version": 2,
                  "type": "calquake-simulation-scenario",
                  "scenario_id": "test",
                  "name": "Test",
                  "created_utc": "2026-09-10T00:00:00Z",
                  "epicenter": { "latitude": 35.0, "longitude": -118.0 },
                  "magnitude": 6.0,
                  "depth_km": 10.0,
                  "intensity_display_mode": "MAXIMUM_REACHED",
                  "assumption_set": "calquake-custom-v1"
                }
                """;
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> SimulationScenarioSerializer.fromJson(badVersion));
        assertTrue(e2.getMessage().contains("Unsupported schema version"));

        // Invalid latitude
        String badLat = """
                {
                  "schema_version": 1,
                  "type": "calquake-simulation-scenario",
                  "scenario_id": "test",
                  "name": "Test",
                  "created_utc": "2026-09-10T00:00:00Z",
                  "epicenter": { "latitude": 95.0, "longitude": -118.0 },
                  "magnitude": 6.0,
                  "depth_km": 10.0,
                  "intensity_display_mode": "MAXIMUM_REACHED",
                  "assumption_set": "calquake-custom-v1"
                }
                """;
        assertThrows(IllegalArgumentException.class,
                () -> SimulationScenarioSerializer.fromJson(badLat));

        // Unknown assumption set
        String badAssumption = """
                {
                  "schema_version": 1,
                  "type": "calquake-simulation-scenario",
                  "scenario_id": "test",
                  "name": "Test",
                  "created_utc": "2026-09-10T00:00:00Z",
                  "epicenter": { "latitude": 35.0, "longitude": -118.0 },
                  "magnitude": 6.0,
                  "depth_km": 10.0,
                  "intensity_display_mode": "MAXIMUM_REACHED",
                  "assumption_set": "unknown-assumption-v99"
                }
                """;
        assertThrows(IllegalArgumentException.class,
                () -> SimulationScenarioSerializer.fromJson(badAssumption));
    }
}

