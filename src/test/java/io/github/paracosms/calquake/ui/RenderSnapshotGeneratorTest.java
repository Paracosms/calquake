package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.FrameState;
import io.github.paracosms.calquake.core.HadleyKanamoriTauPModel;
import io.github.paracosms.calquake.core.ReplayEngine;
import io.github.paracosms.calquake.core.Scenario;
import io.github.paracosms.calquake.data.CaliforniaOutline;
import io.github.paracosms.calquake.data.ScenarioLoader;
import io.github.paracosms.calquake.testsupport.JavaFxTestHelper;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

class RenderSnapshotGeneratorTest {

    @Test
    void captureSnapshots() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            CalQuakeApp app = new CalQuakeApp();
            app.init();
            Stage stage = new Stage();
            try {
                app.start(stage);

                Path outDir = Path.of("target/screenshots");
                Files.createDirectories(outDir);

                // t = 10s: Ridgecrest and Trona reached
                app.getController().seek(10.0);
                app.getMapCanvasPane().renderFrame(app.getController().currentFrame());

                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.web("#FCFAF2"));
                WritableImage snap10 = app.getMapCanvasPane().snapshot(params, null);
                saveImage(snap10, outDir.resolve("mmi_icons_centered_10s.png"),
                        "C:/Users/Andrew/.gemini/antigravity/brain/2cb47e5b-b4ae-4d43-b95f-ec7c905aa103/mmi_icons_centered_10s.png");

                // t = 65s: All 5 cities reached
                app.getController().seek(65.0);
                app.getMapCanvasPane().renderFrame(app.getController().currentFrame());
                WritableImage snap65 = app.getMapCanvasPane().snapshot(params, null);
                saveImage(snap65, outDir.resolve("mmi_icons_centered_65s.png"),
                        "C:/Users/Andrew/.gemini/antigravity/brain/2cb47e5b-b4ae-4d43-b95f-ec7c905aa103/mmi_icons_centered_65s.png");

                // Entire window snapshot at 65s
                WritableImage snapApp = stage.getScene().snapshot(null);
                saveImage(snapApp, outDir.resolve("calquake_full_window_65s.png"),
                        "C:/Users/Andrew/.gemini/antigravity/brain/2cb47e5b-b4ae-4d43-b95f-ec7c905aa103/calquake_full_window_65s.png");
                System.out.println("Saved all snapshots successfully!");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                stage.close();
            }
        });
    }

    private static void saveImage(WritableImage fxImg, Path targetPath, String artifactPathStr) {
        try {
            BufferedImage bImg = toBufferedImage(fxImg);
            ImageIO.write(bImg, "PNG", targetPath.toFile());
            if (artifactPathStr != null) {
                File artFile = new File(artifactPathStr);
                if (artFile.getParentFile().exists()) {
                    ImageIO.write(bImg, "PNG", artFile);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static BufferedImage toBufferedImage(WritableImage fxImg) {
        int width = (int) fxImg.getWidth();
        int height = (int) fxImg.getHeight();
        BufferedImage bImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var reader = fxImg.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bImg.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        return bImg;
    }
}
