package io.github.paracosms.calquake.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reference-free output of a predictive intensity model's preparation phase. */
public record PreparedIntensityResult(
        List<IntensityTimeline> timelines,
        double latestEnvelopePeakSeconds,
        ModelMetadata modelMetadata,
        Map<String, String> diagnostics
) {
    public PreparedIntensityResult {
        Objects.requireNonNull(timelines, "timelines cannot be null");
        if (timelines.isEmpty()) throw new IllegalArgumentException("timelines cannot be empty");
        timelines = List.copyOf(timelines);
        if (!Double.isFinite(latestEnvelopePeakSeconds) || latestEnvelopePeakSeconds < 0.0) {
            throw new IllegalArgumentException("latest envelope peak must be finite and non-negative");
        }
        Objects.requireNonNull(modelMetadata, "modelMetadata cannot be null");
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
    }
}
