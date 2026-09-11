package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.EarthquakeEvent;
import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.EventSource;
import io.github.paracosms.calquake.core.Mechanism;
import io.github.paracosms.calquake.core.ReferenceLocation;
import io.github.paracosms.calquake.core.ReferenceIntensity;
import io.github.paracosms.calquake.core.RuptureGeometry;
import io.github.paracosms.calquake.core.Scenario;
import io.github.paracosms.calquake.core.ScenarioInputs;
import io.github.paracosms.calquake.core.ScenarioReferences;
import io.github.paracosms.calquake.core.ScientificConfiguration;
import io.github.paracosms.calquake.core.SimulationSite;
import io.github.paracosms.calquake.core.SiteCondition;
import io.github.paracosms.calquake.core.SiteConditionProvenance;
import io.github.paracosms.calquake.core.TravelTimeConfiguration;
import io.github.paracosms.calquake.core.TravelTimeModel;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Loads and validates normalized event and reference location resources using Jackson.
 * Enforces strict validation of coordinates, units, depth, and timestamps while
 * preserving exact source precision and direct constructor wiring.
 */
public class ScenarioLoader {

    public static final String DEFAULT_EVENT_RESOURCE = "/data/event.json";
    public static final String DEFAULT_LOCATIONS_RESOURCE = "/data/five_reference_locations.json";
    public static final String NORTHRIDGE_EVENT_RESOURCE = "/data/northridge/event.json";
    public static final String NORTHRIDGE_LOCATIONS_RESOURCE = "/data/northridge/five_reference_locations.json";
    public static final String SCIENTIFIC_INPUTS_RESOURCE = "/data/scientific_inputs.json";
    public static final String DEFAULT_SIMULATION_SCENARIO_RESOURCE = "/data/default_simulation_scenario.json";

    private final JsonMapper jsonMapper;

    public ScenarioLoader() {
        this.jsonMapper = JsonMapper.builder().build();
    }

    /**
     * Loads the default frozen Demo 0 scenario from classpath resources.
     *
     * @return validated immutable Scenario
     */
    public Scenario loadDefaultScenario() {
        return loadScenarioFromResources(DEFAULT_EVENT_RESOURCE, DEFAULT_LOCATIONS_RESOURCE);
    }

    /**
     * Loads the 1994 Northridge scenario from classpath resources.
     *
     * @return validated immutable Scenario
     */
    public Scenario loadNorthridgeScenario() {
        return loadScenarioFromResources(NORTHRIDGE_EVENT_RESOURCE, NORTHRIDGE_LOCATIONS_RESOURCE);
    }

    /**
     * Loads a scenario by event name or identifier ("Ridgecrest" or "Northridge").
     * Defaults to the Ridgecrest scenario.
     *
     * @param eventName event name or identifier
     * @return validated immutable Scenario
     */
    public Scenario loadScenario(String eventName) {
        if (eventName != null && (eventName.equalsIgnoreCase("Northridge") || eventName.equalsIgnoreCase("ci3144585"))) {
            return loadNorthridgeScenario();
        }
        return loadDefaultScenario();
    }

    /**
     * Loads the default starter custom simulation scenario from versioned defaults resource.
     *
     * @return validated immutable Scenario for Simulation mode
     */
    public Scenario loadStarterSimulationScenario() {
        JsonNode root;
        try (InputStream stream = getResourceStream(DEFAULT_SIMULATION_SCENARIO_RESOURCE)) {
            root = jsonMapper.readTree(stream);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read default simulation scenario resource", e);
        }
        String scenarioId = requireText(root, "scenario_id");
        String name = requireText(root, "name");
        String createdUtc = requireText(root, "created_utc");
        JsonNode epicenterNode = requireObject(root, "epicenter");
        double lat = requireDouble(epicenterNode, "latitude");
        double lon = requireDouble(epicenterNode, "longitude");
        GeoPoint epicenter = new GeoPoint(lat, lon);
        double magnitude = requireDouble(root, "magnitude");
        double depthKm = requireDouble(root, "depth_km");

        EarthquakeEvent event = new EarthquakeEvent(
                scenarioId, "calquake", name, Instant.parse(createdUtc),
                epicenter, depthKm, magnitude, "mw", "");

        List<ReferenceLocation> locations;
        try (InputStream stream = getResourceStream(DEFAULT_LOCATIONS_RESOURCE)) {
            locations = loadReferenceLocations(stream);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read default locations resource", e);
        }
        return new Scenario(event, locations);
    }

