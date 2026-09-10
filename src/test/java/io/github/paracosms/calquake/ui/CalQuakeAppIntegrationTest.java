package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.HadleyKanamoriTauPModel;
import io.github.paracosms.calquake.core.ReplayController;
import io.github.paracosms.calquake.core.ReplayEngine;
import io.github.paracosms.calquake.core.Scenario;
import io.github.paracosms.calquake.data.CaliforniaOutline;
import io.github.paracosms.calquake.data.ScenarioLoader;
import io.github.paracosms.calquake.testsupport.FakeMonotonicClock;
import io.github.paracosms.calquake.testsupport.JavaFxTestHelper;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalQuakeAppIntegrationTest {

    @Test
    void applicationShellExposesReplayWorkflow() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            CalQuakeApp app = new CalQuakeApp();
            app.init();
            Stage stage = new Stage();
            try {
                app.start(stage);

                assertEquals("CalQuake", stage.getTitle());
                assertNotNull(stage.getScene());
                assertNotNull(app.getMapCanvasPane());

                assertEquals(List.of("Mode", "Settings"),
                        app.getMenuBar().getMenus().stream().map(menu -> menu.getText()).toList());
                assertEquals(List.of("Replay"),
                        app.getModeMenu().getItems().stream().map(item -> item.getText()).toList());
                assertFalse(app.getSettingsMenu().getItems().isEmpty());

                assertEquals(List.of("Ridgecrest", "Northridge"), app.getEventSelector().getItems());
                assertEquals("Ridgecrest", app.getEventSelector().getValue());

                assertTrue(app.getController().isPaused());
                assertEquals(0.0, app.getController().elapsedSeconds(), 1e-9);
                assertEquals("PAUSED", app.getHudStateLabel().getText());
                assertEquals("00:00:00.00", app.getElapsedDigitsLabel().getText());
                assertFalse(app.getPlayPauseButton().isDisable());

                double width = app.getMapCanvasPane().getWidth();
                double height = app.getMapCanvasPane().getHeight();
                var epicenter = app.getMapCanvasPane().getEpicenterScreenPoint();
                assertTrue(epicenter.xPx() >= 0.0 && epicenter.xPx() <= width);
                assertTrue(epicenter.yPx() >= 0.0 && epicenter.yPx() <= height);
                for (var location : app.getScenario().locations()) {
                    var point = app.getMapCanvasPane().getLocationScreenPoint(location.city());
                    assertTrue(point.xPx() >= 0.0 && point.xPx() <= width,
                            location.city() + " must be visible horizontally");
                    assertTrue(point.yPx() >= 0.0 && point.yPx() <= height,
                            location.city() + " must be visible vertically");
                }
            } finally {
                app.stop();
                stage.close();
            }
        });
    }

    @Test
    void replayControlsDrivePauseResumeRestartAndFinish() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            Scenario scenario = new ScenarioLoader().loadDefaultScenario();
            CaliforniaOutline outline = CaliforniaOutline.loadDefault();
            ReplayEngine engine = ReplayEngine.create(scenario, new HadleyKanamoriTauPModel());
            FakeMonotonicClock clock = new FakeMonotonicClock(1_000_000_000L);
            ReplayController controller = new ReplayController(scenario, engine, clock);
            CalQuakeApp app = new CalQuakeApp(scenario, outline, controller);
            Stage stage = new Stage();
            try {
                app.start(stage);

                app.getPlayPauseButton().fire();
                assertTrue(controller.isPlaying());
                assertEquals("PLAYING", app.getHudStateLabel().getText());

                clock.advanceSeconds(4.0);
                controller.tick();
                app.updateTimeDisplays();
                assertEquals("00:00:04.00", app.getElapsedDigitsLabel().getText());

                app.getPlayPauseButton().fire();
                assertTrue(controller.isPaused());
                clock.advanceSeconds(20.0);
                controller.tick();
                assertEquals(4.0, controller.elapsedSeconds(), 1e-9,
                        "Paused wall-clock time must not advance the replay");

                app.getPlayPauseButton().fire();
                clock.advanceSeconds(2.0);
                controller.tick();
                assertEquals(6.0, controller.elapsedSeconds(), 1e-9);

                app.getRestartButton().fire();
                assertTrue(controller.isPaused());
                assertEquals(0.0, controller.elapsedSeconds(), 1e-9);
                assertEquals("00:00:00.00", app.getElapsedDigitsLabel().getText());

                app.getPlayPauseButton().fire();
                clock.advanceSeconds(125.0);
                controller.tick();
                app.updateControlStates();
                app.updateTimeDisplays();
                assertTrue(controller.isFinished());
                assertEquals(120.0, controller.elapsedSeconds(), 1e-9);
                assertTrue(app.getPlayPauseButton().isDisable());
                assertTrue(app.getControlStateLabel().getText().contains("Require Restart"));

                app.getRestartButton().fire();
                assertTrue(controller.isPaused());
                assertFalse(app.getPlayPauseButton().isDisable());
            } finally {
                app.stop();
                stage.close();
            }
        });
    }

    @Test
    void windowLifecyclePreservesPlaybackAcrossRepeatedRestores() throws Exception {
        CalQuakeApp[] appRef = new CalQuakeApp[1];
        Stage[] stageRef = new Stage[1];

        try {
            JavaFxTestHelper.runOnFxThread(() -> {
                CalQuakeApp app = new CalQuakeApp();
                app.init();
                Stage stage = new Stage();
                app.start(stage);
                stage.requestFocus();
                appRef[0] = app;
                stageRef[0] = stage;
            });

            JavaFxTestHelper.runOnFxThread(() -> {
                assertTrue(stageRef[0].isFocused(), "Lifecycle test requires the shown stage to be focused");
                appRef[0].getController().play();
            });

            for (int cycle = 0; cycle < 3; cycle++) {
                JavaFxTestHelper.runOnFxThread(() -> {
                    stageRef[0].setIconified(true);
                    assertTrue(appRef[0].getController().isPaused());
                });
                JavaFxTestHelper.runOnFxThread(() -> {
                    stageRef[0].setIconified(false);
                    stageRef[0].requestFocus();
                    assertTrue(appRef[0].getController().isPlaying());
                });
            }

            JavaFxTestHelper.runOnFxThread(() -> {
                appRef[0].getController().pause();
                stageRef[0].setIconified(true);
                stageRef[0].setIconified(false);
                stageRef[0].requestFocus();
                assertTrue(appRef[0].getController().isPaused(),
                        "An explicitly paused replay must remain paused after restoration");
            });
        } finally {
            if (appRef[0] != null) {
                JavaFxTestHelper.runOnFxThread(() -> {
                    appRef[0].stop();
                    if (stageRef[0] != null) {
                        stageRef[0].close();
                    }
                });
            }
        }
    }

    @Test
    void startupFailureShowsUsefulDiagnostic() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            CalQuakeApp app = new CalQuakeApp();
            app.setStartupErrorForTesting(new FileNotFoundException("Missing scenario resource: event.json"));
            Stage stage = new Stage();
            try {
                app.start(stage);

                assertEquals("CalQuake — Startup Error", stage.getTitle());
                assertNotNull(stage.getScene());
                boolean explainsFailure = stage.getScene().getRoot().lookupAll(".label").stream()
                        .filter(Label.class::isInstance)
                        .map(Label.class::cast)
                        .map(Label::getText)
                        .anyMatch(text -> text.contains("Missing scenario resource: event.json"));
                assertTrue(explainsFailure, "The startup error must explain which resource is missing");
            } finally {
                stage.close();
            }
        });
    }
}
