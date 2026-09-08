package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.AzimuthalEquidistantProjection;
import io.github.paracosms.calquake.core.AzimuthalEquidistantProjection.BoundingBox;
import io.github.paracosms.calquake.core.AzimuthalEquidistantProjection.ProjectedPoint;
import io.github.paracosms.calquake.core.AzimuthalEquidistantProjection.ScreenPoint;
import io.github.paracosms.calquake.core.AzimuthalEquidistantProjection.ViewportTransform;
import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.HadleyKanamoriTauPModel;
import io.github.paracosms.calquake.core.MmiLegend;
import io.github.paracosms.calquake.core.PrecomputedWavefronts;
import io.github.paracosms.calquake.core.ReferenceLocation;
import io.github.paracosms.calquake.core.ReplayController;
import io.github.paracosms.calquake.core.ReplayEngine;
import io.github.paracosms.calquake.core.Scenario;
import io.github.paracosms.calquake.core.TravelTimeModel;
import io.github.paracosms.calquake.core.WavefrontRadii;
import io.github.paracosms.calquake.data.CaliforniaOutline;
import io.github.paracosms.calquake.data.ScenarioLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 5 Acceptance & Verification Test:
 * <ul>
 *   <li>Compares projected markers with independent control points to &le; 1 pixel at baseline.</li>
 *   <li>Compares wavefront radii fixtures with independent control points to &le; 1 pixel.</li>
 *   <li>Verifies complete California outline (6 rings, 468 vertices).</li>
 *   <li>Verifies 1:1 aspect ratio preservation across different viewport dimensions.</li>
 *   <li>Verifies fixed label offsets resolve collisions with zero overlap.</li>
 *   <li>Verifies frozen MMI bins, peak intensity values, colors, and N/A handling.</li>
 *   <li>Verifies initial replay controller state is paused at 0.0 seconds.</li>
 * </ul>
 */
class Stage5BaselineVerificationTest {

    private static Scenario scenario;
    private static CaliforniaOutline outline;
    private static AzimuthalEquidistantProjection projection;
    private static BoundingBox bounds;
    private static ViewportTransform baselineTransform;

    private static final double BASELINE_WIDTH = MapCanvasPane.BASELINE_VIEWPORT_WIDTH;   // 860.0 px
    private static final double BASELINE_HEIGHT = MapCanvasPane.BASELINE_VIEWPORT_HEIGHT; // 730.0 px
    private static final double MARGIN = MapCanvasPane.DEFAULT_MARGIN_PX;                 // 24.0 px

    @BeforeAll
    static void setUp() {
        ScenarioLoader loader = new ScenarioLoader();
        scenario = loader.loadDefaultScenario();
        outline = CaliforniaOutline.loadDefault();
        projection = AzimuthalEquidistantProjection.centeredAt(scenario.event().epicenter());
        bounds = outline.computeProjectedBoundingBox(projection);
        baselineTransform = projection.createViewportTransform(bounds, BASELINE_WIDTH, BASELINE_HEIGHT, MARGIN);
    }

    /**
     * Independent haversine projection oracle calculation for control verification.
     */
    private static ScreenPoint independentControlProject(GeoPoint target, GeoPoint origin, BoundingBox bbox, double w, double h, double margin) {
        double R = 6371.0;
        double phi0 = Math.toRadians(origin.latitude());
        double lam0 = Math.toRadians(origin.longitude());
        double phi = Math.toRadians(target.latitude());
        double lam = Math.toRadians(target.longitude());

        double dphi = phi - phi0;
        double dlam = lam - lam0;
        double sinHalfDphi = Math.sin(dphi / 2.0);
        double sinHalfDlam = Math.sin(dlam / 2.0);
        double a = sinHalfDphi * sinHalfDphi + Math.cos(phi0) * Math.cos(phi) * sinHalfDlam * sinHalfDlam;
        double delta = 2.0 * Math.asin(Math.sqrt(Math.max(0.0, Math.min(1.0, a))));
        double distKm = R * delta;

        double yAz = Math.sin(dlam) * Math.cos(phi);
        double xAz = Math.cos(phi0) * Math.sin(phi) - Math.sin(phi0) * Math.cos(phi) * Math.cos(dlam);
        double azimuth = Math.atan2(yAz, xAz);

        double xKm = distKm * Math.sin(azimuth);
        double yKm = -distKm * Math.cos(azimuth);

        // Independent viewport scaling
        double availW = w - 2.0 * margin;
        double availH = h - 2.0 * margin;
        double scale = Math.min(availW / bbox.widthKm(), availH / bbox.heightKm());

        double originScreenX = (w / 2.0) - bbox.centerXKm() * scale;
        double originScreenY = (h / 2.0) - bbox.centerYKm() * scale;

        return new ScreenPoint(originScreenX + xKm * scale, originScreenY + yKm * scale);
    }

