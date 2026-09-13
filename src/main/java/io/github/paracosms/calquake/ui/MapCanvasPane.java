package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.ApplicationMode;
import io.github.paracosms.calquake.core.FrameState;
import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.IntensityDisplayMode;
import io.github.paracosms.calquake.core.IntensityStatus;
import io.github.paracosms.calquake.core.LocationIntensityState;
import io.github.paracosms.calquake.core.MapScenario;
import io.github.paracosms.calquake.core.MercatorProjection;
import io.github.paracosms.calquake.core.MercatorProjection.BoundingBox;
import io.github.paracosms.calquake.core.MercatorProjection.ProjectedPoint;
import io.github.paracosms.calquake.core.MercatorProjection.ScreenPoint;
import io.github.paracosms.calquake.core.MercatorProjection.ViewportTransform;
import io.github.paracosms.calquake.core.ReferenceLocation;
import io.github.paracosms.calquake.core.Scenario;
import io.github.paracosms.calquake.core.SimulationSite;
import io.github.paracosms.calquake.core.WavefrontRadii;
import io.github.paracosms.calquake.data.CaliforniaOutline;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.Locale;

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
 * Preserves strict 1:1 aspect ratio across resizing via {@link MercatorProjection}.
 */
public class MapCanvasPane extends Pane {

    public static final double DEFAULT_MARGIN_PX = 24.0;
    /**
     * Exact viewport width (890.0 px) allocated to the map pane by the 1280x800 application window layout
     * (1280.0 px window width - 390.0 px sidebar scroll width).
     */
    public static final double BASELINE_VIEWPORT_WIDTH = 890.0;
    /**
     * Exact viewport height (719.0 px) allocated to the map pane by the 1280x800 application window layout
     * (800.0 px window height - 53.0 px header bar - 28.0 px status bar).
     */
    public static final double BASELINE_VIEWPORT_HEIGHT = 719.0;

    public static final double MIN_ZOOM = 0.5;
    public static final double MAX_ZOOM = 25.0;
    public static final double DEFAULT_ZOOM_STEP = 1.25;
    public static final double DEFAULT_PAN_STEP_PX = 60.0;

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

    private MapScenario mapScenario;
    private CaliforniaOutline outline;
    private MercatorProjection projection;
    private BoundingBox projectedBounds;
    private ViewportTransform currentTransform;

    private double zoomFactor = 1.0;
    private double centerKmX;
    private double centerKmY;

    private double dragStartX;
    private double dragStartY;
    private boolean isDragging;

    private double lastWidth = -1.0;
    private double lastHeight = -1.0;
    private FrameState lastFrame;
    private final Tooltip mapTooltip;
    private ApplicationMode applicationMode = ApplicationMode.SIMULATION;

    public MapCanvasPane(MapScenario mapScenario, CaliforniaOutline outline) {
        this.mapScenario = Objects.requireNonNull(mapScenario, "mapScenario cannot be null");
        this.outline = Objects.requireNonNull(outline, "outline cannot be null");

        this.projection = MercatorProjection.californiaDefault();
        this.projectedBounds = outline.computeProjectedBoundingBox(projection);
        this.centerKmX = projectedBounds.centerXKm();
        this.centerKmY = projectedBounds.centerYKm();

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

        this.mapTooltip = new Tooltip();
        mapTooltip.setShowDelay(Duration.millis(80));
        mapTooltip.setHideDelay(Duration.millis(150));
        setupMouseInteractions();

        updateTransform(BASELINE_VIEWPORT_WIDTH, BASELINE_VIEWPORT_HEIGHT);
    }

