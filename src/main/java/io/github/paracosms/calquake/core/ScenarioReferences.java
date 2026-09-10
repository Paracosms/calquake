package io.github.paracosms.calquake.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Historical references keyed independently from predictor inputs by stable site id. */
public record ScenarioReferences(Map<String, ReferenceIntensity> bySiteId) {
    public ScenarioReferences {
        Objects.requireNonNull(bySiteId, "bySiteId cannot be null");
        LinkedHashMap<String, ReferenceIntensity> copy = new LinkedHashMap<>();
        bySiteId.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Reference map contains a blank site id");
            }
            Objects.requireNonNull(value, "Reference map contains a null value for " + key);
            if (!key.equals(value.siteId())) {
                throw new IllegalArgumentException("Reference key does not match value site id: " + key);
            }
            if (copy.put(key, value) != null) {
                throw new IllegalArgumentException("Duplicate reference site id: " + key);
            }
        });
        bySiteId = Map.copyOf(copy);
    }

    public ScenarioReferences() {
        this(Map.of());
    }

    public Optional<ReferenceIntensity> find(String siteId) {
        return Optional.ofNullable(bySiteId.get(siteId));
    }

    public ReferenceIntensity require(String siteId) {
        ReferenceIntensity reference = bySiteId.get(siteId);
        if (reference == null) {
            throw new IllegalArgumentException("Missing historical reference for site '" + siteId + "'");
        }
        return reference;
    }
}
