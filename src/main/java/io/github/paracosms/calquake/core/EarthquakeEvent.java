package io.github.paracosms.calquake.core;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable domain representation of an earthquake event.
 *
 * @param id            unique event identifier (e.g. "ci38457511")
 * @param network       contributing network code (e.g. "ci")
 * @param title         descriptive event title
 * @param originUtc     UTC origin timestamp preserving full precision (Instant)
 * @param epicenter     hypocenter surface epicenter as a GeoPoint
 * @param depthKm       hypocentral depth in kilometers (must be non-negative and finite)
 * @param magnitude     event magnitude value
 * @param magnitudeType magnitude type designation (e.g. "mw")
 * @param url           catalog or authoritative reference URL
 */
public record EarthquakeEvent(
        String id,
        String network,
        String title,
        Instant originUtc,
        GeoPoint epicenter,
        double depthKm,
        double magnitude,
        String magnitudeType,
        String url
) {

    public EarthquakeEvent {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Event id cannot be null or blank");
        }
        Objects.requireNonNull(originUtc, "originUtc cannot be null");
        Objects.requireNonNull(epicenter, "epicenter cannot be null");
        if (Double.isNaN(depthKm) || Double.isInfinite(depthKm) || depthKm < 0.0) {
            throw new IllegalArgumentException("Depth must be a finite non-negative number: " + depthKm);
        }
        if (Double.isNaN(magnitude) || Double.isInfinite(magnitude)) {
            throw new IllegalArgumentException("Magnitude must be a finite number: " + magnitude);
        }
        if (network == null) {
            network = "";
        }
        if (title == null) {
            title = "";
        }
        if (magnitudeType == null) {
            magnitudeType = "";
        }
        if (url == null) {
            url = "";
        }
    }
}
