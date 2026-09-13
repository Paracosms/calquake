package io.github.paracosms.calquake.core;

import io.github.paracosms.calquake.data.ScenarioLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordedReplayPreparerTest {
    @ParameterizedTest
    @ValueSource(strings = {"Ridgecrest", "Northridge"})
    void historicalPeakAppearsExactlyAtSAndDurationIsLatestSPlusTen(String eventName) {
        ScenarioLoader loader = new ScenarioLoader();
        ScenarioLoader.ScenarioBundle bundle = loader.loadScenarioBundle(eventName);
        PreparedReplay replay = new RecordedReplayPreparer(new HadleyKanamoriTauPModel())
                .prepare(bundle.inputs(), bundle.references());
        double latestS = replay.timelinesBySiteId().values().stream()
                .mapToDouble(IntensityTimeline::sArrivalSeconds).max().orElseThrow();
        assertEquals(latestS + 10.0, replay.durationSeconds(), 1.0e-9);
        for (IntensityTimeline timeline : replay.timelinesBySiteId().values()) {
            assertTrue(timeline.stateAt(timeline.sArrivalSeconds() - 1.0e-6).currentMmi().isEmpty());
            double expected = bundle.references().require(timeline.site().id()).shakeMapPeakMmi().orElseThrow();
            assertEquals(expected, timeline.stateAt(timeline.sArrivalSeconds()).currentMmi().orElseThrow(), 0.0);
            assertEquals(expected, timeline.stateAt(replay.durationSeconds()).currentMmi().orElseThrow(), 0.0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ridgecrest", "Northridge"})
    void recordedModeExplicitlyRequiresReferences(String eventName) {
        ScenarioInputs inputs = new ScenarioLoader().loadScenarioInputs(eventName);
        assertThrows(IllegalArgumentException.class,
                () -> new RecordedReplayPreparer(new HadleyKanamoriTauPModel())
                        .prepare(inputs, new ScenarioReferences(Map.of())));
    }

    @org.junit.jupiter.api.Test
    void recordedReplayWorksWithAbsentFaultMechanismAndVs30() {
        ScenarioLoader loader = new ScenarioLoader();
        ScenarioLoader.ScenarioBundle recordedBundle = loader.loadRecordedScenarioBundle("Ridgecrest");
        PreparedReplay replayFromRecorded = new RecordedReplayPreparer(new HadleyKanamoriTauPModel())
                .prepare(recordedBundle.inputs(), recordedBundle.references());
        org.junit.jupiter.api.Assertions.assertNotNull(replayFromRecorded);

        // Compare with bundle loaded with full scientific inputs
        ScenarioLoader.ScenarioBundle fullBundle = loader.loadScenarioBundle("Ridgecrest");
        PreparedReplay replayFromFull = new RecordedReplayPreparer(new HadleyKanamoriTauPModel())
                .prepare(fullBundle.inputs(), fullBundle.references());

        assertEquals(replayFromFull.durationSeconds(), replayFromRecorded.durationSeconds(), 1.0e-9);
        for (String siteId : replayFromRecorded.timelinesBySiteId().keySet()) {
            var tRecorded = replayFromRecorded.timelinesBySiteId().get(siteId);
            var tFull = replayFromFull.timelinesBySiteId().get(siteId);
            assertEquals(tFull.sArrivalSeconds(), tRecorded.sArrivalSeconds(), 1.0e-9);
            assertEquals(tFull.pArrivalSeconds(), tRecorded.pArrivalSeconds(), 1.0e-9);
            assertEquals(tFull.finalMmi().orElseThrow(), tRecorded.finalMmi().orElseThrow(), 1.0e-9);
        }
    }
}
