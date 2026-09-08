package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.EarthquakeEvent;
import io.github.paracosms.calquake.core.FrameState;
import io.github.paracosms.calquake.core.HadleyKanamoriTauPModel;
import io.github.paracosms.calquake.core.MmiLegend;
import io.github.paracosms.calquake.core.ReferenceLocation;
import io.github.paracosms.calquake.core.ReplayController;
import io.github.paracosms.calquake.core.ReplayEngine;
import io.github.paracosms.calquake.core.Scenario;
import io.github.paracosms.calquake.core.TravelTimeModel;
import io.github.paracosms.calquake.data.CaliforniaOutline;
import io.github.paracosms.calquake.data.ScenarioLoader;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * Main JavaFX application for CalQuake Demo 0.
 * <p>
 * Implements Stage 5: Draw the complete static demo screen at the 1280x800 validation baseline
 * using classic desktop / Frutiger Aero utility aesthetic.
 */
public class CalQuakeApp extends Application {

    public static final double BASELINE_WIDTH = 1280.0;
    public static final double BASELINE_HEIGHT = 800.0;
    public static final double MIN_WIDTH = 1024.0;
    public static final double MIN_HEIGHT = 640.0;

    private Scenario scenario;
    private CaliforniaOutline outline;
    private ReplayController controller;
    private MapCanvasPane mapCanvasPane;
    private Stage lifecycleStage;
    private boolean windowInactive;
    private boolean wasPlayingBeforeDeactivation;

    // Controls & Readouts
    private Button playPauseButton;
    private Button restartButton;
    private Label elapsedDigitsLabel;
    private Label elapsedSubLabel;
    private Label hudStateLabel;
    private Label controlStateLabel;
    private Label controlTimeLabel;

    @Override
    public void init() {
        // Load default frozen scenario, outline, and models
        ScenarioLoader loader = new ScenarioLoader();
        this.scenario = loader.loadDefaultScenario();
        this.outline = CaliforniaOutline.loadDefault();

        TravelTimeModel model = new HadleyKanamoriTauPModel();
        ReplayEngine engine = ReplayEngine.create(scenario, model);
        this.controller = new ReplayController(scenario, engine);
    }

    private AnimationTimer animationTimer;

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        // 1. Top Header Bar
        VBox headerBar = buildHeaderBar();
        root.setTop(headerBar);

        // 2. Center: Map Canvas Viewport with Overlaid Top-Left Timer Box
        this.mapCanvasPane = new MapCanvasPane(scenario, outline);
        mapCanvasPane.getStyleClass().add("map-viewport-frame");

        StackPane centerStack = new StackPane();
        centerStack.getChildren().add(mapCanvasPane);

        // Timer HUD Box Overlay (matching reference photo in top-left)
        VBox timerHudBox = buildTimerHudOverlay();
        StackPane.setAlignment(timerHudBox, Pos.TOP_LEFT);
        StackPane.setMargin(timerHudBox, new Insets(16.0, 0, 0, 16.0));
        centerStack.getChildren().add(timerHudBox);

        root.setCenter(centerStack);

        // 3. Right Sidebar: Replay Controls, Reference Locations, MMI Legend
        VBox sidebar = buildSidebar();
        ScrollPane sidebarScroll = new ScrollPane(sidebar);
        sidebarScroll.setFitToWidth(true);
        sidebarScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sidebarScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sidebarScroll.setPrefWidth(390.0);
        sidebarScroll.setMinWidth(360.0);
        root.setRight(sidebarScroll);

        // 4. Bottom Status Bar (Classic Windows Sunken Panes)
        HBox statusBar = buildStatusBar();
        root.setBottom(statusBar);

        // Wire button events
        setupControlHandlers();
        updateTimeDisplays();

