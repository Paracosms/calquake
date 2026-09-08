package io.github.paracosms.calquake.core;

import io.github.paracosms.calquake.data.ScenarioLoader;
import io.github.paracosms.calquake.testsupport.FakeMonotonicClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying Stage 4 ReplayController lifecycle state machine
 * and monotonic clock integration:
 * <ul>
 *   <li>Starts paused at 0.0 s.</li>
 *   <li>Pause preserves elapsed time; clock progression during pause has no effect.</li>
 *   <li>Resume continues from preserved elapsed time.</li>
 *   <li>Restart returns to 0.0 s and pauses.</li>
 *   <li>At 120.0 s, halts in FINISHED state and requires Restart.</li>
 *   <li>Derives time from clock differences, independent of frame rate or tick frequency.</li>
 *   <li>Keeps origin UTC separate from playback clock.</li>
 * </ul>
 */
class ReplayControllerTest {

    private Scenario scenario;
    private ReplayEngine engine;
    private FakeMonotonicClock clock;
    private ReplayController controller;

    @BeforeEach
    void setUp() {
        ScenarioLoader loader = new ScenarioLoader();
        scenario = loader.loadDefaultScenario();
        HadleyKanamoriTauPModel model = HadleyKanamoriTauPModel.create();
        engine = ReplayEngine.create(scenario, model);
        clock = new FakeMonotonicClock(1_000_000_000L); // 1.0 s baseline
        controller = new ReplayController(scenario, engine, clock);
    }

    @Test
    @DisplayName("Verify controller starts PAUSED at 0.0 seconds")
    void testInitialState() {
        assertEquals(PlaybackState.PAUSED, controller.state());
        assertTrue(controller.isPaused());
        assertFalse(controller.isPlaying());
        assertFalse(controller.isFinished());
        assertEquals(0.0, controller.elapsedSeconds(), 1e-9);

        FrameState frame = controller.currentFrame();
        assertEquals(0.0, frame.elapsedSeconds(), 1e-9);
        assertFalse(frame.frontRadii().hasP());
        assertFalse(frame.frontRadii().hasS());
    }

    @Test
    @DisplayName("Verify playback progression driven by monotonic clock differences")
    void testPlaybackProgression() {
        controller.play();
        assertEquals(PlaybackState.PLAYING, controller.state());
        assertTrue(controller.isPlaying());

        // Advance 5.0 seconds
        clock.advanceSeconds(5.0);
        FrameState frame = controller.tick();

        assertEquals(5.0, controller.elapsedSeconds(), 1e-6);
        assertEquals(5.0, frame.elapsedSeconds(), 1e-6);
        assertTrue(frame.frontRadii().hasP());
        assertTrue(frame.frontRadii().hasS());
    }

    @Test
    @DisplayName("Verify time derivation is frame-rate independent (accumulates clock diffs, not frame counts)")
    void testFrameRateIndependence() {
        // Scenario A: 1 tick of 10.0 seconds
        controller.play();
        clock.advanceSeconds(10.0);
        controller.tick();
        double elapsedA = controller.elapsedSeconds();
        assertEquals(10.0, elapsedA, 1e-6);

        // Scenario B: reset and run 600 ticks of 1/60th second
        controller.restart();
        assertEquals(0.0, controller.elapsedSeconds(), 1e-9);
        controller.play();

        double tickStepSec = 1.0 / 60.0;
        for (int i = 0; i < 600; i++) {
            clock.advanceSeconds(tickStepSec);
            controller.tick();
        }
        double elapsedB = controller.elapsedSeconds();
        assertEquals(10.0, elapsedB, 1e-4);
    }

    @Test
    @DisplayName("Verify pause preserves elapsed time even while clock advances")
    void testPausePreservesTime() {
        controller.play();
        clock.advanceSeconds(15.0);
        controller.pause();

        assertEquals(PlaybackState.PAUSED, controller.state());
        assertEquals(15.0, controller.elapsedSeconds(), 1e-6);

        // Advance clock by 100 seconds while paused
        clock.advanceSeconds(100.0);
        controller.tick();

        // Elapsed time must remain 15.0 s
        assertEquals(15.0, controller.elapsedSeconds(), 1e-6);
        assertEquals(PlaybackState.PAUSED, controller.state());
    }

    @Test
    @DisplayName("Verify resume continues from preserved elapsed time")
    void testResumeContinuesPlayback() {
        controller.play();
        clock.advanceSeconds(12.5);
        controller.pause();
        assertEquals(12.5, controller.elapsedSeconds(), 1e-6);

        // Wait while paused
        clock.advanceSeconds(50.0);

        // Resume
        controller.play();
        assertEquals(PlaybackState.PLAYING, controller.state());

        // Advance another 7.5 seconds
        clock.advanceSeconds(7.5);
        controller.tick();

        // Total should be 12.5 + 7.5 = 20.0 seconds
        assertEquals(20.0, controller.elapsedSeconds(), 1e-6);
    }

