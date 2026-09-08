package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.AzimuthalEquidistantProjection;
import io.github.paracosms.calquake.core.AzimuthalEquidistantProjection.BoundingBox;
import io.github.paracosms.calquake.core.AzimuthalEquidistantProjection.ProjectedPoint;
import io.github.paracosms.calquake.core.AzimuthalEquidistantProjection.ScreenPoint;
import io.github.paracosms.calquake.core.AzimuthalEquidistantProjection.ViewportTransform;
import io.github.paracosms.calquake.core.FrameState;
import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.LocationIntensityState;
import io.github.paracosms.calquake.core.ReferenceLocation;
import io.github.paracosms.calquake.core.Scenario;
import io.github.paracosms.calquake.core.WavefrontRadii;
import io.github.paracosms.calquake.data.CaliforniaOutline;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dual-canvas map viewport for Demo 0:
 * <ul>
 *   <li>{@code staticCanvas}: Renders background, California landmass, epicenter, and the 5 historical reference
 *       locations with their peak MMI badges and collision-free labels. Redrawn on size/layout changes, scenario
 *       changes, and explicit lifecycle refreshes.</li>
 *   <li>{@code dynamicCanvas}: Layered directly on top, transparent, strictly clipped to viewport bounds,
 *       ready to receive {@link FrameState} wavefront circles.</li>
 * </ul>
 * Preserves strict 1:1 aspect ratio across resizing via {@link AzimuthalEquidistantProjection}.
 */
public class MapCanvasPane extends Pane {

    public static final double DEFAULT_MARGIN_PX = 24.0;
    public static final double BASELINE_VIEWPORT_WIDTH = 860.0;
    public static final double BASELINE_VIEWPORT_HEIGHT = 730.0;

    // Fixed label offsets (dx, dy) relative to projected screen point to prevent overlaps
    public record LabelOffset(double dx, double dy, String align) {}
    public static final Map<String, LabelOffset> FIXED_LABEL_OFFSETS = Map.of(
            "EPICENTER", new LabelOffset(-150.0, -35.0, "RIGHT"),
            "Ridgecrest", new LabelOffset(14.0, 14.0, "LEFT"),
            "Trona", new LabelOffset(14.0, -30.0, "LEFT"),
            "Bakersfield", new LabelOffset(-155.0, -6.0, "RIGHT"),
            "Los Angeles", new LabelOffset(14.0, 14.0, "LEFT"),
            "Fresno", new LabelOffset(-155.0, -14.0, "RIGHT")
    );

    private final Canvas staticCanvas;
    private final Canvas dynamicCanvas;
    private final Rectangle clipRect;

    private Scenario scenario;
    private CaliforniaOutline outline;
    private AzimuthalEquidistantProjection projection;
    private BoundingBox projectedBounds;
    private ViewportTransform currentTransform;

    private double lastWidth = -1.0;
    private double lastHeight = -1.0;
    private FrameState lastFrame;

    public MapCanvasPane(Scenario scenario, CaliforniaOutline outline) {
        this.scenario = Objects.requireNonNull(scenario, "scenario cannot be null");
        this.outline = Objects.requireNonNull(outline, "outline cannot be null");

        this.projection = AzimuthalEquidistantProjection.centeredAt(scenario.event().epicenter());
        this.projectedBounds = outline.computeProjectedBoundingBox(projection);

        this.staticCanvas = new Canvas();
        this.dynamicCanvas = new Canvas();

        setPrefSize(BASELINE_VIEWPORT_WIDTH, BASELINE_VIEWPORT_HEIGHT);
        setMinSize(300.0, 200.0);

        // Enforce strict clipping to viewport bounds
        this.clipRect = new Rectangle();
        this.clipRect.widthProperty().bind(widthProperty());
        this.clipRect.heightProperty().bind(heightProperty());
        setClip(clipRect);

        getChildren().addAll(staticCanvas, dynamicCanvas);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        double w = getWidth();
        double h = getHeight();

        if (w > 0 && h > 0 && (Math.abs(w - lastWidth) > 0.5 || Math.abs(h - lastHeight) > 0.5
                || canvasDimensionsOutOfSync(w, h))) {
            synchronizeCanvasDimensions(w, h);

            redrawStaticMap();
            if (lastFrame != null) {
                renderFrame(lastFrame);
            }
        }
    }

