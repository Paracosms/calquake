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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
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
    private Throwable startupError;

    // Header & Mode Controls
    private MenuBar menuBar;
    private Menu calQuakeMenu;
    private Menu modeMenu;
    private MenuItem replayMenuItem;
    private Menu settingsMenu;
    private Button settingsButton;

    // Event Selector
    private ComboBox<String> eventSelector;

    // Controls & Readouts
    private Button playPauseButton;
    private Button restartButton;
    private Label elapsedDigitsLabel;
    private Label elapsedSubLabel;
    private Label hudStateLabel;
    private Label controlStateLabel;
    private Label controlTimeLabel;
    private Label statusReplayLabel;
    private AnimationTimer animationTimer;

    public CalQuakeApp() {
    }

    public CalQuakeApp(Scenario scenario, CaliforniaOutline outline, ReplayController controller) {
        this.scenario = scenario;
        this.outline = outline;
        this.controller = controller;
    }

    @Override
    public void init() {
        if (this.scenario != null && this.outline != null && this.controller != null) {
            return;
        }
        try {
            // Load default frozen scenario, outline, and models
            if (this.scenario == null) {
                ScenarioLoader loader = new ScenarioLoader();
                this.scenario = loader.loadDefaultScenario();
            }
            if (this.outline == null) {
                this.outline = CaliforniaOutline.loadDefault();
            }
            if (this.controller == null) {
                TravelTimeModel model = new HadleyKanamoriTauPModel();
                ReplayEngine engine = ReplayEngine.create(scenario, model);
                this.controller = new ReplayController(scenario, engine);
            }
        } catch (Throwable t) {
            this.startupError = t;
        }
    }

    @Override
    public void start(Stage primaryStage) {
        if (startupError != null) {
            Scene errorScene = buildStartupErrorScene(primaryStage, startupError);
            primaryStage.setTitle("CalQuake — Startup Error");
            primaryStage.setScene(errorScene);
            primaryStage.show();
            return;
        }

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        // 1. Top Header Bar
        HBox headerBar = buildHeaderBar();
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

        primaryStage.setTitle("CalQuake");
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
        applyWindowActivityState(inactive);
    }

    void applyWindowActivityState(boolean inactive) {
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

    private HBox buildHeaderBar() {
        HBox header = new HBox(8.0);
        header.getStyleClass().add("app-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMinHeight(28.0);
        header.setPrefHeight(28.0);
        header.setMaxHeight(28.0);

        // Top-level application header: Mode and Settings controls (Frutiger Aero / Windows 7 style)
        this.menuBar = new MenuBar();
        menuBar.getStyleClass().add("app-menu-bar");

        // 1. Mode menu (dropdown with Replay only)
        this.modeMenu = new Menu("Mode");
        this.replayMenuItem = new MenuItem("Replay");
        modeMenu.getItems().add(replayMenuItem);

        // 2. Settings menu (placeholder for later work)
        this.settingsMenu = new Menu("Settings");
        MenuItem settingsItem = new MenuItem("Settings...");
        settingsItem.setDisable(true);
        settingsMenu.getItems().add(settingsItem);

        // Retain calQuakeMenu reference for testing compatibility
        this.calQuakeMenu = new Menu("CalQuake");

        // Nonfunctional settings button kept for backward compatibility if queried
        this.settingsButton = new Button("Settings");
        settingsButton.getStyleClass().add("button");
        settingsButton.setOnAction(e -> {});

        menuBar.getMenus().addAll(modeMenu, settingsMenu);
        HBox.setHgrow(menuBar, Priority.ALWAYS);
        header.getChildren().add(menuBar);
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
        sidebar.setStyle("-fx-background-color: #F0F3F7;");

        // Section 1: Replay Controls
        VBox controlsBox = buildControlsBox();

        // Section 2: Compact Event Selector (replaces Five Reference Locations)
        VBox eventBox = buildEventSelectorBox();

        // Section 3: USGS ShakeMap MMI Legend
        VBox legendBox = buildLegendBox();

        sidebar.getChildren().addAll(controlsBox, eventBox, legendBox);
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

        // Wavefront legend indicators
        VBox frontLegendBox = new VBox(4.0);
        frontLegendBox.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 6px; -fx-border-color: #CBD5E1; -fx-border-width: 1px; -fx-border-radius: 3px;");

        Label frontTitle = new Label("Wavefront Fronts (TauP Hadley-Kanamori):");
        frontTitle.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #475569;");

        HBox pRow = new HBox(6.0);
        pRow.setAlignment(Pos.CENTER_LEFT);
        javafx.scene.shape.Line pLine = new javafx.scene.shape.Line(0, 0, 22, 0);
        pLine.setStroke(Color.web("#06B6D4"));
        pLine.setStrokeWidth(2.0);
        pLine.getStrokeDashArray().addAll(6.0, 4.0);
        Label pText = new Label("P-Wave: Dashed cyan circle (Compressional)");
        pText.setStyle("-fx-font-size: 9px; -fx-text-fill: #0E7490; -fx-font-weight: bold;");
        pRow.getChildren().addAll(pLine, pText);

        HBox sRow = new HBox(6.0);
        sRow.setAlignment(Pos.CENTER_LEFT);
        javafx.scene.shape.Line sLine = new javafx.scene.shape.Line(0, 0, 22, 0);
        sLine.setStroke(Color.web("#F97316"));
        sLine.setStrokeWidth(2.5);
        Label sText = new Label("S-Wave: Solid orange circle (Shear)");
        sText.setStyle("-fx-font-size: 9px; -fx-text-fill: #C2410C; -fx-font-weight: bold;");
        sRow.getChildren().addAll(sLine, sText);

        frontLegendBox.getChildren().addAll(frontTitle, pRow, sRow);

        box.getChildren().addAll(title, buttonsRow, statusInfo, frontLegendBox);
        return box;
    }

    private VBox buildEventSelectorBox() {
        VBox box = new VBox(6.0);
        box.getStyleClass().add("group-box");

        Label title = new Label("Event Selector");
        title.getStyleClass().add("group-box-title");

        Label subtitle = new Label("Select earthquake sequence:");
        subtitle.setStyle("-fx-font-size: 9.5px; -fx-text-fill: #64748B;");

        this.eventSelector = new ComboBox<>();
        eventSelector.getItems().addAll("Ridgecrest", "Northridge");
        eventSelector.setValue("Ridgecrest");
        eventSelector.setMaxWidth(Double.MAX_VALUE);
        eventSelector.getStyleClass().add("event-selector");

        box.getChildren().addAll(title, subtitle, eventSelector);
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

        this.statusReplayLabel = new Label("Replay: READY (0.00s / 120.00s)");
        statusReplayLabel.getStyleClass().add("status-pane");
        statusReplayLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0D3B66;");

        bar.getChildren().addAll(p1, p2, p3, spacer, statusReplayLabel);
        return bar;
    }

    private void setupControlHandlers() {
        playPauseButton.setOnAction(e -> {
            controller.togglePlayPause();
            updateControlStates();
            updateTimeDisplays();
            if (mapCanvasPane != null) {
                mapCanvasPane.renderFrame(controller.currentFrame());
            }
        });

        restartButton.setOnAction(e -> {
            controller.restart();
            wasPlayingBeforeDeactivation = false;
            updateControlStates();
            updateTimeDisplays();
            if (mapCanvasPane != null) {
                mapCanvasPane.renderFrame(controller.currentFrame());
            }
        });
    }

    void updateControlStates() {
        double elapsed = controller.elapsedSeconds();
        if (controller.isPlaying()) {
            playPauseButton.setDisable(false);
            playPauseButton.setText("⏸  Pause");
            hudStateLabel.setText("PLAYING");
            hudStateLabel.getStyleClass().setAll("status-badge-playing");
            controlStateLabel.setText("State: PLAYING");
            if (statusReplayLabel != null) {
                statusReplayLabel.setText(String.format("Replay: PLAYING (T + %.1f s)", elapsed));
            }
        } else if (controller.isPaused()) {
            playPauseButton.setDisable(false);
            playPauseButton.setText("▶  Play");
            hudStateLabel.setText("PAUSED");
            hudStateLabel.getStyleClass().setAll("status-badge-paused");
            if (elapsed == 0.0) {
                controlStateLabel.setText("State: PAUSED (Ready)");
                if (statusReplayLabel != null) {
                    statusReplayLabel.setText("Replay: READY (0.00s / 120.00s)");
                }
            } else {
                controlStateLabel.setText("State: PAUSED");
                if (statusReplayLabel != null) {
                    statusReplayLabel.setText(String.format("Replay: PAUSED (T + %.1f s)", elapsed));
                }
            }
        } else if (controller.isFinished()) {
            playPauseButton.setDisable(true);
            playPauseButton.setText("▶  Play");
            hudStateLabel.setText("FINISHED");
            hudStateLabel.getStyleClass().setAll("status-badge-finished");
            controlStateLabel.setText("State: FINISHED (Require Restart)");
            if (statusReplayLabel != null) {
                statusReplayLabel.setText("Replay: FINISHED (Require Restart)");
            }
        }
    }

    void updateTimeDisplays() {
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

    Scene buildStartupErrorScene(Stage stage, Throwable error) {
        VBox root = new VBox(16.0);
        root.setPadding(new Insets(24.0));
        root.setStyle("-fx-background-color: #F0F3F7; -fx-font-family: 'Segoe UI', Tahoma, sans-serif;");

        Label heading = new Label("⚠  CalQuake Startup Error");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #991B1B;");

        Label desc = new Label("Failed to initialize offline replay resources:\n" +
                (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName()));
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12px; -fx-text-fill: #1E293B;");

        java.io.StringWriter sw = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(sw));
        javafx.scene.control.TextArea stackArea = new javafx.scene.control.TextArea(sw.toString());
        stackArea.setEditable(false);
        stackArea.setWrapText(false);
        stackArea.setPrefRowCount(10);
        VBox.setVgrow(stackArea, Priority.ALWAYS);

        Button exitBtn = new Button("Exit");
        exitBtn.setOnAction(e -> stage.close());
        exitBtn.setPrefWidth(90.0);

        root.getChildren().addAll(heading, desc, stackArea, exitBtn);
        return new Scene(root, 640.0, 400.0);
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

    public MenuBar getMenuBar() {
        return menuBar;
    }

    public Menu getCalQuakeMenu() {
        return calQuakeMenu;
    }

    public Menu getModeMenu() {
        return modeMenu;
    }

    public MenuItem getReplayMenuItem() {
        return replayMenuItem;
    }

    public Menu getSettingsMenu() {
        return settingsMenu;
    }

    public Button getSettingsButton() {
        return settingsButton;
    }

    public ComboBox<String> getEventSelector() {
        return eventSelector;
    }

    public Button getPlayPauseButton() {
        return playPauseButton;
    }

    public Button getRestartButton() {
        return restartButton;
    }

    public Label getHudStateLabel() {
        return hudStateLabel;
    }

    public Label getControlStateLabel() {
        return controlStateLabel;
    }

    public Label getElapsedDigitsLabel() {
        return elapsedDigitsLabel;
    }

    public Label getElapsedSubLabel() {
        return elapsedSubLabel;
    }

    public Label getControlTimeLabel() {
        return controlTimeLabel;
    }

    public Label getStatusReplayLabel() {
        return statusReplayLabel;
    }

    public AnimationTimer getAnimationTimer() {
        return animationTimer;
    }

    public Throwable getStartupError() {
        return startupError;
    }

    void setStartupErrorForTesting(Throwable t) {
        this.startupError = t;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
