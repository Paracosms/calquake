package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.MercatorProjection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Loads, validates, and holds the frozen California state cartographic boundary outline
 * from {@code /data/california_outline.json}.
 * <p>
 * Provenance: US Census Bureau 2020 Cartographic Boundary shapefile (cb_2020_us_state_20m).
 * Consists of 6 closed polygon rings (5 Channel Islands + 1 mainland ring; 468 vertices total).
 */
public final class CaliforniaOutline {

    public static final String DEFAULT_RESOURCE = "/data/california_outline.json";
    public static final int EXPECTED_RINGS = 6;
    public static final int EXPECTED_VERTICES = 468;

    public record GeographicBoundingBox(
            double minLongitude,
            double minLatitude,
            double maxLongitude,
            double maxLatitude
    ) {}

    private final List<List<GeoPoint>> rings;
    private final int totalVertices;
    private final GeographicBoundingBox geoBounds;

    public CaliforniaOutline(List<List<GeoPoint>> rings) {
        Objects.requireNonNull(rings, "rings cannot be null");
        if (rings.size() != EXPECTED_RINGS) {
            throw new IllegalArgumentException("Expected " + EXPECTED_RINGS + " rings, found: " + rings.size());
        }

        int vertexCount = 0;
        double minLon = Double.POSITIVE_INFINITY;
        double minLat = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;

        List<List<GeoPoint>> immutableRings = new ArrayList<>();
        for (List<GeoPoint> ring : rings) {
            if (ring == null || ring.size() < 3) {
                throw new IllegalArgumentException("Polygon ring must have at least 3 vertices");
            }
            vertexCount += ring.size();
            for (GeoPoint pt : ring) {
                minLon = Math.min(minLon, pt.longitude());
                maxLon = Math.max(maxLon, pt.longitude());
                minLat = Math.min(minLat, pt.latitude());
                maxLat = Math.max(maxLat, pt.latitude());
            }
            immutableRings.add(List.copyOf(ring));
        }

        if (vertexCount != EXPECTED_VERTICES) {
            throw new IllegalArgumentException("Expected " + EXPECTED_VERTICES + " vertices, found: " + vertexCount);
        }

        this.rings = List.copyOf(immutableRings);
        this.totalVertices = vertexCount;
        this.geoBounds = new GeographicBoundingBox(minLon, minLat, maxLon, maxLat);
    }

    /**
     * Loads the default frozen California outline from classpath.
     *
     * @return validated CaliforniaOutline
     */
    public static CaliforniaOutline loadDefault() {
        return loadFromResource(DEFAULT_RESOURCE);
    }

    /**
     * Loads California outline from a classpath resource path.
     *
     * @param resourcePath classpath path
     * @return validated CaliforniaOutline
     */
    public static CaliforniaOutline loadFromResource(String resourcePath) {
        try (InputStream stream = CaliforniaOutline.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return load(stream);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load California outline: " + resourcePath, e);
        }
    }

    /**
     * Loads California outline from an InputStream.
     *
     * @param stream JSON input stream
     * @return validated CaliforniaOutline
     */
    public static CaliforniaOutline load(InputStream stream) {
        Objects.requireNonNull(stream, "stream cannot be null");
        try {
            JsonMapper mapper = JsonMapper.builder().build();
            JsonNode root = mapper.readTree(stream);
            return parse(root);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("Malformed California outline JSON", e);
        }
    }

    private static CaliforniaOutline parse(JsonNode root) {
        JsonNode features = root.get("features");
        if (features == null || !features.isArray() || features.isEmpty()) {
            throw new IllegalArgumentException("Missing or empty 'features' array");
        }

        JsonNode feature = features.get(0);
        JsonNode geometry = feature.get("geometry");
        if (geometry == null || !"MultiPolygon".equalsIgnoreCase(geometry.get("type").asString())) {
            throw new IllegalArgumentException("Expected geometry type MultiPolygon");
        }

        JsonNode coordinates = geometry.get("coordinates");
        if (coordinates == null || !coordinates.isArray()) {
            throw new IllegalArgumentException("Missing coordinates array in MultiPolygon");
        }

        List<List<GeoPoint>> rings = new ArrayList<>();
        for (int i = 0; i < coordinates.size(); i++) {
            JsonNode polyNode = coordinates.get(i);
            if (!polyNode.isArray() || polyNode.isEmpty()) {
                continue;
            }
            // First linear ring is the exterior boundary
            JsonNode exteriorRing = polyNode.get(0);
            List<GeoPoint> ringPoints = new ArrayList<>();
            for (int j = 0; j < exteriorRing.size(); j++) {
                JsonNode pt = exteriorRing.get(j);
                double lon = pt.get(0).asDouble();
                double lat = pt.get(1).asDouble();
                ringPoints.add(new GeoPoint(lat, lon));
            }
            rings.add(ringPoints);
        }

        return new CaliforniaOutline(rings);
    }

    public List<List<GeoPoint>> rings() {
        return rings;
    }

    public int ringCount() {
        return rings.size();
    }

    public int totalVertices() {
        return totalVertices;
    }

    public GeographicBoundingBox geographicBounds() {
        return geoBounds;
    }

    /**
     * Projects all rings using the given projection.
     *
     * @param projection projection to apply
     * @return projected polygon rings in kilometers
     */
    public List<List<MercatorProjection.ProjectedPoint>> projectRings(MercatorProjection projection) {
        Objects.requireNonNull(projection, "projection cannot be null");
        List<List<MercatorProjection.ProjectedPoint>> projected = new ArrayList<>(rings.size());
        for (List<GeoPoint> ring : rings) {
            List<MercatorProjection.ProjectedPoint> projectedRing = new ArrayList<>(ring.size());
            for (GeoPoint pt : ring) {
                projectedRing.add(projection.project(pt));
            }
            projected.add(Collections.unmodifiableList(projectedRing));
        }
        return Collections.unmodifiableList(projected);
    }

    /**
     * Computes the bounding box in projection space (kilometers) for all vertices.
     *
     * @param projection projection to apply
     * @return bounding box in kilometers
     */
    public MercatorProjection.BoundingBox computeProjectedBoundingBox(MercatorProjection projection) {
        Objects.requireNonNull(projection, "projection cannot be null");
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (List<GeoPoint> ring : rings) {
            for (GeoPoint pt : ring) {
                MercatorProjection.ProjectedPoint p = projection.project(pt);
                minX = Math.min(minX, p.xKm());
                maxX = Math.max(maxX, p.xKm());
                minY = Math.min(minY, p.yKm());
                maxY = Math.max(maxY, p.yKm());
            }
        }

        return new MercatorProjection.BoundingBox(minX, minY, maxX, maxY);
    }
}