    /**
     * Loads the starter simulation bundle with reference-free ScenarioInputs from the modular city catalog.
     */
    public ScenarioBundle loadStarterSimulationBundle(TravelTimeModel travelTimeModel) {
        return loadStarterSimulationBundle(travelTimeModel, SimulationSiteCatalog.loadDefault());
    }

    /**
     * Loads the starter simulation bundle with reference-free ScenarioInputs from a provided SimulationSiteCatalog.
     */
    public ScenarioBundle loadStarterSimulationBundle(TravelTimeModel travelTimeModel, SimulationSiteCatalog catalog) {
        Scenario scenario = loadStarterSimulationScenario();
        List<SimulationSite> sites = catalog != null ? catalog.sites() : SimulationSiteCatalog.loadDefault().sites();
        ScenarioInputs inputs = ScenarioInputs.forCustomScenario(
                EventSource.from(scenario.event()), sites, travelTimeModel);
        return new ScenarioBundle(scenario, inputs, new ScenarioReferences(Map.of()));
    }

    /** Loads the legacy display scenario plus structurally separated inputs and references. */
    public ScenarioBundle loadScenarioBundle(String eventName) {
        Scenario scenario = loadScenario(eventName);
        JsonNode manifest;
        try (InputStream stream = getResourceStream(SCIENTIFIC_INPUTS_RESOURCE)) {
            manifest = jsonMapper.readTree(stream);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read scientific input manifest", e);
        }
        JsonNode eventNode = manifest.get("events").get(scenario.event().id());
        if (eventNode == null || !eventNode.isObject()) {
            throw new IllegalArgumentException("No scientific inputs for bundled event " + scenario.event().id());
        }
        JsonNode mechanismNode = requireObject(eventNode, "mechanism");
        Mechanism mechanism = new Mechanism(
                requireDouble(mechanismNode, "rake_degrees"),
                requireDouble(mechanismNode, "strike_degrees"),
                requireDouble(mechanismNode, "dip_degrees"),
                optionalText(mechanismNode, "style", ""),
                optionalText(mechanismNode, "provenance", ""));
        JsonNode ruptureNode = requireObject(eventNode, "rupture");
        RuptureGeometry rupture = parseRupture(ruptureNode, mechanism);

        Map<String, String> eventMetadata = new LinkedHashMap<>();
        if (!scenario.event().url().isBlank()) eventMetadata.put("catalogUrl", scenario.event().url());
        eventMetadata.put("scientificManifest", SCIENTIFIC_INPUTS_RESOURCE);
        EventSource source = new EventSource(
                scenario.event().id(), scenario.event().network(), scenario.event().title(),
                scenario.event().originUtc(), scenario.event().magnitude(), scenario.event().magnitudeType(),
                scenario.event().epicenter(), scenario.event().depthKm(),
                java.util.Optional.of(rupture), java.util.Optional.of(mechanism), eventMetadata);

        JsonNode conditions = requireObject(eventNode, "site_conditions");
        List<SimulationSite> sites = new ArrayList<>();
        LinkedHashMap<String, ReferenceIntensity> references = new LinkedHashMap<>();
        for (ReferenceLocation location : scenario.locations()) {
            JsonNode value = conditions.get(location.geoid());
            if (value == null || !value.isNumber()) {
                throw new IllegalArgumentException("Missing bundled Vs30 for site " + location.geoid());
            }
            SiteCondition siteCondition = new SiteCondition(value.asDouble(),
                    SiteConditionProvenance.MAPPED_PROXY, "USGS-ShakeMap-Atlas-SVEL-Vs30-grid");
            sites.add(new SimulationSite(location.geoid(), location.city(),
                    location.internalPoint(), siteCondition));
            references.put(location.geoid(), ReferenceIntensity.fromReferenceLocation(location));
        }
        JsonNode models = requireObject(manifest, "models");
        ScientificConfiguration configuration = new ScientificConfiguration(
                Map.of(
                        "bssa14", optionalText(requireObject(models, "bssa14"), "revision", ""),
                        "worden2012", optionalText(requireObject(models, "worden2012"), "id", ""),
                        "envelope", optionalText(requireObject(models, "envelope"), "id", "")),
                Map.of(
                        "timelineStepSeconds", requireDouble(requireObject(models, "timeline"), "step_seconds"),
                        "convergenceStepSeconds", requireDouble(requireObject(models, "timeline"), "convergence_step_seconds")));
        ScenarioInputs inputs = new ScenarioInputs(source, sites,
                new TravelTimeConfiguration("Hadley-Kanamori (TauP)", "TauP-3.2.1",
                        Map.of("resource", "/data/hadley_kanamori.nd")), configuration);
        return new ScenarioBundle(scenario, inputs, new ScenarioReferences(references));
    }