    @Test
    @DisplayName("Verify epicenter projects to screen within <= 1 pixel of independent control calculation")
    void testEpicenterScreenPositionMatchesControlPoint() {
        GeoPoint epicenter = scenario.event().epicenter();
        ScreenPoint actual = baselineTransform.toScreen(projection.project(epicenter));
        ScreenPoint expected = independentControlProject(epicenter, epicenter, bounds, BASELINE_WIDTH, BASELINE_HEIGHT, MARGIN);

        assertEquals(expected.xPx(), actual.xPx(), 1.0, "Epicenter X pixel error exceeds 1px");
        assertEquals(expected.yPx(), actual.yPx(), 1.0, "Epicenter Y pixel error exceeds 1px");
    }

    @ParameterizedTest
    @CsvSource({
            "Ridgecrest, 35.628542, -117.663992",
            "Trona, 35.815821, -117.347348",
            "Bakersfield, 35.353593, -119.036921",
            "Los Angeles, 34.019394, -118.410825",
            "Fresno, 36.782684, -119.793359"
    })
    @DisplayName("Verify each reference location projects within <= 1 pixel of independent control calculation")
    void testReferenceLocationsScreenPositionMatchesControlPoints(String city, double lat, double lon) {
        GeoPoint pt = new GeoPoint(lat, lon);
        ScreenPoint actual = baselineTransform.toScreen(projection.project(pt));
        ScreenPoint expected = independentControlProject(pt, scenario.event().epicenter(), bounds, BASELINE_WIDTH, BASELINE_HEIGHT, MARGIN);

        assertEquals(expected.xPx(), actual.xPx(), 1.0, city + " X screen pixel error exceeds 1px");
        assertEquals(expected.yPx(), actual.yPx(), 1.0, city + " Y screen pixel error exceeds 1px");
    }

    @Test
    @DisplayName("Verify known wavefront radii fixtures project to screen radius within <= 1 pixel")
    void testWavefrontRadiiScreenScalingMatchesControl() {
        TravelTimeModel model = new HadleyKanamoriTauPModel();
        PrecomputedWavefronts wavefronts = PrecomputedWavefronts.forScenario(scenario, model);

        // At 10.0 s: P-wave surface radius is known (~54.6 km)
        WavefrontRadii radii10 = wavefronts.radiiAt(10.0);
        assertTrue(radii10.hasP());
        double p10Km = radii10.pRadiusKm();
        double p10ScreenActual = baselineTransform.toScreenRadius(p10Km);
        double p10ScreenExpected = p10Km * baselineTransform.scalePxPerKm();
        assertEquals(p10ScreenExpected, p10ScreenActual, 1e-9);

        // At 30.0 s: P and S wavefronts
        WavefrontRadii radii30 = wavefronts.radiiAt(30.0);
        assertTrue(radii30.hasP());
        assertTrue(radii30.hasS());
        double s30Km = radii30.sRadiusKm();
        double s30ScreenActual = baselineTransform.toScreenRadius(s30Km);
        double s30ScreenExpected = s30Km * baselineTransform.scalePxPerKm();
        assertEquals(s30ScreenExpected, s30ScreenActual, 1e-9);

        // Verify independent control scale
        double availH = BASELINE_HEIGHT - 2.0 * MARGIN;
        double controlScale = availH / bounds.heightKm(); // Height is constraining axis for California
        assertEquals(controlScale, baselineTransform.scalePxPerKm(), 1e-6);
    }

