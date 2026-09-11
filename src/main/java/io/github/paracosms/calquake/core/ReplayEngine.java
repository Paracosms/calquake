package io.github.paracosms.calquake.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure prepared-replay frame lookup engine. */
public final class ReplayEngine {
    private final Scenario scenario;
    private final TravelTimeModel model;
    private final IntensitySource intensitySource;
    private final PreparedReplay preparedReplay;
    private final List<LocationIntensityState> fixedIntensities;

    /** Legacy adapter that prepares Recorded mode once at construction. */
    public ReplayEngine(Scenario scenario, TravelTimeModel model, IntensitySource intensitySource) {
        this.scenario = Objects.requireNonNull(scenario, "scenario cannot be null");
        this.model = Objects.requireNonNull(model, "model cannot be null");
        this.intensitySource = Objects.requireNonNull(intensitySource, "intensitySource cannot be null");
        ScenarioInputs inputs = ScenarioInputs.fromLegacyScenario(scenario, model);
        LinkedHashMap<String, ReferenceIntensity> refs = new LinkedHashMap<>();
        for (ReferenceLocation location : scenario.locations()) {
            refs.put(location.geoid(), ReferenceIntensity.fromReferenceLocation(
                    location, intensitySource.getPeakIntensity(location)));
        }
        this.preparedReplay = new RecordedReplayPreparer(model)
                .prepare(inputs, new ScenarioReferences(refs));
        this.fixedIntensities = inputs.sites().stream()
                .map(site -> preparedReplay.timelinesBySiteId().get(site.id())
                        .stateAt(preparedReplay.durationSeconds()))
                .toList();
    }

    private ReplayEngine(Scenario scenario, TravelTimeModel model, PreparedReplay replay) {
        this.scenario = scenario;
        this.model = Objects.requireNonNull(model, "model cannot be null");
        this.preparedReplay = Objects.requireNonNull(replay, "replay cannot be null");
        this.intensitySource = null;
        this.fixedIntensities = replay.inputs().sites().stream()
                .map(site -> replay.timelinesBySiteId().get(site.id()).stateAt(replay.durationSeconds()))
                .toList();
    }

    public static ReplayEngine create(Scenario scenario, TravelTimeModel model) {
        return new ReplayEngine(scenario, model, IntensitySource.scenarioPeak());
    }

    public static ReplayEngine create(Scenario scenario, TravelTimeModel model, IntensitySource intensitySource) {
        return new ReplayEngine(scenario, model, intensitySource);
    }

    public static ReplayEngine createPrepared(
            Scenario scenario, TravelTimeModel model, PreparedReplay preparedReplay) {
        Objects.requireNonNull(scenario, "scenario cannot be null");
        if (!scenario.event().id().equals(preparedReplay.inputs().event().id())) {
            throw new IllegalArgumentException("Scenario and prepared replay event IDs differ");
        }
        return new ReplayEngine(scenario, model, preparedReplay);
    }

    public static ReplayEngine createPrepared(
            TravelTimeModel model, PreparedReplay preparedReplay) {
        return new ReplayEngine(null, model, preparedReplay);
    }

    private IntensityDisplayMode intensityDisplayMode = IntensityDisplayMode.MAXIMUM_REACHED;

    public IntensityDisplayMode intensityDisplayMode() {
        return intensityDisplayMode;
    }

    public void setIntensityDisplayMode(IntensityDisplayMode intensityDisplayMode) {
        this.intensityDisplayMode = Objects.requireNonNull(intensityDisplayMode, "intensityDisplayMode cannot be null");
    }

    public FrameState frameAt(double elapsedSeconds) {
        return frameAt(elapsedSeconds, this.intensityDisplayMode);
    }

    public FrameState frameAt(double elapsedSeconds, IntensityDisplayMode mode) {
        validateTime(elapsedSeconds);
        Objects.requireNonNull(mode, "mode cannot be null");
        List<LocationIntensityState> states = preparedReplay.inputs().sites().stream()
                .map(site -> preparedReplay.timelinesBySiteId().get(site.id()).stateAt(elapsedSeconds, mode))
                .toList();
        return new FrameState(elapsedSeconds, preparedReplay.inputs().event().epicenter(),
                preparedReplay.wavefronts().radiiAt(elapsedSeconds), states);
    }

    /** Compatibility overload; alternate scenarios are prepared independently. */
    public FrameState frameAt(Scenario targetScenario, double elapsedSeconds) {
        return frameAt(targetScenario, elapsedSeconds, this.intensityDisplayMode);
    }

    public FrameState frameAt(Scenario targetScenario, double elapsedSeconds, IntensityDisplayMode mode) {
        Objects.requireNonNull(targetScenario, "targetScenario cannot be null");
        if (targetScenario.equals(scenario)) return frameAt(elapsedSeconds, mode);
        if (intensitySource == null) {
            throw new IllegalArgumentException("A prepared engine cannot evaluate a different scenario");
        }
        ReplayEngine engine = new ReplayEngine(targetScenario, model, intensitySource);
        engine.setIntensityDisplayMode(mode);
        return engine.frameAt(elapsedSeconds, mode);
    }

    private static void validateTime(double elapsedSeconds) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0.0) {
            throw new IllegalArgumentException(
                    "Elapsed seconds must be a non-negative finite number: " + elapsedSeconds);
        }
    }

    public TravelTimeModel model() { return model; }
    public Scenario scenario() { return scenario; }
    public PrecomputedWavefronts wavefronts() { return preparedReplay.wavefronts(); }
    public IntensitySource intensitySource() { return intensitySource; }
    public List<LocationIntensityState> fixedIntensities() { return fixedIntensities; }
    public PreparedReplay preparedReplay() { return preparedReplay; }
}
