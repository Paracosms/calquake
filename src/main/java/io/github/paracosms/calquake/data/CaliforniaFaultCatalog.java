package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.MappedFault;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Loads and caches the authoritative USGS Quaternary Fault and Fold Database
 * (California catalog) from {@code /data/geodata/california_faults.geojson}.
 */
public final class CaliforniaFaultCatalog {

    public static final String DEFAULT_RESOURCE = "/data/geodata/california_faults.geojson";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private static volatile CaliforniaFaultCatalog defaultInstance;

    private final List<MappedFault> faults;

    public CaliforniaFaultCatalog(List<MappedFault> faults) {
        Objects.requireNonNull(faults, "faults cannot be null");
        this.faults = List.copyOf(faults);
    }

    public static CaliforniaFaultCatalog loadDefault() {
        CaliforniaFaultCatalog instance = defaultInstance;
        if (instance == null) {
            synchronized (CaliforniaFaultCatalog.class) {
                instance = defaultInstance;
                if (instance == null) {
                    defaultInstance = instance = loadFromResource(DEFAULT_RESOURCE);
                }
            }
        }
        return instance;
    }

    public static CaliforniaFaultCatalog loadFromResource(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath cannot be null");
        try (InputStream stream = CaliforniaFaultCatalog.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalArgumentException("Fault catalog resource not found: " + resourcePath);
            }
            return loadFromStream(stream);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read fault catalog: " + resourcePath, e);
        }
    }

    public static CaliforniaFaultCatalog loadFromStream(InputStream stream) {
        Objects.requireNonNull(stream, "stream cannot be null");
        try {
            JsonNode root = JSON_MAPPER.readTree(stream);
            return fromJsonNode(root);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("Failed to parse fault catalog GeoJSON", e);
        }
    }

    public static CaliforniaFaultCatalog fromJsonNode(JsonNode root) {
        Objects.requireNonNull(root, "root cannot be null");
        JsonNode featuresNode = root.get("features");
        if (featuresNode == null || !featuresNode.isArray()) {
            throw new IllegalArgumentException("GeoJSON missing 'features' array");
        }

        List<MappedFault> list = new ArrayList<>();
        for (int i = 0; i < featuresNode.size(); i++) {
            JsonNode feature = featuresNode.get(i);
            JsonNode props = feature.get("properties");
            JsonNode geom = feature.get("geometry");

            if (geom == null || props == null) {
                continue;
            }

            int objectId = props.has("object_id") ? props.get("object_id").asInt() : 0;
            String faultId = getText(props, "fault_id");
            String sectionId = getText(props, "section_id");
            String faultName = getText(props, "fault_name");
            String sectionName = getText(props, "section_name");
            String age = getText(props, "age");
            String lineType = getText(props, "line_type");
            String mappedCertainty = getText(props, "mapped_certainty");
            String strike = getText(props, "strike");
            String slipSense = getText(props, "slip_sense");
            String dipDirection = getText(props, "dip_direction");
            String faultUrl = getText(props, "fault_url");

            String geomType = geom.has("type") ? geom.get("type").asString() : "";
            JsonNode coordsNode = geom.get("coordinates");
            if (coordsNode == null || !coordsNode.isArray()) {
                continue;
            }

            List<List<GeoPoint>> polylines = new ArrayList<>();
            if ("LineString".equalsIgnoreCase(geomType)) {
                List<GeoPoint> line = parseLine(coordsNode);
                if (line.size() >= 2) {
                    polylines.add(line);
                }
            } else if ("MultiLineString".equalsIgnoreCase(geomType)) {
                for (int j = 0; j < coordsNode.size(); j++) {
                    List<GeoPoint> line = parseLine(coordsNode.get(j));
                    if (line.size() >= 2) {
                        polylines.add(line);
                    }
                }
            }

            if (!polylines.isEmpty()) {
                list.add(new MappedFault(
                        objectId, faultId, sectionId, faultName, sectionName, age,
                        lineType, mappedCertainty, strike, slipSense, dipDirection, faultUrl,
                        polylines
                ));
            }
        }

        return new CaliforniaFaultCatalog(list);
    }

    private static List<GeoPoint> parseLine(JsonNode lineNode) {
        if (lineNode == null || !lineNode.isArray()) {
            return Collections.emptyList();
        }
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < lineNode.size(); i++) {
            JsonNode pt = lineNode.get(i);
            if (pt != null && pt.isArray() && pt.size() >= 2) {
                double lon = pt.get(0).asDouble();
                double lat = pt.get(1).asDouble();
                if (Double.isFinite(lon) && Double.isFinite(lat)) {
                    points.add(new GeoPoint(lat, lon));
                }
            }
        }
        return points;
    }

    private static String getText(JsonNode node, String field) {
        JsonNode val = node.get(field);
        return (val == null || !val.isString()) ? "" : val.asString().trim();
    }

    public List<MappedFault> faults() {
        return faults;
    }

    public int size() {
        return faults.size();
    }
}
