package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.FrameState;
import io.github.paracosms.calquake.core.HadleyKanamoriTauPModel;
import io.github.paracosms.calquake.core.ReplayEngine;
import io.github.paracosms.calquake.core.Scenario;
import io.github.paracosms.calquake.data.CaliforniaOutline;
import io.github.paracosms.calquake.data.ScenarioLoader;
import io.github.paracosms.calquake.testsupport.JavaFxTestHelper;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RenderSnapshotTest {

    @Test
    void testRenderSnapshot() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            try {
                ScenarioLoader loader = new ScenarioLoader();
                Scenario scenario = loader.loadDefaultScenario();
                CaliforniaOutline outline = CaliforniaOutline.loadDefault();

                MapCanvasPane pane = new MapCanvasPane(scenario, outline);
                pane.resize(860, 730);
                pane.redrawStaticMap();

                WritableImage image = pane.snapshot(null, null);
                File file = new File("target/snapshot.png");
                int imgW = (int) image.getWidth();
                int imgH = (int) image.getHeight();
                java.awt.image.BufferedImage bImage = new java.awt.image.BufferedImage(imgW, imgH, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                javafx.scene.image.PixelReader pr = image.getPixelReader();
                for (int y = 0; y < imgH; y++) {
                    for (int x = 0; x < imgW; x++) {
                        bImage.setRGB(x, y, pr.getArgb(x, y));
                    }
                }
                ImageIO.write(bImage, "png", file);

                Color centerColor = image.getPixelReader().getColor((int) image.getWidth() / 2, (int) image.getHeight() / 2);
                assertNotNull(centerColor);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testRefreshRestoresCaliforniaOutlineLabelsAndCurrentFrame() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            ScenarioLoader loader = new ScenarioLoader();
            Scenario scenario = loader.loadDefaultScenario();
            CaliforniaOutline outline = CaliforniaOutline.loadDefault();
            ReplayEngine engine = ReplayEngine.create(scenario, new HadleyKanamoriTauPModel());
            FrameState frame = engine.frameAt(30.0);

            MapCanvasPane pane = new MapCanvasPane(scenario, outline);
            pane.resize(860.0, 730.0);
            pane.refresh(frame);

            Canvas staticCanvas = (Canvas) pane.getChildren().get(0);
            Canvas dynamicCanvas = (Canvas) pane.getChildren().get(1);
            WritableImage staticBefore = staticCanvas.snapshot(null, null);
            WritableImage dynamicBefore = dynamicCanvas.snapshot(null, null);

            // Simulate the blank backing surfaces observed after a window restoration.
            staticCanvas.getGraphicsContext2D().clearRect(0.0, 0.0, staticCanvas.getWidth(), staticCanvas.getHeight());
            dynamicCanvas.getGraphicsContext2D().clearRect(0.0, 0.0, dynamicCanvas.getWidth(), dynamicCanvas.getHeight());

            pane.refresh(frame);

            WritableImage staticAfter = staticCanvas.snapshot(null, null);
            WritableImage dynamicAfter = dynamicCanvas.snapshot(null, null);
            assertImagesSimilar(staticBefore, staticAfter);
            assertImagesSimilar(dynamicBefore, dynamicAfter);
            assertTrue(countOpaquePixels(staticAfter) > 500_000,
                    "California static layer should be fully rendered after restoration");
            assertTrue(countLandFillPixels(staticAfter) > 1_000,
                    "California outline fill should be rendered after restoration");
            assertTrue(countDarkPixels(staticAfter) > 100,
                    "California labels and outline should be rendered after restoration");
            assertTrue(countWavefrontPixels(dynamicAfter) > 10,
                    "The supplied current frame should be rendered after restoration");
        });
    }

    private static void assertImagesSimilar(WritableImage expected, WritableImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());

        var expectedPixels = expected.getPixelReader();
        var actualPixels = actual.getPixelReader();
        int differentPixels = 0;
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                if (expectedPixels.getArgb(x, y) != actualPixels.getArgb(x, y)) {
                    differentPixels++;
                }
            }
        }
        int allowedDifferences = (int) (expected.getWidth() * expected.getHeight() * 0.05);
        assertTrue(differentPixels <= allowedDifferences,
                "Restoration changed too many rendered pixels: " + differentPixels);
    }

    private static int countOpaquePixels(WritableImage image) {
        int count = 0;
        var pixels = image.getPixelReader();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((pixels.getArgb(x, y) >>> 24) != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countLandFillPixels(WritableImage image) {
        int count = 0;
        var pixels = image.getPixelReader();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = pixels.getArgb(x, y);
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                if (red >= 245 && green >= 243 && blue >= 235) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countDarkPixels(WritableImage image) {
        int count = 0;
        var pixels = image.getPixelReader();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = pixels.getArgb(x, y);
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                if ((argb >>> 24) != 0 && red < 120 && green < 140 && blue < 160) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countWavefrontPixels(WritableImage image) {
        int count = 0;
        var pixels = image.getPixelReader();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = pixels.getArgb(x, y);
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                boolean cyan = blue > 150 && green > 120 && red < 80;
                boolean orange = red > 180 && green > 50 && green < 180 && blue < 100;
                if ((argb >>> 24) != 0 && (cyan || orange)) {
                    count++;
                }
            }
        }
        return count;
    }
}
