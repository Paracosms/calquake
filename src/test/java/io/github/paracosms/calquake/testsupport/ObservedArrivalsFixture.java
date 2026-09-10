package io.github.paracosms.calquake.testsupport;

import io.github.paracosms.calquake.core.GeoPoint;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the frozen observed arrival picks fixture (ci38457511) for testing and scientific validation.
 * Held strictly in test support.
 */
public final class ObservedArrivalsFixture {

    public static final String RIDGECREST_FIXTURE_RESOURCE = "/fixtures/observed_picks_ci38457511.json";
    public static final String NORTHRIDGE_FIXTURE_RESOURCE = "/fixtures/observed_picks_ci3144585.json";
    public static final String FIXTURE_RESOURCE = RIDGECREST_FIXTURE_RESOURCE;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ObservedArrivalsFixture() {
    }

    public static List<ObservedArrival> loadAllPicks() {
        List<ObservedArrival> all = new ArrayList<>();
        all.addAll(loadPPicks());
        all.addAll(loadSPicks());
        return List.copyOf(all);
    }

    public static List<ObservedArrival> loadPPicks() {
        return loadPicksForField(FIXTURE_RESOURCE, "p_picks");
    }

    public static List<ObservedArrival> loadSPicks() {
        return loadPicksForField(FIXTURE_RESOURCE, "s_picks");
    }

    public static List<ObservedArrival> loadNorthridgeAllPicks() {
        List<ObservedArrival> all = new ArrayList<>();
        all.addAll(loadNorthridgePPicks());
        all.addAll(loadNorthridgeSPicks());
        return List.copyOf(all);
    }

    public static List<ObservedArrival> loadNorthridgePPicks() {
        return loadPicksForField(NORTHRIDGE_FIXTURE_RESOURCE, "p_picks");
    }

    public static List<ObservedArrival> loadNorthridgeSPicks() {
        return loadPicksForField(NORTHRIDGE_FIXTURE_RESOURCE, "s_picks");
    }

    private static List<ObservedArrival> loadPicksForField(String resourcePath, String fieldName) {
        try (InputStream is = ObservedArrivalsFixture.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Missing test fixture resource: " + resourcePath);
            }
            JsonNode root = MAPPER.readTree(is);
            JsonNode picksNode = root.get(fieldName);
            if (picksNode == null || !picksNode.isArray()) {
                throw new IllegalStateException("Missing array field '" + fieldName + "' in " + FIXTURE_RESOURCE);
            }

            List<ObservedArrival> picks = new ArrayList<>();
            for (JsonNode p : picksNode) {
                String network = p.get("network").asText();
                String station = p.get("station").asText();
                String channel = p.get("channel").asText();
                String location = p.has("location") ? p.get("location").asText("") : "";
                String phase = p.get("phase").asText();
                double quality = p.get("quality").asDouble();
                double distanceKm = p.get("distance_km").asDouble();
                double observedTimeSec = p.get("observed_time_sec").asDouble();
                Instant absoluteUtc = Instant.parse(p.get("absolute_time_utc").asText());

                JsonNode pCoords = p.get("phase_file_coordinates");
                GeoPoint phasePoint = new GeoPoint(pCoords.get("latitude").asDouble(), pCoords.get("longitude").asDouble());
                double phaseElev = pCoords.has("elevation_m") ? pCoords.get("elevation_m").asDouble() : 0.0;

                String polarity = p.has("polarity") ? p.get("polarity").asText("") : "";
                String onset = p.has("onset") ? p.get("onset").asText("") : "";

                GeoPoint metaPoint = null;
                double metaElev = 0.0;
                if (p.has("station_metadata_coordinates") && p.get("station_metadata_coordinates").isObject()) {
                    JsonNode mCoords = p.get("station_metadata_coordinates");
                    metaPoint = new GeoPoint(mCoords.get("latitude").asDouble(), mCoords.get("longitude").asDouble());
                    metaElev = mCoords.has("elevation_m") ? mCoords.get("elevation_m").asDouble() : 0.0;
                }

                picks.add(new ObservedArrival(
                        network, station, channel, location, phase, quality, distanceKm,
                        observedTimeSec, absoluteUtc, phasePoint, phaseElev, polarity, onset,
                        metaPoint, metaElev
                ));
            }
            return List.copyOf(picks);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + FIXTURE_RESOURCE, e);
        }
    }
}
