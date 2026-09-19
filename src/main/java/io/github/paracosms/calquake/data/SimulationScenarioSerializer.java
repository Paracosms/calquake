package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.IntensityDisplayMode;
import io.github.paracosms.calquake.core.SimulationAssumptionSet;
import io.github.paracosms.calquake.core.SimulationScenarioSettings;
import io.github.paracosms.calquake.core.SimulationSite;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/**
 * Dedicated serializer and loader for CalQuake simulation scenarios.
 * Implements atomic save and transactional, validating import semantics.
 */
public final class SimulationScenarioSerializer {

    /**
     * Container for loaded scenario settings and optional bundled cities.
     */
    public record LoadedScenario(
            SimulationScenarioSettings settings,
            List<SimulationSite> cities
    ) {
        public LoadedScenario {
            Objects.requireNonNull(settings, "settings cannot be null");
            cities = (cities == null || cities.isEmpty()) ? List.of() : List.copyOf(cities);
        }

        public boolean hasCities() {
            return !cities.isEmpty();
        }
    }

    public sealed interface ImportedPackage permits ImportedScenario, ImportedSiteCatalog {
    }

    public record ImportedScenario(LoadedScenario scenario) implements ImportedPackage {
        public ImportedScenario {
            Objects.requireNonNull(scenario, "scenario cannot be null");
        }
    }

    public record ImportedSiteCatalog(List<SimulationSite> sites) implements ImportedPackage {
        public ImportedSiteCatalog {
            Objects.requireNonNull(sites, "sites cannot be null");
            if (sites.isEmpty()) {
                throw new IllegalArgumentException("Sites catalog cannot be empty");
            }
            sites = List.copyOf(sites);
        }
    }

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private SimulationScenarioSerializer() {}

    /**
     * Serializes scenario settings to a versioned UTF-8 JSON string without bundled cities.
     */
    public static String toJson(SimulationScenarioSettings settings) {
        return toJson(settings, null);
    }

    /**
     * Serializes scenario settings and optional bundled cities to a versioned UTF-8 JSON string.
     */
    public static String toJson(SimulationScenarioSettings settings, List<SimulationSite> sites) {
        Objects.requireNonNull(settings, "settings cannot be null");
        ObjectNode root = JSON_MAPPER.createObjectNode();
        root.put("schema_version", SimulationScenarioDto.CURRENT_SCHEMA_VERSION);
        root.put("type", SimulationScenarioDto.SCENARIO_TYPE);
        root.put("scenario_id", settings.scenarioId());
        root.put("name", settings.displayName());
        root.put("created_utc", settings.createdUtc().toString());

        ObjectNode epicenterNode = root.putObject("epicenter");
        epicenterNode.put("latitude", settings.epicenter().latitude());
        epicenterNode.put("longitude", settings.epicenter().longitude());

        root.put("magnitude", settings.magnitude());
        root.put("depth_km", settings.depthKm());
        root.put("intensity_display_mode", settings.intensityDisplayMode().name());
        root.put("assumption_set", settings.assumptionSetId());

        if (sites != null && !sites.isEmpty()) {
            ArrayNode citiesArray = root.putArray("cities");
            for (SimulationSite site : sites) {
                ObjectNode siteNode = citiesArray.addObject();
                siteNode.put("id", site.id());
                siteNode.put("display_name", site.displayName());
                siteNode.put("latitude", site.coordinates().latitude());
                siteNode.put("longitude", site.coordinates().longitude());
            }
        }

        try {
            return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize simulation scenario to JSON", e);
        }
    }

    /**
     * Atomically writes the given scenario settings to the destination file.
     * Writes to a temporary sibling file first, then atomically moves it to replace destination.
     */
    public static void writeToFile(SimulationScenarioSettings settings, Path targetFile) throws IOException {
        writeToFile(settings, null, targetFile);
    }

