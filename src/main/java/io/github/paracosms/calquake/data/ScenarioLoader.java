package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.EarthquakeEvent;
import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.ReferenceLocation;
import io.github.paracosms.calquake.core.Scenario;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads and validates normalized event and reference location resources using Jackson.
 * Enforces strict validation of coordinates, units, depth, and timestamps while
 * preserving exact source precision and direct constructor wiring.
 */
public class ScenarioLoader {

    public static final String DEFAULT_EVENT_RESOURCE = "/data/event.json";
    public static final String DEFAULT_LOCATIONS_RESOURCE = "/data/five_reference_locations.json";

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
