package io.github.paracosms.calquake.core;

import java.util.Map;

/** Stable identity and parameters for the replay travel-time calculation. */
public record TravelTimeConfiguration(
        String modelId,
        String version,
        Map<String, String> parameters
) {
    public TravelTimeConfiguration {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("Travel-time model id cannot be null or blank");
        }
        modelId = modelId.trim();
        version = version == null ? "" : version.trim();
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    public static TravelTimeConfiguration forModel(TravelTimeModel model) {
        if (model == null) {
            throw new NullPointerException("model cannot be null");
        }
        if (model instanceof HadleyKanamoriTauPModel) {
            return new TravelTimeConfiguration(
                    HadleyKanamoriTauPModel.MODEL_NAME,
                    "TauP-3.2.1",
                    Map.of("resource", HadleyKanamoriTauPModel.MODEL_RESOURCE)
            );
        }
        return new TravelTimeConfiguration(model.getClass().getName(), "", Map.of());
    }
}
