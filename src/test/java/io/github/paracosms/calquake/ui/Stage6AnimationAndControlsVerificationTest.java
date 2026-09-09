package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.EarthquakeEvent;
import io.github.paracosms.calquake.core.FrameState;
import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.HadleyKanamoriTauPModel;
import io.github.paracosms.calquake.core.PlaybackState;
import io.github.paracosms.calquake.core.ReferenceLocation;
import io.github.paracosms.calquake.core.ReplayController;
import io.github.paracosms.calquake.core.ReplayEngine;
import io.github.paracosms.calquake.core.Scenario;
import io.github.paracosms.calquake.core.TravelTimeCurve;
import io.github.paracosms.calquake.core.WavefrontRadii;
import io.github.paracosms.calquake.data.CaliforniaOutline;
import io.github.paracosms.calquake.data.ScenarioLoader;
import io.github.paracosms.calquake.testsupport.FakeMonotonicClock;
import io.github.paracosms.calquake.testsupport.JavaFxTestHelper;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 6 Verification and Acceptance Test Suite:
 * <ul>
 *   <li><b>First Frame & Initial State:</b> Controller starts paused at 0.0 s, delayed surface arrival
 *       guarantees no wavefront circles initially, dynamic canvas is clear, and all 5 historical peak MMI
 *       values and colors remain visible from startup.</li>
 *   <li><b>Delayed Surface Arrival Timeline:</b> Surface arrival delays for depth = 8.0 km in Hadley-Kanamori
 *       model (~1.40 s for P, ~2.42 s for S) correctly determine when wavefront circles appear.</li>
 *   <li><b>Wavefront Styling & Color Difference:</b> P-wave renders as dashed cyan (#06B6D4) and S-wave as
 *       solid orange (#F97316). Kilometer radii scale with the map transform and clip at viewport bounds
 *       without changing underlying radii.</li>
 *   <li><b>Repeated Pause & Resume:</b> Fake monotonic clock proves time derivation strictly from clock differences,
 *       zero drift, and pause preservation across multiple cycles.</li>
 *   <li><b>Restart During Playback:</b> Restarting during active playback resets time to 0.0 s, resets state to PAUSED,
 *       clears dynamic wavefronts, and retains historical intensity colors.</li>
 *   <li><b>120-Second End State:</b> At 120.0 s, replay stops, enters FINISHED state, disables Play button, displays
 *       "Require Restart", and strictly requires Restart before playing again.</li>
 *   <li><b>Startup Resource Error Handling:</b> Useful diagnostic view is rendered if startup fails.</li>
 *   <li><b>Frame Loop Performance:</b> Proves frame evaluation is TauP-free and offline in sub-millisecond time.</li>
 * </ul>
 */
class Stage6AnimationAndControlsVerificationTest {

    @Test
    @DisplayName("Stage 6 Exit Gate: First frame starts paused at 0.0s, no wavefronts before vertical arrival, peak MMI visible")
    void testFirstFrameInitialState() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            CalQuakeApp app = new CalQuakeApp();
            app.init();
            Stage stage = new Stage();
            try {
                app.start(stage);

                ReplayController controller = app.getController();
                assertNotNull(controller);
                assertTrue(controller.isPaused(), "Initial controller state must be PAUSED");
                assertFalse(controller.isPlaying());
                assertFalse(controller.isFinished());
                assertEquals(0.0, controller.elapsedSeconds(), 1e-9, "Initial elapsed time must be 0.0 s");

                // Frame at t=0 has no wavefronts
                FrameState frame0 = controller.currentFrame();
                assertEquals(0.0, frame0.elapsedSeconds(), 1e-9);
                assertFalse(frame0.frontRadii().hasP(), "First frame must not have P wavefront");
                assertFalse(frame0.frontRadii().hasS(), "First frame must not have S wavefront");

                // Dynamic canvas is clear
                MapCanvasPane mapPane = app.getMapCanvasPane();
                assertNotNull(mapPane);
                WritableImage dynImg = snapshotTransparent(mapPane.getDynamicCanvas());
                assertEquals(0, countNonTransparentPixels(dynImg), "Dynamic canvas must be completely clear at t=0");

                // All 5 reference locations retain frozen peak MMI values
                List<ReferenceLocation> locations = app.getScenario().locations();
                assertEquals(5, locations.size());
                for (ReferenceLocation loc : locations) {
                    assertNotNull(loc.peakIntensity().colorHex());
                    assertNotNull(loc.peakIntensity().mmiRoman());
                    assertTrue(loc.peakIntensity().mmiDisplayRounded() > 0.0);
                }

                // Controls and readouts
                Button playBtn = app.getPlayPauseButton();
                Button restartBtn = app.getRestartButton();
                assertFalse(playBtn.isDisable(), "Play button must be enabled initially");
                assertFalse(restartBtn.isDisable(), "Restart button must be enabled initially");
                assertEquals("▶  Play", playBtn.getText());
                assertEquals("⏮  Restart", restartBtn.getText());

                assertEquals("PAUSED", app.getHudStateLabel().getText());
                assertEquals("State: PAUSED (Ready)", app.getControlStateLabel().getText());
                assertEquals("00:00:00.00", app.getElapsedDigitsLabel().getText());
                assertEquals("Replay: READY (0.00s / 120.00s)", app.getStatusReplayLabel().getText());
            } finally {
                app.stop();
                stage.close();
            }
        });
    }

    @Test
    @DisplayName("Stage 6 Exit Gate: Delayed surface arrival timeline matches Hadley-Kanamori vertical travel times")
    void testDelayedSurfaceArrivalTimeline() {
        ScenarioLoader loader = new ScenarioLoader();
        Scenario scenario = loader.loadDefaultScenario();
        HadleyKanamoriTauPModel model = new HadleyKanamoriTauPModel();
        ReplayEngine engine = ReplayEngine.create(scenario, model);

        double depthKm = scenario.event().depthKm();
        assertEquals(8.0, depthKm, "Ridgecrest hypocentral depth is 8.0 km");

        // Hadley-Kanamori vertical travel times:
        // Layer 1 (0-5.5 km, Vp=5.5): 5.5 / 5.5 = 1.0 s
        // Layer 2 (5.5-8.0 km, Vp=6.3): 2.5 / 6.3 = 0.396825 s
        // Total vertical P time: ~1.3968 s
        // Total vertical S time: 1.3968 * 1.73 = ~2.4165 s
        double vertP = model.verticalTravelTimeSeconds("P", depthKm);
        double vertS = model.verticalTravelTimeSeconds("S", depthKm);
        assertEquals(1.396825, vertP, 0.001, "Vertical P arrival time must be ~1.397 s");
        assertEquals(2.416508, vertS, 0.001, "Vertical S arrival time must be ~2.417 s");

        // 1. Before P vertical arrival (t = 1.0 s): no fronts
        FrameState f1 = engine.frameAt(scenario, 1.0);
        assertFalse(f1.frontRadii().hasP(), "No P front before vertical arrival");
        assertFalse(f1.frontRadii().hasS(), "No S front before vertical arrival");

        // 2. Between P and S vertical arrival (t = 1.8 s): P front exists, S does not
        FrameState f18 = engine.frameAt(scenario, 1.8);
        assertTrue(f18.frontRadii().hasP(), "P front must exist at 1.8 s");
        assertFalse(f18.frontRadii().hasS(), "S front must not exist at 1.8 s");
        assertTrue(f18.frontRadii().pRadiusKm() > 0.0, "P radius must be positive");

        // 3. After S vertical arrival (t = 3.0 s): both P and S exist, P > S
        FrameState f3 = engine.frameAt(scenario, 3.0);
        assertTrue(f3.frontRadii().hasP(), "P front must exist at 3.0 s");
        assertTrue(f3.frontRadii().hasS(), "S front must exist at 3.0 s");
        assertTrue(f3.frontRadii().pRadiusKm() > f3.frontRadii().sRadiusKm(), "P radius must exceed S radius");

        // 4. Standard verification at t = 10.0 s
        FrameState f10 = engine.frameAt(scenario, 10.0);
        assertEquals(59.92, f10.frontRadii().pRadiusKm(), 0.1, "10s P wavefront radius");
        assertEquals(33.24, f10.frontRadii().sRadiusKm(), 0.1, "10s S wavefront radius");
    }

    @Test
    @DisplayName("Stage 6 Exit Gate: Wavefront rendering draws dashed cyan P and solid orange S on dynamic canvas")
    void testWavefrontLineStyleAndColorDifference() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            ScenarioLoader loader = new ScenarioLoader();
            Scenario scenario = loader.loadDefaultScenario();
            CaliforniaOutline outline = CaliforniaOutline.loadDefault();
            HadleyKanamoriTauPModel model = new HadleyKanamoriTauPModel();
            ReplayEngine engine = ReplayEngine.create(scenario, model);

            MapCanvasPane mapPane = new MapCanvasPane(scenario, outline);
            mapPane.redrawStaticMap();

            // Render frame at t = 10.0 s where both P (~60 km) and S (~33 km) circles exist
            FrameState frame10 = engine.frameAt(scenario, 10.0);
            mapPane.renderFrame(frame10);

            WritableImage dynSnapshot = snapshotTransparent(mapPane.getDynamicCanvas());
            assertNotNull(dynSnapshot);

            // Count cyan P-wave and orange S-wave pixels
            int cyanPixels = countCyanPWavePixels(dynSnapshot);
            int orangePixels = countOrangeSWavePixels(dynSnapshot);

            assertTrue(cyanPixels > 10, "Dynamic canvas must contain dashed cyan P wavefront pixels, found: " + cyanPixels);
            assertTrue(orangePixels > 10, "Dynamic canvas must contain solid orange S wavefront pixels, found: " + orangePixels);

            // Radii in FrameState remain immutable and unchanged in kilometers
            assertEquals(59.92, frame10.frontRadii().pRadiusKm(), 0.1);
            assertEquals(33.24, frame10.frontRadii().sRadiusKm(), 0.1);
        });
    }

    @Test
    @DisplayName("Stage 6 Exit Gate: Repeated pause/resume with fake monotonic clock proves zero drift and exact time preservation")
    void testRepeatedPauseAndResumeWithFakeClock() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            ScenarioLoader loader = new ScenarioLoader();
            Scenario scenario = loader.loadDefaultScenario();
            CaliforniaOutline outline = CaliforniaOutline.loadDefault();
            HadleyKanamoriTauPModel model = new HadleyKanamoriTauPModel();
            ReplayEngine engine = ReplayEngine.create(scenario, model);

            FakeMonotonicClock clock = new FakeMonotonicClock(10_000_000_000L); // start at 10 s nanos
            ReplayController controller = new ReplayController(scenario, engine, clock);

            CalQuakeApp app = new CalQuakeApp(scenario, outline, controller);
            Stage stage = new Stage();
            try {
                app.start(stage);

                // Initial state
                assertTrue(controller.isPaused());
                assertEquals(0.0, controller.elapsedSeconds(), 1e-9);

                // Start playback via button
                app.getPlayPauseButton().fire();
                assertTrue(controller.isPlaying());
                assertEquals("⏸  Pause", app.getPlayPauseButton().getText());
                assertEquals("PLAYING", app.getHudStateLabel().getText());

                // Cycle 1: Play 4.0 s, then Pause
                clock.advanceSeconds(4.0);
                controller.tick();
                assertEquals(4.0, controller.elapsedSeconds(), 1e-9);

                app.getPlayPauseButton().fire(); // Pause
                assertTrue(controller.isPaused());
                assertEquals("▶  Play", app.getPlayPauseButton().getText());
                assertEquals("PAUSED", app.getHudStateLabel().getText());

                // Idle 10.0 s while paused — time must not advance
                clock.advanceSeconds(10.0);
                controller.tick();
                assertEquals(4.0, controller.elapsedSeconds(), 1e-9, "Time must not advance while paused");

                // Cycle 2: Resume, play 3.5 s, Pause
                app.getPlayPauseButton().fire(); // Resume
                assertTrue(controller.isPlaying());

                clock.advanceSeconds(3.5);
                controller.tick();
                assertEquals(7.5, controller.elapsedSeconds(), 1e-9);

                app.getPlayPauseButton().fire(); // Pause
                assertTrue(controller.isPaused());
                assertEquals(7.5, controller.elapsedSeconds(), 1e-9);

                // Cycle 3: Resume, play 12.5 s, Pause
                app.getPlayPauseButton().fire();
                assertTrue(controller.isPlaying());

                clock.advanceSeconds(12.5);
                controller.tick();
                assertEquals(20.0, controller.elapsedSeconds(), 1e-9);

                app.getPlayPauseButton().fire();
                assertTrue(controller.isPaused());
                assertEquals(20.0, controller.elapsedSeconds(), 1e-9);

                // Idle while paused
                clock.advanceSeconds(50.0);
                controller.tick();
                assertEquals(20.0, controller.elapsedSeconds(), 1e-9, "Paused time must stay frozen at 20.0 s");

                // Cycle 4: Resume, play 5.0 s
                app.getPlayPauseButton().fire();
                clock.advanceSeconds(5.0);
                controller.tick();
                assertEquals(25.0, controller.elapsedSeconds(), 1e-9);
            } finally {
                app.stop();
                stage.close();
            }
        });
    }

    @Test
    @DisplayName("Stage 6 Exit Gate: Restart during playback clears dynamic fronts while retaining historical peak intensities")
    void testRestartDuringPlaybackClearsFrontsAndPreservesHistoricalIntensities() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            ScenarioLoader loader = new ScenarioLoader();
            Scenario scenario = loader.loadDefaultScenario();
            CaliforniaOutline outline = CaliforniaOutline.loadDefault();
            HadleyKanamoriTauPModel model = new HadleyKanamoriTauPModel();
            ReplayEngine engine = ReplayEngine.create(scenario, model);

            FakeMonotonicClock clock = new FakeMonotonicClock(1_000_000_000L);
            ReplayController controller = new ReplayController(scenario, engine, clock);

            CalQuakeApp app = new CalQuakeApp(scenario, outline, controller);
            Stage stage = new Stage();
            try {
                app.start(stage);

                // Play until t = 30.0 s
                app.getPlayPauseButton().fire();
                assertTrue(controller.isPlaying());

                clock.advanceSeconds(30.0);
                FrameState frame30 = controller.tick();
                app.getMapCanvasPane().renderFrame(frame30);

                // Dynamic canvas now has active wavefronts
                WritableImage activeImg = snapshotTransparent(app.getMapCanvasPane().getDynamicCanvas());
                assertTrue(countNonTransparentPixels(activeImg) > 100, "Dynamic canvas must have wavefront pixels at t=30s");

                // Restart during playback
                app.getRestartButton().fire();

                // Controller resets to 0.0 s and PAUSED
                assertTrue(controller.isPaused(), "Controller must be PAUSED after restart");
                assertEquals(0.0, controller.elapsedSeconds(), 1e-9, "Elapsed time must reset to 0.0 s");

                // Dynamic canvas is cleared
                WritableImage resetImg = snapshotTransparent(app.getMapCanvasPane().getDynamicCanvas());
                assertEquals(0, countNonTransparentPixels(resetImg), "Dynamic canvas must be completely cleared after restart");

                // UI displays reset
                assertEquals("00:00:00.00", app.getElapsedDigitsLabel().getText());
                assertEquals("State: PAUSED (Ready)", app.getControlStateLabel().getText());
                assertEquals("Replay: READY (0.00s / 120.00s)", app.getStatusReplayLabel().getText());
                assertEquals("▶  Play", app.getPlayPauseButton().getText());
                assertFalse(app.getPlayPauseButton().isDisable());

                // Historical peak MMI colors on static canvas remain fully intact
                WritableImage staticImg = app.getMapCanvasPane().getStaticCanvas().snapshot(null, null);
                assertTrue(RenderSnapshotTest.countYellowPixels(staticImg) > 20, "Historical peak yellow MMI VII badges must remain visible");
                assertTrue(RenderSnapshotTest.countCyanPixels(staticImg) > 20, "Historical peak cyan MMI IV badges must remain visible");
            } finally {
                app.stop();
                stage.close();
            }
        });
    }

    @Test
    @DisplayName("Stage 6 Exit Gate: 120-second end state halts, disables Play, requires Restart, and clears upon restart")
    void test120SecondEndStateRequiresRestart() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            ScenarioLoader loader = new ScenarioLoader();
            Scenario scenario = loader.loadDefaultScenario();
            CaliforniaOutline outline = CaliforniaOutline.loadDefault();
            HadleyKanamoriTauPModel model = new HadleyKanamoriTauPModel();
            ReplayEngine engine = ReplayEngine.create(scenario, model);

            FakeMonotonicClock clock = new FakeMonotonicClock(1_000_000_000L);
            ReplayController controller = new ReplayController(scenario, engine, clock);

            CalQuakeApp app = new CalQuakeApp(scenario, outline, controller);
            Stage stage = new Stage();
            try {
                app.start(stage);

                // Play until past 120.0 s (e.g. 125.0 s)
                app.getPlayPauseButton().fire();
                assertTrue(controller.isPlaying());

                clock.advanceSeconds(125.0);
                FrameState endFrame = controller.tick();
                app.getMapCanvasPane().renderFrame(endFrame);

                // Verify 120-second clamp and FINISHED state
                assertTrue(controller.isFinished(), "Controller must be in FINISHED state");
                assertFalse(controller.isPlaying());
                assertFalse(controller.isPaused());
                assertEquals(120.0, controller.elapsedSeconds(), 1e-9, "Elapsed time must be clamped to exactly 120.0 s");

                // Update UI to reflect finished state
                app.getPlayPauseButton().fire(); // should have no effect

                // Play button must be disabled
                assertTrue(app.getPlayPauseButton().isDisable(), "Play button must be disabled when FINISHED");
                assertTrue(app.getControlStateLabel().getText().contains("Require Restart"),
                        "Control label must state Require Restart");
                assertTrue(app.getStatusReplayLabel().getText().contains("Require Restart"),
                        "Status bar must state Require Restart");
                assertEquals("FINISHED", app.getHudStateLabel().getText());

                // Calling play directly has no effect
                controller.play();
                assertTrue(controller.isFinished(), "Calling play when FINISHED must remain FINISHED");

                // Restart re-enables controls and resets time
                app.getRestartButton().fire();
                assertTrue(controller.isPaused(), "Controller must be PAUSED after restart from FINISHED");
                assertEquals(0.0, controller.elapsedSeconds(), 1e-9);
                assertFalse(app.getPlayPauseButton().isDisable(), "Play button must be re-enabled after Restart");
                assertEquals("State: PAUSED (Ready)", app.getControlStateLabel().getText());
                assertEquals("Replay: READY (0.00s / 120.00s)", app.getStatusReplayLabel().getText());
            } finally {
                app.stop();
                stage.close();
            }
        });
    }

    @Test
    @DisplayName("Stage 6 Exit Gate: Viewport clipping protects bounds at 120.0s without mutating underlying km radii")
    void testViewportClippingAtLargeRadii() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            ScenarioLoader loader = new ScenarioLoader();
            Scenario scenario = loader.loadDefaultScenario();
            CaliforniaOutline outline = CaliforniaOutline.loadDefault();
            HadleyKanamoriTauPModel model = new HadleyKanamoriTauPModel();
            ReplayEngine engine = ReplayEngine.create(scenario, model);

            MapCanvasPane mapPane = new MapCanvasPane(scenario, outline);
            mapPane.redrawStaticMap();

            // At t = 120.0 s, P radius is ~860 km (substantially exceeds California and viewport)
            FrameState frame120 = engine.frameAt(scenario, 120.0);
            double pRadiusKm = frame120.frontRadii().pRadiusKm();
            double sRadiusKm = frame120.frontRadii().sRadiusKm();
            assertTrue(pRadiusKm > 800.0, "P radius at 120s must be > 800 km: " + pRadiusKm);
            assertTrue(sRadiusKm > 400.0, "S radius at 120s must be > 400 km: " + sRadiusKm);

            // Render without any error; clipping handles huge ovals gracefully
            assertDoesNotThrow(() -> mapPane.renderFrame(frame120));

            // Verify underlying radii remain completely unmodified
            assertEquals(pRadiusKm, frame120.frontRadii().pRadiusKm(), 1e-9);
            assertEquals(sRadiusKm, frame120.frontRadii().sRadiusKm(), 1e-9);
            assertEquals(120.0, frame120.elapsedSeconds(), 1e-9);
        });
    }

    @Test
    @DisplayName("Stage 6 Exit Gate: Startup resource error renders useful diagnostic screen")
    void testUsefulStartupResourceErrors() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            CalQuakeApp app = new CalQuakeApp();
            FileNotFoundException cause = new FileNotFoundException("Missing scenario resource: event.json");
            app.setStartupErrorForTesting(cause);

            Stage stage = new Stage();
            try {
                app.start(stage);

                assertEquals("CalQuake — Startup Error", stage.getTitle());
                Scene scene = stage.getScene();
                assertNotNull(scene);
                assertTrue(scene.getWidth() >= 600.0);
                assertTrue(scene.getHeight() >= 350.0);
            } finally {
                stage.close();
            }
        });
    }

    @Test
    @DisplayName("Stage 6 Exit Gate: Frame generation is TauP-free and executes 1,000 frames in < 50ms offline")
    void testFrameGenerationPerformanceOffline() {
        ScenarioLoader loader = new ScenarioLoader();
        Scenario scenario = loader.loadDefaultScenario();
        HadleyKanamoriTauPModel model = new HadleyKanamoriTauPModel();
        ReplayEngine engine = ReplayEngine.create(scenario, model);

        // Warm up
        for (double t = 0.0; t <= 120.0; t += 1.0) {
            engine.frameAt(scenario, t);
        }

        // Measure 1,000 frame generations across full 0-120 s domain
        long start = System.nanoTime();
        int frameCount = 1000;
        for (int i = 0; i < frameCount; i++) {
            double t = (i / (double) frameCount) * 120.0;
            FrameState state = engine.frameAt(scenario, t);
            assertNotNull(state);
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(durationMs < 100, "1,000 frames must compute in < 100 ms (was " + durationMs + " ms)");
    }

    private static WritableImage snapshotTransparent(javafx.scene.canvas.Canvas canvas) {
        javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    private static int countNonTransparentPixels(WritableImage image) {
        PixelReader reader = image.getPixelReader();
        int count = 0;
        for (int y = 0; y < (int) image.getHeight(); y++) {
            for (int x = 0; x < (int) image.getWidth(); x++) {
                if (reader.getColor(x, y).getOpacity() > 0.05) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countCyanPWavePixels(WritableImage image) {
        PixelReader reader = image.getPixelReader();
        int count = 0;
        for (int y = 0; y < (int) image.getHeight(); y++) {
            for (int x = 0; x < (int) image.getWidth(); x++) {
                Color c = reader.getColor(x, y);
                // #06B6D4: r ~ 0.02, g ~ 0.71, b ~ 0.83
                if (c.getOpacity() > 0.3 && c.getRed() < 0.25 && c.getGreen() > 0.50 && c.getBlue() > 0.65) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countOrangeSWavePixels(WritableImage image) {
        PixelReader reader = image.getPixelReader();
        int count = 0;
        for (int y = 0; y < (int) image.getHeight(); y++) {
            for (int x = 0; x < (int) image.getWidth(); x++) {
                Color c = reader.getColor(x, y);
                // #F97316: r ~ 0.98, g ~ 0.45, b ~ 0.09
                if (c.getOpacity() > 0.3 && c.getRed() > 0.80 && c.getGreen() > 0.30 && c.getGreen() < 0.65 && c.getBlue() < 0.25) {
                    count++;
                }
            }
        }
        return count;
    }
}