        // 5. Animation Timer for Replay Engine Loop
        this.animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (controller.isPlaying()) {
                    io.github.paracosms.calquake.core.FrameState frame = controller.tick();
                    updateTimeDisplays();
                    updateControlStates();
                    if (mapCanvasPane != null) {
                        mapCanvasPane.renderFrame(frame);
                    }
                } else if (controller.isFinished()) {
                    updateControlStates();
                }
            }
        };
        animationTimer.start();

        Scene scene = new Scene(root, BASELINE_WIDTH, BASELINE_HEIGHT);
        String cssPath = Objects.requireNonNull(getClass().getResource("/styles/calquake.css")).toExternalForm();
        scene.getStylesheets().add(cssPath);

        primaryStage.setTitle("CalQuake — M 7.1 Ridgecrest Earthquake Sequence Replay");
        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            if (animationTimer != null) {
                animationTimer.stop();
            }
        });

        setupWindowLifecycleHandlers(primaryStage);
        primaryStage.show();
        handleWindowActivityChanged();
    }

    @Override
    public void stop() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
        lifecycleStage = null;
    }

    private void setupWindowLifecycleHandlers(Stage stage) {
        this.lifecycleStage = stage;
        this.windowInactive = false;
        this.wasPlayingBeforeDeactivation = false;

        stage.focusedProperty().addListener((observable, oldValue, newValue) -> handleWindowActivityChanged());
        stage.iconifiedProperty().addListener((observable, oldValue, newValue) -> handleWindowActivityChanged());
    }

    private void handleWindowActivityChanged() {
        if (lifecycleStage == null) {
            return;
        }

        boolean inactive = !lifecycleStage.isFocused() || lifecycleStage.isIconified();
        if (inactive == windowInactive) {
            return;
        }

        windowInactive = inactive;
        if (inactive) {
            wasPlayingBeforeDeactivation = controller.isPlaying();
            if (wasPlayingBeforeDeactivation) {
                controller.pause();
                updateTimeDisplays();
                updateControlStates();
            }
            return;
        }

        boolean shouldResume = wasPlayingBeforeDeactivation;
        wasPlayingBeforeDeactivation = false;
        if (shouldResume) {
            controller.play();
        }
        updateTimeDisplays();
        updateControlStates();
        refreshMapAfterWindowActivation();
    }

    private void refreshMapAfterWindowActivation() {
        if (mapCanvasPane == null) {
            return;
        }

        Platform.runLater(() -> {
            if (lifecycleStage == null || !lifecycleStage.isFocused() || lifecycleStage.isIconified()) {
                return;
            }
            FrameState currentFrame = controller.currentFrame();
            mapCanvasPane.refresh(currentFrame);
            updateTimeDisplays();
            updateControlStates();
        });
    }

    private VBox buildHeaderBar() {
        VBox header = new VBox(3.0);
        header.getStyleClass().add("app-header");

        HBox titleRow = new HBox(12.0);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("CalQuake: M 7.1 Ridgecrest Earthquake Sequence Replay");
        titleLabel.getStyleClass().add("app-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge = new Label("Historical Peak MMI View");
        badge.getStyleClass().add("status-badge-paused");

        titleRow.getChildren().addAll(titleLabel, spacer, badge);

        EarthquakeEvent event = scenario.event();
        String metadata = String.format(
                "Origin UTC: %s  |  Magnitude: %.1f %s  |  Hypocenter: %.4f°N, %.4f°W  |  Depth: %.1f km  |  Event ID: %s",
                event.originUtc(),
                event.magnitude(),
                event.magnitudeType().toUpperCase(),
                event.epicenter().latitude(),
                Math.abs(event.epicenter().longitude()),
                event.depthKm(),
                event.id()
        );
        Label subLabel = new Label(metadata);
        subLabel.getStyleClass().add("app-subtitle");

        header.getChildren().addAll(titleRow, subLabel);
        return header;
    }

    private VBox buildTimerHudOverlay() {
        VBox box = new VBox(2.0);
        box.getStyleClass().add("timer-overlay-box");
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        box.setMouseTransparent(true);

        HBox topRow = new HBox(8.0);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("REPLAY ELAPSED TIME");
        title.getStyleClass().add("timer-subtext");

        this.hudStateLabel = new Label("PAUSED");
        hudStateLabel.getStyleClass().add("status-badge-paused");

        topRow.getChildren().addAll(title, hudStateLabel);

        this.elapsedDigitsLabel = new Label("00:00:00.00");
        elapsedDigitsLabel.getStyleClass().add("timer-digits");

        this.elapsedSubLabel = new Label("T + 0.0 s  (Max: 120.0 s)");
        elapsedSubLabel.getStyleClass().add("timer-subtext");

        box.getChildren().addAll(topRow, elapsedDigitsLabel, elapsedSubLabel);
        return box;
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(10.0);
        sidebar.setPadding(new Insets(10.0));
        sidebar.setStyle("-fx-background-color: #ECE9D8;");

        // Section 1: Replay Controls
        VBox controlsBox = buildControlsBox();

        // Section 2: Historical Peak MMI (Five Reference Locations)
        VBox locationsBox = buildLocationsBox();

        // Section 3: USGS ShakeMap MMI Legend
        VBox legendBox = buildLegendBox();

        sidebar.getChildren().addAll(controlsBox, locationsBox, legendBox);
        return sidebar;
    }

    private VBox buildControlsBox() {
        VBox box = new VBox(8.0);
        box.getStyleClass().add("group-box");

        Label title = new Label("Replay Controls (Initially Paused)");
        title.getStyleClass().add("group-box-title");

        HBox buttonsRow = new HBox(8.0);
        buttonsRow.setAlignment(Pos.CENTER_LEFT);

        this.playPauseButton = new Button("▶  Play");
        playPauseButton.getStyleClass().addAll("button", "button-primary");
        playPauseButton.setPrefWidth(95.0);

        this.restartButton = new Button("⏮  Restart");
        restartButton.getStyleClass().add("button");
        restartButton.setPrefWidth(95.0);

        buttonsRow.getChildren().addAll(playPauseButton, restartButton);

        VBox statusInfo = new VBox(2.0);
        this.controlStateLabel = new Label("State: PAUSED (Ready)");
        controlStateLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B;");

        this.controlTimeLabel = new Label("Elapsed: 0.00 s / 120.00 s");
        controlTimeLabel.setStyle("-fx-font-family: 'Consolas', monospace; -fx-text-fill: #475569;");

        statusInfo.getChildren().addAll(controlStateLabel, controlTimeLabel);

        box.getChildren().addAll(title, buttonsRow, statusInfo);
        return box;
    }

    private VBox buildLocationsBox() {
        VBox box = new VBox(6.0);
        box.getStyleClass().add("group-box");

        Label title = new Label("Five Reference Locations — Historical Peak MMI");
        title.getStyleClass().add("group-box-title");

        Label subtitle = new Label("Values from USGS Atlas ShakeMap v1 (Frozen replay ground truth):");
        subtitle.setStyle("-fx-font-size: 9.5px; -fx-text-fill: #64748B;");
        box.getChildren().addAll(title, subtitle);

        for (ReferenceLocation loc : scenario.locations()) {
            HBox row = new HBox(8.0);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("location-row");

            // MMI Badge
            String colorHex = loc.peakIntensity().colorHex();
            Rectangle badgeRect = new Rectangle(26.0, 20.0, Color.web(colorHex));
            badgeRect.setStroke(Color.web("#475569"));
            badgeRect.setArcWidth(3.0);
            badgeRect.setArcHeight(3.0);

            Label badgeText = new Label(loc.peakIntensity().mmiRoman());
            badgeText.setStyle("-fx-font-weight: bold; -fx-font-size: 9.5px; -fx-text-fill: #000000;");

            StackPane badgeStack = new StackPane(badgeRect, badgeText);
            badgeStack.setPrefSize(26.0, 20.0);

            VBox info = new VBox(1.0);
            Label cityLabel = new Label(loc.city());
            cityLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #0F172A;");

            String desc = String.format("Peak MMI: %.1f (%s)  |  GEOID: %s",
                    loc.peakIntensity().mmiDisplayRounded(),
                    loc.peakIntensity().shakingDescription(),
                    loc.geoid());
            Label descLabel = new Label(desc);
            descLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #475569;");

            info.getChildren().addAll(cityLabel, descLabel);
            HBox.setHgrow(info, Priority.ALWAYS);

            row.getChildren().addAll(badgeStack, info);
            box.getChildren().add(row);
        }

        return box;
    }

    private VBox buildLegendBox() {
        VBox box = new VBox(6.0);
        box.getStyleClass().add("group-box");

        Label title = new Label("USGS ShakeMap MMI Scale (Worden et al., 2012)");
        title.getStyleClass().add("group-box-title");

        GridPane grid = new GridPane();
        grid.getStyleClass().add("mmi-legend-grid");
        grid.setHgap(6.0);
        grid.setVgap(2.0);
        grid.setPadding(new Insets(4.0));

        int r = 0;
        for (MmiLegend.MmiBin bin : MmiLegend.ALL_BINS) {
            // Color Swatch
            Rectangle swatch = new Rectangle(20.0, 14.0, Color.web(bin.colorHex()));
            swatch.setStroke(Color.web("#94A3B8"));
            swatch.setStrokeWidth(0.8);
            swatch.setArcWidth(2.0);
            swatch.setArcHeight(2.0);

            Label romanLabel = new Label(bin.roman());
            romanLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 10px; -fx-min-width: 32px;");

            Label shakeLabel = new Label(bin.shakingDescriptor());
            shakeLabel.setStyle("-fx-font-size: 9.5px; -fx-text-fill: #1E293B;");

            Label damageLabel = new Label("(" + bin.damageDescriptor() + ")");
            damageLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #64748B;");

            grid.add(swatch, 0, r);
            grid.add(romanLabel, 1, r);
            grid.add(shakeLabel, 2, r);
            grid.add(damageLabel, 3, r);
            r++;
        }

        box.getChildren().addAll(title, grid);
        return box;
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox(8.0);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        Label p1 = new Label("Baseline: 1280×800 | Aspect Ratio: 1:1");
        p1.getStyleClass().add("status-pane");

        Label p2 = new Label("Model: Hadley-Kanamori (TauP 3.2.1)");
        p2.getStyleClass().add("status-pane");

        Label p3 = new Label("Outline: California cb_2020_20m (6 rings, 468 vertices)");
        p3.getStyleClass().add("status-pane");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label p4 = new Label("Stage 5 Static Screen: READY");
        p4.getStyleClass().add("status-pane");
        p4.setStyle("-fx-font-weight: bold; -fx-text-fill: #0D3B66;");

        bar.getChildren().addAll(p1, p2, p3, spacer, p4);
        return bar;
    }

    private void setupControlHandlers() {
        playPauseButton.setOnAction(e -> {
            controller.togglePlayPause();
            updateControlStates();
        });

        restartButton.setOnAction(e -> {
            controller.restart();
            wasPlayingBeforeDeactivation = false;
            updateControlStates();
            updateTimeDisplays();
            if (mapCanvasPane != null) {
                mapCanvasPane.renderFrame(null);
            }
        });
    }

    private void updateControlStates() {
        if (controller.isPlaying()) {
            playPauseButton.setText("⏸  Pause");
            hudStateLabel.setText("PLAYING");
            hudStateLabel.getStyleClass().setAll("status-badge-playing");
            controlStateLabel.setText("State: PLAYING");
        } else if (controller.isPaused()) {
            playPauseButton.setText("▶  Play");
            hudStateLabel.setText("PAUSED");
            hudStateLabel.getStyleClass().setAll("status-badge-paused");
            controlStateLabel.setText("State: PAUSED");
        } else if (controller.isFinished()) {
            playPauseButton.setText("▶  Play");
            hudStateLabel.setText("FINISHED");
            hudStateLabel.getStyleClass().setAll("status-badge-finished");
            controlStateLabel.setText("State: FINISHED (Require Restart)");
        }
    }

    private void updateTimeDisplays() {
        double elapsed = controller.elapsedSeconds();
        int minutes = (int) (elapsed / 60.0);
        int seconds = (int) (elapsed % 60.0);
        int centis = (int) Math.round((elapsed - Math.floor(elapsed)) * 100.0);
        if (centis >= 100) centis = 99;

        String formatted = String.format("00:%02d:%02d.%02d", minutes, seconds, centis);
        elapsedDigitsLabel.setText(formatted);
        elapsedSubLabel.setText(String.format("T + %.1f s  (Max: %.1f s)", elapsed, ReplayController.MAX_REPLAY_SECONDS));
        controlTimeLabel.setText(String.format("Elapsed: %.2f s / %.2f s", elapsed, ReplayController.MAX_REPLAY_SECONDS));
    }

    // Accessors for testing and verification
    public Scenario getScenario() {
        return scenario;
    }

    public CaliforniaOutline getOutline() {
        return outline;
    }

    public ReplayController getController() {
        return controller;
    }

    public MapCanvasPane getMapCanvasPane() {
        return mapCanvasPane;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
