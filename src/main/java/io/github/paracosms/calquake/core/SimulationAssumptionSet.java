package io.github.paracosms.calquake.core;

import java.util.Objects;

/**
 * Versioned fixed assumption set for CalQuake simulation mode.
 */
public record SimulationAssumptionSet(
        String id,
        Mechanism mechanism,
        String ruptureScalingModel,
        double defaultVs30,
        SiteConditionProvenance siteProvenance
) {
    public static final String DEFAULT_ID = "calquake-custom-v1";

    public static final SimulationAssumptionSet CALQUAKE_CUSTOM_V1 = new SimulationAssumptionSet(
            DEFAULT_ID,
            new Mechanism(0.0, 0.0, 90.0, "STRIKE_SLIP", "calquake-custom-v1 generic strike-slip"),
            RuptureGeometryProvider.MODEL_ID,
            760.0,
            SiteConditionProvenance.DEFAULT
    );

    public SimulationAssumptionSet {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(mechanism, "mechanism cannot be null");
        Objects.requireNonNull(ruptureScalingModel, "ruptureScalingModel cannot be null");
        if (!Double.isFinite(defaultVs30) || defaultVs30 <= 0.0) {
            throw new IllegalArgumentException("defaultVs30 must be positive and finite");
        }
        Objects.requireNonNull(siteProvenance, "siteProvenance cannot be null");
    }

    public static SimulationAssumptionSet resolve(String id) {
        if (DEFAULT_ID.equals(id)) {
            return CALQUAKE_CUSTOM_V1;
        }
        throw new IllegalArgumentException("Unknown assumption set: " + id);
    }
}