    public ScenarioInputs loadScenarioInputs(String eventName) {
        return loadScenarioBundle(eventName).inputs();
    }

    public ScenarioReferences loadScenarioReferences(String eventName) {
        return loadScenarioBundle(eventName).references();
    }

    private RuptureGeometry parseRupture(JsonNode node, Mechanism mechanism) {
        JsonNode partsNode = node.get("surface_projection_parts");
        if (partsNode == null || !partsNode.isArray() || partsNode.isEmpty()) {
            throw new IllegalArgumentException("Rupture surface_projection_parts must be a non-empty array");
        }
        List<List<GeoPoint>> parts = new ArrayList<>();
        for (JsonNode partNode : partsNode) {
            if (!partNode.isArray() || partNode.size() < 2) {
                throw new IllegalArgumentException("Rupture parts require at least two [lat, lon] points");
            }
            List<GeoPoint> part = new ArrayList<>();
            for (JsonNode point : partNode) {
                if (!point.isArray() || point.size() < 2
                        || !point.get(0).isNumber() || !point.get(1).isNumber()) {
                    throw new IllegalArgumentException("Invalid rupture [lat, lon] point");
                }
                part.add(new GeoPoint(point.get(0).asDouble(), point.get(1).asDouble()));
            }
            parts.add(part);
        }
        return new RuptureGeometry(parts,
                requireDouble(node, "top_depth_km"), requireDouble(node, "bottom_depth_km"),
                mechanism.strikeDegrees(), mechanism.dipDegrees(),
                optionalText(node, "source_id", ""), optionalText(node, "source_sha256", ""), false);
    }

    /** One load operation yielding display data, predictor inputs, and evaluation references. */
    public record ScenarioBundle(Scenario scenario, ScenarioInputs inputs, ScenarioReferences references) {}

    /**
     * Loads a scenario from specified classpath resources.
     *
     * @param eventResourcePath     resource path to event JSON
     * @param locationsResourcePath resource path to reference locations JSON
     * @return validated immutable Scenario
     */
    public Scenario loadScenarioFromResources(String eventResourcePath, String locationsResourcePath) {
        EarthquakeEvent event;
        try (InputStream eventStream = getResourceStream(eventResourcePath)) {
            event = loadEvent(eventStream);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read event resource: " + eventResourcePath, e);
        }

        List<ReferenceLocation> locations;
        try (InputStream locationsStream = getResourceStream(locationsResourcePath)) {
            locations = loadReferenceLocations(locationsStream);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read locations resource: " + locationsResourcePath, e);
        }

        return new Scenario(event, locations);
    }

