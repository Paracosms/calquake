package io.github.paracosms.calquake.data;

import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.MercatorProjection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Bundled 2020 U.S. Census cartographic county boundary lines, projected for drawing. */
public final class CaliforniaCounties {
    private static final String RESOURCE = "/data/geodata/california_counties.geojson";

    private CaliforniaCounties() {}

    public static List<List<MercatorProjection.ProjectedPoint>> loadProjected(MercatorProjection projection) {
        try (InputStream input = CaliforniaCounties.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing county boundaries: " + RESOURCE);
            JsonNode root = JsonMapper.builder().build().readTree(input);
            if (!"MultiLineString".equals(root.get("type").asString())) {
                throw new IllegalArgumentException("Unsupported county boundary geometry");
            }
            List<List<MercatorProjection.ProjectedPoint>> rings = new ArrayList<>();
            for (JsonNode line : root.get("coordinates")) {
                List<MercatorProjection.ProjectedPoint> points = new ArrayList<>();
                for (JsonNode point : line) {
                    points.add(projection.project(new GeoPoint(point.get(1).asDouble(), point.get(0).asDouble())));
                }
                rings.add(List.copyOf(points));
            }
            return List.copyOf(rings);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load county boundaries", e);
        }
    }

}