    public MapCanvasPane(Scenario scenario, CaliforniaOutline outline) {
        this(MapScenario.fromLegacyScenario(scenario), outline);
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
     * Updates the bound MapScenario and refreshes the map.
     */
    public void setMapScenario(MapScenario mapScenario) {
        this.mapScenario = Objects.requireNonNull(mapScenario, "mapScenario cannot be null");
        redrawStaticMap();
    }

    /**
     * Legacy adapter updating the bound scenario and refreshing the map.
     */
    public void setScenario(Scenario scenario) {
        setMapScenario(MapScenario.fromLegacyScenario(scenario));
    }

    public MapScenario getMapScenario() {
        return mapScenario;
    }

    public void setApplicationMode(ApplicationMode applicationMode) {
        this.applicationMode = Objects.requireNonNull(applicationMode, "applicationMode cannot be null");
        redrawStaticMap();
    }

    public ApplicationMode getApplicationMode() {
        return applicationMode;
    }

    /**
     * Redraws static geometry onto {@code staticCanvas}.
     * Executed when size, scenario, or window lifecycle state changes.
     */
    public void redrawStaticMap() {
        double w = getWidth() > 0 ? getWidth() : BASELINE_VIEWPORT_WIDTH;
        double h = getHeight() > 0 ? getHeight() : BASELINE_VIEWPORT_HEIGHT;
        synchronizeCanvasDimensions(w, h);

        updateTransform(w, h);
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

        // 3. Epicenter marker and label
        drawEpicenter(gc);

        // 4. Simulation Site Station Dots (neutral base map markers)
        drawSiteDots(gc);
    }

    private void updateTransform(double w, double h) {
        double availW = w - 2.0 * DEFAULT_MARGIN_PX;
        double availH = h - 2.0 * DEFAULT_MARGIN_PX;
        if (availW <= 0 || availH <= 0) {
            return;
        }

        double baseScale = Math.min(availW / projectedBounds.widthKm(), availH / projectedBounds.heightKm());
        double scale = baseScale * zoomFactor;

        double originScreenX = (w / 2.0) - centerKmX * scale;
        double originScreenY = (h / 2.0) - centerKmY * scale;

        this.currentTransform = new ViewportTransform(scale, originScreenX, originScreenY);
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

        if (currentTransform == null) return;

        // Invert viewport corners to lat/lon so grid lines render across the visible view
        ProjectedPoint pTopLeft = currentTransform.toProjected(0, 0);
        ProjectedPoint pBottomRight = currentTransform.toProjected(w, h);

        GeoPoint geoTopLeft = projection.unproject(pTopLeft);
        GeoPoint geoBottomRight = projection.unproject(pBottomRight);

        double minLon = Math.min(geoTopLeft.longitude(), geoBottomRight.longitude()) - 2.0;
        double maxLon = Math.max(geoTopLeft.longitude(), geoBottomRight.longitude()) + 2.0;
        double minLat = Math.min(geoTopLeft.latitude(), geoBottomRight.latitude()) - 2.0;
        double maxLat = Math.max(geoTopLeft.latitude(), geoBottomRight.latitude()) + 2.0;

        minLon = Math.max(-180.0, Math.floor(minLon / 2.0) * 2.0);
        maxLon = Math.min(180.0, Math.ceil(maxLon / 2.0) * 2.0);
        minLat = Math.max(-85.0, Math.floor(minLat / 2.0) * 2.0);
        maxLat = Math.min(85.0, Math.ceil(maxLat / 2.0) * 2.0);

        for (double lon = minLon; lon <= maxLon; lon += 2.0) {
            ProjectedPoint p = projection.project(new GeoPoint(MercatorProjection.DEFAULT_CENTER_LATITUDE, lon));
            ScreenPoint sp = currentTransform.toScreen(p);
            if (sp.xPx() >= -10 && sp.xPx() <= w + 10) {
                gc.strokeLine(sp.xPx(), 0, sp.xPx(), h);
            }
        }
        for (double lat = minLat; lat <= maxLat; lat += 2.0) {
            ProjectedPoint p = projection.project(new GeoPoint(lat, MercatorProjection.DEFAULT_CENTER_LONGITUDE));
            ScreenPoint sp = currentTransform.toScreen(p);
            if (sp.yPx() >= -10 && sp.yPx() <= h + 10) {
                gc.strokeLine(0, sp.yPx(), w, sp.yPx());
            }
        }
    }

    private void drawEpicenter(GraphicsContext gc) {
        ScreenPoint epiScreen = currentTransform.toScreen(projection.project(mapScenario.event().epicenter()));
        double ex = epiScreen.xPx();
        double ey = epiScreen.yPx();

        // Draw 5-point star
        drawStar(gc, ex, ey, 14.0, 6.0, Color.web("#DC2626"), Color.web("#7F1D1D"));

        // Omit red info box in simulation mode
        if (applicationMode == ApplicationMode.SIMULATION) {
            return;
        }

        // Label with fixed offset
        LabelOffset offset = FIXED_LABEL_OFFSETS.getOrDefault("EPICENTER", new LabelOffset(-130.0, -28.0, "RIGHT"));
        double lx = ex + offset.dx();
        double ly = ey + offset.dy();

        // Classic badge box
        String title = "★ Epicenter (M " + mapScenario.event().magnitude() + ")";
        String sub = String.format("%.2f°N, %.2f°W  (%s km)",
                mapScenario.event().epicenter().latitude(),
                Math.abs(mapScenario.event().epicenter().longitude()),
                mapScenario.event().depthKm());

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

    private void drawSiteDots(GraphicsContext gc) {
        for (SimulationSite site : mapScenario.sites()) {
            ProjectedPoint projPt = projection.project(site.coordinates());
            ScreenPoint sp = currentTransform.toScreen(projPt);
            double sx = sp.xPx();
            double sy = sp.yPx();

            // Station location dot (white circle with dark border and inner center dot)
            gc.setFill(Color.WHITE);
            gc.setStroke(Color.web("#1E293B"));
            gc.setLineWidth(1.5);
            gc.fillOval(sx - 4.5, sy - 4.5, 9.0, 9.0);
            gc.strokeOval(sx - 4.5, sy - 4.5, 9.0, 9.0);
            gc.setFill(Color.web("#1E293B"));
            gc.fillOval(sx - 2.0, sy - 2.0, 4.0, 4.0);
        }
    }

    private void drawRevealedIntensityBadges(GraphicsContext gc, FrameState frame) {
        if (frame == null || frame.locationIntensities() == null) {
            return;
        }
        double badgeSize = 24.0;
        for (LocationIntensityState state : frame.locationIntensities()) {
            if (!state.isRevealed()) {
                continue;
            }
            ProjectedPoint projPt = projection.project(state.internalPoint());
            ScreenPoint sp = currentTransform.toScreen(projPt);
            double sx = sp.xPx();
            double sy = sp.yPx();

            // Display MMI icon directly centered on the city dot
            double lx = sx - (badgeSize / 2.0);
            double ly = sy - (badgeSize / 2.0);

            drawLocationBadge(gc, lx, ly, state.mmiRoman(), state.colorHex(), badgeSize);
        }
    }

    private void drawLocationBadge(
            GraphicsContext gc,
            double x,
            double y,
            String roman,
            String colorHex
    ) {
        drawLocationBadge(gc, x, y, roman, colorHex, 24.0);
    }

    private void drawLocationBadge(
            GraphicsContext gc,
            double x,
            double y,
            String roman,
            String colorHex,
            double badgeSize
    ) {
        try {
            Image icon = MmiIconLoader.getIcon(roman);
            if (icon != null && !icon.isError()) {
                gc.drawImage(icon, x, y, badgeSize, badgeSize);
                return;
            }
        } catch (Exception ignored) {
            // Fall back to procedural drawing if icon resource is unavailable
        }

        // MMI Color Square (the colored square is the only display)
        Color mmiColor = Color.web(colorHex);
        gc.setFill(mmiColor);
        gc.setStroke(Color.web("#475569"));
        gc.setLineWidth(1.0);
        gc.fillRoundRect(x, y, badgeSize, badgeSize, 2, 2);
        gc.strokeRoundRect(x, y, badgeSize, badgeSize, 2, 2);

        // Roman numeral inside colored square
        double lum = 0.299 * mmiColor.getRed() + 0.587 * mmiColor.getGreen() + 0.114 * mmiColor.getBlue();
        gc.setFill(lum > 0.55 ? Color.BLACK : Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, roman.length() > 3 ? 8.5 : 10.5));
        double textX = x + (badgeSize / 2.0) - (roman.length() * 3.0);
        gc.fillText(roman, Math.max(x + 2, textX), y + 15.0);
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
     * Dashed cyan circle with low-opacity fill for P-wave,
     * solid orange circle with low-opacity fill for S-wave.
     * Radii are scaled with the viewport transform and strictly clipped to viewport bounds.
     *
     * @param frame current replay frame snapshot
     */
    public void renderFrame(FrameState frame) {
        this.lastFrame = frame;
        double w = getWidth() > 0 ? getWidth() : BASELINE_VIEWPORT_WIDTH;
        double h = getHeight() > 0 ? getHeight() : BASELINE_VIEWPORT_HEIGHT;
        synchronizeCanvasDimensions(w, h);

        GraphicsContext gc = dynamicCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        if (currentTransform == null) {
            redrawStaticMap();
        }

        if (frame == null || currentTransform == null) {
            return;
        }

        WavefrontRadii radii = frame.frontRadii();

        gc.save();
        gc.beginPath();
        gc.rect(0, 0, w, h);
        gc.clip();

        // P-wave: low-opacity cyan fill with dashed cyan moving outline (when surface arrival has occurred)
        if (radii.hasP()) {
            double rKm = radii.pRadiusKm();
            renderWavefrontRing(gc, frame.epicenter(), rKm,
                    Color.web("#06B6D4", 0.15), Color.web("#06B6D4"), 2.0, new double[]{6.0, 4.0});
        }

        // S-wave: low-opacity orange fill with solid orange moving outline (when surface arrival has occurred)
        if (radii.hasS()) {
            double rKm = radii.sRadiusKm();
            renderWavefrontRing(gc, frame.epicenter(), rKm,
                    Color.web("#F97316", 0.15), Color.web("#F97316"), 2.5, null);
        }

        // Draw revealed MMI badges on dynamic canvas when S-wave arrival has occurred
        drawRevealedIntensityBadges(gc, frame);

        gc.restore();
    }

    private void renderWavefrontRing(GraphicsContext gc, GeoPoint epicenter, double radiusKm,
                                     Color fill, Color stroke, double strokeWidth, double[] dashes) {
        List<ProjectedPoint> points = projection.geodesicCirclePoints(epicenter, radiusKm, 48);
        if (points.isEmpty()) {
            return;
        }

        gc.beginPath();
        ScreenPoint first = currentTransform.toScreen(points.get(0));
        gc.moveTo(first.xPx(), first.yPx());
        for (int i = 1; i < points.size(); i++) {
            ScreenPoint pt = currentTransform.toScreen(points.get(i));
            gc.lineTo(pt.xPx(), pt.yPx());
        }
        gc.closePath();

        gc.setFill(fill);
        gc.fill();

        gc.setStroke(stroke);
        gc.setLineWidth(strokeWidth);
        if (dashes != null) {
            gc.setLineDashes(dashes);
        } else {
            gc.setLineDashes((double[]) null);
        }
        gc.stroke();
        gc.setLineDashes((double[]) null);
    }

    public Canvas getDynamicCanvas() {
        return dynamicCanvas;
    }

    public Canvas getStaticCanvas() {
        return staticCanvas;
    }

    public FrameState getLastFrame() {
        return lastFrame;
    }

    public ViewportTransform currentTransform() {
        return currentTransform;
    }

    public MercatorProjection projection() {
        return projection;
    }

    public BoundingBox projectedBounds() {
        return projectedBounds;
    }

    public ScreenPoint getEpicenterScreenPoint() {
        if (currentTransform == null) {
            redrawStaticMap();
        }
        return currentTransform.toScreen(projection.project(mapScenario.event().epicenter()));
    }

    public ScreenPoint getLocationScreenPoint(String cityName) {
        if (currentTransform == null) {
            redrawStaticMap();
        }
        for (SimulationSite site : mapScenario.sites()) {
            if (site.displayName().equalsIgnoreCase(cityName) || site.id().equalsIgnoreCase(cityName)) {
                ProjectedPoint p = projection.project(site.coordinates());
                return currentTransform.toScreen(p);
            }
        }
        throw new IllegalArgumentException("Unknown simulation site: " + cityName);
    }

    public Tooltip getMapTooltip() {
        return mapTooltip;
    }

    private void setupMouseInteractions() {
        setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                dragStartX = e.getX();
                dragStartY = e.getY();
                isDragging = false;
            }
        });

