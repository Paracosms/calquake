package io.github.paracosms.calquake.data;

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
import java.util.List;
import java.util.Objects;

/**
 * Dedicated serializer and loader for CalQuake simulation sites (cities).
 * Implements atomic save and transactional, validating import semantics.
 */
public final class SimulationSiteSerializer {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private SimulationSiteSerializer() {}

    /**
     * Serializes simulation sites to a pretty-printed UTF-8 JSON string array.
     */
    public static String toJson(List<SimulationSite> sites) {
        Objects.requireNonNull(sites, "sites cannot be null");
        if (sites.isEmpty()) {
            throw new IllegalArgumentException("Simulation sites list cannot be empty");
        }
        ArrayNode root = JSON_MAPPER.createArrayNode();
        for (SimulationSite site : sites) {
            ObjectNode siteNode = root.addObject();
            siteNode.put("id", site.id());
            siteNode.put("display_name", site.displayName());
            siteNode.put("latitude", site.coordinates().latitude());
            siteNode.put("longitude", site.coordinates().longitude());
        }
        try {
            return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize simulation sites to JSON", e);
        }
    }

    /**
     * Atomically writes the simulation sites to the destination file.
     * Writes to a temporary sibling file first, then atomically moves it to replace destination.
     */
    public static void writeToFile(List<SimulationSite> sites, Path targetFile) throws IOException {
        Objects.requireNonNull(sites, "sites cannot be null");
        Objects.requireNonNull(targetFile, "targetFile cannot be null");

        Path absTarget = targetFile.toAbsolutePath().normalize();
        Path parent = absTarget.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = Files.createTempFile(parent != null ? parent : Path.of("."), ".calquake-cities-tmp-", ".json");
        try {
            String json = toJson(sites);
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
     * Reads and parses simulation sites from a file.
     */
    public static List<SimulationSite> readFromFile(Path path) throws IOException {
        Objects.requireNonNull(path, "path cannot be null");
        try (InputStream stream = Files.newInputStream(path)) {
            return fromInputStream(stream);
        }
    }

    /**
     * Parses and validates simulation sites from an InputStream.
     */
    public static List<SimulationSite> fromInputStream(InputStream stream) {
        Objects.requireNonNull(stream, "stream cannot be null");
        try {
            JsonNode root = JSON_MAPPER.readTree(stream);
            return fromJsonNode(root);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Malformed simulation sites JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Parses and validates simulation sites from a JSON string.
     */
    public static List<SimulationSite> fromJson(String json) {
        Objects.requireNonNull(json, "json cannot be null");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Simulation sites JSON cannot be blank");
        }
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            return fromJsonNode(root);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Malformed simulation sites JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Parses and validates a JSON node according to simulation site catalog rules.
     */
    public static List<SimulationSite> fromJsonNode(JsonNode root) {
        return JsonSimulationSiteCatalog.fromJsonNode(root).sites();
    }
}
