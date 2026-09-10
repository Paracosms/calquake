package io.github.paracosms.calquake.core;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Earthquake source inputs, separated from all site intensity answers. */
public record EventSource(
        String id,
        String network,
        String title,
        Instant originUtc,
        double magnitude,
        String magnitudeType,
        GeoPoint epicenter,
        double depthKm,
        Optional<RuptureGeometry> ruptureGeometry,
        Optional<Mechanism> mechanism,
        Map<String, String> metadata
) {
    public EventSource {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Event source id cannot be null or blank");
        }
        id = id.trim();
        network = network == null ? "" : network.trim();
        title = title == null ? "" : title.trim();
        Objects.requireNonNull(originUtc, "originUtc cannot be null");
        if (!Double.isFinite(magnitude)) {
            throw new IllegalArgumentException("Magnitude must be finite: " + magnitude);
        }
        magnitudeType = magnitudeType == null ? "" : magnitudeType.trim();
        Objects.requireNonNull(epicenter, "epicenter cannot be null");
        if (!Double.isFinite(depthKm) || depthKm < 0.0) {
            throw new IllegalArgumentException("Depth must be finite and non-negative: " + depthKm);
        }
        ruptureGeometry = ruptureGeometry == null ? Optional.empty() : ruptureGeometry;
        mechanism = mechanism == null ? Optional.empty() : mechanism;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static EventSource from(EarthquakeEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        Map<String, String> metadata = event.url().isBlank()
                ? Map.of()
                : Map.of("catalogUrl", event.url());
        return new EventSource(
                event.id(), event.network(), event.title(), event.originUtc(),
                event.magnitude(), event.magnitudeType(), event.epicenter(), event.depthKm(),
                Optional.empty(), Optional.empty(), metadata
        );
    }

    public EarthquakeEvent toEarthquakeEvent() {
        return new EarthquakeEvent(
                id, network, title, originUtc, epicenter, depthKm, magnitude, magnitudeType,
                metadata.getOrDefault("catalogUrl", "")
        );
    }
}