    /**
     * Updates the bound scenario and recalculates projection bounds.
     */
    public void setScenario(Scenario scenario) {
        this.scenario = Objects.requireNonNull(scenario, "scenario cannot be null");
        this.projection = AzimuthalEquidistantProjection.centeredAt(scenario.event().epicenter());
        this.projectedBounds = outline.computeProjectedBoundingBox(projection);
        redrawStaticMap();
    }

    /**
     * Redraws static geometry onto {@code staticCanvas}.
     * Executed when size, scenario, or window lifecycle state changes.
     */
    public void redrawStaticMap() {
        double w = getWidth() > 0 ? getWidth() : BASELINE_VIEWPORT_WIDTH;
        double h = getHeight() > 0 ? getHeight() : BASELINE_VIEWPORT_HEIGHT;

        this.currentTransform = projection.createViewportTransform(projectedBounds, w, h, DEFAULT_MARGIN_PX);
        GraphicsContext gc = staticCanvas.getGraphicsContext2D();

        // Clear canvas
        gc.clearRect(0, 0, w, h);

        // 1. Ocean background (soft muted blue-gray cartographic fill)
        gc.setFill(Color.web("#E2EDF6"));
        gc.fillRect(0, 0, w, h);

        // Subtle graticule / grid lines (classic desktop GIS feel)
        drawGridLines(gc, w, h);

        // 2. California landmass (all 6 closed polygon rings)
        List<List<ProjectedPoint>> projectedRings = outline.projectRings(projection);
        gc.setFill(Color.web("#FCFAF2"));
        gc.setStroke(Color.web("#475569"));
        gc.setLineWidth(1.4);
        gc.setLineCap(StrokeLineCap.ROUND);

        for (List<ProjectedPoint> ring : projectedRings) {
            if (ring.isEmpty()) continue;
            gc.beginPath();
            ScreenPoint first = currentTransform.toScreen(ring.get(0));
            gc.moveTo(first.xPx(), first.yPx());
            for (int i = 1; i < ring.size(); i++) {
                ScreenPoint pt = currentTransform.toScreen(ring.get(i));
                gc.lineTo(pt.xPx(), pt.yPx());
            }
            gc.closePath();
            gc.fill();
            gc.stroke();
        }

        // 3. Map Title / Coordinate Bar (classic desktop top-right stamp)
        drawCartographicScale(gc, w, h);

        // 4. Epicenter marker and label
        drawEpicenter(gc);

        // 5. Five Reference Locations with Peak MMI Badges
        drawReferenceLocations(gc);
    }

    /**
     * Restores both canvas layers after a window lifecycle event.
     * <p>
     * A JavaFX {@link Canvas} can lose its backing surface while a window is minimized or
     * deactivated without a corresponding layout-size change. Re-applying the dimensions and
     * explicitly repainting both layers makes restoration independent of the next animation pulse.
     *
     * @param frame current replay frame to render on the dynamic layer
     */
    public void refresh(FrameState frame) {
        double w = getWidth() > 0 ? getWidth() : BASELINE_VIEWPORT_WIDTH;
        double h = getHeight() > 0 ? getHeight() : BASELINE_VIEWPORT_HEIGHT;

        synchronizeCanvasDimensions(w, h);
        redrawStaticMap();
        renderFrame(frame);
    }

    private boolean canvasDimensionsOutOfSync(double w, double h) {
        return Math.abs(staticCanvas.getWidth() - w) > 0.5
                || Math.abs(staticCanvas.getHeight() - h) > 0.5
                || Math.abs(dynamicCanvas.getWidth() - w) > 0.5
                || Math.abs(dynamicCanvas.getHeight() - h) > 0.5;
    }

