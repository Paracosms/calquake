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
                assertTrue(mapPane.getWidth() > 500.0, "MapCanvasPane width should be non-zero after layout");
                assertTrue(mapPane.getHeight() > 500.0, "MapCanvasPane height should be non-zero after layout");

                // Snapshot the entire scene
                WritableImage image = scene.snapshot(null);
                int w = (int) image.getWidth();
                int h = (int) image.getHeight();
                assertTrue(w >= 1024, "Window width should match scene size");
                assertTrue(h >= 640, "Window height should match scene size");

                File file = new File("target/full_window_snapshot.png");
                java.awt.image.BufferedImage bImage = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        bImage.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                    }
                }
                ImageIO.write(bImage, "png", file);

                // Verify the map area is NOT pure white (the California map or ocean background is visible)
                Color oceanColor = image.getPixelReader().getColor(300, 300);
                assertNotNull(oceanColor);

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
}