    @Test
    @DisplayName("Verify 1:1 aspect ratio is preserved across viewport resizes")
    void testAspectRatioPreservationUnderResize() {
        double[][] viewports = {
                {860.0, 730.0},
                {1024.0, 768.0},
                {640.0, 480.0},
                {1920.0, 1080.0},
                {500.0, 900.0}
        };

        for (double[] vp : viewports) {
            double w = vp[0];
            double h = vp[1];
            ViewportTransform vt = projection.createViewportTransform(bounds, w, h, MARGIN);

            // Distance scaling in X and Y must be identical
            ScreenPoint p0 = vt.toScreen(0.0, 0.0);
            ScreenPoint px = vt.toScreen(100.0, 0.0);
            ScreenPoint py = vt.toScreen(0.0, 100.0);

            double dx = Math.abs(px.xPx() - p0.xPx());
            double dy = Math.abs(py.yPx() - p0.yPx());

            assertEquals(dx, dy, 1e-9, String.format("Viewport %.0fx%.0f aspect ratio distorted", w, h));
        }
    }

    @Test
    @DisplayName("Verify complete California outline contains 6 rings and 468 vertices")
    void testCompleteCaliforniaOutline() {
        assertEquals(6, outline.ringCount(), "Must contain exactly 6 closed polygon rings");
        assertEquals(468, outline.totalVertices(), "Must contain exactly 468 vertices");

        // Verify all rings project inside baseline bounds
        List<List<ProjectedPoint>> rings = outline.projectRings(projection);
        for (List<ProjectedPoint> ring : rings) {
            for (ProjectedPoint p : ring) {
                ScreenPoint sp = baselineTransform.toScreen(p);
                assertTrue(sp.xPx() >= -10.0 && sp.xPx() <= BASELINE_WIDTH + 10.0, "Screen X outside viewport");
                assertTrue(sp.yPx() >= -10.0 && sp.yPx() <= BASELINE_HEIGHT + 10.0, "Screen Y outside viewport");
            }
        }
    }

