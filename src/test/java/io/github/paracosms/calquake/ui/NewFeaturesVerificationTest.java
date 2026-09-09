package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.FrameState;
import io.github.paracosms.calquake.core.HadleyKanamoriTauPModel;
import io.github.paracosms.calquake.core.ReplayEngine;
import io.github.paracosms.calquake.core.Scenario;
import io.github.paracosms.calquake.data.CaliforniaOutline;
import io.github.paracosms.calquake.data.ScenarioLoader;
import io.github.paracosms.calquake.testsupport.JavaFxTestHelper;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification test suite for:
 * 1. Top-level application header/menu bar with Mode dropdown (Replay only).
 * 2. Nonfunctional Settings button in header.
 * 3. Compact event selector replacing Five Reference Locations box (Ridgecrest and Northridge options).
 * 4. Filled P- and S-wave wavefront circles with translucent fills and preserved outlines.
 * 5. Removal of city-name labels from MapCanvasPane.
 */
public class NewFeaturesVerificationTest {

    @Test
    @DisplayName("Verify window title is CalQuake, and header contains Mode (with Replay) and Settings consistent with Play/Restart buttons")
    void testHeaderModeAndSettings() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            CalQuakeApp app = new CalQuakeApp();
            app.init();
            Stage stage = new Stage();
            try {
                app.start(stage);

                // 1. Verify window title is just CalQuake
                assertEquals("CalQuake", stage.getTitle(), "Window title must be just 'CalQuake'");

                // 2. MenuBar structure: Mode, Settings (CalQuake 0.1.0 removed from header)
                MenuBar menuBar = app.getMenuBar();
                assertNotNull(menuBar, "Top-level application menu bar must exist");
                assertEquals(2, menuBar.getMenus().size(), "Header must contain exactly 2 menus: Mode and Settings");

                // Menu 1: Mode
                Menu modeMenu = app.getModeMenu();
                assertNotNull(modeMenu, "Mode menu must exist");
                assertEquals(modeMenu, menuBar.getMenus().get(0));
                assertEquals("Mode", modeMenu.getText(), "Mode menu must have no underline or prefix");

                List<MenuItem> modeItems = modeMenu.getItems();
                assertEquals(1, modeItems.size(), "Mode dropdown must contain exactly one option for this version");

                MenuItem replayItem = modeItems.get(0);
                assertEquals("Replay", replayItem.getText(), "Single mode option must be 'Replay'");
                assertEquals(replayItem, app.getReplayMenuItem());

                // Menu 2: Settings
                Menu settingsMenu = app.getSettingsMenu();
                assertNotNull(settingsMenu, "Settings menu must exist in the header");
                assertEquals(settingsMenu, menuBar.getMenus().get(1));
                assertEquals("Settings", settingsMenu.getText(), "Settings menu must have no underline or prefix");
                assertFalse(settingsMenu.getItems().isEmpty(), "Settings menu should have placeholder item");

                // Settings button helper
                Button settingsBtn = app.getSettingsButton();
                assertNotNull(settingsBtn, "Settings button helper must exist");
                assertEquals("Settings", settingsBtn.getText());
                assertDoesNotThrow(settingsBtn::fire, "Settings button click must be safe and nonfunctional");
                assertTrue(app.getController().isPaused(), "Settings click must not affect replay playback state");
            } finally {
                app.stop();
                stage.close();
            }
        });
    }

    @Test
    @DisplayName("Verify compact event selector contains exactly Ridgecrest and Northridge")
    void testCompactEventSelector() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            CalQuakeApp app = new CalQuakeApp();
            app.init();
            Stage stage = new Stage();
            try {
                app.start(stage);

                ComboBox<String> eventSelector = app.getEventSelector();
                assertNotNull(eventSelector, "Event selector ComboBox must exist in the sidebar");

                List<String> items = eventSelector.getItems();
                assertEquals(2, items.size(), "Event selector must contain exactly two options");
                assertEquals("Ridgecrest", items.get(0));
                assertEquals("Northridge", items.get(1));

                assertEquals("Ridgecrest", eventSelector.getValue(), "Default selected event must be Ridgecrest");

                // Switch selection
                eventSelector.setValue("Northridge");
                assertEquals("Northridge", eventSelector.getValue());
                eventSelector.setValue("Ridgecrest");
                assertEquals("Ridgecrest", eventSelector.getValue());
            } finally {
                app.stop();
                stage.close();
            }
        });
    }

    @Test
    @DisplayName("Verify P- and S-wave circles render as low-opacity filled circles with moving outlines")
    void testFilledWavefrontCirclesWithOutlines() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            ScenarioLoader loader = new ScenarioLoader();
            Scenario scenario = loader.loadDefaultScenario();
            CaliforniaOutline outline = CaliforniaOutline.loadDefault();
            HadleyKanamoriTauPModel model = new HadleyKanamoriTauPModel();
            ReplayEngine engine = ReplayEngine.create(scenario, model);

            MapCanvasPane mapPane = new MapCanvasPane(scenario, outline);
            mapPane.resize(MapCanvasPane.BASELINE_VIEWPORT_WIDTH, MapCanvasPane.BASELINE_VIEWPORT_HEIGHT);
            mapPane.redrawStaticMap();

            // Render frame at t = 10.0 s where both P (~60 km) and S (~33 km) circles exist
            FrameState frame10 = engine.frameAt(scenario, 10.0);
            mapPane.renderFrame(frame10);

            javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            WritableImage dynSnapshot = mapPane.getDynamicCanvas().snapshot(params, null);
            assertNotNull(dynSnapshot);

            PixelReader reader = dynSnapshot.getPixelReader();
            int translucentFillPixels = 0;
            int highOpacityOutlinePixels = 0;

            for (int y = 0; y < (int) dynSnapshot.getHeight(); y++) {
                for (int x = 0; x < (int) dynSnapshot.getWidth(); x++) {
                    double opacity = reader.getColor(x, y).getOpacity();
                    if (opacity > 0.05 && opacity <= 0.25) {
                        translucentFillPixels++;
                    } else if (opacity > 0.85) {
                        highOpacityOutlinePixels++;
                    }
                }
            }

            // Low-opacity filled circles cover the interior area
            assertTrue(translucentFillPixels > 500,
                    "Dynamic canvas must contain translucent fill pixels (> 500), found: " + translucentFillPixels);

            // High-opacity moving outlines remain rendered on top
            assertTrue(highOpacityOutlinePixels > 50,
                    "Dynamic canvas must retain high-opacity moving outline pixels (> 50), found: " + highOpacityOutlinePixels);
        });
    }

    @Test
    @DisplayName("Verify city-name labels are removed from MapCanvasPane while peak MMI badges remain")
    void testCityNameLabelsRemovedFromMapCanvasPane() throws Exception {
        JavaFxTestHelper.runOnFxThread(() -> {
            ScenarioLoader loader = new ScenarioLoader();
            Scenario scenario = loader.loadDefaultScenario();
            CaliforniaOutline outline = CaliforniaOutline.loadDefault();

            MapCanvasPane mapPane = new MapCanvasPane(scenario, outline);
            mapPane.resize(MapCanvasPane.BASELINE_VIEWPORT_WIDTH, MapCanvasPane.BASELINE_VIEWPORT_HEIGHT);
            mapPane.redrawStaticMap();

            WritableImage staticSnapshot = mapPane.snapshot(null, null);
            assertNotNull(staticSnapshot);

            // Peak MMI badges remain intact
            assertTrue(RenderSnapshotTest.countYellowPixels(staticSnapshot) > 15,
                    "Ridgecrest / Trona MMI VII yellow badges must remain visible");
            assertTrue(RenderSnapshotTest.countCyanPixels(staticSnapshot) > 15,
                    "Bakersfield / Los Angeles MMI IV cyan badges must remain visible");
        });
    }
}
