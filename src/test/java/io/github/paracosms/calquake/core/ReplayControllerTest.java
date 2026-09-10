package io.github.paracosms.calquake.core;

import io.github.paracosms.calquake.data.ScenarioLoader;
import io.github.paracosms.calquake.testsupport.FakeMonotonicClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayControllerTest {

    private Scenario scenario;
    private FakeMonotonicClock clock;
    private ReplayController controller;

    @BeforeEach
    void setUp() {
        scenario = new ScenarioLoader().loadDefaultScenario();
        ReplayEngine engine = ReplayEngine.create(scenario, HadleyKanamoriTauPModel.create());
        clock = new FakeMonotonicClock(1_000_000_000L);
        controller = new ReplayController(scenario, engine, clock);
    }

    @Test
    void startsPausedWithInitialFrame() {
        assertEquals(PlaybackState.PAUSED, controller.state());
        assertTrue(controller.isPaused());
        assertFalse(controller.isPlaying());
        assertFalse(controller.isFinished());
        assertEquals(0.0, controller.elapsedSeconds(), 1e-9);
        assertEquals(controller.engine().preparedReplay().durationSeconds(),
                controller.durationSeconds(), 1e-9);

        FrameState frame = controller.currentFrame();
        assertEquals(0.0, frame.elapsedSeconds(), 1e-9);
        assertFalse(frame.frontRadii().hasP());
        assertFalse(frame.frontRadii().hasS());
    }

    @Test
    void elapsedTimeTracksClockRatherThanTickFrequency() {
        controller.play();
        clock.advanceSeconds(10.0);
        FrameState singleTickFrame = controller.tick();
        assertEquals(10.0, singleTickFrame.elapsedSeconds(), 1e-6);
        assertTrue(singleTickFrame.frontRadii().hasP());
        assertTrue(singleTickFrame.frontRadii().hasS());

        controller.restart();
        controller.play();
        for (int i = 0; i < 600; i++) {
            clock.advanceSeconds(1.0 / 60.0);
            controller.tick();
        }

        assertEquals(10.0, controller.elapsedSeconds(), 1e-4,
                "One long tick and 600 short ticks must represent the same elapsed time");
    }

    @Test
    void pauseResumeExcludesInactiveTimeAcrossRepeatedCycles() {
        double expectedElapsed = 0.0;
        controller.play();

        for (double activeSeconds : new double[]{2.0, 3.5, 1.5}) {
            clock.advanceSeconds(activeSeconds);
            controller.pause();
            expectedElapsed += activeSeconds;
            assertEquals(expectedElapsed, controller.elapsedSeconds(), 1e-6);
            assertTrue(controller.isPaused());

            clock.advanceSeconds(20.0);
            controller.tick();
            assertEquals(expectedElapsed, controller.elapsedSeconds(), 1e-6,
                    "Inactive wall-clock time must not advance the replay");
            controller.play();
        }

        clock.advanceSeconds(1.0);
        controller.tick();
        assertEquals(8.0, controller.elapsedSeconds(), 1e-6);
        assertTrue(controller.isPlaying());
    }

    @Test
    void replayEndRequiresRestartBeforePlaybackCanContinue() {
        controller.play();
        clock.advanceSeconds(controller.durationSeconds() + 5.0);
        FrameState frame = controller.tick();

        assertEquals(PlaybackState.FINISHED, controller.state());
        assertEquals(controller.durationSeconds(), controller.elapsedSeconds(), 1e-9);
        assertEquals(controller.durationSeconds(), frame.elapsedSeconds(), 1e-9);

        clock.advanceSeconds(10.0);
        controller.tick();
        controller.play();
        controller.togglePlayPause();
        assertEquals(PlaybackState.FINISHED, controller.state());
        assertEquals(controller.durationSeconds(), controller.elapsedSeconds(), 1e-9);

        controller.restart();
        assertTrue(controller.isPaused());
        assertEquals(0.0, controller.elapsedSeconds(), 1e-9);
        controller.play();
        clock.advanceSeconds(2.0);
        controller.tick();
        assertEquals(2.0, controller.elapsedSeconds(), 1e-6);
    }

    @Test
    void pausingAtEndBoundaryAlsoFinishesReplay() {
        controller.play();
        clock.advanceSeconds(controller.durationSeconds() + 0.5);
        controller.pause();

        assertEquals(PlaybackState.FINISHED, controller.state());
        assertEquals(controller.durationSeconds(), controller.elapsedSeconds(), 1e-9);
    }

    @Test
    void simulatedUtcUsesEventOriginAndReplayElapsedTime() {
        Instant origin = scenario.event().originUtc();
        assertEquals(origin, controller.simulatedUtc());

        controller.play();
        clock.advanceSeconds(45.5);
        controller.tick();

        assertEquals(origin.plus(Duration.ofMillis(45_500L)), controller.simulatedUtc());
    }

    @Test
    void seekUpdatesElapsedTimeAndTransitionsState() {
        // 1. Seeking while paused updates elapsed time and current frame
        controller.seek(45.0);
        assertEquals(45.0, controller.elapsedSeconds(), 1e-9);
        assertTrue(controller.isPaused());
        assertEquals(45.0, controller.currentFrame().elapsedSeconds(), 1e-9);

        // 2. Seeking while playing preserves playing state and smoothly continues
        controller.play();
        assertTrue(controller.isPlaying());
        controller.seek(30.0);
        assertEquals(30.0, controller.elapsedSeconds(), 1e-9);
        assertTrue(controller.isPlaying());

        clock.advanceSeconds(5.0);
        controller.tick();
        assertEquals(35.0, controller.elapsedSeconds(), 1e-6);

        // 3. Seeking past the prepared duration clamps to that duration and finishes
        controller.seek(controller.durationSeconds() + 30.0);
        assertEquals(controller.durationSeconds(), controller.elapsedSeconds(), 1e-9);
        assertTrue(controller.isFinished());

        // 4. Seeking backward from FINISHED transitions to PAUSED
        controller.seek(60.0);
        assertEquals(60.0, controller.elapsedSeconds(), 1e-9);
        assertTrue(controller.isPaused());

        // 5. Seeking negative value clamps to 0.0
        controller.seek(-10.0);
        assertEquals(0.0, controller.elapsedSeconds(), 1e-9);
        assertTrue(controller.isPaused());
    }

    @Test
    void explicitShortDurationControlsTickPauseSeekAndFinish() {
        ReplayEngine engine = ReplayEngine.create(scenario, HadleyKanamoriTauPModel.create());
        ReplayController shortReplay = new ReplayController(scenario, engine, clock, 12.5);

        assertEquals(12.5, shortReplay.durationSeconds(), 1e-9);

        shortReplay.play();
        clock.advanceSeconds(20.0);
        FrameState finalFrame = shortReplay.tick();

        assertTrue(shortReplay.isFinished());
        assertEquals(12.5, shortReplay.elapsedSeconds(), 1e-9);
        assertEquals(12.5, finalFrame.elapsedSeconds(), 1e-9);

        shortReplay.seek(4.0);
        assertTrue(shortReplay.isPaused());
        assertEquals(4.0, shortReplay.elapsedSeconds(), 1e-9);

        shortReplay.seek(99.0);
        assertTrue(shortReplay.isFinished());
        assertEquals(12.5, shortReplay.elapsedSeconds(), 1e-9);
    }

    @Test
    void explicitLongDurationDoesNotFinishAtLegacyBoundary() {
        ReplayEngine engine = ReplayEngine.create(scenario, HadleyKanamoriTauPModel.create());
        ReplayController longReplay = new ReplayController(scenario, engine, clock, 180.0);

        longReplay.play();
        clock.advanceSeconds(125.0);
        longReplay.tick();

        assertTrue(longReplay.isPlaying());
        assertEquals(125.0, longReplay.elapsedSeconds(), 1e-9);

        clock.advanceSeconds(60.0);
        longReplay.pause();
        assertTrue(longReplay.isFinished());
        assertEquals(180.0, longReplay.elapsedSeconds(), 1e-9);
    }

    @Test
    void explicitDurationMustBePositiveAndFinite() {
        ReplayEngine engine = ReplayEngine.create(scenario, HadleyKanamoriTauPModel.create());

        assertThrows(IllegalArgumentException.class,
                () -> new ReplayController(scenario, engine, clock, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new ReplayController(scenario, engine, clock, -1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new ReplayController(scenario, engine, clock, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new ReplayController(scenario, engine, clock, Double.POSITIVE_INFINITY));
    }
}