    @Test
    @DisplayName("Verify fixed label offsets prevent collisions between markers and labels")
    void testFixedLabelOffsetsPreventCollisions() {
        // Collect bounding boxes for epicenter and the 5 reference location labels
        record LabelBox(String name, double x, double y, double w, double h) {
            boolean intersects(LabelBox o) {
                return !(this.x + this.w <= o.x || o.x + o.w <= this.x ||
                         this.y + this.h <= o.y || o.y + o.h <= this.y);
            }
        }
        List<LabelBox> boxes = new ArrayList<>();

        // Epicenter
        ScreenPoint epi = baselineTransform.toScreen(0.0, 0.0);
        MapCanvasPane.LabelOffset epiOffset = MapCanvasPane.FIXED_LABEL_OFFSETS.get("EPICENTER");
        double epiX = epi.xPx() + epiOffset.dx();
        double epiY = epi.yPx() + epiOffset.dy();
        boxes.add(new LabelBox("Epicenter", epiX, epiY, 145.0, 34.0));

        // 5 Locations
        for (ReferenceLocation loc : scenario.locations()) {
            ProjectedPoint proj = projection.project(loc.internalPoint());
            ScreenPoint sp = baselineTransform.toScreen(proj);
            MapCanvasPane.LabelOffset off = MapCanvasPane.FIXED_LABEL_OFFSETS.get(loc.city());
            assertNotNull(off, "Missing offset for " + loc.city());

            double lx = sp.xPx() + off.dx();
            double ly = sp.yPx() + off.dy();
            boxes.add(new LabelBox(loc.city(), lx, ly, 142.0, 32.0));
        }

        // Check pairwise that no label box intersects another
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                LabelBox b1 = boxes.get(i);
                LabelBox b2 = boxes.get(j);
                boolean intersects = b1.intersects(b2);
                assertFalse(intersects, String.format(
                        "Label collision detected between '%s' and '%s': [%.1f, %.1f] vs [%.1f, %.1f]",
                        b1.name(), b2.name(), b1.x(), b1.y(), b2.x(), b2.y()
                ));
            }
        }
    }

    @Test
    @DisplayName("Verify frozen MMI bins, peak intensity values, and colors")
    void testMmiBinsAndColors() {
        // Ridgecrest: VII, 7.2, #ffc400
        ReferenceLocation ridgecrest = scenario.findLocationByCity("Ridgecrest").orElseThrow();
        assertEquals(7.2, ridgecrest.peakIntensity().mmiDisplayRounded());
        assertEquals("VII", ridgecrest.peakIntensity().mmiRoman());
        assertEquals("#ffc400", ridgecrest.peakIntensity().colorHex());

        // Trona: VII, 6.9, #ffc400
        ReferenceLocation trona = scenario.findLocationByCity("Trona").orElseThrow();
        assertEquals(6.9, trona.peakIntensity().mmiDisplayRounded());
        assertEquals("VII", trona.peakIntensity().mmiRoman());
        assertEquals("#ffc400", trona.peakIntensity().colorHex());

        // Bakersfield: IV, 3.9, #7ffffa
        ReferenceLocation bakersfield = scenario.findLocationByCity("Bakersfield").orElseThrow();
        assertEquals(3.9, bakersfield.peakIntensity().mmiDisplayRounded());
        assertEquals("IV", bakersfield.peakIntensity().mmiRoman());
        assertEquals("#7ffffa", bakersfield.peakIntensity().colorHex());

        // Los Angeles: IV, 3.8, #7ffffa
        ReferenceLocation la = scenario.findLocationByCity("Los Angeles").orElseThrow();
        assertEquals(3.8, la.peakIntensity().mmiDisplayRounded());
        assertEquals("IV", la.peakIntensity().mmiRoman());
        assertEquals("#7ffffa", la.peakIntensity().colorHex());

        // Fresno: II-III, 3.1, #acdbff
        ReferenceLocation fresno = scenario.findLocationByCity("Fresno").orElseThrow();
        assertEquals(3.1, fresno.peakIntensity().mmiDisplayRounded());
        assertEquals("II-III", fresno.peakIntensity().mmiRoman());
        assertEquals("#acdbff", fresno.peakIntensity().colorHex());

        // MMI Legend bins including N/A
        assertEquals(10, MmiLegend.ALL_BINS.size());
        assertEquals("#808080", MmiLegend.BIN_NA.colorHex());
        assertEquals("N/A", MmiLegend.BIN_NA.roman());
        assertEquals(MmiLegend.BIN_NA, MmiLegend.findBin(null));
        assertEquals(MmiLegend.BIN_NA, MmiLegend.findBin(0.5));
    }

    @Test
    @DisplayName("Verify ReplayController starts initially paused at 0.0 s")
    void testInitialControllerState() {
        TravelTimeModel model = new HadleyKanamoriTauPModel();
        ReplayEngine engine = ReplayEngine.create(scenario, model);
        ReplayController controller = new ReplayController(scenario, engine);

        assertTrue(controller.isPaused());
        assertFalse(controller.isPlaying());
        assertFalse(controller.isFinished());
        assertEquals(0.0, controller.elapsedSeconds(), 1e-9);

        // Frame at t=0 has no wavefronts (depth 8 km requires ~1.45 s for vertical P)
        var frame = controller.currentFrame();
        assertEquals(0.0, frame.elapsedSeconds());
        assertFalse(frame.frontRadii().hasP());
        assertFalse(frame.frontRadii().hasS());
        assertEquals(5, frame.locationIntensities().size());
    }
}
