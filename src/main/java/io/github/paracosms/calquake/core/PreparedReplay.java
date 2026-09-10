package io.github.paracosms.calquake.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Complete immutable replay consumed by playback; no scientific work occurs per frame. */
public record PreparedReplay(
        ScenarioInputs inputs,
        MmiMode mode,
        String inputSignature,
        PrecomputedWavefronts wavefronts,
        Map<String, IntensityTimeline> timelinesBySiteId,
        double durationSeconds,
        ModelMetadata modelMetadata,
        Optional<ScenarioReferences> references,
        Map<String, String> provenance
) {
    public PreparedReplay {
        Objects.requireNonNull(inputs, "inputs cannot be null");
        Objects.requireNonNull(mode, "mode cannot be null");
        if (inputSignature == null || inputSignature.isBlank()) {
            throw new IllegalArgumentException("inputSignature cannot be blank");
        }
        Objects.requireNonNull(wavefronts, "wavefronts cannot be null");
        Objects.requireNonNull(timelinesBySiteId, "timelinesBySiteId cannot be null");
        LinkedHashMap<String, IntensityTimeline> ordered = new LinkedHashMap<>();
        for (SimulationSite site : inputs.sites()) {
            IntensityTimeline timeline = timelinesBySiteId.get(site.id());
            if (timeline == null) {
                throw new IllegalArgumentException("Missing timeline for site " + site.id());
            }
            ordered.put(site.id(), timeline);
        }
        timelinesBySiteId = Map.copyOf(ordered);
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0.0) {
            throw new IllegalArgumentException("durationSeconds must be positive and finite");
        }
        Objects.requireNonNull(modelMetadata, "modelMetadata cannot be null");
        references = references == null ? Optional.empty() : references;
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
    }
}