    private void synchronizeCanvasDimensions(double w, double h) {
        lastWidth = w;
        lastHeight = h;

        if (Math.abs(staticCanvas.getWidth() - w) > 0.5) {
            staticCanvas.setWidth(w);
        }
        if (Math.abs(staticCanvas.getHeight() - h) > 0.5) {
            staticCanvas.setHeight(h);
        }
        if (Math.abs(dynamicCanvas.getWidth() - w) > 0.5) {
            dynamicCanvas.setWidth(w);
        }
        if (Math.abs(dynamicCanvas.getHeight() - h) > 0.5) {
            dynamicCanvas.setHeight(h);
        }
    }

    private void drawGridLines(GraphicsContext gc, double w, double h) {
        gc.setStroke(Color.web("#CFDFED"));
        gc.setLineWidth(0.75);

        // 50 km distance coordinate grid lines from epicenter
        double originX = currentTransform.originScreenXPx();
        double originY = currentTransform.originScreenYPx();
        double stepPx = currentTransform.toScreenRadius(100.0); // 100 km grid

        if (stepPx > 15.0) {
            for (double x = originX % stepPx; x < w; x += stepPx) {
                gc.strokeLine(x, 0, x, h);
            }
            for (double y = originY % stepPx; y < h; y += stepPx) {
                gc.strokeLine(0, y, w, y);
            }
        }
    }

    private void drawCartographicScale(GraphicsContext gc, double w, double h) {
        // Distance scale bar (100 km) in bottom-left corner
        double scaleKm = 100.0;
        double scalePx = currentTransform.toScreenRadius(scaleKm);

        double barX = 20.0;
        double barY = h - 25.0;

        // Scale bar box
        gc.setFill(Color.web("#FFFFFF", 0.85));
        gc.setStroke(Color.web("#7A8B9E"));
        gc.setLineWidth(1.0);
        gc.fillRect(barX - 6, barY - 18, scalePx + 12, 28);
        gc.strokeRect(barX - 6, barY - 18, scalePx + 12, 28);

        // Scale bar
        gc.setStroke(Color.web("#1E293B"));
        gc.setLineWidth(2.5);
        gc.strokeLine(barX, barY, barX + scalePx, barY);
        gc.strokeLine(barX, barY - 4, barX, barY + 4);
        gc.strokeLine(barX + scalePx, barY - 4, barX + scalePx, barY + 4);

        gc.setFill(Color.web("#1E293B"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.0));
        gc.fillText("0", barX - 3, barY - 6);
        gc.fillText("100 km", barX + scalePx - 20, barY - 6);

        // North arrow
        double arrowX = w - 40.0;
        double arrowY = 35.0;
        gc.setFill(Color.web("#1E293B"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12.0));
        gc.fillText("N ↑", arrowX - 6, arrowY);
    }

