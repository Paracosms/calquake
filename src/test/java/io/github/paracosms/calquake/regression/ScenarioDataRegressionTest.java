package io.github.paracosms.calquake.regression;

import io.github.paracosms.calquake.core.EarthquakeEvent;
import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.HadleyKanamoriTauPModel;
import io.github.paracosms.calquake.core.InputSignature;
import io.github.paracosms.calquake.core.MmiMode;
import io.github.paracosms.calquake.core.ReferenceLocation;
import io.github.paracosms.calquake.core.Scenario;
import io.github.paracosms.calquake.core.ScenarioInputs;
import io.github.paracosms.calquake.core.SimulationScenarioSettings;
import io.github.paracosms.calquake.core.SimulationSite;
import io.github.paracosms.calquake.data.ScenarioLoader;
import io.github.paracosms.calquake.data.SimulationScenarioSerializer;
import io.github.paracosms.calquake.data.SimulationSiteCatalog;
import io.github.paracosms.calquake.data.SimulationSiteSerializer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioDataRegressionTest {

    @Test
    void ridgecrestBundledScenarioFidelity() {
        assertScenarioFidelity(
                "Ridgecrest",
                "ci38457511",
                Instant.parse("2019-07-06T03:19:53.040Z"),
                35.7695,
                -117.5993333,
                8.0,
                7.1,
                List.of(
                        new ExpectedLocation("Ridgecrest", "0660704", 35.628542, -117.663992, 7.2, 41.53, 1e-2),
                        new ExpectedLocation("Trona", "0680515", 35.815821, -117.347348, 6.9, null, null),
                        new ExpectedLocation("Bakersfield", "0603526", 35.353593, -119.036921, 3.9, null, null),
                        new ExpectedLocation("Los Angeles", "0644000", 34.019394, -118.410825, 3.8, null, null),
                        new ExpectedLocation("Fresno", "0627000", 36.782684, -119.793359, 3.1, null, null)
                )
        );
    }

    @Test
    void northridgeBundledScenarioFidelity() {
        assertScenarioFidelity(
                "Northridge",
                "ci3144585",
                Instant.parse("1994-01-17T12:30:55.388Z"),
                34.213,
                -118.537,
                18.2,
                6.7,
                List.of(
                        new ExpectedLocation("Los Angeles", "0644000", 34.019394, -118.410825, 7.2, 35.91, 1e-2),
                        new ExpectedLocation("Bakersfield", "0603526", 35.353593, -119.036921, 4.5, 4.821, 1e-3),
                        new ExpectedLocation("Ridgecrest", "0660704", 35.628542, -117.663992, 5.3, 4.202, 1e-3),
                        new ExpectedLocation("Trona", "0680515", 35.815821, -117.347348, 4.3, 2.064, 1e-3),
                        new ExpectedLocation("Fresno", "0627000", 36.782684, -119.793359, 3.0, 0.7956, 1e-4)
                )
        );
    }

    private void assertScenarioFidelity(
            String eventName,
            String expectedId,
            Instant expectedOrigin,
            double expectedLat,
            double expectedLon,
            double expectedDepth,
            double expectedMag,
            List<ExpectedLocation> expectedLocations
    ) {
        Scenario scenario = new ScenarioLoader().loadScenario(eventName);
        assertNotNull(scenario);

        EarthquakeEvent event = scenario.event();
        assertEquals(expectedId, event.id());
        assertEquals(expectedOrigin, event.originUtc());
        assertEquals(expectedLat, event.epicenter().latitude(), 1e-9);
        assertEquals(expectedLon, event.epicenter().longitude(), 1e-9);
        assertEquals(expectedDepth, event.depthKm(), 1e-9);
        assertEquals(expectedMag, event.magnitude(), 1e-9);

        assertEquals(5, scenario.locations().size());
        for (ExpectedLocation expected : expectedLocations) {
            ReferenceLocation loc = scenario.findLocationByCity(expected.city()).orElseThrow();
            assertEquals(expected.geoid(), loc.geoid());
            assertEquals(expected.latitude(), loc.internalPoint().latitude(), 1e-6);
            assertEquals(expected.longitude(), loc.internalPoint().longitude(), 1e-6);
            assertEquals(expected.mmi(), loc.peakIntensity().mmiSourceDecimal(), 1e-9);
            if (expected.pga() != null) {
                assertNotNull(loc.groundMotion());
                assertEquals(expected.pga(), loc.groundMotion().pgaPctG(), expected.pgaTolerance());
            }
        }
    }

    private record ExpectedLocation(
            String city,
            String geoid,
            double latitude,
            double longitude,
            double mmi,
            Double pga,
            Double pgaTolerance
    ) {}

    @Test
    void derivativeFixturesMatchProvenanceManifest() throws Exception {
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
    void starterSimulationBundleAndSettings() {
        ScenarioLoader loader = new ScenarioLoader();
        HadleyKanamoriTauPModel model = new HadleyKanamoriTauPModel();

        var bundle = loader.loadStarterSimulationBundle(model);
        assertNotNull(bundle);
        assertEquals(22, bundle.inputs().sites().size());
        assertTrue(bundle.references().bySiteId().isEmpty(), "Custom simulation bundle must have no historical references");

        SimulationScenarioSettings settings = loader.loadStarterSimulationSettings();
        assertNotNull(settings);
        assertEquals("custom-california-scenario-v1", settings.scenarioId());
        assertEquals("Custom California Scenario", settings.displayName());
        assertEquals(35.5, settings.epicenter().latitude(), 1e-6);
        assertEquals(-118.5, settings.epicenter().longitude(), 1e-6);
        assertEquals(6.5, settings.magnitude(), 1e-6);
        assertEquals(10.0, settings.depthKm(), 1e-6);
        assertEquals("calquake-custom-v2", settings.assumptionSetId());

        SimulationSiteCatalog catalog = SimulationSiteCatalog.loadDefault();
        assertNotNull(catalog);
        List<SimulationSite> sites = catalog.sites();
        assertEquals(22, sites.size(), "Default catalog contains the 22 California cities");

        SimulationSite ridgecrest = catalog.requireById("ridgecrest");
        assertEquals("Ridgecrest", ridgecrest.displayName());
        assertEquals(35.622456, ridgecrest.coordinates().latitude(), 1e-6);
        assertEquals(-117.670898, ridgecrest.coordinates().longitude(), 1e-6);

        assertTrue(catalog.findById("ridgecrest").isPresent());
        assertEquals("los-angeles", catalog.requireByDisplayName("Los Angeles").id());
    }

    @Test
    void serializerRoundTripPreservesScenarioCitiesAndSignature() throws Exception {
        SimulationScenarioSettings original = new SimulationScenarioSettings(
                "custom-bundled-scenario-1",
                "Custom Bundled Scenario",
                Instant.parse("2026-09-13T10:00:00Z"),
                new GeoPoint(37.7749, -122.4194),
                6.8,
                8.0,
                io.github.paracosms.calquake.core.IntensityDisplayMode.CURRENT_SHAKING,
                "calquake-custom-v2"
        );
        List<SimulationSite> originalSites = List.of(
                new SimulationSite("sf-custom", "San Francisco Bundled", new GeoPoint(37.7749, -122.4194)),
                new SimulationSite("oak-custom", "Oakland Bundled", new GeoPoint(37.8044, -122.2712))
        );

        String json = SimulationScenarioSerializer.toJson(original, originalSites);
        assertNotNull(json);
        assertTrue(json.contains("\"cities\" : ["));
        assertTrue(json.contains("\"sf-custom\""));
        assertTrue(json.contains("\"Oakland Bundled\""));

        SimulationScenarioSerializer.LoadedScenario loaded = SimulationScenarioSerializer.fromPackageJson(json);
        assertEquals(original, loaded.settings());
        assertTrue(loaded.hasCities());
        assertEquals(2, loaded.cities().size());
        assertEquals("sf-custom", loaded.cities().get(0).id());
        assertEquals("San Francisco Bundled", loaded.cities().get(0).displayName());
        assertEquals(37.7749, loaded.cities().get(0).coordinates().latitude(), 1e-6);
        assertEquals(-122.4194, loaded.cities().get(0).coordinates().longitude(), 1e-6);

        Path tempFile = Files.createTempFile("calquake-bundled-", ".json");
        try {
            SimulationScenarioSerializer.writeToFile(original, originalSites, tempFile);
            assertTrue(Files.size(tempFile) > 0);

            SimulationScenarioSerializer.LoadedScenario fromFile = SimulationScenarioSerializer.readPackageFromFile(tempFile);
            assertEquals(original, fromFile.settings());
            assertTrue(fromFile.hasCities());
            assertEquals(2, fromFile.cities().size());

            SimulationScenarioSettings settingsOnly = SimulationScenarioSerializer.readFromFile(tempFile);
            assertEquals(original, settingsOnly);

            HadleyKanamoriTauPModel model = new HadleyKanamoriTauPModel();
            var inputs1 = ScenarioInputs.forCustomScenario(original, fromFile.cities(), model);
            var inputs2 = ScenarioInputs.forCustomScenario(fromFile.settings(), fromFile.cities(), model);
            assertEquals(InputSignature.compute(inputs1, MmiMode.SIMULATED),
                    InputSignature.compute(inputs2, MmiMode.SIMULATED));
        } finally {
            Files.deleteIfExists(tempFile);
        }

        List<SimulationSite> defaultSites = SimulationSiteCatalog.loadDefault().sites();
        assertEquals(22, defaultSites.size());

        String sitesJson = SimulationSiteSerializer.toJson(defaultSites);
        assertNotNull(sitesJson);
        assertTrue(sitesJson.startsWith("["), "Should be serialized as top-level JSON array");
        assertTrue(sitesJson.contains("\"id\" : \"eureka\""));
        assertTrue(sitesJson.contains("\"display_name\" : \"Eureka\""));
        assertTrue(sitesJson.contains("\"latitude\" : 40.802071"));
        assertTrue(sitesJson.contains("\"longitude\" : -124.163673"));

        List<SimulationSite> deserializedSites = SimulationSiteSerializer.fromJson(sitesJson);
        assertEquals(defaultSites.size(), deserializedSites.size());
        for (int i = 0; i < defaultSites.size(); i++) {
            SimulationSite orig = defaultSites.get(i);
            SimulationSite des = deserializedSites.get(i);
            assertEquals(orig.id(), des.id());
            assertEquals(orig.displayName(), des.displayName());
            assertEquals(orig.coordinates().latitude(), des.coordinates().latitude(), 1e-6);
            assertEquals(orig.coordinates().longitude(), des.coordinates().longitude(), 1e-6);
        }
    }
}
