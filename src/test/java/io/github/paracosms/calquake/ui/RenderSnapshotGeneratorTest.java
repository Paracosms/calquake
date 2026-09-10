package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.testsupport.JavaFxTestHelper;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderSnapshotGeneratorTest {

    @Test
    void captureSnapshots() throws Exception {
        Path outDir = Path.of("target", "render-snapshots");
        Files.createDirectories(outDir);

        JavaFxTestHelper.runOnFxThread(() -> {
            CalQuakeApp app = new CalQuakeApp();
            app.init();
            Stage stage = new Stage();
            try {
                app.start(stage);
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();

                // t = 10s: Ridgecrest and Trona reached
                app.getController().seek(10.0);
                app.getMapCanvasPane().renderFrame(app.getController().currentFrame());

                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.web("#FCFAF2"));
                WritableImage snap10 = app.getMapCanvasPane().snapshot(params, null);
                assertMeaningfullyRendered(snap10, "10-second map");
                saveImage(snap10, outDir.resolve("mmi_icons_centered_10s.png"));

                // t = 65s: All 5 cities reached
                app.getController().seek(65.0);
                app.getMapCanvasPane().renderFrame(app.getController().currentFrame());
                WritableImage snap65 = app.getMapCanvasPane().snapshot(params, null);
                assertMeaningfullyRendered(snap65, "65-second map");
                assertImagesDiffer(snap10, snap65);
                saveImage(snap65, outDir.resolve("mmi_icons_centered_65s.png"));

                // Entire window snapshot at 65s
                WritableImage snapApp = stage.getScene().snapshot(null);
                assertMeaningfullyRendered(snapApp, "65-second application window");
                saveImage(snapApp, outDir.resolve("calquake_full_window_65s.png"));
            } finally {
                app.stop();
                stage.close();
            }
        });
    }

    private static void saveImage(WritableImage image, Path targetPath) {
        try {
            BufferedImage bufferedImage = toBufferedImage(image);
            assertTrue(ImageIO.write(bufferedImage, "PNG", targetPath.toFile()),
                    "A PNG writer must be available");
            assertTrue(Files.isRegularFile(targetPath), "Snapshot file was not created: " + targetPath);
            assertTrue(Files.size(targetPath) > 1_024L,
                    "Snapshot file is unexpectedly small: " + targetPath);

            BufferedImage persistedImage = ImageIO.read(targetPath.toFile());
            assertNotNull(persistedImage, "Snapshot cannot be decoded: " + targetPath);
            assertEquals(bufferedImage.getWidth(), persistedImage.getWidth());
            assertEquals(bufferedImage.getHeight(), persistedImage.getHeight());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write render snapshot " + targetPath, e);
        }
    }

    private static void assertMeaningfullyRendered(WritableImage image, String description) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        assertTrue(width >= 100 && height >= 100,
                description + " must have meaningful dimensions but was " + width + "x" + height);

        PixelReader reader = image.getPixelReader();
        assertNotNull(reader, description + " must expose rendered pixels");
        long visiblePixels = 0L;
        Set<Integer> distinctColors = new HashSet<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = reader.getArgb(x, y);
                if ((argb >>> 24) != 0) {
                    visiblePixels++;
                }
                if (distinctColors.size() < 32) {
                    distinctColors.add(argb);
                }
            }
        }

        assertTrue(visiblePixels > (long) width * height / 2,
                description + " must contain substantial visible content");
        assertTrue(distinctColors.size() >= 8,
                description + " must contain varied rendered content");
    }

    private static void assertImagesDiffer(WritableImage first, WritableImage second) {
        int width = (int) first.getWidth();
        int height = (int) first.getHeight();
        assertEquals(width, (int) second.getWidth());
        assertEquals(height, (int) second.getHeight());

        PixelReader firstReader = first.getPixelReader();
        PixelReader secondReader = second.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (firstReader.getArgb(x, y) != secondReader.getArgb(x, y)) {
                    return;
                }
            }
        }
        throw new AssertionError("Snapshots at 10 seconds and 65 seconds must differ");
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
