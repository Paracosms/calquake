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
                pane.resize(MapCanvasPane.BASELINE_VIEWPORT_WIDTH, MapCanvasPane.BASELINE_VIEWPORT_HEIGHT);
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

                // 1. Dimensions match baseline viewport
                assertEquals((int) MapCanvasPane.BASELINE_VIEWPORT_WIDTH, imgW, "Snapshot width must match baseline");
                assertEquals((int) MapCanvasPane.BASELINE_VIEWPORT_HEIGHT, imgH, "Snapshot height must match baseline");

                // 2. Entire image must be 100% opaque
                assertEquals(imgW * imgH, countOpaquePixels(image), "Every pixel in snapshot must be fully opaque");

                // 3. Pacific ocean background is rendered at offshore coordinate (100, 200)
                Color oceanSample = pr.getColor(100, 200);
                assertEquals(0.886, oceanSample.getRed(), 0.05, "Pacific Ocean red channel must match #E2EDF6");
                assertEquals(0.929, oceanSample.getGreen(), 0.05, "Pacific Ocean green channel must match #E2EDF6");
                assertEquals(0.965, oceanSample.getBlue(), 0.05, "Pacific Ocean blue channel must match #E2EDF6");

                // 4. Meaningful distribution of map elements
                assertTrue(countOceanPixels(image) > 200_000,
                        "Pacific Ocean background should cover > 200,000 pixels");
                assertTrue(countLandFillPixels(image) > 50_000,
                        "California landmass fill should cover > 50,000 pixels");
                assertTrue(countDarkPixels(image) > 1_000,
                        "Borders, graticule, and labels should render > 1,000 dark pixels");

                // 5. Epicenter and marker badges are rendered
                assertTrue(countRedPixels(image) > 10,
                        "Epicenter marker red pixels should be visible");
                assertTrue(countYellowPixels(image) > 20,
                        "Ridgecrest / Trona peak MMI VII badge yellow pixels should be visible");
                assertTrue(countCyanPixels(image) > 20,
                        "Bakersfield / Los Angeles peak MMI IV badge cyan pixels should be visible");
                assertTrue(countUniqueColors(image) > 50,
                        "Snapshot should contain diverse color palette (> 50 distinct colors)");
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
            pane.resize(MapCanvasPane.BASELINE_VIEWPORT_WIDTH, MapCanvasPane.BASELINE_VIEWPORT_HEIGHT);
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

    static int countOpaquePixels(WritableImage image) {
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

    static int countOceanPixels(WritableImage image) {
        int count = 0;
        var pixels = image.getPixelReader();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = pixels.getArgb(x, y);
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                // #E2EDF6: R ~ 226, G ~ 237, B ~ 246
                if (red >= 215 && red <= 235 && green >= 225 && green <= 245 && blue >= 235) {
                    count++;
                }
            }
        }
        return count;
    }

    static int countLandFillPixels(WritableImage image) {
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

    static int countDarkPixels(WritableImage image) {
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

    static int countRedPixels(WritableImage image) {
        int count = 0;
        var pixels = image.getPixelReader();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = pixels.getArgb(x, y);
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                // Red epicenter #DC2626
                if (red > 180 && green < 60 && blue < 60) {
                    count++;
                }
            }
        }
        return count;
    }

    static int countYellowPixels(WritableImage image) {
        int count = 0;
        var pixels = image.getPixelReader();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = pixels.getArgb(x, y);
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                // Yellow badge #FFC400
                if (red > 220 && green > 160 && green < 230 && blue < 50) {
                    count++;
                }
            }
        }
        return count;
    }

    static int countCyanPixels(WritableImage image) {
        int count = 0;
        var pixels = image.getPixelReader();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = pixels.getArgb(x, y);
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                // Cyan badge #7FFFFA
                if (red < 160 && green > 220 && blue > 220) {
                    count++;
                }
            }
        }
        return count;
    }

    static int countWavefrontPixels(WritableImage image) {
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

    static int countUniqueColors(WritableImage image) {
        java.util.Set<Integer> unique = new java.util.HashSet<>();
        var pixels = image.getPixelReader();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                unique.add(pixels.getArgb(x, y));
            }
        }
        return unique.size();
    }
}
