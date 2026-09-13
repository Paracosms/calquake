package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.ApplicationMode;
import io.github.paracosms.calquake.core.HadleyKanamoriTauPModel;
import io.github.paracosms.calquake.core.IntensityDisplayMode;
import io.github.paracosms.calquake.core.MmiMode;
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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

                // Mode menu contains ordered items: Simulation then Replay
                assertEquals(List.of("Mode", "Settings"),
                        app.getMenuBar().getMenus().stream().map(menu -> menu.getText()).toList());
                assertEquals(List.of("Simulation", "Replay"),
                        app.getModeMenu().getItems().stream().map(item -> item.getText()).toList());
                assertTrue(app.getSimulationMenuItem().isSelected());
                assertFalse(app.getReplayMenuItem().isSelected());
                assertEquals(ApplicationMode.SIMULATION, app.getCurrentMode());
                assertFalse(app.getSettingsMenu().getItems().isEmpty());

                // In Simulation mode, eventSelector is omitted from sidebar
                assertNull(app.getEventSelector());
                assertNull(app.getMmiModeSelector());
                assertNotNull(app.getSimulationSidebar());
                assertNotNull(app.getEpicenterLatField());

                assertTrue(app.getController().isPaused());
                assertEquals(0.0, app.getController().elapsedSeconds(), 1e-9);
                assertEquals("PAUSED", app.getHudStateLabel().getText());
                assertEquals("SIMULATION ELAPSED TIME", app.getHudTitleLabel().getText());
                assertEquals("00:00.00", app.getElapsedDigitsLabel().getText());
                assertFalse(app.getPlayPauseButton().isDisable());

                assertNotNull(app.getTimelineScrubber());
                assertEquals(0.0, app.getTimelineScrubber().getMin(), 1e-9);
                assertEquals(app.getController().durationSeconds(), app.getTimelineScrubber().getMax(), 1e-9);
                assertEquals(0.0, app.getTimelineScrubber().getValue(), 1e-9);

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

                // Switch to Replay mode
                app.switchMode(ApplicationMode.REPLAY);
                assertEquals(ApplicationMode.REPLAY, app.getCurrentMode());
                assertTrue(app.getReplayMenuItem().isSelected());
                assertFalse(app.getSimulationMenuItem().isSelected());
                assertEquals("REPLAY ELAPSED TIME", app.getHudTitleLabel().getText());

                assertNotNull(app.getEventSelector());
                assertEquals(List.of("Ridgecrest", "Northridge"), app.getEventSelector().getItems());
                assertEquals("Ridgecrest", app.getEventSelector().getValue());
                assertEquals(List.of(MmiMode.RECORDED, MmiMode.SIMULATED),
                        app.getMmiModeSelector().getItems());
                assertEquals(MmiMode.RECORDED, app.getMmiModeSelector().getValue());
                assertEquals("mmi-mode-selector", app.getMmiModeSelector().getId());
                assertEquals("MMI replay mode", app.getMmiModeSelector().getAccessibleText());

                // Switch back to Simulation mode
                app.switchMode(ApplicationMode.SIMULATION);
                assertEquals(ApplicationMode.SIMULATION, app.getCurrentMode());
                assertTrue(app.getSimulationMenuItem().isSelected());
                assertNull(app.getEventSelector());
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
                assertEquals("00:04.00", app.getElapsedDigitsLabel().getText());

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
                assertEquals("00:00.00", app.getElapsedDigitsLabel().getText());

                app.getPlayPauseButton().fire();
                clock.advanceSeconds(controller.durationSeconds() + 5.0);
                controller.tick();
                app.updateControlStates();
                app.updateTimeDisplays();
                assertTrue(controller.isFinished());
                assertEquals(controller.durationSeconds(), controller.elapsedSeconds(), 1e-9);
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

    @Test
    void timelineScrubberSeeksReplayAndUpdatesUi() throws Exception {
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

                // Scrub forward to 50.0 seconds
                app.getTimelineScrubber().setValue(50.0);
                assertEquals(50.0, controller.elapsedSeconds(), 1e-9);
                assertEquals("00:50.00", app.getElapsedDigitsLabel().getText());
                assertTrue(controller.isPaused());

                // Scrub to the prepared duration to finish playback
                app.getTimelineScrubber().setValue(controller.durationSeconds());
                assertTrue(controller.isFinished());
                assertTrue(app.getPlayPauseButton().isDisable());

                // Scrubbing back from finished re-enables Play
                app.getTimelineScrubber().setValue(25.0);
                assertEquals(25.0, controller.elapsedSeconds(), 1e-9);
                assertTrue(controller.isPaused());
                assertFalse(app.getPlayPauseButton().isDisable());
            } finally {
                app.stop();
                stage.close();
            }
        });
    }

    @Test
    void eventSelectorSwitchesScenarioAndRefreshesReplayState() throws Exception {
        CalQuakeApp[] appRef = new CalQuakeApp[1];
        Stage[] stageRef = new Stage[1];
        try {
            JavaFxTestHelper.runOnFxThread(() -> {
                CalQuakeApp app = new CalQuakeApp();
                app.init();
                Stage stage = new Stage();
                app.start(stage);
                app.switchMode(ApplicationMode.REPLAY);

                assertEquals("Ridgecrest", app.getEventSelector().getValue());
                assertEquals("ci38457511", app.getScenario().event().id());
                assertEquals(7.1, app.getScenario().event().magnitude(), 1e-9);
                assertEquals(8.0, app.getScenario().event().depthKm(), 1e-9);
                assertEquals(0.0, app.getController().elapsedSeconds(), 1e-9);
                app.getEventSelector().setValue("Northridge");
                assertTrue(app.isPreparingReplay());
                assertEquals("ci38457511", app.getInstalledScenario().event().id(),
                        "The old replay remains atomically installed while preparation runs");
                appRef[0] = app;
                stageRef[0] = stage;
            });
            appRef[0].getPreparationFuture().get(10, TimeUnit.SECONDS);
            JavaFxTestHelper.runOnFxThread(() -> {});
            JavaFxTestHelper.runOnFxThread(() -> {
                CalQuakeApp app = appRef[0];
                assertEquals("Northridge", app.getEventSelector().getValue());
                assertEquals("ci3144585", app.getScenario().event().id());
                assertEquals(6.7, app.getScenario().event().magnitude(), 1e-9);
                assertEquals(18.2, app.getScenario().event().depthKm(), 1e-9);
                assertEquals(0.0, app.getController().elapsedSeconds(), 1e-9);
                assertTrue(app.getController().isPaused());
                assertEquals("00:00.00", app.getElapsedDigitsLabel().getText());

                // Verify locations for Northridge
                assertEquals(5, app.getScenario().locations().size());
                var la = app.getScenario().findLocationByCity("Los Angeles").orElseThrow();
                assertEquals(7.2, la.peakIntensity().mmiSourceDecimal(), 1e-9);
                app.getEventSelector().setValue("Ridgecrest");
            });
            appRef[0].getPreparationFuture().get(10, TimeUnit.SECONDS);
            JavaFxTestHelper.runOnFxThread(() -> {});
            JavaFxTestHelper.runOnFxThread(() -> {
                CalQuakeApp app = appRef[0];
                assertEquals("Ridgecrest", app.getEventSelector().getValue());
                assertEquals("ci38457511", app.getScenario().event().id());
                assertEquals(7.1, app.getScenario().event().magnitude(), 1e-9);
                assertEquals(8.0, app.getScenario().event().depthKm(), 1e-9);
                assertEquals(0.0, app.getController().elapsedSeconds(), 1e-9);
            });
        } finally {
            if (appRef[0] != null) {
                JavaFxTestHelper.runOnFxThread(() -> {
                    appRef[0].stop();
                    if (stageRef[0] != null) stageRef[0].close();
                });
            }
        }
    }

    @Test
    void modeAndRapidEventChangesPrepareOffThreadAndFinalGenerationWins() throws Exception {
        CalQuakeApp[] appRef = new CalQuakeApp[1];
        Stage[] stageRef = new Stage[1];
        try {
            JavaFxTestHelper.runOnFxThread(() -> {
                CalQuakeApp app = new CalQuakeApp();
                app.init();
                Stage stage = new Stage();
                app.start(stage);
                app.switchMode(ApplicationMode.REPLAY);

                app.getMmiModeSelector().setValue(MmiMode.SIMULATED);
                assertTrue(app.isPreparingReplay());
                assertTrue(app.getPlayPauseButton().isDisable());
                assertTrue(app.getTimelineScrubber().isDisable());
                assertEquals("Preparing replay...", app.getControlStateLabel().getText());
                app.getEventSelector().setValue("Northridge");
                appRef[0] = app;
                stageRef[0] = stage;
            });
            appRef[0].getPreparationFuture().get(10, TimeUnit.SECONDS);
            JavaFxTestHelper.runOnFxThread(() -> {});
            JavaFxTestHelper.runOnFxThread(() -> {
                assertFalse(appRef[0].isPreparingReplay());
                assertEquals(MmiMode.SIMULATED, appRef[0].getPreparedReplay().mode());
                assertEquals("ci3144585", appRef[0].getInstalledScenario().event().id());
                assertEquals(0.0, appRef[0].getController().elapsedSeconds(), 0.0);
                assertEquals(appRef[0].getPreparedReplay().durationSeconds(),
                        appRef[0].getTimelineScrubber().getMax(), 0.0);
            });
        } finally {
            if (appRef[0] != null) {
                JavaFxTestHelper.runOnFxThread(() -> {
                    appRef[0].stop();
                    if (stageRef[0] != null) stageRef[0].close();
                });
            }
        }
    }

    @Test
    void modeSwitchingResetsPlaybackPreservesSelectionsAndIsolatesControllers() throws Exception {
        CalQuakeApp[] appRef = new CalQuakeApp[1];
        Stage[] stageRef = new Stage[1];
        try {
            JavaFxTestHelper.runOnFxThread(() -> {
                CalQuakeApp app = new CalQuakeApp();
                app.init();
                Stage stage = new Stage();
                app.start(stage);
                appRef[0] = app;
                stageRef[0] = stage;

                // 1. App opens in Simulation mode
                assertEquals(ApplicationMode.SIMULATION, app.getCurrentMode());
                assertEquals("custom-california-scenario-v1", app.getInstalledScenario().event().id());
                assertTrue(app.getController().isPaused());

                // 2. Play simulation
                app.getPlayPauseButton().fire();
                assertTrue(app.getController().isPlaying());
                assertTrue(app.getEpicenterLatField().isDisable(), "Settings inputs lock during playback");

                // 3. Switch to Replay mode
                app.switchMode(ApplicationMode.REPLAY);
                assertEquals(ApplicationMode.REPLAY, app.getCurrentMode());
                assertFalse(app.getSimulationController().isPlaying(),
                        "Leaving mode must pause/reset active playback");
                assertEquals(0.0, app.getSimulationController().elapsedSeconds(), 1e-9);
                assertEquals(0.0, app.getReplayController().elapsedSeconds(), 1e-9);
                assertTrue(app.getReplayController().isPaused());

                // 4. Change event in Replay mode
                app.getEventSelector().setValue("Northridge");
            });

            appRef[0].getPreparationFuture().get(10, TimeUnit.SECONDS);
            JavaFxTestHelper.runOnFxThread(() -> {});
            JavaFxTestHelper.runOnFxThread(() -> {
                CalQuakeApp app = appRef[0];
                assertEquals("ci3144585", app.getInstalledScenario().event().id());
                assertEquals("Northridge", app.getEventSelector().getValue());

                // 5. Edit Simulation draft while in Simulation mode
                app.switchMode(ApplicationMode.SIMULATION);
                assertEquals(ApplicationMode.SIMULATION, app.getCurrentMode());
                assertEquals("custom-california-scenario-v1", app.getInstalledScenario().event().id());
                app.getEpicenterLatField().setText("36.0000");

                // 6. Switch back to Replay: Replay selection 'Northridge' is preserved
                app.switchMode(ApplicationMode.REPLAY);
                assertEquals(ApplicationMode.REPLAY, app.getCurrentMode());
                assertEquals("Northridge", app.getEventSelector().getValue());
                assertEquals("ci3144585", app.getInstalledScenario().event().id());

                // 7. Switch back to Simulation: draft edit '36.0000' is preserved
                app.switchMode(ApplicationMode.SIMULATION);
                assertEquals("36.0000", app.getEpicenterLatField().getText());
            });
        } finally {
            if (appRef[0] != null) {
                JavaFxTestHelper.runOnFxThread(() -> {
                    appRef[0].stop();
                    if (stageRef[0] != null) stageRef[0].close();
                });
            }
        }
    }

    @Test
    void simulationSettingsLifecycleValidationStalenessAndPlaybackLocking() throws Exception {
        CalQuakeApp[] appRef = new CalQuakeApp[1];
        Stage[] stageRef = new Stage[1];
        try {
            JavaFxTestHelper.runOnFxThread(() -> {
                CalQuakeApp app = new CalQuakeApp();
                app.init();
                Stage stage = new Stage();
                app.start(stage);
                appRef[0] = app;
                stageRef[0] = stage;

                // 1. Initial simulation state
                assertEquals(ApplicationMode.SIMULATION, app.getCurrentMode());
                assertNotNull(app.getSimulationInstalledSettings());
                assertEquals(35.5, app.getSimulationInstalledSettings().epicenter().latitude(), 1e-6);
                assertFalse(app.isDraftStale());
                assertFalse(app.getPlayPauseButton().isDisable());
                assertFalse(app.getEpicenterLatField().isDisable());
                assertFalse(app.getMagnitudeField().isDisable());

                // 2. Settings lock during playback and unlock on pause
                app.getPlayPauseButton().fire();
                assertTrue(app.getController().isPlaying());
                assertTrue(app.getEpicenterLatField().isDisable());
                assertTrue(app.getEpicenterLonField().isDisable());
                assertTrue(app.getMagnitudeField().isDisable());
                assertTrue(app.getDepthField().isDisable());
                assertTrue(app.getIntensityDisplaySelector().isDisable());
                assertTrue(app.getApplyButton().isDisable());
                assertTrue(app.getSaveButton().isDisable(), "Save must be disabled while playing");
                assertTrue(app.getImportButton().isDisable(), "Import must be disabled while playing");

                app.getPlayPauseButton().fire();
                assertTrue(app.getController().isPaused());
                assertFalse(app.getEpicenterLatField().isDisable());
                assertFalse(app.getApplyButton().isDisable());
                assertFalse(app.getSaveButton().isDisable(), "Save must be enabled while paused");
                assertFalse(app.getImportButton().isDisable(), "Import must be enabled while paused");

                // 3. Draft editing marks stale and disables Play button
                app.getEpicenterLatField().setText("36.2000");
                assertTrue(app.isDraftStale());
                assertTrue(app.getPlayPauseButton().isDisable(), "Play must be disabled while draft is stale");
                assertTrue(app.getSimSettingsStatusLabel().getText().contains("draft")
                        || app.getSimSettingsStatusLabel().getText().contains("modified")
                        || app.getSimSettingsStatusLabel().getText().contains("Stale"));

                // 4. Invalid input rejected on Apply without corrupting installed scenario
                app.getEpicenterLatField().setText("120.0000"); // Invalid latitude > 90
                app.getApplyButton().fire();
                assertTrue(app.isDraftStale());
                assertTrue(app.getPlayPauseButton().isDisable());
                assertTrue(app.getSimSettingsStatusLabel().getText().contains("Error"));
                assertEquals(35.5, app.getSimulationInstalledSettings().epicenter().latitude(), 1e-6,
                        "Installed settings must not be corrupted by invalid input");

                // 5. Warned input (mag 8.6 out of BSSA14 domain) prepares and runs
                app.getEpicenterLatField().setText("35.5000");
                app.getMagnitudeField().setText("8.6");
                app.getApplyButton().fire();
                assertTrue(app.isPreparingReplay());
            });

            // Wait for asynchronous preparation of warned scenario
            appRef[0].getPreparationFuture().get(10, TimeUnit.SECONDS);
            JavaFxTestHelper.runOnFxThread(() -> {});

            JavaFxTestHelper.runOnFxThread(() -> {
                CalQuakeApp app = appRef[0];
                assertFalse(app.isPreparingReplay());
                assertFalse(app.isDraftStale());
                assertEquals(8.6, app.getSimulationInstalledSettings().magnitude(), 1e-6);
                assertFalse(app.getSimulationWarnings().isEmpty(), "Domain warnings must be collected");
                assertTrue(app.getSimWarningBanner().isVisible(), "Persistent warning banner must be visible");
                assertTrue(app.getSimWarningBannerLabel().getText().contains("BSSA14"));
                assertFalse(app.getPlayPauseButton().isDisable(), "Warned scenario must be playable");

                // Play warned scenario
                app.getPlayPauseButton().fire();
                assertTrue(app.getController().isPlaying());

                // 6. Switching intensity display mode while paused updates immediately without re-preparation
                app.getPlayPauseButton().fire();
                assertTrue(app.getController().isPaused());
                app.getIntensityDisplaySelector().getSelectionModel().select(
                        IntensityDisplayMode.CURRENT_SHAKING.label());
                assertFalse(app.isPreparingReplay(), "Switching display mode must not rerun preparation");
                assertFalse(app.isDraftStale(), "Presentation mode must not mark draft stale");
                assertEquals(IntensityDisplayMode.CURRENT_SHAKING, app.getController().intensityDisplayMode());
                assertEquals(IntensityDisplayMode.CURRENT_SHAKING, app.getSimulationInstalledSettings().intensityDisplayMode());
                assertNotNull(app.getSimLegendMeaningLabel());
                assertTrue(app.getSimLegendMeaningLabel().getText().contains("Current estimated shaking"));

                // Switch back to MAXIMUM_REACHED
                app.getIntensityDisplaySelector().getSelectionModel().select(
                        IntensityDisplayMode.MAXIMUM_REACHED.label());
                assertFalse(app.isPreparingReplay());
                assertFalse(app.isDraftStale());
                assertEquals(IntensityDisplayMode.MAXIMUM_REACHED, app.getController().intensityDisplayMode());
                assertEquals(IntensityDisplayMode.MAXIMUM_REACHED, app.getSimulationInstalledSettings().intensityDisplayMode());
                assertTrue(app.getSimLegendMeaningLabel().getText().contains("Maximum estimated MMI reached"));
            });
        } finally {
            if (appRef[0] != null) {
                JavaFxTestHelper.runOnFxThread(() -> {
                    appRef[0].stop();
                    if (stageRef[0] != null) stageRef[0].close();
                });
            }
        }
    }

    @Test
    void simulationSaveAndImportRoundTripWorkflow() throws Exception {
        CalQuakeApp[] appRef = new CalQuakeApp[1];
        Stage[] stageRef = new Stage[1];
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("calquake-ui-test-", ".calquake.json");
        java.nio.file.Path badFile = java.nio.file.Files.createTempFile("calquake-ui-bad-", ".json");
        try {
            java.nio.file.Files.writeString(badFile, "{ \"schema_version\": 999, \"type\": \"bad\" }");

            JavaFxTestHelper.runOnFxThread(() -> {
                CalQuakeApp app = new CalQuakeApp();
                app.init();
                Stage stage = new Stage();
                app.start(stage);
                appRef[0] = app;
                stageRef[0] = stage;

                // 1. Save valid initial scenario to temp file
                app.saveScenarioToFile(app.getSimulationDraftSettings(), tempFile);
                assertTrue(java.nio.file.Files.exists(tempFile));
                assertTrue(app.getSimSettingsStatusLabel().getText().contains("Saved"));

                // 2. Modify draft
                app.getEpicenterLatField().setText("34.1234");
                app.getMagnitudeField().setText("7.4");

                // 3. Import invalid file -> fails transactionally without mutating state
                boolean importedBad = app.importScenarioFromFile(badFile);
                assertFalse(importedBad);
                assertTrue(app.getSimSettingsStatusLabel().getText().contains("Import failed"));
                assertEquals("34.1234", app.getEpicenterLatField().getText());
                assertEquals("7.4", app.getMagnitudeField().getText());

                // 4. Import previously saved valid scenario
                boolean importedGood = app.importScenarioFromFile(tempFile);
                assertTrue(importedGood);
                assertTrue(app.isPreparingReplay());
            });

            appRef[0].getPreparationFuture().get(10, TimeUnit.SECONDS);
            JavaFxTestHelper.runOnFxThread(() -> {});

            JavaFxTestHelper.runOnFxThread(() -> {
                CalQuakeApp app = appRef[0];
                assertFalse(app.isPreparingReplay());
                assertFalse(app.isDraftStale());
                assertEquals(35.5, app.getSimulationInstalledSettings().epicenter().latitude(), 1e-6);
                assertEquals(6.5, app.getSimulationInstalledSettings().magnitude(), 1e-6);
                assertEquals("35.5000", app.getEpicenterLatField().getText());
                assertEquals("6.5", app.getMagnitudeField().getText());
                assertFalse(app.getPlayPauseButton().isDisable());
            });
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
            java.nio.file.Files.deleteIfExists(badFile);
            if (appRef[0] != null) {
                JavaFxTestHelper.runOnFxThread(() -> {
                    appRef[0].stop();
                    if (stageRef[0] != null) stageRef[0].close();
                });
            }
        }
    }

    @Test
    void simulationAndReplayLayoutSimplifications() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            CalQuakeApp app = new CalQuakeApp();
            app.init();
            Stage stage = new Stage();
            try {
                app.start(stage);

                // 1. Hud state label (Playing/Paused/Finished) removed from HUD overlay hierarchy for all modes
                assertNotNull(app.getHudStateLabel());
                assertNull(app.getHudStateLabel().getParent(),
                        "Playing/Paused/Finished badge must not be attached to HUD overlay");

                // 2. Apply button text is 'Apply'
                assertEquals("Apply", app.getApplyButton().getText());

                // 3. MapCanvasPane application mode tracks active mode
                assertEquals(ApplicationMode.SIMULATION, app.getMapCanvasPane().getApplicationMode());

                // 4. In Simulation sidebar, check that subtitle and disclaimer are removed
                javafx.scene.Node simSidebar = app.getSimulationSidebar();
                assertNotNull(simSidebar);
                List<Label> simLabels = collectLabels(simSidebar);
                assertFalse(simLabels.stream().anyMatch(l -> "Enter custom earthquake parameters:".equals(l.getText())),
                        "Subtitle 'Enter custom earthquake parameters:' must be removed");
                assertFalse(simLabels.stream().anyMatch(l -> l.getText() != null && l.getText().contains("Exploratory Toy Simulation")),
                        "Disclaimer box must be removed from simulation sidebar");

                // 5. MMI Scale title is 'MMI Scale'
                assertTrue(simLabels.stream().anyMatch(l -> "MMI Scale".equals(l.getText())),
                        "Title must say 'MMI Scale'");
                assertFalse(simLabels.stream().anyMatch(l -> l.getText() != null && l.getText().contains("Worden et al., 2012")),
                        "Title must not contain 'Worden et al., 2012'");

                // 6. Updating meaning label is not in simulation legend hierarchy
                assertNull(app.getSimLegendMeaningLabel().getParent(),
                        "Updating text must be removed from simulation MMI scale");

                // 7. N/A Outside Coverage is removed from simulation legend
                assertFalse(simLabels.stream().anyMatch(l -> "Outside coverage".equalsIgnoreCase(l.getText())
                                || "N/A".equals(l.getText())),
                        "N/A Outside coverage must be removed from simulation MMI scale");

                // 8. In Replay sidebar, verify N/A Outside Coverage is also removed
                app.switchMode(ApplicationMode.REPLAY);
                assertEquals(ApplicationMode.REPLAY, app.getMapCanvasPane().getApplicationMode());
                assertNull(app.getHudStateLabel().getParent(),
                        "Playing/Paused/Finished badge must remain omitted in replay mode as well");

                javafx.scene.Node replaySidebar = app.getReplaySidebar();
                assertNotNull(replaySidebar);
                List<Label> replayLabels = collectLabels(replaySidebar);
                assertFalse(replayLabels.stream().anyMatch(l -> "Outside coverage".equalsIgnoreCase(l.getText())
                                || "N/A".equals(l.getText())),
                        "N/A Outside coverage must also be removed from replay MMI scale");
            } finally {
                app.stop();
                stage.close();
            }
        });
    }

    private static List<Label> collectLabels(javafx.scene.Node root) {
        List<Label> result = new java.util.ArrayList<>();
        if (root instanceof Label l) {
            result.add(l);
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                result.addAll(collectLabels(child));
            }
        }
        return result;
    }
}
