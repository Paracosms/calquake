package io.github.paracosms.calquake.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Selects explicit Recorded or reference-isolated Simulated preparation and caches results. */
public final class ReplayPreparer {
    private final TravelTimeModel travelTimeModel;
    private final RecordedReplayPreparer recordedPreparer;
    private final IntensityModel simulatedModel;
    private final PreparedReplayCache cache;

    public ReplayPreparer(TravelTimeModel travelTimeModel) {
        this(travelTimeModel, new SimulatedMmiModel(travelTimeModel), new PreparedReplayCache());
    }

    public ReplayPreparer(TravelTimeModel travelTimeModel, IntensityModel simulatedModel,
                          PreparedReplayCache cache) {
        this.travelTimeModel = Objects.requireNonNull(travelTimeModel);
        this.recordedPreparer = new RecordedReplayPreparer(travelTimeModel);
        this.simulatedModel = Objects.requireNonNull(simulatedModel);
        this.cache = Objects.requireNonNull(cache);
    }

    public PreparedReplay prepare(ScenarioInputs inputs, ScenarioReferences references, MmiMode mode) {
        Objects.requireNonNull(inputs);
        Objects.requireNonNull(references);
        Objects.requireNonNull(mode);
        String signature = InputSignature.compute(inputs, mode,
                mode == MmiMode.RECORDED ? Optional.of(references) : Optional.empty());
        Optional<PreparedReplay> hit = cache.get(signature);
        if (hit.isPresent()) return hit.get();
        PreparedReplay replay;
        if (mode == MmiMode.RECORDED) {
            replay = recordedPreparer.prepare(inputs, references);
        } else {
            PreparedIntensityResult result = simulatedModel.prepare(inputs);
            double latestS = result.timelines().stream()
                    .mapToDouble(IntensityTimeline::sArrivalSeconds).max().orElseThrow();
            double duration = Math.max(latestS + 10.0, result.latestEnvelopePeakSeconds());
            LinkedHashMap<String, IntensityTimeline> timelines = new LinkedHashMap<>();
            result.timelines().forEach(t -> timelines.put(t.site().id(), t));
            replay = new PreparedReplay(inputs, MmiMode.SIMULATED, signature,
                    PrecomputedWavefronts.precompute(travelTimeModel, inputs.event().depthKm(), duration),
                    timelines, duration, result.modelMetadata(), Optional.empty(),
                    Map.of("displayMeaning", "Maximum predicted MMI reached so far",
                            "inputIsolation", "Historical MMI/PGA/PGV are evaluation targets only"));
        }
        return cache.putIfAbsent(replay);
    }

    public PreparedReplayCache cache() { return cache; }
}
