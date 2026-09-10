package io.github.paracosms.calquake.core;

import java.util.Map;
import java.util.Optional;

/** Versioned model identity and reproducibility/provenance attributes. */
public record ModelMetadata(
        String modelId,
        String modelVersion,
        Map<String, String> attributes
) {
    public ModelMetadata {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("Model id cannot be null or blank");
        }
        modelId = modelId.trim();
        modelVersion = modelVersion == null ? "" : modelVersion.trim();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public Optional<String> attribute(String name) {
        return Optional.ofNullable(attributes.get(name));
    }

    public static ModelMetadata recorded(String travelTimeModelId, String travelTimeVersion) {
        return new ModelMetadata(
                "recorded-shakemap-peak-at-modeled-s-arrival",
                "1",
                Map.of(
                        "intensityProvenance", "USGS ShakeMap historical peak sample",
                        "travelTimeModel", travelTimeModelId,
                        "travelTimeVersion", travelTimeVersion
                )
        );
    }
}
