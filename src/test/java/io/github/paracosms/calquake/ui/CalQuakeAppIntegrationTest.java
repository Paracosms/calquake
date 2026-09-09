package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.testsupport.JavaFxTestHelper;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CalQuakeAppIntegrationTest {

    @Test
    void testFullCalQuakeAppWindow() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            try {
                CalQuakeApp app = new CalQuakeApp();
                app.init();
                Stage stage = new Stage();
                app.start(stage);

                Scene scene = stage.getScene();
                assertNotNull(scene);

                MapCanvasPane mapPane = app.getMapCanvasPane();
                assertNotNull(mapPane);

                // 1. Verify map viewport dimensions in actual 1280x800 application layout with compact header (28 px)
                assertEquals(MapCanvasPane.BASELINE_VIEWPORT_WIDTH, mapPane.getWidth(), 1.0,
                        "MapCanvasPane width in 1280x800 window must match BASELINE_VIEWPORT_WIDTH (890 px)");
                assertEquals(744.0, mapPane.getHeight(), 1.0,
                        "MapCanvasPane height in 1280x800 window with compact header must be 744 px");

                // 2. Verify marker positions on the live mapPane match independent control calculation <= 1px
                var bounds = app.getOutline().computeProjectedBoundingBox(mapPane.projection());
                var epiPt = app.getScenario().event().epicenter();
                var epiLive = mapPane.getEpicenterScreenPoint();
                var epiExpected = Stage5BaselineVerificationTest.independentControlProject(
                        epiPt, epiPt, bounds, mapPane.getWidth(), mapPane.getHeight(), MapCanvasPane.DEFAULT_MARGIN_PX);
                assertEquals(epiExpected.xPx(), epiLive.xPx(), 1.0, "Live Epicenter X matches control <= 1px");
                assertEquals(epiExpected.yPx(), epiLive.yPx(), 1.0, "Live Epicenter Y matches control <= 1px");

                for (var loc : app.getScenario().locations()) {
                    var locLive = mapPane.getLocationScreenPoint(loc.city());
                    var locExpected = Stage5BaselineVerificationTest.independentControlProject(
                            loc.internalPoint(), epiPt, bounds, mapPane.getWidth(), mapPane.getHeight(), MapCanvasPane.DEFAULT_MARGIN_PX);
                    assertEquals(locExpected.xPx(), locLive.xPx(), 1.0, loc.city() + " live X matches control <= 1px");
                    assertEquals(locExpected.yPx(), locLive.yPx(), 1.0, loc.city() + " live Y matches control <= 1px");
                }

                // 3. Snapshot the entire scene
                WritableImage image = scene.snapshot(null);
                int w = (int) image.getWidth();
                int h = (int) image.getHeight();
                assertEquals((int) CalQuakeApp.BASELINE_WIDTH, w, "Window width should match baseline (1280 px)");
                assertEquals((int) CalQuakeApp.BASELINE_HEIGHT, h, "Window height should match baseline (800 px)");

                File file = new File("target/full_window_snapshot.png");
                java.awt.image.BufferedImage bImage = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        bImage.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                    }
                }
                ImageIO.write(bImage, "png", file);

                // 4. Validate screenshot pixels:
                // Entire window must be fully opaque
                assertEquals(w * h, RenderSnapshotTest.countOpaquePixels(image),
                        "Entire application window must be fully opaque");

                // Ocean area inside map (x=80, y=350, within 890x744 viewport): #E2EDF6
                Color oceanColor = image.getPixelReader().getColor(80, 350);
                assertEquals(0.886, oceanColor.getRed(), 0.05, "Map ocean red channel must match #E2EDF6");
                assertEquals(0.929, oceanColor.getGreen(), 0.05, "Map ocean green channel must match #E2EDF6");
                assertEquals(0.965, oceanColor.getBlue(), 0.05, "Map ocean blue channel must match #E2EDF6");

                // California landmass area inside map (x=250, y=300): #FCFAF2
                Color landColor = image.getPixelReader().getColor(250, 300);
                assertEquals(0.988, landColor.getRed(), 0.05, "Map land red channel must match #FCFAF2");
                assertEquals(0.980, landColor.getGreen(), 0.05, "Map land green channel must match #FCFAF2");
                assertEquals(0.949, landColor.getBlue(), 0.05, "Map land blue channel must match #FCFAF2");

                // Header area (x=200, y=14): classic desktop menu bar
                Color headerColor = image.getPixelReader().getColor(200, 14);
                assertTrue(headerColor.getRed() > 0.40 && headerColor.getGreen() > 0.40 && headerColor.getBlue() > 0.40,
                        "Header should have classic desktop menu bar styling");

                // Sidebar area (x=1050, y=200): light sidebar panel
                Color sidebarColor = image.getPixelReader().getColor(1050, 200);
                assertTrue(sidebarColor.getRed() > 0.85 && sidebarColor.getGreen() > 0.85 && sidebarColor.getBlue() > 0.85,
                        "Sidebar should have light panel styling");

                // Status bar area (x=50, y=785): classic status bar
                Color statusColor = image.getPixelReader().getColor(50, 785);
                assertTrue(statusColor.getRed() > 0.60 && statusColor.getGreen() > 0.60,
                        "Status bar should have classic panel styling");

                // Map content rendering checks across the full window
                assertTrue(RenderSnapshotTest.countLandFillPixels(image) > 10_000,
                        "Full window should contain California landmass pixels");
                assertTrue(RenderSnapshotTest.countDarkPixels(image) > 2_000,
                        "Full window should contain labels, borders, and text");
                assertTrue(RenderSnapshotTest.countYellowPixels(image) > 20,
                        "Full window should contain yellow MMI VII badge pixels");
                assertTrue(RenderSnapshotTest.countCyanPixels(image) > 20,
                        "Full window should contain cyan MMI IV badge pixels");
                assertTrue(RenderSnapshotTest.countRedPixels(image) > 10,
                        "Full window should contain red epicenter marker pixels");
                assertTrue(RenderSnapshotTest.countUniqueColors(image) > 50,
                        "Full window snapshot must contain rich color palette (> 50 distinct colors)");

                app.stop();
                stage.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testPlaybackTickingAndReset() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            try {
                CalQuakeApp app = new CalQuakeApp();
                app.init();
                Stage stage = new Stage();
                app.start(stage);

                var controller = app.getController();
                assertTrue(controller.isPaused());
                assertEquals(0.0, controller.elapsedSeconds());

                // Start playback
                controller.play();
                assertTrue(controller.isPlaying());

                // Advance clock & tick
                Thread.sleep(50);
                var frame = controller.tick();
                assertTrue(controller.elapsedSeconds() > 0.0, "Elapsed time should advance when playing");
                assertNotNull(frame);

                app.getMapCanvasPane().renderFrame(frame);

                // Pause
                controller.pause();
                assertTrue(controller.isPaused());
                double pausedTime = controller.elapsedSeconds();

                Thread.sleep(20);
                controller.tick();
                assertEquals(pausedTime, controller.elapsedSeconds(), "Time should not advance while paused");

                // Restart
                controller.restart();
                assertTrue(controller.isPaused());
                assertEquals(0.0, controller.elapsedSeconds());

                app.stop();
                stage.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testWindowLifecyclePreservesPlaybackAcrossRepeatedRestores() throws Exception {
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
                assertTrue(appRef[0].getController().isPlaying());
            });

            for (int cycle = 0; cycle < 3; cycle++) {
                JavaFxTestHelper.runOnFxThread(() -> {
                    stageRef[0].setIconified(true);
                    assertTrue(appRef[0].getController().isPaused(),
                            "Playback should pause when the stage is inactive");
                });

                JavaFxTestHelper.runOnFxThread(() -> {
                    stageRef[0].setIconified(false);
                    stageRef[0].requestFocus();
                    assertTrue(appRef[0].getController().isPlaying(),
                            "Playback should resume after restoring a previously playing stage");
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
}