    @Test
    @DisplayName("Verify inactive time is excluded when playback resumes near the 120s limit")
    void testInactiveTimeDoesNotCountTowardReplayLimit() {
        controller.play();
        clock.advanceSeconds(110.0);
        controller.tick();
        assertEquals(110.0, controller.elapsedSeconds(), 1e-6);

        // Window deactivation: pause anchors the monotonic clock at the deactivation instant.
        controller.pause();
        clock.advanceSeconds(11.0);
        controller.tick();
        assertEquals(110.0, controller.elapsedSeconds(), 1e-6,
                "Time spent inactive must not advance the replay");
        assertTrue(controller.isPaused());

        // Window reactivation: resume from the preserved elapsed time.
        controller.play();
        clock.advanceSeconds(1.0);
        controller.tick();
        assertEquals(111.0, controller.elapsedSeconds(), 1e-6);
        assertTrue(controller.isPlaying());
        assertFalse(controller.isFinished());
    }

    @Test
    @DisplayName("Verify repeated deactivate/reactivate cycles preserve active playback time")
    void testRepeatedDeactivateReactivateCycles() {
        controller.play();

        for (int cycle = 0; cycle < 3; cycle++) {
            clock.advanceSeconds(2.0);
            controller.pause();
            clock.advanceSeconds(15.0);
            controller.play();
        }

        clock.advanceSeconds(1.0);
        controller.tick();

        assertEquals(7.0, controller.elapsedSeconds(), 1e-6,
                "Only the active intervals should contribute to elapsed time");
        assertTrue(controller.isPlaying());
    }

    @Test
    @DisplayName("Verify an explicitly paused replay remains paused while inactive time passes")
    void testExplicitlyPausedReplayRemainsPaused() {
        assertTrue(controller.isPaused());

        clock.advanceSeconds(25.0);
        controller.tick();

        assertTrue(controller.isPaused());
        assertEquals(0.0, controller.elapsedSeconds(), 1e-9);
    }

    @Test
    @DisplayName("Verify togglePlayPause switches between PAUSED and PLAYING")
    void testTogglePlayPause() {
        assertTrue(controller.isPaused());
        controller.togglePlayPause();
        assertTrue(controller.isPlaying());

        clock.advanceSeconds(3.0);
        controller.togglePlayPause();
        assertTrue(controller.isPaused());
        assertEquals(3.0, controller.elapsedSeconds(), 1e-6);
    }

    @Test
    @DisplayName("Verify 120s end boundary enters FINISHED state and requires Restart")
    void testReplayEndBoundary() {
        controller.play();
        // Advance past 120 seconds
        clock.advanceSeconds(125.0);
        FrameState frame = controller.tick();

        assertEquals(PlaybackState.FINISHED, controller.state());
        assertTrue(controller.isFinished());
        assertFalse(controller.isPlaying());
        assertFalse(controller.isPaused());
        assertEquals(120.0, controller.elapsedSeconds(), 1e-9, "Time must clamp to 120.0 s");
        assertEquals(120.0, frame.elapsedSeconds(), 1e-9);

        // Advancing clock further or ticking stays FINISHED at 120.0
        clock.advanceSeconds(10.0);
        controller.tick();
        assertEquals(120.0, controller.elapsedSeconds(), 1e-9);
        assertEquals(PlaybackState.FINISHED, controller.state());

        // Calling play while FINISHED must be a no-op
        controller.play();
        assertEquals(PlaybackState.FINISHED, controller.state());
        assertEquals(120.0, controller.elapsedSeconds(), 1e-9);

        // Calling togglePlayPause while FINISHED must be a no-op
        controller.togglePlayPause();
        assertEquals(PlaybackState.FINISHED, controller.state());

        // Calling restart resets to 0 and PAUSED
        controller.restart();
        assertEquals(PlaybackState.PAUSED, controller.state());
        assertEquals(0.0, controller.elapsedSeconds(), 1e-9);
        assertTrue(controller.isPaused());

        // Can now play again
        controller.play();
        assertTrue(controller.isPlaying());
        clock.advanceSeconds(2.0);
        controller.tick();
        assertEquals(2.0, controller.elapsedSeconds(), 1e-6);
    }

    @Test
    @DisplayName("Verify pause when reaching 120s also transitions to FINISHED")
    void testPauseAtEndBoundary() {
        controller.play();
        clock.advanceSeconds(120.5);
        controller.pause();

        assertEquals(PlaybackState.FINISHED, controller.state());
        assertEquals(120.0, controller.elapsedSeconds(), 1e-9);
    }

    @Test
    @DisplayName("Verify simulated UTC timestamp advances with elapsed time independent of system clock")
    void testSimulatedUtc() {
        Instant origin = scenario.event().originUtc();
        assertEquals(origin, controller.simulatedUtc());

        controller.play();
        clock.advanceSeconds(45.5);
        controller.tick();

        Instant expected = origin.plus(Duration.ofMillis(45500L));
        assertEquals(expected, controller.simulatedUtc());
    }
}