        setOnMouseDragged(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                double dx = e.getX() - dragStartX;
                double dy = e.getY() - dragStartY;
                if (!isDragging && (Math.abs(dx) > 2.0 || Math.abs(dy) > 2.0)) {
                    isDragging = true;
                    setCursor(Cursor.CLOSED_HAND);
                    mapTooltip.hide();
                }
                if (isDragging) {
                    pan(dx, dy);
                    dragStartX = e.getX();
                    dragStartY = e.getY();
                }
            }
        });

        setOnMouseReleased(e -> {
            if (isDragging) {
                isDragging = false;
                setCursor(Cursor.DEFAULT);
            }
        });

        setOnScroll(e -> {
            double deltaY = e.getDeltaY();
            if (deltaY != 0) {
                double factor = deltaY > 0 ? 1.15 : (1.0 / 1.15);
                zoom(factor, e.getX(), e.getY());
                e.consume();
            }
        });

        setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                zoom(DEFAULT_ZOOM_STEP, e.getX(), e.getY());
            }
        });

        setOnMouseMoved(e -> {
            if (isDragging || currentTransform == null) return;
            double mx = e.getX();
            double my = e.getY();

            // Check simulation sites
            for (SimulationSite site : mapScenario.sites()) {
                ScreenPoint sp = currentTransform.toScreen(projection.project(site.coordinates()));
                double dx = mx - sp.xPx();
                double dy = my - sp.yPx();
                if (dx * dx + dy * dy <= 256.0) { // 16px radius
                    LocationIntensityState locState = findSiteState(site.id());
                    mapTooltip.setText(buildSiteTooltipText(site, locState));
                    try {
                        if (getScene() != null && getScene().getWindow() != null && !mapTooltip.isShowing()) {
                            mapTooltip.show(this, e.getScreenX() + 12, e.getScreenY() + 12);
                        }
                    } catch (Exception ignored) {}
                    return;
                }
            }

            // Check epicenter
            ScreenPoint epi = currentTransform.toScreen(projection.project(mapScenario.event().epicenter()));
            double edx = mx - epi.xPx();
            double edy = my - epi.yPx();
            if (edx * edx + edy * edy <= 256.0) {
                mapTooltip.setText(String.format(Locale.US,
                        "★ Epicenter\nMagnitude: M %.1f\nDepth: %.1f km\nLocation: %.4f°N, %.4f°W",
                        mapScenario.event().magnitude(),
                        mapScenario.event().depthKm(),
                        mapScenario.event().epicenter().latitude(),
                        Math.abs(mapScenario.event().epicenter().longitude())));
                try {
                    if (getScene() != null && getScene().getWindow() != null && !mapTooltip.isShowing()) {
                        mapTooltip.show(this, e.getScreenX() + 12, e.getScreenY() + 12);
                    }
                } catch (Exception ignored) {}
                return;
            }

            mapTooltip.hide();
        });

        setOnMouseExited(e -> mapTooltip.hide());
    }

    public void zoomIn() {
        double w = getWidth() > 0 ? getWidth() : BASELINE_VIEWPORT_WIDTH;
        double h = getHeight() > 0 ? getHeight() : BASELINE_VIEWPORT_HEIGHT;
        zoom(DEFAULT_ZOOM_STEP, w / 2.0, h / 2.0);
    }

    public void zoomOut() {
        double w = getWidth() > 0 ? getWidth() : BASELINE_VIEWPORT_WIDTH;
        double h = getHeight() > 0 ? getHeight() : BASELINE_VIEWPORT_HEIGHT;
        zoom(1.0 / DEFAULT_ZOOM_STEP, w / 2.0, h / 2.0);
    }

    public void zoom(double factor, double pivotXPx, double pivotYPx) {
        double oldZoom = zoomFactor;
        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoomFactor * factor));
        if (Math.abs(newZoom - oldZoom) < 1e-6) {
            return;
        }

        double w = getWidth() > 0 ? getWidth() : BASELINE_VIEWPORT_WIDTH;
        double h = getHeight() > 0 ? getHeight() : BASELINE_VIEWPORT_HEIGHT;
        if (currentTransform == null) {
            updateTransform(w, h);
        }

        double oldScale = currentTransform.scalePxPerKm();
        double pivotKmX = (pivotXPx - currentTransform.originScreenXPx()) / oldScale;
        double pivotKmY = (pivotYPx - currentTransform.originScreenYPx()) / oldScale;

        double availW = w - 2.0 * DEFAULT_MARGIN_PX;
        double availH = h - 2.0 * DEFAULT_MARGIN_PX;
        double baseScale = Math.min(availW / projectedBounds.widthKm(), availH / projectedBounds.heightKm());
        double newScale = baseScale * newZoom;

        this.zoomFactor = newZoom;
        this.centerKmX = pivotKmX + (w / 2.0 - pivotXPx) / newScale;
        this.centerKmY = pivotKmY + (h / 2.0 - pivotYPx) / newScale;

        redrawStaticMap();
        if (lastFrame != null) {
            renderFrame(lastFrame);
        }
    }

    public void pan(double deltaXPx, double deltaYPx) {
        double w = getWidth() > 0 ? getWidth() : BASELINE_VIEWPORT_WIDTH;
        double h = getHeight() > 0 ? getHeight() : BASELINE_VIEWPORT_HEIGHT;
        if (currentTransform == null) {
            updateTransform(w, h);
        }

        double scale = currentTransform.scalePxPerKm();
        this.centerKmX -= deltaXPx / scale;
        this.centerKmY -= deltaYPx / scale;

        redrawStaticMap();
        if (lastFrame != null) {
            renderFrame(lastFrame);
        }
    }

    public void panUp() {
        pan(0.0, DEFAULT_PAN_STEP_PX);
    }

    public void panDown() {
        pan(0.0, -DEFAULT_PAN_STEP_PX);
    }

    public void panLeft() {
        pan(DEFAULT_PAN_STEP_PX, 0.0);
    }

    public void panRight() {
        pan(-DEFAULT_PAN_STEP_PX, 0.0);
    }

    public void resetView() {
        this.zoomFactor = 1.0;
        this.centerKmX = projectedBounds.centerXKm();
        this.centerKmY = projectedBounds.centerYKm();

        redrawStaticMap();
        if (lastFrame != null) {
            renderFrame(lastFrame);
        }
    }

    public double getZoomFactor() {
        return zoomFactor;
    }

    public double getCenterKmX() {
        return centerKmX;
    }

    public double getCenterKmY() {
        return centerKmY;
    }

    private LocationIntensityState findSiteState(String siteId) {
        if (lastFrame == null || lastFrame.locationIntensities() == null) return null;
        for (LocationIntensityState state : lastFrame.locationIntensities()) {
            if (state.site().id().equals(siteId)) return state;
        }
        return null;
    }

    private String buildSiteTooltipText(SimulationSite site, LocationIntensityState locState) {
        StringBuilder sb = new StringBuilder(site.displayName());
        if (locState != null) {
            String modePhrase = locState.displayMode() == IntensityDisplayMode.CURRENT_SHAKING
                    ? IntensityDisplayMode.CURRENT_SHAKING.label()
                    : IntensityDisplayMode.MAXIMUM_REACHED.label();
            sb.append("\n").append(modePhrase).append(": ");
            if (locState.status() == IntensityStatus.NOT_ARRIVED) {
                sb.append("Not arrived");
            } else if (locState.status() == IntensityStatus.SHAKING_ENDED) {
                sb.append("Shaking ended");
            } else if (locState.isRevealed()) {
                sb.append(locState.mmiRoman()).append(" (").append(locState.shakingDescription()).append(")");
                if (locState.currentMmi().isPresent()) {
                    sb.append(String.format(Locale.US, " [MMI %.1f]", locState.currentMmi().getAsDouble()));
                }
            } else {
                sb.append(locState.status());
            }

            if (locState.currentPgvCmPerSecond().isPresent()) {
                sb.append(String.format(Locale.US, "\nPGV: %.2f cm/s", locState.currentPgvCmPerSecond().getAsDouble()));
            }
            sb.append(String.format(Locale.US, "\nDistance: %.1f km | S-arrival: %.1f s",
                    locState.distanceKm(), locState.sArrivalTimeSeconds()));
        }
        return sb.toString();
    }
}
