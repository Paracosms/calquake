package io.github.paracosms.calquake.core;

import io.github.paracosms.calquake.data.ScenarioLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedMmiTimelineTest {
    @ParameterizedTest
    @ValueSource(strings = {"Ridgecrest", "Northridge"})
    void simulatedTimelineIsProgressiveDeterministicAndPeakNormalized(String eventName) {
        ScenarioLoader.ScenarioBundle bundle = new ScenarioLoader().loadScenarioBundle(eventName);
        ReplayPreparer preparer = new ReplayPreparer(new HadleyKanamoriTauPModel());
        PreparedReplay replay = preparer.prepare(bundle.inputs(), bundle.references(), MmiMode.SIMULATED);
        assertEquals(MmiMode.SIMULATED, replay.mode());
        assertTrue(replay.references().isEmpty());
        Worden2012Gmice gmice = new Worden2012Gmice();
        for (IntensityTimeline timeline : replay.timelinesBySiteId().values()) {
            double previousMmi = Double.NEGATIVE_INFINITY;
            for (IntensityTimeline.Sample sample : timeline.samples()) {
                if (sample.mmi().isPresent()) {
                    assertTrue(sample.mmi().getAsDouble() + 1.0e-12 >= previousMmi);
                    previousMmi = sample.mmi().getAsDouble();
                }
            }
            double peakPgv = timeline.predictedPeakPgv().orElseThrow();
            LocationIntensityState finalState = timeline.stateAt(replay.durationSeconds());
            assertEquals(peakPgv, finalState.currentPgvCmPerSecond().orElseThrow(), 1.0e-9);
            assertEquals(gmice.fromPgvCmPerSecond(peakPgv), finalState.currentMmi().orElseThrow(), 1.0e-9);
            assertFalse(timeline.stateAt(0.0).isRevealed());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ridgecrest", "Northridge"})
    void referencesCannotAffectPredictionsOrSimulatedCacheSignature(String eventName) {
        ScenarioLoader.ScenarioBundle bundle = new ScenarioLoader().loadScenarioBundle(eventName);
        ReplayPreparer preparer = new ReplayPreparer(new HadleyKanamoriTauPModel());
        PreparedReplay original = preparer.prepare(bundle.inputs(), bundle.references(), MmiMode.SIMULATED);
        LinkedHashMap<String, ReferenceIntensity> changed = new LinkedHashMap<>();
        bundle.references().bySiteId().forEach((id, reference) -> changed.put(id,
                new ReferenceIntensity(id, OptionalDouble.of(1.0),
                        java.util.Optional.of(MmiLegend.BIN_I), reference.samplingMetadata(),
                        java.util.Optional.empty(), "deliberately changed evaluation target")));
        PreparedReplay withChangedTargets = preparer.prepare(
                bundle.inputs(), new ScenarioReferences(changed), MmiMode.SIMULATED);
        assertSame(original, withChangedTargets);
        assertEquals(original.inputSignature(), withChangedTargets.inputSignature());
        assertEquals(original.timelinesBySiteId(), withChangedTargets.timelinesBySiteId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ridgecrest", "Northridge"})
    void halvingTimelineStepMeetsFinalAndThresholdConvergencePolicy(String eventName) {
        ScenarioInputs inputs = new ScenarioLoader().loadScenarioInputs(eventName);
        HadleyKanamoriTauPModel travelTimes = new HadleyKanamoriTauPModel();
        PreparedIntensityResult coarse = new SimulatedMmiModel(
                travelTimes, new Bssa14GroundMotion(), new EmpiricalEnvelopeModel(),
                new Worden2012Gmice(), 0.05).prepare(inputs);
        PreparedIntensityResult fine = new SimulatedMmiModel(
                travelTimes, new Bssa14GroundMotion(), new EmpiricalEnvelopeModel(),
                new Worden2012Gmice(), 0.025).prepare(inputs);

        Map<String, IntensityTimeline> fineBySite = fine.timelines().stream()
                .collect(java.util.stream.Collectors.toMap(t -> t.site().id(), t -> t));
        for (IntensityTimeline coarseTimeline : coarse.timelines()) {
            IntensityTimeline fineTimeline = fineBySite.get(coarseTimeline.site().id());
            assertEquals(coarseTimeline.finalMmi().orElseThrow(),
                    fineTimeline.finalMmi().orElseThrow(), 0.01);
            for (double threshold : new double[]{3.5, 4.5, 5.5}) {
                OptionalDouble coarseCrossing = firstCrossing(coarseTimeline, threshold);
                OptionalDouble fineCrossing = firstCrossing(fineTimeline, threshold);
                assertEquals(coarseCrossing.isPresent(), fineCrossing.isPresent(),
                        coarseTimeline.site().id() + " threshold " + threshold);
                if (coarseCrossing.isPresent()) {
                    assertEquals(fineCrossing.getAsDouble(), coarseCrossing.getAsDouble(), 0.1,
                            coarseTimeline.site().id() + " threshold " + threshold);
                }
            }
        }
    }

    private static OptionalDouble firstCrossing(IntensityTimeline timeline, double threshold) {
        return timeline.samples().stream()
                .filter(sample -> sample.mmi().isPresent()
                        && sample.mmi().getAsDouble() >= threshold)
                .mapToDouble(IntensityTimeline.Sample::elapsedSeconds)
                .findFirst();
    }
}
