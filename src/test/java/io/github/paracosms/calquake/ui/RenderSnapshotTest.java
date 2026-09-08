package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.Scenario;
import io.github.paracosms.calquake.data.CaliforniaOutline;
import io.github.paracosms.calquake.data.ScenarioLoader;
import io.github.paracosms.calquake.testsupport.JavaFxTestHelper;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
