package io.github.paracosms.calquake.core;

import java.util.Map;

/** Versioned scientific configuration included in replay input signatures. */
public record ScientificConfiguration(
        Map<String, String> versionIds,
        Map<String, Double> numericParameters
) {
    public ScientificConfiguration {
        versionIds = versionIds == null ? Map.of() : Map.copyOf(versionIds);
        numericParameters = numericParameters == null ? Map.of() : Map.copyOf(numericParameters);
        numericParameters.forEach((name, value) -> {
            if (name == null || name.isBlank() || value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("Scientific numeric parameters must have names and finite values");
            }
        });
    }

    public static ScientificConfiguration empty() {
        return new ScientificConfiguration(Map.of(), Map.of());
    }
}