    private void drawEpicenter(GraphicsContext gc) {
        ScreenPoint epiScreen = currentTransform.toScreen(0.0, 0.0);
        double ex = epiScreen.xPx();
        double ey = epiScreen.yPx();

        // Draw 5-point star
        drawStar(gc, ex, ey, 14.0, 6.0, Color.web("#DC2626"), Color.web("#7F1D1D"));

        // Label with fixed offset
        LabelOffset offset = FIXED_LABEL_OFFSETS.getOrDefault("EPICENTER", new LabelOffset(-130.0, -28.0, "RIGHT"));
        double lx = ex + offset.dx();
        double ly = ey + offset.dy();

        // Classic badge box
        String title = "★ Epicenter (M " + scenario.event().magnitude() + ")";
        String sub = String.format("%.2f°N, %.2f°W  (%s km)",
                scenario.event().epicenter().latitude(),
                Math.abs(scenario.event().epicenter().longitude()),
                scenario.event().depthKm());

        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.0));
        double boxW = 145.0;
        double boxH = 34.0;

        gc.setFill(Color.web("#FEF2F2", 0.92));
        gc.setStroke(Color.web("#DC2626"));
        gc.setLineWidth(1.2);
        gc.fillRoundRect(lx, ly, boxW, boxH, 4, 4);
        gc.strokeRoundRect(lx, ly, boxW, boxH, 4, 4);

        // Connecting tick line from box to epicenter
        gc.setStroke(Color.web("#DC2626", 0.7));
        gc.setLineWidth(1.0);
        gc.strokeLine(lx + boxW, ly + boxH / 2, ex - 14, ey);

        gc.setFill(Color.web("#991B1B"));
        gc.fillText(title, lx + 6, ly + 14);
        gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 9.5));
        gc.setFill(Color.web("#450A0A"));
        gc.fillText(sub, lx + 6, ly + 27);
    }

    private void drawReferenceLocations(GraphicsContext gc) {
        for (ReferenceLocation loc : scenario.locations()) {
            ProjectedPoint projPt = projection.project(loc.internalPoint());
            ScreenPoint sp = currentTransform.toScreen(projPt);
            double sx = sp.xPx();
            double sy = sp.yPx();

            ReferenceLocation.PeakIntensity intensity = loc.peakIntensity();
            String colorHex = intensity.colorHex();
            String roman = intensity.mmiRoman();
            double rounded = intensity.mmiDisplayRounded();

            // 1. Station location dot
            gc.setFill(Color.WHITE);
            gc.setStroke(Color.web("#1E293B"));
            gc.setLineWidth(1.5);
            gc.fillOval(sx - 4.5, sy - 4.5, 9.0, 9.0);
            gc.strokeOval(sx - 4.5, sy - 4.5, 9.0, 9.0);
            gc.setFill(Color.web("#1E293B"));
            gc.fillOval(sx - 2.0, sy - 2.0, 4.0, 4.0);

            // 2. Intensity badge & city label
            LabelOffset offset = FIXED_LABEL_OFFSETS.getOrDefault(loc.city(), new LabelOffset(14.0, -10.0, "LEFT"));
            double lx = sx + offset.dx();
            double ly = sy + offset.dy();

            // Connecting lead line
            gc.setStroke(Color.web("#64748B", 0.7));
            gc.setLineWidth(1.0);
            if ("RIGHT".equals(offset.align())) {
                gc.strokeLine(lx + 140.0, ly + 15.0, sx - 5.0, sy);
            } else {
                gc.strokeLine(lx, ly + 15.0, sx + 5.0, sy);
            }

            drawLocationBadge(gc, lx, ly, loc.city(), roman, rounded, colorHex, intensity.shakingDescription());
        }
    }

    private void drawLocationBadge(
            GraphicsContext gc,
            double x,
            double y,
            String city,
            String roman,
            double roundedMmi,
            String colorHex,
            String descriptor
    ) {
        double totalW = 142.0;
        double totalH = 32.0;
        double badgeW = 28.0;

        // Label outer background box (classic Windows etched card)
        gc.setFill(Color.web("#FFFFFF", 0.94));
        gc.setStroke(Color.web("#94A3B8"));
        gc.setLineWidth(1.0);
        gc.fillRoundRect(x, y, totalW, totalH, 3, 3);
        gc.strokeRoundRect(x, y, totalW, totalH, 3, 3);

        // MMI Color Tag / Box (similar to Shindo boxes in Japanese seismic viewers)
        Color mmiColor = Color.web(colorHex);
        gc.setFill(mmiColor);
        gc.setStroke(Color.web("#475569"));
        gc.setLineWidth(1.0);
        gc.fillRoundRect(x + 2, y + 2, badgeW, totalH - 4, 2, 2);
        gc.strokeRoundRect(x + 2, y + 2, badgeW, totalH - 4, 2, 2);

        // Roman numeral inside badge
        // Choose text color based on luminance
        double lum = 0.299 * mmiColor.getRed() + 0.587 * mmiColor.getGreen() + 0.114 * mmiColor.getBlue();
        gc.setFill(lum > 0.55 ? Color.BLACK : Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, roman.length() > 3 ? 9.0 : 11.0));
        double textX = x + (badgeW / 2.0) - (roman.length() * 3.2);
        gc.fillText(roman, Math.max(x + 4, textX), y + 18);

        // City name & Historical peak MMI label
        gc.setFill(Color.web("#0F172A"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        gc.fillText(city, x + badgeW + 6, y + 13);

        gc.setFill(Color.web("#475569"));
        gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 8.5));
        gc.fillText(String.format("Hist Peak MMI %.1f (%s)", roundedMmi, descriptor), x + badgeW + 6, y + 25);
    }

    private void drawStar(GraphicsContext gc, double cx, double cy, double rOuter, double rInner, Color fill, Color stroke) {
        int points = 5;
        double[] xPoints = new double[points * 2];
        double[] yPoints = new double[points * 2];
        double angleStep = Math.PI / points;
        double startAngle = -Math.PI / 2.0;

        for (int i = 0; i < points * 2; i++) {
            double r = (i % 2 == 0) ? rOuter : rInner;
            double angle = startAngle + i * angleStep;
            xPoints[i] = cx + r * Math.cos(angle);
            yPoints[i] = cy + r * Math.sin(angle);
        }

        gc.setFill(fill);
        gc.setStroke(stroke);
        gc.setLineWidth(1.5);
        gc.fillPolygon(xPoints, yPoints, points * 2);
        gc.strokePolygon(xPoints, yPoints, points * 2);
    }

    /**
     * Renders dynamic wavefronts from a {@link FrameState}.
     * Clears dynamic canvas. If wavefronts are available, draws them.
     * (Full animation connected in Stage 6).
     *
     * @param frame current replay frame snapshot
     */
    public void renderFrame(FrameState frame) {
        this.lastFrame = frame;
        GraphicsContext gc = dynamicCanvas.getGraphicsContext2D();
        double w = getWidth() > 0 ? getWidth() : BASELINE_VIEWPORT_WIDTH;
        double h = getHeight() > 0 ? getHeight() : BASELINE_VIEWPORT_HEIGHT;

        gc.clearRect(0, 0, w, h);

        if (frame == null || currentTransform == null) {
            return;
        }

        WavefrontRadii radii = frame.frontRadii();
        ScreenPoint epiScreen = currentTransform.toScreen(0.0, 0.0);
        double ex = epiScreen.xPx();
        double ey = epiScreen.yPx();

        // P-wave: dashed cyan circle (when surface arrival has occurred)
        if (radii.hasP()) {
            double rKm = radii.pRadiusKm();
            double rPx = currentTransform.toScreenRadius(rKm);
            gc.setStroke(Color.web("#06B6D4"));
            gc.setLineWidth(2.0);
            gc.setLineDashes(6.0, 4.0);
            gc.strokeOval(ex - rPx, ey - rPx, rPx * 2.0, rPx * 2.0);
            gc.setLineDashes((double[]) null);
        }

        // S-wave: solid orange circle (when surface arrival has occurred)
        if (radii.hasS()) {
            double rKm = radii.sRadiusKm();
            double rPx = currentTransform.toScreenRadius(rKm);
            gc.setStroke(Color.web("#F97316"));
            gc.setLineWidth(2.5);
            gc.strokeOval(ex - rPx, ey - rPx, rPx * 2.0, rPx * 2.0);
        }
    }

    public ViewportTransform currentTransform() {
        return currentTransform;
    }

    public AzimuthalEquidistantProjection projection() {
        return projection;
    }

    public BoundingBox projectedBounds() {
        return projectedBounds;
    }

    public ScreenPoint getEpicenterScreenPoint() {
        if (currentTransform == null) {
            redrawStaticMap();
        }
        return currentTransform.toScreen(0.0, 0.0);
    }

    public ScreenPoint getLocationScreenPoint(String cityName) {
        if (currentTransform == null) {
            redrawStaticMap();
        }
        for (ReferenceLocation loc : scenario.locations()) {
            if (loc.city().equalsIgnoreCase(cityName)) {
                ProjectedPoint p = projection.project(loc.internalPoint());
                return currentTransform.toScreen(p);
            }
        }
        throw new IllegalArgumentException("Unknown reference city: " + cityName);
    }
}
