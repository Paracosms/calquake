package io.github.paracosms.calquake.core;

import java.util.Objects;
import java.util.Optional;

/**
 * A predictor input site. It deliberately contains no historical MMI, observed
 * ground motion, or ShakeMap sampling node.
 */
public record SimulationSite(
        String id,
        String displayName,
        GeoPoint coordinates,
        Optional<SiteCondition> siteCondition
) {
    public SimulationSite {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Site id cannot be null or blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Site display name cannot be null or blank");
        }
        id = id.trim();
        displayName = displayName.trim();
        Objects.requireNonNull(coordinates, "coordinates cannot be null");
        siteCondition = siteCondition == null ? Optional.empty() : siteCondition;
    }

    public SimulationSite(String id, String displayName, GeoPoint coordinates) {
        this(id, displayName, coordinates, Optional.empty());
    }

    public SimulationSite(String id, String displayName, GeoPoint coordinates, SiteCondition siteCondition) {
        this(id, displayName, coordinates, Optional.ofNullable(siteCondition));
    }

    /** Legacy-resource adapter. Deliberately does not promote the unverified SVEL field to Vs30. */
    public static SimulationSite fromReferenceLocation(ReferenceLocation location) {
        Objects.requireNonNull(location, "location cannot be null");
        return new SimulationSite(location.geoid(), location.city(), location.internalPoint());
    }
}
