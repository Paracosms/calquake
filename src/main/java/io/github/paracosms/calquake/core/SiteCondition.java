package io.github.paracosms.calquake.core;

import java.util.Objects;

/** Immutable shallow-site condition used by predictive ground-motion models. */
public record SiteCondition(
        double vs30MetersPerSecond,
        SiteConditionProvenance provenance,
        String sourceId
) {
    public SiteCondition {
        if (!Double.isFinite(vs30MetersPerSecond) || vs30MetersPerSecond <= 0.0) {
            throw new IllegalArgumentException("Vs30 must be a finite positive value in m/s: "
                    + vs30MetersPerSecond);
        }
        Objects.requireNonNull(provenance, "provenance cannot be null");
        sourceId = sourceId == null ? "" : sourceId.trim();
    }

    public static SiteCondition defaultRock() {
        return new SiteCondition(760.0, SiteConditionProvenance.DEFAULT, "calquake-default-vs30-760");
    }
}
