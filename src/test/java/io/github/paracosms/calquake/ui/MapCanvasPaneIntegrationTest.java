package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.EventSource;
import io.github.paracosms.calquake.core.FrameState;
import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.HadleyKanamoriTauPModel;
import io.github.paracosms.calquake.core.MapScenario;
import io.github.paracosms.calquake.core.MmiMode;
import io.github.paracosms.calquake.core.PreparedReplay;
import io.github.paracosms.calquake.core.ReplayPreparer;
import io.github.paracosms.calquake.core.ReplayEngine;
import io.github.paracosms.calquake.core.Scenario;
import io.github.paracosms.calquake.core.ScenarioInputs;
import io.github.paracosms.calquake.core.ScenarioReferences;
import io.github.paracosms.calquake.core.SimulationSite;
import io.github.paracosms.calquake.data.CaliforniaOutline;
import io.github.paracosms.calquake.data.ScenarioLoader;
import io.github.paracosms.calquake.testsupport.JavaFxTestHelper;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapCanvasPaneIntegrationTest {

    private static Scenario scenario;
    private static CaliforniaOutline outline;
    private static ReplayEngine engine;

    @BeforeAll
    static void setUp() {
        scenario = new ScenarioLoader().loadDefaultScenario();
        outline = CaliforniaOutline.loadDefault();
        engine = ReplayEngine.create(scenario, new HadleyKanamoriTauPModel());
    }

    @Test
    void staticMapAndTimedWavefrontsRender() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            MapCanvasPane pane = new MapCanvasPane(scenario, outline);
            pane.resize(MapCanvasPane.BASELINE_VIEWPORT_WIDTH, MapCanvasPane.BASELINE_VIEWPORT_HEIGHT);

            FrameState initialFrame = engine.frameAt(scenario, 0.0);
            pane.refresh(initialFrame);
            assertEquals(initialFrame, pane.getLastFrame());
            assertTrue(countVisiblePixels(pane.getStaticCanvas()) > 0, "The base map must render");
            assertEquals(0, countVisiblePixels(pane.getDynamicCanvas()),
                    "No wavefront is visible before its surface arrival");

            FrameState activeFrame = engine.frameAt(scenario, 10.0);
            pane.renderFrame(activeFrame);
            assertEquals(activeFrame, pane.getLastFrame());
            assertTrue(countVisiblePixels(pane.getDynamicCanvas()) > 0,
                    "P and S wavefronts must render after their arrivals");

            FrameState finalFrame = engine.frameAt(scenario, engine.preparedReplay().durationSeconds());
            assertDoesNotThrow(() -> pane.renderFrame(finalFrame),
                    "Wavefronts extending beyond the viewport must be clipped safely");
            assertEquals(finalFrame, pane.getLastFrame());
        });
    }

    @Test
    void refreshRestoresClearedCanvasLayersAndCurrentFrame() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            FrameState frame = engine.frameAt(scenario, 30.0);
            MapCanvasPane pane = new MapCanvasPane(scenario, outline);
            pane.resize(MapCanvasPane.BASELINE_VIEWPORT_WIDTH, MapCanvasPane.BASELINE_VIEWPORT_HEIGHT);
            pane.refresh(frame);

            clear(pane.getStaticCanvas());
            clear(pane.getDynamicCanvas());
            assertEquals(0, countVisiblePixels(pane.getStaticCanvas()));
            assertEquals(0, countVisiblePixels(pane.getDynamicCanvas()));

            pane.refresh(frame);

            assertTrue(countVisiblePixels(pane.getStaticCanvas()) > 0,
                    "Window restoration must repaint the base map");
            assertTrue(countVisiblePixels(pane.getDynamicCanvas()) > 0,
                    "Window restoration must repaint the current wavefronts");
            assertEquals(frame, pane.getLastFrame());
        });
    }

    @Test
    void frameRenderingMeetsInteractiveThroughputBudget() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            MapCanvasPane pane = new MapCanvasPane(scenario, outline);
            pane.resize(MapCanvasPane.BASELINE_VIEWPORT_WIDTH, MapCanvasPane.BASELINE_VIEWPORT_HEIGHT);
            pane.redrawStaticMap();

            int frameCount = 500;
            FrameState[] frames = new FrameState[frameCount];
            for (int i = 0; i < frameCount; i++) {
                frames[i] = engine.frameAt(scenario,
                        i * engine.preparedReplay().durationSeconds() / frameCount);
            }
            for (int i = 0; i < 50; i++) {
                pane.renderFrame(frames[i]);
            }

            long startNanos = System.nanoTime();
            for (FrameState frame : frames) {
                pane.renderFrame(frame);
            }
            double millisecondsPerFrame = (System.nanoTime() - startNanos) / 1_000_000.0 / frameCount;

            assertTrue(millisecondsPerFrame < 33.33,
                    () -> String.format("Average render time %.2f ms exceeds the 30 FPS budget", millisecondsPerFrame));
        });
    }

    @Test
    void mmiIconsDisplayDirectlyOnCityDotCenterWhenSWaveArrives() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            MapCanvasPane pane = new MapCanvasPane(scenario, outline);
            pane.resize(MapCanvasPane.BASELINE_VIEWPORT_WIDTH, MapCanvasPane.BASELINE_VIEWPORT_HEIGHT);
            pane.redrawStaticMap();

            // Locate screen point for Ridgecrest
            var ridgecrestPoint = pane.getLocationScreenPoint("Ridgecrest");
            int rx = (int) Math.round(ridgecrestPoint.xPx());
            int ry = (int) Math.round(ridgecrestPoint.yPx());

            // t = 0.0: S-wave has not arrived
            FrameState f0 = engine.frameAt(scenario, 0.0);
            pane.renderFrame(f0);

            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            WritableImage dynImg0 = pane.getDynamicCanvas().snapshot(params, null);
            int alphaAtDot0 = (dynImg0.getPixelReader().getArgb(rx, ry) >>> 24);
            assertEquals(0, alphaAtDot0, "City dot position on dynamic canvas must be transparent before S-wave arrival");

            // t = 10.0: Ridgecrest S-wave arrival has occurred (~5.8s)
            FrameState f10 = engine.frameAt(scenario, 10.0);
            pane.renderFrame(f10);

            WritableImage dynImg10 = pane.getDynamicCanvas().snapshot(params, null);
            int alphaAtDot10 = (dynImg10.getPixelReader().getArgb(rx, ry) >>> 24);
            assertTrue(alphaAtDot10 > 0, "MMI icon must be rendered directly on the center of the city dot on S-wave arrival");
        });
    }

    @Test
    void simulatedPreparedFramesRenderProgressiveBadgesFromFrameState() throws Exception {
        ScenarioLoader.ScenarioBundle bundle = new ScenarioLoader().loadScenarioBundle("Ridgecrest");
        HadleyKanamoriTauPModel model = new HadleyKanamoriTauPModel();
        PreparedReplay replay = new ReplayPreparer(model)
                .prepare(bundle.inputs(), bundle.references(), MmiMode.SIMULATED);
        ReplayEngine simulated = ReplayEngine.createPrepared(bundle.scenario(), model, replay);
        var timeline = replay.timelinesBySiteId().values().iterator().next();
        double firstAvailable = timeline.samples().stream()
                .filter(sample -> sample.mmi().isPresent())
                .findFirst().orElseThrow().elapsedSeconds();

        JavaFxTestHelper.runOnFxThread(() -> {
            MapCanvasPane pane = new MapCanvasPane(bundle.scenario(), outline);
            pane.resize(MapCanvasPane.BASELINE_VIEWPORT_WIDTH, MapCanvasPane.BASELINE_VIEWPORT_HEIGHT);
            FrameState early = simulated.frameAt(firstAvailable);
            FrameState completed = simulated.frameAt(replay.durationSeconds());
            double earlyMmi = early.locationIntensities().stream()
                    .filter(state -> state.geoid().equals(timeline.site().id()))
                    .findFirst().orElseThrow().currentMmi().orElseThrow();
            double finalMmi = completed.locationIntensities().stream()
                    .filter(state -> state.geoid().equals(timeline.site().id()))
                    .findFirst().orElseThrow().currentMmi().orElseThrow();
            assertTrue(finalMmi >= earlyMmi);
            pane.renderFrame(early);
            pane.renderFrame(completed);
            assertEquals(completed, pane.getLastFrame());
        });
    }

    @Test
    void syntheticScenarioInputsWithoutReferencesRendersAllConfiguredSitesWithoutReferenceLocations() throws Exception {
        // Stage B exit gate: a synthetic ScenarioInputs with no ScenarioReferences renders all configured sites
        // and frame-supplied statuses without constructing any ReferenceLocation.
        EventSource event = new EventSource(
                "synthetic-event-1", "custom", "Synthetic California Test",
                Instant.parse("2026-09-10T00:00:00Z"), 6.2, "mw",
                new GeoPoint(36.0, -119.5), 12.0,
                Optional.empty(), Optional.empty(), Map.of());

        List<SimulationSite> sites = List.of(
                new SimulationSite("site-alpha", "Alpha City", new GeoPoint(35.5, -119.0)),
                new SimulationSite("site-beta", "Beta Town", new GeoPoint(36.5, -120.0)),
                new SimulationSite("site-gamma", "Gamma Village", new GeoPoint(37.0, -120.5))
        );

        HadleyKanamoriTauPModel model = new HadleyKanamoriTauPModel();
        ScenarioInputs inputs = ScenarioInputs.forCustomScenario(event, sites, model);
        ScenarioReferences emptyReferences = new ScenarioReferences();
        PreparedReplay replay = new ReplayPreparer(model).prepare(inputs, emptyReferences, MmiMode.SIMULATED);
        ReplayEngine engine = ReplayEngine.createPrepared(model, replay);

        JavaFxTestHelper.runOnFxThread(() -> {
            MapScenario mapScenario = MapScenario.fromInputs(inputs);
            MapCanvasPane pane = new MapCanvasPane(mapScenario, outline);
            pane.resize(MapCanvasPane.BASELINE_VIEWPORT_WIDTH, MapCanvasPane.BASELINE_VIEWPORT_HEIGHT);
            pane.redrawStaticMap();

            assertTrue(countVisiblePixels(pane.getStaticCanvas()) > 0);

            // Screen points for each synthetic site can be found
            for (SimulationSite site : sites) {
                var pt = pane.getLocationScreenPoint(site.displayName());
                assertTrue(pt.xPx() > 0 && pt.xPx() < pane.getWidth());
                assertTrue(pt.yPx() > 0 && pt.yPx() < pane.getHeight());
            }

            // Frame rendering with no ReferenceLocations
            FrameState frame = engine.frameAt(replay.durationSeconds());
            assertDoesNotThrow(() -> pane.renderFrame(frame));
            assertEquals(frame, pane.getLastFrame());
            assertTrue(countVisiblePixels(pane.getDynamicCanvas()) > 0);
        });
    }

    private static void clear(Canvas canvas) {
        canvas.getGraphicsContext2D().clearRect(0.0, 0.0, canvas.getWidth(), canvas.getHeight());
    }

    private static int countVisiblePixels(Canvas canvas) {
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        WritableImage image = canvas.snapshot(parameters, null);
        int count = 0;
        for (int y = 0; y < (int) image.getHeight(); y++) {
            for (int x = 0; x < (int) image.getWidth(); x++) {
                if ((image.getPixelReader().getArgb(x, y) >>> 24) != 0) {
                    count++;
                }
            }
        }
        return count;
    }
}
