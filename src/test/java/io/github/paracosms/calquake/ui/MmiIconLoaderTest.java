package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.MmiLegend;
import io.github.paracosms.calquake.testsupport.JavaFxTestHelper;
import io.github.paracosms.calquake.testsupport.MmiIconGenerator;
import javafx.scene.image.Image;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MmiIconLoaderTest {

    @BeforeAll
    static void setUp() throws IOException {
        Path resourceDir = Path.of("src/main/resources/icons/mmi");
        MmiIconGenerator.generateAll(resourceDir);

        Path targetDir = Path.of("target/classes/icons/mmi");
        if (Files.exists(targetDir.getParent())) {
            MmiIconGenerator.generateAll(targetDir);
        }
    }

    @Test
    void allAssetFilesExistAndAreNonEmpty() {
        Path resourceDir = Path.of("src/main/resources/icons/mmi");
        assertTrue(Files.isDirectory(resourceDir), "Resource directory must exist");

        for (MmiIconGenerator.IconSpec spec : MmiIconGenerator.ICONS) {
            Path svgPath = resourceDir.resolve(spec.id() + ".svg");
            Path pngPath = resourceDir.resolve(spec.id() + ".png");

            assertTrue(Files.isRegularFile(svgPath), "SVG file must exist: " + svgPath);
            assertTrue(Files.isRegularFile(pngPath), "PNG file must exist: " + pngPath);

            assertDoesNotThrow(() -> {
                long svgSize = Files.size(svgPath);
                long pngSize = Files.size(pngPath);
                assertTrue(svgSize > 50, "SVG file size must be non-trivial: " + svgPath);
                assertTrue(pngSize > 100, "PNG file size must be non-trivial: " + pngPath);
            });
        }
    }

    @Test
    void resolveBaseNameCoversAllLegendBins() {
        for (MmiLegend.MmiBin bin : MmiLegend.ALL_BINS) {
            String base = MmiIconLoader.resolveBaseName(bin.roman());
            assertNotNull(base);
            assertTrue(base.startsWith("mmi_"));
        }
    }

    @Test
    void getResourcePathProducesExpectedPaths() {
        assertEquals("/icons/mmi/mmi_vii.png", MmiIconLoader.getResourcePath("VII", false));
        assertEquals("/icons/mmi/mmi_vii.svg", MmiIconLoader.getResourcePath("VII", true));
        assertEquals("/icons/mmi/mmi_ii_iii.png", MmiIconLoader.getResourcePath("II-III", false));
        assertEquals("/icons/mmi/mmi_x_plus.png", MmiIconLoader.getResourcePath("X+", false));
        assertEquals("/icons/mmi/mmi_na.png", MmiIconLoader.getResourcePath("UNKNOWN", false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"I", "II-III", "IV", "V", "VI", "VII", "VIII", "IX", "X+", "N/A"})
    void loadIconsForStandardBins(String roman) throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            Image icon = MmiIconLoader.getIcon(roman);
            assertNotNull(icon, "Icon must load for " + roman);
            assertFalse(icon.isError(), "Icon must not have loading error: " + icon.getException());
            assertTrue(icon.getWidth() > 0, "Icon width must be positive");
            assertTrue(icon.getHeight() > 0, "Icon height must be positive");
        });
    }

    @Test
    void fallbackToNaForNullOrInvalid() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            Image iconNull = MmiIconLoader.getIcon((String) null);
            assertNotNull(iconNull);
            assertFalse(iconNull.isError());

            Image iconInvalid = MmiIconLoader.getIcon("NON_EXISTENT_MMI");
            assertNotNull(iconInvalid);
            assertFalse(iconInvalid.isError());
        });
    }
}
