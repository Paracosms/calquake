package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.SimulationSite;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Jackson-based loader and container for simulation sites.
 */
public final class JsonSimulationSiteCatalog implements SimulationSiteCatalog {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final List<SimulationSite> sites;

    public JsonSimulationSiteCatalog(List<SimulationSite> sites) {
        Objects.requireNonNull(sites, "sites cannot be null");
        if (sites.isEmpty()) {
            throw new IllegalArgumentException("Simulation site catalog cannot be empty");
        }
        Set<String> seenIds = new HashSet<>();
        for (SimulationSite site : sites) {
            Objects.requireNonNull(site, "Simulation site cannot be null");
            if (!seenIds.add(site.id().toLowerCase())) {
                throw new IllegalArgumentException("Duplicate simulation site id: " + site.id());
            }
        }
        this.sites = List.copyOf(sites);
    }

    @Override
    public List<SimulationSite> sites() {
        return sites;
    }

    public static JsonSimulationSiteCatalog loadFromResource(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath cannot be null");
        try (InputStream stream = JsonSimulationSiteCatalog.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalArgumentException("Simulation sites resource not found: " + resourcePath);
            }
            return loadFromJsonStream(stream);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read simulation sites resource: " + resourcePath, e);
        }
    }

    public static JsonSimulationSiteCatalog loadFromJsonStream(InputStream stream) {
        Objects.requireNonNull(stream, "stream cannot be null");
        try {
            JsonNode root = JSON_MAPPER.readTree(stream);
            return fromJsonNode(root);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("Failed to parse simulation sites JSON", e);
        }
    }

    public static JsonSimulationSiteCatalog loadFromJsonString(String json) {
        Objects.requireNonNull(json, "json cannot be null");
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            return fromJsonNode(root);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("Failed to parse simulation sites JSON", e);
        }
    }

    public static JsonSimulationSiteCatalog fromJsonNode(JsonNode root) {
        Objects.requireNonNull(root, "root cannot be null");
        JsonNode sitesNode;
        if (root.isArray()) {
            sitesNode = root;
        } else if (root.isObject() && root.has("sites") && root.get("sites").isArray()) {
            sitesNode = root.get("sites");
        } else {
            throw new IllegalArgumentException("JSON must be an array of sites or an object with a 'sites' array");
        }

        List<SimulationSite> sites = new ArrayList<>();
        for (int i = 0; i < sitesNode.size(); i++) {
            JsonNode item = sitesNode.get(i);
            if (!item.isObject()) {
                throw new IllegalArgumentException("Site entry at index " + i + " is not a JSON object");
            }
            String id = requireText(item, "id", i);
            String displayName = resolveDisplayName(item, i);
            double latitude = requireCoordinate(item, "latitude", i, -90.0, 90.0);
            double longitude = requireCoordinate(item, "longitude", i, -180.0, 180.0);

            sites.add(new SimulationSite(id, displayName, new GeoPoint(latitude, longitude)));
        }

        return new JsonSimulationSiteCatalog(sites);
    }

    private static String requireText(JsonNode node, String fieldName, int index) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isString() || field.asString().isBlank()) {
            throw new IllegalArgumentException("Site at index " + index + " must have a non-blank '" + fieldName + "' string");
        }
        return field.asString().trim();
    }

    private static String resolveDisplayName(JsonNode node, int index) {
        if (node.has("display_name")) {
            return requireText(node, "display_name", index);
        }
        if (node.has("displayName")) {
            return requireText(node, "displayName", index);
        }
        if (node.has("name")) {
            return requireText(node, "name", index);
        }
        throw new IllegalArgumentException("Site at index " + index + " is missing 'display_name'");
    }

    private static double requireCoordinate(JsonNode node, String fieldName, int index, double min, double max) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isNumber()) {
            throw new IllegalArgumentException("Site at index " + index + " must have a numeric '" + fieldName + "'");
        }
        double val = field.asDouble();
        if (!Double.isFinite(val) || val < min || val > max) {
            throw new IllegalArgumentException(String.format(
                    "Site at index %d has invalid %s: %f (must be finite in [%f, %f])", index, fieldName, val, min, max));
        }
        return val;
    }
}
