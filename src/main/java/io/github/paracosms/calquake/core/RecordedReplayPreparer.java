package io.github.paracosms.calquake.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Explicitly joins historical ShakeMap references to modeled arrivals. */
public final class RecordedReplayPreparer {
    private final TravelTimeModel travelTimeModel;

    public RecordedReplayPreparer(TravelTimeModel travelTimeModel) {
        this.travelTimeModel = Objects.requireNonNull(travelTimeModel, "travelTimeModel cannot be null");
    }

    public PreparedReplay prepare(ScenarioInputs inputs, ScenarioReferences references) {
        Objects.requireNonNull(inputs, "inputs cannot be null");
        Objects.requireNonNull(references, "references cannot be null");
        ModelMetadata metadata = ModelMetadata.recorded(
                inputs.travelTimeConfiguration().modelId(), inputs.travelTimeConfiguration().version());
        LinkedHashMap<String, IntensityTimeline> timelines = new LinkedHashMap<>();
        double latestS = 0.0;
        for (SimulationSite site : inputs.sites()) {
            ReferenceIntensity reference = references.require(site.id());
            if (reference.shakeMapPeakMmi().isEmpty()) {
                throw new IllegalArgumentException("Missing ShakeMap peak MMI for site '" + site.id() + "'");
            }
            double distance = inputs.event().epicenter().distanceKmTo(site.coordinates());
            double p = travelTimeModel.travelTimeSeconds("P", distance, inputs.event().depthKm());
            double s = travelTimeModel.travelTimeSeconds("S", distance, inputs.event().depthKm());
            requireArrival(site, "P", p);
            requireArrival(site, "S", s);
            latestS = Math.max(latestS, s);
            double mmi = reference.shakeMapPeakMmi().getAsDouble();
            MmiLegend.MmiBin bin = reference.shakeMapDisplayBin()
                    .orElseGet(() -> MmiLegend.findBin(Math.max(1.0, mmi)));
            timelines.put(site.id(), IntensityTimeline.recorded(site, p, s, distance, mmi, bin, metadata));
        }
        double duration = latestS + 10.0;
        return new PreparedReplay(inputs, MmiMode.RECORDED,
                InputSignature.compute(inputs, MmiMode.RECORDED, Optional.of(references)),
                PrecomputedWavefronts.precompute(travelTimeModel, inputs.event().depthKm(), duration),
                timelines, duration, metadata, Optional.of(references),
                Map.of("displayMeaning", "Historical USGS ShakeMap peak revealed at modeled S arrival"));
    }

    private static void requireArrival(SimulationSite site, String phase, double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.0) {
            throw new IllegalArgumentException("Missing valid " + phase + " arrival for site '" + site.id() + "'");
        }
    }
}
