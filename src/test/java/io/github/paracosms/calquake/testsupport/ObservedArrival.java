package io.github.paracosms.calquake.testsupport;

import io.github.paracosms.calquake.core.GeoPoint;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a frozen observed seismic phase arrival pick.
 * Held strictly in test support, outside runtime replay.
 *
 * @param network                     seismic network code (e.g. "CI")
 * @param station                     station code (e.g. "CLC")
 * @param channel                     channel code (e.g. "HHZ")
 * @param location                    location code (e.g. "" or "00")
 * @param phase                       phase identification ("P" or "S")
 * @param quality                     STP pick quality (>= 0.5)
 * @param distanceKm                  epicentral distance in km
 * @param observedTimeSec             observed travel time from frozen origin in seconds
 * @param absoluteTimeUtc             absolute pick UTC timestamp
 * @param phaseCoordinates            coordinates from phase file
 * @param phaseElevationM             elevation in meters from phase file
 * @param polarity                    first motion polarity (e.g. "c.", "d.")
 * @param onset                       onset sharpness ("i" or "e")
 * @param stationMetadataCoordinates  coordinates from station XML metadata
 * @param stationMetadataElevationM   elevation in meters from station XML metadata
 */
public record ObservedArrival(
        String network,
        String station,
        String channel,
        String location,
        String phase,
        double quality,
        double distanceKm,
        double observedTimeSec,
        Instant absoluteTimeUtc,
        GeoPoint phaseCoordinates,
        double phaseElevationM,
        String polarity,
        String onset,
        GeoPoint stationMetadataCoordinates,
        double stationMetadataElevationM
) {
    public ObservedArrival {
        Objects.requireNonNull(network, "network cannot be null");
        Objects.requireNonNull(station, "station cannot be null");
        Objects.requireNonNull(channel, "channel cannot be null");
        Objects.requireNonNull(phase, "phase cannot be null");
        Objects.requireNonNull(absoluteTimeUtc, "absoluteTimeUtc cannot be null");
        Objects.requireNonNull(phaseCoordinates, "phaseCoordinates cannot be null");
        if (location == null) {
            location = "";
        }
        if (polarity == null) {
            polarity = "";
        }
        if (onset == null) {
            onset = "";
        }
    }
}
