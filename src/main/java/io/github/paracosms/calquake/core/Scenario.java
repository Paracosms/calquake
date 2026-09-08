package io.github.paracosms.calquake.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable replay scenario encapsulating an earthquake event and reference locations.
 *
 * @param event     the earthquake event
 * @param locations immutable list of reference locations
 */
public record Scenario(
        EarthquakeEvent event,
        List<ReferenceLocation> locations
) {

    public Scenario {
        Objects.requireNonNull(event, "event cannot be null");
        Objects.requireNonNull(locations, "locations cannot be null");
        if (locations.isEmpty()) {
            throw new IllegalArgumentException("locations cannot be empty");
        }
        locations = List.copyOf(locations);
    }

    /**
     * Find a reference location by city name (case-insensitive).
     *
     * @param city the city name to search for
     * @return Optional containing the location if found
     */
    public Optional<ReferenceLocation> findLocationByCity(String city) {
        if (city == null) {
            return Optional.empty();
        }
        return locations.stream()
                .filter(loc -> loc.city().equalsIgnoreCase(city.trim()))
                .findFirst();
    }

    /**
     * Find a reference location by Census GEOID.
     *
     * @param geoid the GEOID string
     * @return Optional containing the location if found
     */
    public Optional<ReferenceLocation> findLocationByGeoid(String geoid) {
        if (geoid == null) {
            return Optional.empty();
        }
        return locations.stream()
                .filter(loc -> loc.geoid().equals(geoid.trim()))
                .findFirst();
    }
}