    /**
     * Loads an EarthquakeEvent from an InputStream.
     *
     * @param inputStream JSON input stream
     * @return validated immutable EarthquakeEvent
     */
    public EarthquakeEvent loadEvent(InputStream inputStream) {
        Objects.requireNonNull(inputStream, "inputStream cannot be null");
        try {
            JsonNode root = jsonMapper.readTree(inputStream);
            return parseEventNode(root);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Malformed event JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Loads an EarthquakeEvent from a JSON string.
     *
     * @param jsonString JSON content
     * @return validated immutable EarthquakeEvent
     */
    public EarthquakeEvent loadEvent(String jsonString) {
        Objects.requireNonNull(jsonString, "jsonString cannot be null");
        try {
            JsonNode root = jsonMapper.readTree(jsonString);
            return parseEventNode(root);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Malformed event JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Loads a list of ReferenceLocations from an InputStream.
     *
     * @param inputStream JSON input stream
     * @return validated immutable list of ReferenceLocation
     */
    public List<ReferenceLocation> loadReferenceLocations(InputStream inputStream) {
        Objects.requireNonNull(inputStream, "inputStream cannot be null");
        try {
            JsonNode root = jsonMapper.readTree(inputStream);
            return parseLocationsNode(root);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Malformed reference locations JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Loads a list of ReferenceLocations from a JSON string.
     *
     * @param jsonString JSON content
     * @return validated immutable list of ReferenceLocation
     */
    public List<ReferenceLocation> loadReferenceLocations(String jsonString) {
        Objects.requireNonNull(jsonString, "jsonString cannot be null");
        try {
            JsonNode root = jsonMapper.readTree(jsonString);
            return parseLocationsNode(root);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Malformed reference locations JSON: " + e.getMessage(), e);
        }
    }

    private EarthquakeEvent parseEventNode(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Event JSON root must be an object");
        }

        // Support wrapping in an "event" property (as in provenance_manifest.json)
        JsonNode eventNode = root.has("event") && root.get("event").isObject() ? root.get("event") : root;

        String id = requireText(eventNode, "id");
        String network = optionalText(eventNode, "network", "");
        String title = optionalText(eventNode, "title", "");

        String originUtcStr = requireText(eventNode, "origin_utc");
        Instant originUtc;
        try {
            originUtc = Instant.parse(originUtcStr);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid origin_utc ISO-8601 timestamp: " + originUtcStr, e);
        }

        double latitude = requireDouble(eventNode, "latitude");
        double longitude = requireDouble(eventNode, "longitude");
        GeoPoint epicenter = new GeoPoint(latitude, longitude);

        double depthKm = requireDouble(eventNode, "depth_km");
        if (depthKm < 0.0) {
            throw new IllegalArgumentException("Depth cannot be negative: " + depthKm);
        }

        double magnitude = requireDouble(eventNode, "magnitude");
        String magnitudeType = optionalText(eventNode, "magnitude_type", "");
        String url = optionalText(eventNode, "url", "");

        return new EarthquakeEvent(id, network, title, originUtc, epicenter, depthKm, magnitude, magnitudeType, url);
    }

    private List<ReferenceLocation> parseLocationsNode(JsonNode root) {
        if (root == null || (!root.isObject() && !root.isArray())) {
            throw new IllegalArgumentException("Locations JSON root must be an object or array");
        }

        JsonNode locationsArray = root.isArray() ? root : root.get("locations");
        if (locationsArray == null || !locationsArray.isArray() || locationsArray.isEmpty()) {
            throw new IllegalArgumentException("Missing or empty 'locations' array");
        }

        List<ReferenceLocation> locations = new ArrayList<>();
        for (int i = 0; i < locationsArray.size(); i++) {
            JsonNode locNode = locationsArray.get(i);
            if (!locNode.isObject()) {
                throw new IllegalArgumentException("Location entry at index " + i + " must be an object");
            }
            locations.add(parseSingleLocation(locNode, i));
        }

        return List.copyOf(locations);
    }

    private ReferenceLocation parseSingleLocation(JsonNode node, int index) {
        String city = requireText(node, "city");
        String officialName = optionalText(node, "official_name", city);
        String geoid = requireText(node, "geoid");
        String ansicode = optionalText(node, "ansicode", "");
        String lsad = optionalText(node, "lsad", "");

        // internal_point
        JsonNode internalPointNode = requireObject(node, "internal_point");
        double intLat = requireDouble(internalPointNode, "latitude");
        double intLon = requireDouble(internalPointNode, "longitude");
        GeoPoint internalPoint = new GeoPoint(intLat, intLon);

        // sampled_grid_node
        JsonNode sampledNode = requireObject(node, "sampled_grid_node");
        double nodeLat = requireDouble(sampledNode, "latitude");
        double nodeLon = requireDouble(sampledNode, "longitude");
        double offsetKm = requireDouble(sampledNode, "offset_km");
        ReferenceLocation.SampledGridNode gridNode =
                new ReferenceLocation.SampledGridNode(new GeoPoint(nodeLat, nodeLon), offsetKm);

        // peak_intensity
        JsonNode intensityNode = requireObject(node, "peak_intensity");
        double mmiSourceDecimal = requireDouble(intensityNode, "mmi_source_decimal");
        double mmiDisplayRounded = requireDouble(intensityNode, "mmi_display_rounded");
        String mmiRoman = optionalText(intensityNode, "mmi_roman", "");
        String shakingDescription = optionalText(intensityNode, "shaking_description", "");
        String damageDescription = optionalText(intensityNode, "damage_description", "");
        String colorHex = optionalText(intensityNode, "color_hex", "#ffffff");
        ReferenceLocation.PeakIntensity peakIntensity = new ReferenceLocation.PeakIntensity(
                mmiSourceDecimal, mmiDisplayRounded, mmiRoman, shakingDescription, damageDescription, colorHex
        );

        // optional ground_motion
        ReferenceLocation.GroundMotion groundMotion = null;
        if (node.has("ground_motion") && node.get("ground_motion").isObject()) {
            JsonNode gm = node.get("ground_motion");
            groundMotion = new ReferenceLocation.GroundMotion(
                    requireDouble(gm, "pga_pct_g"),
                    requireDouble(gm, "pgv_cm_s"),
                    requireDouble(gm, "psa03_pct_g"),
                    requireDouble(gm, "psa10_pct_g"),
                    requireDouble(gm, "psa30_pct_g"),
                    requireDouble(gm, "svel_m_s")
            );
        }

        return new ReferenceLocation(
                city, officialName, geoid, ansicode, lsad, internalPoint, gridNode, peakIntensity, groundMotion
        );
    }

    private static String requireText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isString() || field.asString().isBlank()) {
            throw new IllegalArgumentException("Missing or invalid required text field: '" + fieldName + "'");
        }
        return field.asString().trim();
    }

    private static String optionalText(JsonNode node, String fieldName, String defaultValue) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isString()) {
            return defaultValue;
        }
        return field.asString().trim();
    }

    private static double requireDouble(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isNumber()) {
            throw new IllegalArgumentException("Missing or invalid required numeric field: '" + fieldName + "'");
        }
        double val = field.asDouble();
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            throw new IllegalArgumentException("Numeric field '" + fieldName + "' must be finite: " + val);
        }
        return val;
    }

    private static JsonNode requireObject(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isObject()) {
            throw new IllegalArgumentException("Missing or invalid required object field: '" + fieldName + "'");
        }
        return field;
    }

    private static InputStream getResourceStream(String resourcePath) {
        InputStream stream = ScenarioLoader.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IllegalArgumentException("Classpath resource not found: " + resourcePath);
        }
        return stream;
    }
}
