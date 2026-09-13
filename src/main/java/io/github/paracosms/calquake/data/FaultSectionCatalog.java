package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.FaultSection;
import io.github.paracosms.calquake.core.GeoPoint;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads, validates, and caches the offline normalized NSHM23 fault section modeling catalog
 * from {@code /data/geodata/california_fault_sections.geojson}.
 */
public final class FaultSectionCatalog {

    public static final String DEFAULT_RESOURCE = "/data/geodata/california_fault_sections.geojson";
    public static final String CATALOG_ID = "nshm23-california-fault-sections-v1";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static volatile FaultSectionCatalog defaultInstance;

    private final List<FaultSection> sections;
    private final String sha256;

    public FaultSectionCatalog(List<FaultSection> sections, String sha256) {
        Objects.requireNonNull(sections, "sections cannot be null");
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("Fault section catalog cannot be empty");
        }
        this.sections = List.copyOf(sections);
        this.sha256 = sha256 == null ? "" : sha256.trim().toLowerCase();
    }

    public static FaultSectionCatalog loadDefault() {
        FaultSectionCatalog instance = defaultInstance;
        if (instance == null) {
            synchronized (FaultSectionCatalog.class) {
                instance = defaultInstance;
                if (instance == null) {
                    defaultInstance = instance = loadFromResource(DEFAULT_RESOURCE);
                }
            }
        }
        return instance;
    }

    public static FaultSectionCatalog loadFromResource(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath cannot be null");
        try (InputStream stream = FaultSectionCatalog.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Required fault section catalog resource not found: " + resourcePath);
            }
            byte[] bytes = stream.readAllBytes();
            String sha256 = computeSha256(bytes);
            JsonNode root = JSON_MAPPER.readTree(bytes);
            return fromJsonNode(root, sha256);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read fault section catalog: " + resourcePath, e);
        }
    }

    public static FaultSectionCatalog fromJsonNode(JsonNode root, String sha256) {
        Objects.requireNonNull(root, "root cannot be null");
        JsonNode featuresNode = root.get("features");
        if (featuresNode == null || !featuresNode.isArray()) {
            throw new IllegalArgumentException("Fault section catalog missing 'features' array");
        }
        if (featuresNode.isEmpty()) {
            throw new IllegalArgumentException("Fault section catalog 'features' array is empty");
        }

        List<FaultSection> list = new ArrayList<>(featuresNode.size());
        for (int i = 0; i < featuresNode.size(); i++) {
            JsonNode feature = featuresNode.get(i);
            JsonNode props = feature.get("properties");
            JsonNode geom = feature.get("geometry");
            if (props == null || geom == null) {
                throw new IllegalArgumentException("Fault feature at index " + i + " missing properties or geometry");
            }

            long sectionId = requireLong(props, "section_id");
            String sectionName = requireText(props, "section_name");
            String state = optionalText(props, "state", "CA");
            double dipDeg = requireDouble(props, "dip_deg");
            String dipDir = requireText(props, "dip_dir");
            double rakeDeg = requireDouble(props, "rake_deg");
            double upperDepthKm = requireDouble(props, "upper_depth_km");
            double lowerDepthKm = requireDouble(props, "lower_depth_km");

            JsonNode coordsNode = geom.get("coordinates");
            if (coordsNode == null || !coordsNode.isArray() || coordsNode.size() < 2) {
                throw new IllegalArgumentException("Invalid trace coordinates for section " + sectionId);
            }
            List<GeoPoint> trace = new ArrayList<>(coordsNode.size());
            for (JsonNode ptNode : coordsNode) {
                if (!ptNode.isArray() || ptNode.size() < 2) {
                    throw new IllegalArgumentException("Invalid point coordinate in section " + sectionId);
                }
                double lon = ptNode.get(0).asDouble();
                double lat = ptNode.get(1).asDouble();
                trace.add(new GeoPoint(lat, lon));
            }

            list.add(new FaultSection(
                    sectionId, sectionName, state, trace, dipDeg, dipDir, rakeDeg, upperDepthKm, lowerDepthKm));
        }

        return new FaultSectionCatalog(list, sha256);
    }

    public List<FaultSection> sections() {
        return sections;
    }

    public int size() {
        return sections.size();
    }

    public String datasetId() {
        return CATALOG_ID;
    }

    public String sha256() {
        return sha256;
    }

    public Optional<FaultSection> findById(long sectionId) {
        return sections.stream().filter(s -> s.sectionId() == sectionId).findFirst();
    }

    private static String computeSha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String requireText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isString() || field.asString().isBlank()) {
            throw new IllegalArgumentException("Missing or blank field '" + fieldName + "'");
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
            throw new IllegalArgumentException("Missing or invalid numeric field '" + fieldName + "'");
        }
        double val = field.asDouble();
        if (!Double.isFinite(val)) {
            throw new IllegalArgumentException("Field '" + fieldName + "' must be finite: " + val);
        }
        return val;
    }

    private static long requireLong(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isIntegralNumber()) {
            throw new IllegalArgumentException("Missing or invalid integer field '" + fieldName + "'");
        }
        return field.asLong();
    }
}