    /**
     * Atomically writes the given scenario settings and optional bundled cities to the destination file.
     * Writes to a temporary sibling file first, then atomically moves it to replace destination.
     */
    public static void writeToFile(SimulationScenarioSettings settings, List<SimulationSite> sites, Path targetFile) throws IOException {
        Objects.requireNonNull(settings, "settings cannot be null");
        Objects.requireNonNull(targetFile, "targetFile cannot be null");

        Path absTarget = targetFile.toAbsolutePath().normalize();
        Path parent = absTarget.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = Files.createTempFile(parent != null ? parent : Path.of("."), ".calquake-tmp-", ".json");
        try {
            String json = toJson(settings, sites);
            Files.writeString(tempFile, json, StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tempFile, absTarget, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, absTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Reads and parses scenario settings and optional bundled cities from a file.
     */
    public static LoadedScenario readPackageFromFile(Path path) throws IOException {
        Objects.requireNonNull(path, "path cannot be null");
        try (InputStream stream = Files.newInputStream(path)) {
            return fromPackageInputStream(stream);
        }
    }

    /**
     * Parses and validates scenario settings and optional bundled cities from an InputStream.
     */
    public static LoadedScenario fromPackageInputStream(InputStream stream) {
        Objects.requireNonNull(stream, "stream cannot be null");
        try {
            JsonNode root = JSON_MAPPER.readTree(stream);
            return fromPackageJsonNode(root);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Malformed simulation scenario JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Parses and validates scenario settings and optional bundled cities from a JSON string.
     */
    public static LoadedScenario fromPackageJson(String json) {
        Objects.requireNonNull(json, "json cannot be null");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Simulation scenario JSON cannot be blank");
        }
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            return fromPackageJsonNode(root);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Malformed simulation scenario JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Reads and parses scenario settings from a file.
     */
    public static SimulationScenarioSettings readFromFile(Path path) throws IOException {
        return readPackageFromFile(path).settings();
    }

    /**
     * Parses and validates scenario settings from an InputStream.
     */
    public static SimulationScenarioSettings fromInputStream(InputStream stream) {
        return fromPackageInputStream(stream).settings();
    }

    /**
     * Parses and validates scenario settings from a JSON string.
     */
    public static SimulationScenarioSettings fromJson(String json) {
        return fromPackageJson(json).settings();
    }

    /**
     * Parses and validates a JSON node according to version 1 schema rules, returning settings.
     */
    public static SimulationScenarioSettings fromJsonNode(JsonNode root) {
        return fromPackageJsonNode(root).settings();
    }

    /**
     * Parses and validates a JSON node according to version 1 schema rules, returning scenario package.
     */
    public static LoadedScenario fromPackageJsonNode(JsonNode root) {
        Objects.requireNonNull(root, "root cannot be null");
        if (!root.isObject()) {
            throw new IllegalArgumentException("Root JSON node must be an object");
        }

        // 1. schema_version
        JsonNode schemaVerNode = root.get("schema_version");
        if (schemaVerNode == null || !schemaVerNode.isInt()) {
            throw new IllegalArgumentException("Missing or invalid 'schema_version' (integer required)");
        }
        int schemaVer = schemaVerNode.asInt();
        if (schemaVer != SimulationScenarioDto.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema version: " + schemaVer
                    + ". Expected version " + SimulationScenarioDto.CURRENT_SCHEMA_VERSION);
        }

        // 2. type
        JsonNode typeNode = root.get("type");
        if (typeNode == null || !typeNode.isString() || typeNode.asString().isBlank()) {
            throw new IllegalArgumentException("Missing or blank required 'type' field");
        }
        String type = typeNode.asString().trim();
        if (!SimulationScenarioDto.SCENARIO_TYPE.equalsIgnoreCase(type) && !"simulation_scenario".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("Unknown scenario type: '" + type
                    + "'. Expected '" + SimulationScenarioDto.SCENARIO_TYPE + "'");
        }

        // 3. scenario_id
        JsonNode scenarioIdNode = root.get("scenario_id");
        if (scenarioIdNode == null || !scenarioIdNode.isString() || scenarioIdNode.asString().isBlank()) {
            throw new IllegalArgumentException("Missing or blank 'scenario_id'");
        }
        String scenarioId = scenarioIdNode.asString().trim();

        // 4. name
        JsonNode nameNode = root.get("name");
        if (nameNode == null || !nameNode.isString() || nameNode.asString().isBlank()) {
            throw new IllegalArgumentException("Missing or blank 'name'");
        }
        String name = nameNode.asString().trim();

        // 5. created_utc
        JsonNode createdUtcNode = root.get("created_utc");
        if (createdUtcNode == null || !createdUtcNode.isString() || createdUtcNode.asString().isBlank()) {
            throw new IllegalArgumentException("Missing or blank 'created_utc'");
        }
        Instant createdUtc;
        try {
            createdUtc = Instant.parse(createdUtcNode.asString().trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid 'created_utc' timestamp format: " + createdUtcNode.asString(), e);
        }

        // 6. epicenter
        JsonNode epiNode = root.get("epicenter");
        if (epiNode == null || !epiNode.isObject()) {
            throw new IllegalArgumentException("Missing or invalid 'epicenter' object");
        }
        JsonNode latNode = epiNode.get("latitude");
        if (latNode == null || !latNode.isNumber()) {
            throw new IllegalArgumentException("Missing or invalid 'latitude' in epicenter");
        }
        double lat = latNode.asDouble();
        if (!Double.isFinite(lat) || lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Epicenter latitude must be a finite number between -90 and 90: " + lat);
        }

        JsonNode lonNode = epiNode.get("longitude");
        if (lonNode == null || !lonNode.isNumber()) {
            throw new IllegalArgumentException("Missing or invalid 'longitude' in epicenter");
        }
        double lon = lonNode.asDouble();
        if (!Double.isFinite(lon) || lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("Epicenter longitude must be a finite number between -180 and 180: " + lon);
        }
        GeoPoint epicenter = new GeoPoint(lat, lon);

        // 7. magnitude
        JsonNode magNode = root.get("magnitude");
        if (magNode == null || !magNode.isNumber()) {
            throw new IllegalArgumentException("Missing or invalid 'magnitude'");
        }
        double mag = magNode.asDouble();
        if (!Double.isFinite(mag)) {
            throw new IllegalArgumentException("Magnitude must be a finite number: " + mag);
        }

        // 8. depth_km
        JsonNode depthNode = root.get("depth_km");
        if (depthNode == null || !depthNode.isNumber()) {
            throw new IllegalArgumentException("Missing or invalid 'depth_km'");
        }
        double depth = depthNode.asDouble();
        if (!Double.isFinite(depth) || depth < 0.0) {
            throw new IllegalArgumentException("Depth must be a finite non-negative number: " + depth);
        }

        // 9. intensity_display_mode
        JsonNode modeNode = root.get("intensity_display_mode");
        if (modeNode == null || !modeNode.isString() || modeNode.asString().isBlank()) {
            throw new IllegalArgumentException("Missing or invalid 'intensity_display_mode'");
        }
        String modeStr = modeNode.asString().trim();
        IntensityDisplayMode displayMode;
        try {
            displayMode = IntensityDisplayMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            displayMode = IntensityDisplayMode.fromLabel(modeStr);
        }

        // 10. assumption_set
        JsonNode assumptionNode = root.get("assumption_set");
        if (assumptionNode == null || !assumptionNode.isString() || assumptionNode.asString().isBlank()) {
            throw new IllegalArgumentException("Missing or invalid 'assumption_set'");
        }
        String assumptionSetId = assumptionNode.asString().trim();
        // Resolves explicitly; unknown versions fail safely
        SimulationAssumptionSet.resolve(assumptionSetId);

        // 11. Optional bundled cities
        List<SimulationSite> sites = null;
        JsonNode citiesNode = root.get("cities");
        if (citiesNode == null || citiesNode.isNull() || citiesNode.isMissingNode()) {
            citiesNode = root.get("sites");
        }
        if (citiesNode != null && !citiesNode.isNull() && !citiesNode.isMissingNode()) {
            try {
                sites = SimulationSiteSerializer.fromJsonNode(citiesNode);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid bundled cities in simulation scenario: " + e.getMessage(), e);
            }
            if (sites.isEmpty()) {
                throw new IllegalArgumentException("Bundled cities list cannot be empty");
            }
        }

        SimulationScenarioSettings settings = new SimulationScenarioSettings(
                scenarioId,
                name,
                createdUtc,
                epicenter,
                mag,
                depth,
                displayMode,
                assumptionSetId
        );
        return new LoadedScenario(settings, sites);
    }

    /**
     * Reads and parses any supported CalQuake data package from a file (scenario or sites).
     */
    public static ImportedPackage readAnyFromFile(Path path) throws IOException {
        Objects.requireNonNull(path, "path cannot be null");
        try (InputStream stream = Files.newInputStream(path)) {
            return fromAnyInputStream(stream);
        }
    }

    /**
     * Parses and validates any supported CalQuake data package from an InputStream.
     */
    public static ImportedPackage fromAnyInputStream(InputStream stream) {
        Objects.requireNonNull(stream, "stream cannot be null");
        try {
            JsonNode root = JSON_MAPPER.readTree(stream);
            return fromAnyJsonNode(root);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Malformed JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Parses and validates any supported CalQuake data package from a JSON string.
     */
    public static ImportedPackage fromAnyJson(String json) {
        Objects.requireNonNull(json, "json cannot be null");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Imported JSON cannot be blank");
        }
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            return fromAnyJsonNode(root);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Malformed JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Inspects and parses any supported CalQuake data package from a JSON node.
     */
    public static ImportedPackage fromAnyJsonNode(JsonNode root) {
        Objects.requireNonNull(root, "root cannot be null");
        if (root.isArray()) {
            List<SimulationSite> sites = SimulationSiteSerializer.fromJsonNode(root);
            return new ImportedSiteCatalog(sites);
        }
        if (root.isObject()) {
            boolean isScenario = (root.has("type") && (SimulationScenarioDto.SCENARIO_TYPE.equalsIgnoreCase(root.get("type").asString())
                    || "simulation_scenario".equalsIgnoreCase(root.get("type").asString())))
                    || (root.has("schema_version") && root.has("epicenter"));
            if (isScenario) {
                LoadedScenario scenario = fromPackageJsonNode(root);
                return new ImportedScenario(scenario);
            }
            if ((root.has("cities") && root.get("cities").isArray()) || (root.has("sites") && root.get("sites").isArray())) {
                List<SimulationSite> sites = SimulationSiteSerializer.fromJsonNode(root);
                return new ImportedSiteCatalog(sites);
            }
            throw new IllegalArgumentException("Unrecognized JSON format: Expected simulation scenario or sites catalog");
        }
        throw new IllegalArgumentException("Root JSON node must be an object or array");
    }
}
