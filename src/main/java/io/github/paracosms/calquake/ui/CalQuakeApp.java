package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.ApplicationMode;
import io.github.paracosms.calquake.core.EarthquakeEvent;
import io.github.paracosms.calquake.core.EventSource;
import io.github.paracosms.calquake.core.FrameState;
import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.HadleyKanamoriTauPModel;
import io.github.paracosms.calquake.core.IntensityDisplayMode;
import io.github.paracosms.calquake.core.LocationIntensityState;
import io.github.paracosms.calquake.core.MapScenario;
import io.github.paracosms.calquake.core.MmiLegend;
import io.github.paracosms.calquake.core.MmiMode;
import io.github.paracosms.calquake.core.MonotonicClock;
import io.github.paracosms.calquake.core.PreparedReplay;
import io.github.paracosms.calquake.core.ReplayController;
import io.github.paracosms.calquake.core.ReplayEngine;
import io.github.paracosms.calquake.core.ReplayPreparer;
import io.github.paracosms.calquake.core.ReferenceLocation;
import io.github.paracosms.calquake.core.Scenario;
import io.github.paracosms.calquake.core.ScenarioInputs;
import io.github.paracosms.calquake.core.ScenarioReferences;
import io.github.paracosms.calquake.core.SimulationAssumptionSet;
import io.github.paracosms.calquake.core.SimulationScenarioSettings;
import io.github.paracosms.calquake.core.SimulationSite;
import io.github.paracosms.calquake.core.SimulationValidator;
import io.github.paracosms.calquake.core.TravelTimeModel;
import io.github.paracosms.calquake.data.CaliforniaOutline;
import io.github.paracosms.calquake.data.ScenarioLoader;
import io.github.paracosms.calquake.data.SimulationSiteCatalog;
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
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main JavaFX application for CalQuake.
 * <p>
 * Supports top-level Simulation and Replay modes using classic desktop / Frutiger Aero utility aesthetic.
 */
public class CalQuakeApp extends Application {

    public static final double BASELINE_WIDTH = 1280.0;
    public static final double BASELINE_HEIGHT = 800.0;
    public static final double MIN_WIDTH = 1024.0;
    public static final double MIN_HEIGHT = 640.0;

    private ApplicationMode currentMode = ApplicationMode.SIMULATION;
    private CaliforniaOutline outline;
    private MapCanvasPane mapCanvasPane;
    private Stage lifecycleStage;
    private boolean windowInactive;
    private boolean wasPlayingBeforeDeactivation;
    private Throwable startupError;

    private final ScenarioLoader scenarioLoader = new ScenarioLoader();
    private final TravelTimeModel travelTimeModel = new HadleyKanamoriTauPModel();
    private final ReplayPreparer replayPreparer = new ReplayPreparer(travelTimeModel);
    private final AtomicLong preparationGeneration = new AtomicLong();
    private final ExecutorService preparationExecutor = Executors.newSingleThreadExecutor(new PreparationThreadFactory());
    private Map<String, ScenarioLoader.ScenarioBundle> scenarioBundles = Map.of();

    // Replay mode state
    private Scenario replayScenario;
    private PreparedReplay replayPreparedReplay;
    private ReplayController replayController;
    private Button replayPlayPauseButton;
    private Button replayRestartButton;
    private Slider replayTimelineScrubber;
    private boolean updatingReplayScrubber;
    private Label replayControlStateLabel;
    private Label replayControlTimeLabel;
    private ComboBox<String> eventSelector;
    private ComboBox<MmiMode> mmiModeSelector;
    private String selectedReplayEvent = "Ridgecrest";
    private MmiMode selectedMmiMode = MmiMode.RECORDED;
    private VBox replaySidebar;

    // Simulation mode state
    private Scenario simulationScenario;
    private PreparedReplay simulationPreparedReplay;
    private ReplayController simulationController;
    private Button simPlayPauseButton;
    private Button simRestartButton;
    private Slider simTimelineScrubber;
    private boolean updatingSimScrubber;
    private Label simControlStateLabel;
    private Label simControlTimeLabel;
    private TextField epicenterLatField;
    private TextField epicenterLonField;
    private TextField magnitudeField;
    private TextField depthField;
    private ComboBox<String> intensityDisplaySelector;
    private Button applyButton;
    private Label simSettingsStatusLabel;
    private Button saveButton;
    private Button importButton;
    private VBox simulationSidebar;
    private SimulationSiteCatalog simulationSiteCatalog;
    private SimulationScenarioSettings simulationInstalledSettings;
    private SimulationScenarioSettings simulationDraftSettings;
    private List<String> simulationWarnings = List.of();
    private boolean draftStale;
    private VBox simWarningBanner;
    private Label simWarningBannerLabel;
    private Label simLegendMeaningLabel;
    private Label replayLegendMeaningLabel;

    // Asynchronous preparation state
    private CompletableFuture<?> preparationFuture;
    private boolean preparingReplay;
    private Throwable preparationError;

    // Top Header & Menus
    private MenuBar menuBar;
    private Menu calQuakeMenu;
    private Menu modeMenu;
    private ToggleGroup modeToggleGroup;
    private RadioMenuItem simulationMenuItem;
    private RadioMenuItem replayMenuItem;
    private Menu settingsMenu;
    private Button settingsButton;

    // Center & Layout Containers
    private ScrollPane sidebarScroll;

    // HUD & Status Readouts
    private Label hudTitleLabel;
    private Label hudStateLabel;
    private Label elapsedDigitsLabel;
    private Label elapsedSubLabel;
    private Label statusReplayLabel;
    private AnimationTimer animationTimer;

    public CalQuakeApp() {
        this.currentMode = ApplicationMode.SIMULATION;
    }

    public CalQuakeApp(ApplicationMode mode) {
        this.currentMode = mode != null ? mode : ApplicationMode.SIMULATION;
    }

    public CalQuakeApp(Scenario scenario, CaliforniaOutline outline, ReplayController controller) {
        this(scenario, outline, controller, ApplicationMode.REPLAY);
    }

    public CalQuakeApp(Scenario scenario, CaliforniaOutline outline, ReplayController controller, ApplicationMode mode) {
        this.outline = outline;
        this.currentMode = mode != null ? mode : ApplicationMode.REPLAY;
        if (this.currentMode == ApplicationMode.REPLAY) {
            this.replayScenario = scenario;
            this.replayController = controller;
            if (controller != null && controller.engine() != null) {
                this.replayPreparedReplay = controller.engine().preparedReplay();
            }
        } else {
            this.simulationScenario = scenario;
            this.simulationController = controller;
            if (controller != null && controller.engine() != null) {
                this.simulationPreparedReplay = controller.engine().preparedReplay();
            }
        }
    }

    @Override
    public void init() {
        try {
            if (this.outline == null) {
                this.outline = CaliforniaOutline.loadDefault();
            }

            // Load Replay bundles
            if (this.scenarioBundles.isEmpty()) {
                ScenarioLoader.ScenarioBundle ridgecrest = scenarioLoader.loadScenarioBundle("Ridgecrest");
                ScenarioLoader.ScenarioBundle northridge = scenarioLoader.loadScenarioBundle("Northridge");
                this.scenarioBundles = Map.of("Ridgecrest", ridgecrest, "Northridge", northridge);
            }

            // Initialize Replay mode if not injected
            if (this.replayScenario == null) {
                ScenarioLoader.ScenarioBundle bundle = scenarioBundles.get("Ridgecrest");
                this.replayScenario = bundle.scenario();
                this.replayPreparedReplay = replayPreparer.prepare(
                        bundle.inputs(), bundle.references(), selectedMmiMode);
                ReplayEngine engine = ReplayEngine.createPrepared(
                        replayScenario, travelTimeModel, replayPreparedReplay);
                this.replayController = new ReplayController(
                        replayScenario, engine, MonotonicClock.system(), replayPreparedReplay.durationSeconds());
            }

            if (this.simulationSiteCatalog == null) {
                this.simulationSiteCatalog = SimulationSiteCatalog.loadDefault();
            }

            // Initialize Simulation mode if not injected
            if (this.simulationScenario == null) {
                this.simulationInstalledSettings = scenarioLoader.loadStarterSimulationSettings();
                this.simulationDraftSettings = this.simulationInstalledSettings;
                ScenarioLoader.ScenarioBundle simBundle = scenarioLoader.loadStarterSimulationBundle(travelTimeModel, simulationSiteCatalog);
                this.simulationScenario = simBundle.scenario();
                this.simulationPreparedReplay = replayPreparer.prepare(
                        simBundle.inputs(), simBundle.references(), MmiMode.SIMULATED);
                ReplayEngine simEngine = ReplayEngine.createPrepared(
                        simulationScenario, travelTimeModel, simulationPreparedReplay);
                simEngine.setIntensityDisplayMode(simulationInstalledSettings.intensityDisplayMode());
                this.simulationController = new ReplayController(
                        simulationScenario, simEngine, MonotonicClock.system(), simulationPreparedReplay.durationSeconds());

                SimulationValidator.ValidationResult initialValidation = SimulationValidator.validate(
                        simulationInstalledSettings.epicenter().latitude(),
                        simulationInstalledSettings.epicenter().longitude(),
                        simulationInstalledSettings.magnitude(),
                        simulationInstalledSettings.depthKm(),
                        simulationSiteCatalog.sites(), outline);
                this.simulationWarnings = initialValidation.warnings();
            } else if (this.simulationInstalledSettings == null) {
                EarthquakeEvent ev = simulationScenario.event();
                this.simulationInstalledSettings = new SimulationScenarioSettings(
                        ev.id(), ev.title(), ev.originUtc(), ev.epicenter(), ev.magnitude(), ev.depthKm(),
                        IntensityDisplayMode.MAXIMUM_REACHED, SimulationAssumptionSet.DEFAULT_ID);
                this.simulationDraftSettings = this.simulationInstalledSettings;
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
        this.mapCanvasPane = new MapCanvasPane(getActiveMapScenario(), outline);
        mapCanvasPane.getStyleClass().add("map-viewport-frame");

        StackPane centerStack = new StackPane();
        centerStack.getChildren().add(mapCanvasPane);

        // Timer HUD Box Overlay in top-left
        VBox timerHudBox = buildTimerHudOverlay();
        StackPane.setAlignment(timerHudBox, Pos.TOP_LEFT);
        StackPane.setMargin(timerHudBox, new Insets(16.0, 0, 0, 16.0));
        centerStack.getChildren().add(timerHudBox);

        root.setCenter(centerStack);

        // 3. Right Sidebar: Build both variants and install active mode's sidebar
        this.simulationSidebar = buildSimulationSidebar();
        this.replaySidebar = buildReplaySidebar();

        this.sidebarScroll = new ScrollPane(currentMode == ApplicationMode.SIMULATION ? simulationSidebar : replaySidebar);
        sidebarScroll.setFitToWidth(true);
        sidebarScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sidebarScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sidebarScroll.setPrefWidth(390.0);
        sidebarScroll.setMinWidth(360.0);
        root.setRight(sidebarScroll);

        // 4. Bottom Status Bar (Classic Windows Sunken Panes)
        HBox statusBar = buildStatusBar();
        root.setBottom(statusBar);

        // Wire control event handlers
        setupControlHandlers();
        updateTimeDisplays();
        updateControlStates();

        // 5. Animation Timer for Replay Engine Loop
        this.animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                ReplayController ctrl = getController();
                if (ctrl != null && ctrl.isPlaying()) {
                    FrameState frame = ctrl.tick();
                    updateTimeDisplays();
                    updateControlStates();
                    if (mapCanvasPane != null) {
                        mapCanvasPane.renderFrame(frame);
                    }
                } else if (ctrl != null && ctrl.isFinished()) {
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
        preparationGeneration.incrementAndGet();
        preparationExecutor.shutdownNow();
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
        ReplayController ctrl = getController();
        if (inactive) {
            wasPlayingBeforeDeactivation = ctrl != null && ctrl.isPlaying();
            if (wasPlayingBeforeDeactivation && ctrl != null) {
                ctrl.pause();
                updateTimeDisplays();
                updateControlStates();
            }
            return;
        }

        boolean shouldResume = wasPlayingBeforeDeactivation;
        wasPlayingBeforeDeactivation = false;
        if (shouldResume && ctrl != null) {
            ctrl.play();
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
            ReplayController ctrl = getController();
            FrameState currentFrame = ctrl != null ? ctrl.currentFrame() : null;
            if (currentFrame != null) {
                mapCanvasPane.refresh(currentFrame);
            }
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

        this.menuBar = new MenuBar();
        menuBar.getStyleClass().add("app-menu-bar");

        // 1. Mode menu (Simulation first and selected by default, then Replay)
        this.modeMenu = new Menu("Mode");
        this.modeToggleGroup = new ToggleGroup();

        this.simulationMenuItem = new RadioMenuItem("Simulation");
        simulationMenuItem.setToggleGroup(modeToggleGroup);
        simulationMenuItem.setSelected(currentMode == ApplicationMode.SIMULATION);
        simulationMenuItem.setOnAction(e -> switchMode(ApplicationMode.SIMULATION));

        this.replayMenuItem = new RadioMenuItem("Replay");
        replayMenuItem.setToggleGroup(modeToggleGroup);
        replayMenuItem.setSelected(currentMode == ApplicationMode.REPLAY);
        replayMenuItem.setOnAction(e -> switchMode(ApplicationMode.REPLAY));

        modeMenu.getItems().addAll(simulationMenuItem, replayMenuItem);

        // 2. Settings menu (placeholder for later work)
        this.settingsMenu = new Menu("Settings");
        MenuItem settingsItem = new MenuItem("Settings...");
        settingsItem.setDisable(true);
        settingsMenu.getItems().add(settingsItem);

        this.calQuakeMenu = new Menu("CalQuake");

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

        this.hudTitleLabel = new Label(currentMode == ApplicationMode.SIMULATION
                ? "SIMULATION ELAPSED TIME" : "REPLAY ELAPSED TIME");
        hudTitleLabel.getStyleClass().add("timer-subtext");

        this.hudStateLabel = new Label("PAUSED");
        hudStateLabel.getStyleClass().add("status-badge-paused");

        topRow.getChildren().addAll(hudTitleLabel, hudStateLabel);

        this.elapsedDigitsLabel = new Label("00:00:00.00");
        elapsedDigitsLabel.getStyleClass().add("timer-digits");

        this.elapsedSubLabel = new Label("T + 0.0 s");
        elapsedSubLabel.getStyleClass().add("timer-subtext");

        box.getChildren().addAll(topRow, elapsedDigitsLabel, elapsedSubLabel);
        return box;
    }

    private VBox buildSimulationSidebar() {
        VBox sidebar = new VBox(10.0);
        sidebar.setPadding(new Insets(10.0));
        sidebar.setStyle("-fx-background-color: #F0F3F7;");

        // Section 1: Simulation Controls
        VBox controlsBox = buildSimulationControlsBox();

        // Persistent Warning Banner (shown when installed simulation has domain warnings)
        this.simWarningBanner = buildSimulationWarningBanner();

        // Section 2: Simulation Settings
        VBox settingsBox = buildSimulationSettingsBox();

        // Section 3: Fixed Model Assumptions
        VBox assumptionsBox = buildFixedAssumptionsBox();

        // Section 4: Scenario File
        VBox fileBox = buildScenarioFileBox();

        // Section 5: MMI Legend
        VBox legendBox = buildLegendBox(true);

        // Section 6: Toy Disclaimer
        VBox disclaimerBox = buildDisclaimerBox();

        sidebar.getChildren().addAll(controlsBox, simWarningBanner, settingsBox, assumptionsBox, fileBox, legendBox, disclaimerBox);
        return sidebar;
    }

    private VBox buildSimulationControlsBox() {
        VBox box = new VBox(8.0);
        box.getStyleClass().add("group-box");

        Label title = new Label("Simulation Controls (Initially Paused)");
        title.getStyleClass().add("group-box-title");

        HBox buttonsRow = new HBox(8.0);
        buttonsRow.setAlignment(Pos.CENTER_LEFT);

        this.simPlayPauseButton = new Button("▶  Play");
        simPlayPauseButton.getStyleClass().addAll("button", "button-primary");
        simPlayPauseButton.setPrefWidth(95.0);

        this.simRestartButton = new Button("⏮  Restart");
        simRestartButton.getStyleClass().add("button");
        simRestartButton.setPrefWidth(95.0);

        buttonsRow.getChildren().addAll(simPlayPauseButton, simRestartButton);

        double duration = simulationController != null ? simulationController.durationSeconds() : 120.0;
        this.simTimelineScrubber = new Slider(0.0, duration, 0.0);
        simTimelineScrubber.getStyleClass().add("timeline-scrubber");
        simTimelineScrubber.setMaxWidth(Double.MAX_VALUE);
        simTimelineScrubber.setBlockIncrement(1.0);
        simTimelineScrubber.setMajorTickUnit(Math.max(5.0, duration / 4.0));
        simTimelineScrubber.setMinorTickCount(5);
        simTimelineScrubber.setShowTickMarks(true);
        simTimelineScrubber.setShowTickLabels(false);

        VBox statusInfo = new VBox(2.0);
        this.simControlStateLabel = new Label("State: PAUSED (Ready)");
        simControlStateLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B;");

        this.simControlTimeLabel = new Label("Elapsed: 0.00 s");
        simControlTimeLabel.setStyle("-fx-font-family: 'Consolas', monospace; -fx-text-fill: #475569;");

        statusInfo.getChildren().addAll(simControlStateLabel, simControlTimeLabel);

        VBox frontLegendBox = buildWavefrontLegendBox();

        box.getChildren().addAll(title, buttonsRow, simTimelineScrubber, statusInfo, frontLegendBox);
        return box;
    }

    private VBox buildSimulationWarningBanner() {
        VBox banner = new VBox(4.0);
        banner.setStyle("-fx-background-color: #FFFBEB; -fx-border-color: #FCD34D; -fx-border-width: 1px; -fx-border-radius: 3px; -fx-padding: 8px;");
        Label title = new Label("⚠ Model Domain Warnings");
        title.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #92400E;");
        this.simWarningBannerLabel = new Label();
        simWarningBannerLabel.setWrapText(true);
        simWarningBannerLabel.setStyle("-fx-font-size: 9.5px; -fx-text-fill: #B45309;");
        banner.getChildren().addAll(title, simWarningBannerLabel);
        updateSimulationWarningBanner();
        return banner;
    }

    private void updateSimulationWarningBanner() {
        if (simWarningBanner != null && simWarningBannerLabel != null) {
            if (simulationWarnings.isEmpty()) {
                simWarningBanner.setVisible(false);
                simWarningBanner.setManaged(false);
            } else {
                simWarningBanner.setVisible(true);
                simWarningBanner.setManaged(true);
                simWarningBannerLabel.setText("Outside the model's tested/calibrated range. This toy simulation may be wildly inaccurate.\n• "
                        + String.join("\n• ", simulationWarnings));
            }
        }
    }

    private VBox buildSimulationSettingsBox() {
        VBox box = new VBox(6.0);
        box.getStyleClass().add("group-box");

        Label title = new Label("Simulation Settings");
        title.getStyleClass().add("group-box-title");

        Label subtitle = new Label("Enter custom earthquake parameters:");
        subtitle.setStyle("-fx-font-size: 9.5px; -fx-text-fill: #64748B;");

        GridPane grid = new GridPane();
        grid.setHgap(8.0);
        grid.setVgap(6.0);

        Label latLabel = new Label("Epicenter Latitude:");
        latLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #334155;");
        double initLat = simulationInstalledSettings != null ? simulationInstalledSettings.epicenter().latitude() : 35.5;
        this.epicenterLatField = new TextField(String.format(java.util.Locale.US, "%.4f", initLat));
        epicenterLatField.setPrefWidth(120.0);

        Label lonLabel = new Label("Epicenter Longitude:");
        lonLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #334155;");
        double initLon = simulationInstalledSettings != null ? simulationInstalledSettings.epicenter().longitude() : -118.5;
        this.epicenterLonField = new TextField(String.format(java.util.Locale.US, "%.4f", initLon));
        epicenterLonField.setPrefWidth(120.0);

        Label magLabel = new Label("Magnitude (Mw):");
        magLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #334155;");
        double initMag = simulationInstalledSettings != null ? simulationInstalledSettings.magnitude() : 6.5;
        this.magnitudeField = new TextField(String.format(java.util.Locale.US, "%.1f", initMag));
        magnitudeField.setPrefWidth(120.0);

        Label depthLabel = new Label("Depth (km):");
        depthLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #334155;");
        double initDepth = simulationInstalledSettings != null ? simulationInstalledSettings.depthKm() : 10.0;
        this.depthField = new TextField(String.format(java.util.Locale.US, "%.1f", initDepth));
        depthField.setPrefWidth(120.0);

        Label displayLabel = new Label("Intensity Display:");
        displayLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #334155;");
        this.intensityDisplaySelector = new ComboBox<>();
        intensityDisplaySelector.getItems().addAll(
                IntensityDisplayMode.MAXIMUM_REACHED.label(),
                IntensityDisplayMode.CURRENT_SHAKING.label()
        );
        String initDisplay = simulationInstalledSettings != null
                ? simulationInstalledSettings.intensityDisplayMode().label()
                : IntensityDisplayMode.MAXIMUM_REACHED.label();
        intensityDisplaySelector.setValue(initDisplay);
        intensityDisplaySelector.setMaxWidth(Double.MAX_VALUE);
        intensityDisplaySelector.setTooltip(new Tooltip(
                "Intensity presentation:\n" +
                "• Maximum estimated MMI reached: non-decreasing running maximum\n" +
                "• Current estimated shaking: envelope-derived estimate at selected instant"
        ));

        // Listen for draft changes
        epicenterLatField.textProperty().addListener((obs, oldVal, newVal) -> onSimulationDraftChanged());
        epicenterLonField.textProperty().addListener((obs, oldVal, newVal) -> onSimulationDraftChanged());
        magnitudeField.textProperty().addListener((obs, oldVal, newVal) -> onSimulationDraftChanged());
        depthField.textProperty().addListener((obs, oldVal, newVal) -> onSimulationDraftChanged());
        intensityDisplaySelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                handleIntensityDisplayModeChanged(newVal);
            }
        });

        grid.add(latLabel, 0, 0);
        grid.add(epicenterLatField, 1, 0);
        grid.add(lonLabel, 0, 1);
        grid.add(epicenterLonField, 1, 1);
        grid.add(magLabel, 0, 2);
        grid.add(magnitudeField, 1, 2);
        grid.add(depthLabel, 0, 3);
        grid.add(depthField, 1, 3);

        this.applyButton = new Button("Apply / Prepare");
        applyButton.getStyleClass().addAll("button", "button-primary");
        applyButton.setMaxWidth(Double.MAX_VALUE);
        applyButton.setOnAction(e -> handleApplySettings());

        this.simSettingsStatusLabel = new Label(simulationWarnings.isEmpty() ? "Ready" : "Installed with domain warnings");
        simSettingsStatusLabel.setStyle(simulationWarnings.isEmpty()
                ? "-fx-font-size: 10px; -fx-text-fill: #475569;"
                : "-fx-font-size: 10px; -fx-text-fill: #B45309;");
        simSettingsStatusLabel.setWrapText(true);

        box.getChildren().addAll(title, subtitle, grid, displayLabel, intensityDisplaySelector, applyButton, simSettingsStatusLabel);
        return box;
    }

    void handleIntensityDisplayModeChanged(String newLabel) {
        if (currentMode != ApplicationMode.SIMULATION) return;
        IntensityDisplayMode newMode = IntensityDisplayMode.fromLabel(newLabel);
        if (simulationInstalledSettings != null) {
            this.simulationInstalledSettings = simulationInstalledSettings.withIntensityDisplayMode(newMode);
            this.simulationDraftSettings = simulationInstalledSettings;
        }
        if (simulationController != null) {
            simulationController.setIntensityDisplayMode(newMode);
            if (mapCanvasPane != null) {
                mapCanvasPane.renderFrame(simulationController.currentFrame());
            }
        }
        updateLegendMeaning();
        updateTimeDisplays();
        onSimulationDraftChanged();
    }

    private void onSimulationDraftChanged() {
        if (currentMode != ApplicationMode.SIMULATION || simulationInstalledSettings == null) return;

        String latText = epicenterLatField != null ? epicenterLatField.getText().trim() : "";
        String lonText = epicenterLonField != null ? epicenterLonField.getText().trim() : "";
        String magText = magnitudeField != null ? magnitudeField.getText().trim() : "";
        String depthText = depthField != null ? depthField.getText().trim() : "";

        boolean matches = false;
        try {
            double lat = Double.parseDouble(latText);
            double lon = Double.parseDouble(lonText);
            double mag = Double.parseDouble(magText);
            double depth = Double.parseDouble(depthText);

            if (Math.abs(lat - simulationInstalledSettings.epicenter().latitude()) < 1e-6
                    && Math.abs(lon - simulationInstalledSettings.epicenter().longitude()) < 1e-6
                    && Math.abs(mag - simulationInstalledSettings.magnitude()) < 1e-6
                    && Math.abs(depth - simulationInstalledSettings.depthKm()) < 1e-6) {
                matches = true;
            }
        } catch (NumberFormatException ignored) {}

        this.draftStale = !matches;

        if (draftStale) {
            List<SimulationSite> sites = simulationSiteCatalog != null
                    ? simulationSiteCatalog.sites() : SimulationSiteCatalog.loadDefault().sites();
            SimulationValidator.ValidationResult result = SimulationValidator.validateRaw(
                    latText, lonText, magText, depthText, sites, outline);
            if (!result.isValid()) {
                simSettingsStatusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #991B1B;");
                simSettingsStatusLabel.setText("Error: " + String.join(", ", result.errors()));
            } else if (result.hasWarnings()) {
                simSettingsStatusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #B45309;");
                simSettingsStatusLabel.setText("⚠ Stale draft. " + result.warningSummary());
            } else {
                simSettingsStatusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #475569;");
                simSettingsStatusLabel.setText("Settings modified (stale). Click Apply / Prepare to update simulation.");
            }
        } else {
            if (!simulationWarnings.isEmpty()) {
                simSettingsStatusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #B45309;");
                simSettingsStatusLabel.setText("Installed with warnings. Ready to play.");
            } else {
                simSettingsStatusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #475569;");
                simSettingsStatusLabel.setText("Ready");
            }
        }
        updateControlStates();
    }

    private VBox buildFixedAssumptionsBox() {
        VBox box = new VBox(4.0);
        box.getStyleClass().add("group-box");

        Label title = new Label("Fixed Model Assumptions");
        title.getStyleClass().add("group-box-title");

        Label l1 = new Label("• Rupture: Wells & Coppersmith (1994) all-slip");
        l1.setStyle("-fx-font-size: 9.5px; -fx-text-fill: #334155;");
        Label l2 = new Label("• Mechanism: Generic strike-slip (0° rake / 0° strike / 90° dip)");
        l2.setStyle("-fx-font-size: 9.5px; -fx-text-fill: #334155;");
        Label l3 = new Label("• Site condition: Reference rock, Vs30 760 m/s (DEFAULT)");
        l3.setStyle("-fx-font-size: 9.5px; -fx-text-fill: #334155;");
        Label l4 = new Label("• Ground motion: BSSA14 / Cua-Heaton envelope / Worden (2012)");
        l4.setStyle("-fx-font-size: 9.5px; -fx-text-fill: #334155;");
        Label l5 = new Label("• Assumption set: calquake-custom-v1 (read-only)");
        l5.setStyle("-fx-font-size: 9.5px; -fx-text-fill: #64748B;");

        box.getChildren().addAll(title, l1, l2, l3, l4, l5);
        return box;
    }

    private VBox buildScenarioFileBox() {
        VBox box = new VBox(6.0);
        box.getStyleClass().add("group-box");

        Label title = new Label("Scenario File");
        title.getStyleClass().add("group-box-title");

        HBox buttonsRow = new HBox(8.0);
        buttonsRow.setAlignment(Pos.CENTER_LEFT);

        this.saveButton = new Button("Save to File…");
        saveButton.getStyleClass().add("button");
        saveButton.setPrefWidth(120.0);
        saveButton.setDisable(true);

        this.importButton = new Button("Import…");
        importButton.getStyleClass().add("button");
        importButton.setPrefWidth(120.0);
        importButton.setDisable(true);

        buttonsRow.getChildren().addAll(saveButton, importButton);
        box.getChildren().addAll(title, buttonsRow);
        return box;
    }

    private VBox buildDisclaimerBox() {
        VBox box = new VBox(4.0);
        box.setStyle("-fx-background-color: #FEF2F2; -fx-border-color: #FCA5A5; -fx-border-width: 1px; -fx-border-radius: 3px; -fx-padding: 8px;");

        Label title = new Label("⚠ Exploratory Toy Simulation");
        title.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #991B1B;");

        Label text = new Label("Simulation is an exploratory toy. It is not a forecast, emergency tool, hazard product, or scientifically validated prediction of a future earthquake.");
        text.setWrapText(true);
        text.setStyle("-fx-font-size: 9.5px; -fx-text-fill: #7F1D1D;");

        box.getChildren().addAll(title, text);
        return box;
    }

    private VBox buildReplaySidebar() {
        VBox sidebar = new VBox(10.0);
        sidebar.setPadding(new Insets(10.0));
        sidebar.setStyle("-fx-background-color: #F0F3F7;");

        // Section 1: Replay Controls
        VBox controlsBox = buildReplayControlsBox();

        // Section 2: Event Selector (retains Ridgecrest/Northridge and Recorded/Simulated)
        VBox eventBox = buildEventSelectorBox();

        // Section 3: USGS ShakeMap MMI Legend
        VBox legendBox = buildLegendBox(false);

        sidebar.getChildren().addAll(controlsBox, eventBox, legendBox);
        return sidebar;
    }

    private VBox buildReplayControlsBox() {
        VBox box = new VBox(8.0);
        box.getStyleClass().add("group-box");

        Label title = new Label("Replay Controls (Initially Paused)");
        title.getStyleClass().add("group-box-title");

        HBox buttonsRow = new HBox(8.0);
        buttonsRow.setAlignment(Pos.CENTER_LEFT);

        this.replayPlayPauseButton = new Button("▶  Play");
        replayPlayPauseButton.getStyleClass().addAll("button", "button-primary");
        replayPlayPauseButton.setPrefWidth(95.0);

        this.replayRestartButton = new Button("⏮  Restart");
        replayRestartButton.getStyleClass().add("button");
        replayRestartButton.setPrefWidth(95.0);

        buttonsRow.getChildren().addAll(replayPlayPauseButton, replayRestartButton);

        double duration = replayController != null ? replayController.durationSeconds() : 120.0;
        this.replayTimelineScrubber = new Slider(0.0, duration, 0.0);
        replayTimelineScrubber.getStyleClass().add("timeline-scrubber");
        replayTimelineScrubber.setMaxWidth(Double.MAX_VALUE);
        replayTimelineScrubber.setBlockIncrement(1.0);
        replayTimelineScrubber.setMajorTickUnit(Math.max(5.0, duration / 4.0));
        replayTimelineScrubber.setMinorTickCount(5);
        replayTimelineScrubber.setShowTickMarks(true);
        replayTimelineScrubber.setShowTickLabels(false);

        VBox statusInfo = new VBox(2.0);
        this.replayControlStateLabel = new Label("State: PAUSED (Ready)");
        replayControlStateLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B;");

        this.replayControlTimeLabel = new Label("Elapsed: 0.00 s");
        replayControlTimeLabel.setStyle("-fx-font-family: 'Consolas', monospace; -fx-text-fill: #475569;");

        statusInfo.getChildren().addAll(replayControlStateLabel, replayControlTimeLabel);

        VBox frontLegendBox = buildWavefrontLegendBox();

        box.getChildren().addAll(title, buttonsRow, replayTimelineScrubber, statusInfo, frontLegendBox);
        return box;
    }

    private VBox buildWavefrontLegendBox() {
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
        return frontLegendBox;
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
        boolean isNorthridge = replayScenario != null && replayScenario.event() != null && "ci3144585".equals(replayScenario.event().id());
        eventSelector.setValue(isNorthridge ? "Northridge" : selectedReplayEvent);
        eventSelector.setMaxWidth(Double.MAX_VALUE);
        eventSelector.getStyleClass().add("event-selector");

        this.mmiModeSelector = new ComboBox<>();
        mmiModeSelector.getItems().setAll(MmiMode.RECORDED, MmiMode.SIMULATED);
        mmiModeSelector.setValue(selectedMmiMode);
        mmiModeSelector.setId("mmi-mode-selector");
        mmiModeSelector.setAccessibleText("MMI replay mode");
        mmiModeSelector.setMaxWidth(Double.MAX_VALUE);
        mmiModeSelector.getStyleClass().add("event-selector");

        box.getChildren().addAll(title, subtitle, eventSelector, mmiModeSelector);
        return box;
    }

    private VBox buildLegendBox(boolean isSimulation) {
        VBox box = new VBox(6.0);
        box.getStyleClass().add("group-box");

        Label title = new Label("MMI Scale (Worden et al., 2012)");
        title.getStyleClass().add("group-box-title");

        Label meaningLabel = new Label();
        meaningLabel.setStyle("-fx-font-size: 9.5px; -fx-font-style: italic; -fx-text-fill: #475569;");
        if (isSimulation) {
            this.simLegendMeaningLabel = meaningLabel;
        } else {
            this.replayLegendMeaningLabel = meaningLabel;
        }
        updateLegendMeaning();

        GridPane grid = new GridPane();
        grid.getStyleClass().add("mmi-legend-grid");
        grid.setHgap(6.0);
        grid.setVgap(2.0);
        grid.setPadding(new Insets(4.0));

        int r = 0;
        for (MmiLegend.MmiBin bin : MmiLegend.ALL_BINS) {
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

        box.getChildren().addAll(title, meaningLabel, grid);
        return box;
    }

    void updateLegendMeaning() {
        if (simLegendMeaningLabel != null) {
            IntensityDisplayMode mode = (simulationInstalledSettings != null)
                    ? simulationInstalledSettings.intensityDisplayMode()
                    : (intensityDisplaySelector != null && intensityDisplaySelector.getValue() != null
                    ? IntensityDisplayMode.fromLabel(intensityDisplaySelector.getValue())
                    : IntensityDisplayMode.MAXIMUM_REACHED);
            if (mode == IntensityDisplayMode.CURRENT_SHAKING) {
                simLegendMeaningLabel.setText("Active: Current estimated shaking (envelope-derived)");
            } else {
                simLegendMeaningLabel.setText("Active: Maximum estimated MMI reached so far");
            }
        }
        if (replayLegendMeaningLabel != null) {
            if (selectedMmiMode == MmiMode.RECORDED) {
                replayLegendMeaningLabel.setText("Active: Recorded ShakeMap Peak MMI");
            } else {
                replayLegendMeaningLabel.setText("Active: Maximum estimated MMI reached so far");
            }
        }
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

        this.statusReplayLabel = new Label(currentMode == ApplicationMode.SIMULATION
                ? "Simulation: READY" : "Replay: READY");
        statusReplayLabel.getStyleClass().add("status-pane");
        statusReplayLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0D3B66;");

        bar.getChildren().addAll(p1, p2, p3, spacer, statusReplayLabel);
        return bar;
    }

    public void switchMode(ApplicationMode newMode) {
        if (newMode == null || newMode == this.currentMode) {
            return;
        }

        // 1. Pause and reset active playback on the leaving mode
        ReplayController leavingController = getController();
        if (leavingController != null) {
            if (leavingController.isPlaying()) {
                leavingController.pause();
            }
            leavingController.restart();
        }
        this.wasPlayingBeforeDeactivation = false;

        // 2. Cancel or supersede pending background preparation
        preparationGeneration.incrementAndGet();
        this.preparingReplay = false;
        this.preparationError = null;

        // 3. Update currentMode and sync menu selections
        this.currentMode = newMode;
        if (simulationMenuItem != null && replayMenuItem != null) {
            if (currentMode == ApplicationMode.SIMULATION && !simulationMenuItem.isSelected()) {
                simulationMenuItem.setSelected(true);
            } else if (currentMode == ApplicationMode.REPLAY && !replayMenuItem.isSelected()) {
                replayMenuItem.setSelected(true);
            }
        }

        // 4. Atomically swap sidebar
        VBox newSidebar = (currentMode == ApplicationMode.SIMULATION) ? simulationSidebar : replaySidebar;
        if (sidebarScroll != null) {
            sidebarScroll.setContent(newSidebar);
        }

        // 5. Reset incoming controller to T + 0
        ReplayController enteringController = getController();
        if (enteringController != null) {
            if (enteringController.isPlaying()) {
                enteringController.pause();
            }
            enteringController.restart();
        }

        // 6. Redraw map canvas pane with the incoming scenario and reset frame
        if (mapCanvasPane != null) {
            mapCanvasPane.setMapScenario(getActiveMapScenario());
            if (enteringController != null) {
                mapCanvasPane.renderFrame(enteringController.currentFrame());
            }
        }

        // 7. Update HUD title and control states
        if (hudTitleLabel != null) {
            hudTitleLabel.setText(currentMode == ApplicationMode.SIMULATION
                    ? "SIMULATION ELAPSED TIME" : "REPLAY ELAPSED TIME");
        }
        updateLegendMeaning();
        updateControlStates();
        updateTimeDisplays();
    }

    private void setupControlHandlers() {
        // Simulation Controls
        if (simPlayPauseButton != null) {
            simPlayPauseButton.setOnAction(e -> {
                if (simulationController != null) {
                    simulationController.togglePlayPause();
                    updateControlStates();
                    updateTimeDisplays();
                    if (mapCanvasPane != null) {
                        mapCanvasPane.renderFrame(simulationController.currentFrame());
                    }
                }
            });
        }

        if (simRestartButton != null) {
            simRestartButton.setOnAction(e -> {
                if (simulationController != null) {
                    simulationController.restart();
                    wasPlayingBeforeDeactivation = false;
                    updateControlStates();
                    updateTimeDisplays();
                    if (mapCanvasPane != null) {
                        mapCanvasPane.renderFrame(simulationController.currentFrame());
                    }
                }
            });
        }

        if (simTimelineScrubber != null) {
            simTimelineScrubber.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (updatingSimScrubber || simulationController == null) return;
                simulationController.seek(newVal.doubleValue());
                updateControlStates();
                updateTimeDisplays();
                if (mapCanvasPane != null) {
                    mapCanvasPane.renderFrame(simulationController.currentFrame());
                }
            });
            simTimelineScrubber.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
                if (!isChanging && simulationController != null) {
                    simulationController.seek(simTimelineScrubber.getValue());
                    updateControlStates();
                    updateTimeDisplays();
                    if (mapCanvasPane != null) {
                        mapCanvasPane.renderFrame(simulationController.currentFrame());
                    }
                }
            });
        }

        if (applyButton != null) {
            applyButton.setOnAction(e -> handleApplySettings());
        }

        // Replay Controls
        if (replayPlayPauseButton != null) {
            replayPlayPauseButton.setOnAction(e -> {
                if (replayController != null) {
                    replayController.togglePlayPause();
                    updateControlStates();
                    updateTimeDisplays();
                    if (mapCanvasPane != null) {
                        mapCanvasPane.renderFrame(replayController.currentFrame());
                    }
                }
            });
        }

        if (replayRestartButton != null) {
            replayRestartButton.setOnAction(e -> {
                if (replayController != null) {
                    replayController.restart();
                    wasPlayingBeforeDeactivation = false;
                    updateControlStates();
                    updateTimeDisplays();
                    if (mapCanvasPane != null) {
                        mapCanvasPane.renderFrame(replayController.currentFrame());
                    }
                }
            });
        }

        if (replayTimelineScrubber != null) {
            replayTimelineScrubber.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (updatingReplayScrubber || replayController == null) return;
                replayController.seek(newVal.doubleValue());
                updateControlStates();
                updateTimeDisplays();
                if (mapCanvasPane != null) {
                    mapCanvasPane.renderFrame(replayController.currentFrame());
                }
            });
            replayTimelineScrubber.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
                if (!isChanging && replayController != null) {
                    replayController.seek(replayTimelineScrubber.getValue());
                    updateControlStates();
                    updateTimeDisplays();
                    if (mapCanvasPane != null) {
                        mapCanvasPane.renderFrame(replayController.currentFrame());
                    }
                }
            });
        }

        if (eventSelector != null) {
            eventSelector.setOnAction(e -> {
                String selected = eventSelector.getValue();
                if (selected != null) {
                    selectEvent(selected);
                }
            });
            eventSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    selectEvent(newVal);
                }
            });
        }

        if (mmiModeSelector != null) {
            mmiModeSelector.setOnAction(e -> {
                MmiMode selected = mmiModeSelector.getValue();
                if (selected != null && selected != selectedMmiMode) {
                    selectedMmiMode = selected;
                    updateLegendMeaning();
                    requestReplayPreparation(eventSelector != null ? eventSelector.getValue() : selectedReplayEvent, selectedMmiMode);
                }
            });
            mmiModeSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && newVal != selectedMmiMode) {
                    selectedMmiMode = newVal;
                    updateLegendMeaning();
                    requestReplayPreparation(eventSelector != null ? eventSelector.getValue() : selectedReplayEvent, selectedMmiMode);
                }
            });
        }
    }

    void handleApplySettings() {
        if (epicenterLatField == null) return;
        String latText = epicenterLatField.getText().trim();
        String lonText = epicenterLonField.getText().trim();
        String magText = magnitudeField.getText().trim();
        String depthText = depthField.getText().trim();

        List<SimulationSite> sites = simulationSiteCatalog != null
                ? simulationSiteCatalog.sites() : SimulationSiteCatalog.loadDefault().sites();
        SimulationValidator.ValidationResult validation = SimulationValidator.validateRaw(
                latText, lonText, magText, depthText, sites, outline);

        if (!validation.isValid()) {
            simSettingsStatusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #991B1B;");
            simSettingsStatusLabel.setText("Error: " + String.join(", ", validation.errors()));
            this.draftStale = true;
            updateControlStates();
            return;
        }

        double lat = Double.parseDouble(latText);
        double lon = Double.parseDouble(lonText);
        double mag = Double.parseDouble(magText);
        double depth = Double.parseDouble(depthText);
        IntensityDisplayMode displayMode = IntensityDisplayMode.fromLabel(
                intensityDisplaySelector != null ? intensityDisplaySelector.getValue() : null);

        SimulationScenarioSettings newSettings = new SimulationScenarioSettings(
                simulationInstalledSettings != null ? simulationInstalledSettings.scenarioId() : "custom-california-scenario-v1",
                simulationInstalledSettings != null ? simulationInstalledSettings.displayName() : "Custom California Scenario",
                simulationInstalledSettings != null ? simulationInstalledSettings.createdUtc() : Instant.now(),
                new GeoPoint(lat, lon), mag, depth, displayMode, SimulationAssumptionSet.DEFAULT_ID);

        simSettingsStatusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #475569;");
        simSettingsStatusLabel.setText("Preparing scenario...");
        requestSimulationPreparation(newSettings, validation.warnings());
    }

    public void selectEvent(String eventName) {
        if (eventName == null) {
            return;
        }
        if (replayScenario != null && replayScenario.event() != null && !preparingReplay) {
            boolean isNorthridge = "ci3144585".equals(replayScenario.event().id());
            boolean wantsNorthridge = eventName.equalsIgnoreCase("Northridge") || eventName.equalsIgnoreCase("ci3144585");
            if (isNorthridge == wantsNorthridge) {
                return;
            }
        }

        if (replayController != null && replayController.isPlaying()) {
            replayController.pause();
        }
        this.wasPlayingBeforeDeactivation = false;

        boolean wantsNorthridge = eventName.equalsIgnoreCase("Northridge") || eventName.equalsIgnoreCase("ci3144585");
        String targetValue = wantsNorthridge ? "Northridge" : "Ridgecrest";
        this.selectedReplayEvent = targetValue;
        if (eventSelector != null && !targetValue.equals(eventSelector.getValue())) {
            eventSelector.setValue(targetValue);
            return;
        }
        requestReplayPreparation(targetValue, selectedMmiMode);
    }

    public void requestPreparation(String eventName, MmiMode mode) {
        requestReplayPreparation(eventName, mode);
    }

    private void requestReplayPreparation(String eventName, MmiMode mode) {
        if (eventName == null || mode == null) return;
        if (replayController != null) {
            replayController.pause();
            replayController.restart();
        }
        wasPlayingBeforeDeactivation = false;
        preparingReplay = true;
        preparationError = null;
        long generation = preparationGeneration.incrementAndGet();
        ApplicationMode targetMode = ApplicationMode.REPLAY;
        updateControlStates();
        updateTimeDisplays();

        ScenarioLoader.ScenarioBundle knownBundle = scenarioBundles.get(eventName);
        preparationFuture = CompletableFuture.supplyAsync(() -> {
            ScenarioLoader.ScenarioBundle bundle = knownBundle != null
                    ? knownBundle : scenarioLoader.loadScenarioBundle(eventName);
            PreparedReplay replay = replayPreparer.prepare(bundle.inputs(), bundle.references(), mode);
            return new PreparedInstallation(bundle, replay, targetMode);
        }, preparationExecutor).whenComplete((installation, failure) -> Platform.runLater(() -> {
            if (generation != preparationGeneration.get() || currentMode != targetMode) return;
            if (failure != null) {
                preparingReplay = false;
                preparationError = unwrapCompletionFailure(failure);
                updateControlStates();
                return;
            }
            installPreparedReplay(installation);
        }));
    }

    public void requestSimulationPreparation(double lat, double lon, double magnitude, double depthKm) {
        IntensityDisplayMode mode = (intensityDisplaySelector != null && intensityDisplaySelector.getValue() != null)
                ? IntensityDisplayMode.fromLabel(intensityDisplaySelector.getValue())
                : IntensityDisplayMode.MAXIMUM_REACHED;
        SimulationScenarioSettings settings = new SimulationScenarioSettings(
                simulationInstalledSettings != null ? simulationInstalledSettings.scenarioId() : "custom-california-scenario-v1",
                simulationInstalledSettings != null ? simulationInstalledSettings.displayName() : "Custom California Scenario",
                simulationInstalledSettings != null ? simulationInstalledSettings.createdUtc() : Instant.now(),
                new GeoPoint(lat, lon), magnitude, depthKm, mode, SimulationAssumptionSet.DEFAULT_ID);
        List<SimulationSite> sites = simulationSiteCatalog != null
                ? simulationSiteCatalog.sites() : SimulationSiteCatalog.loadDefault().sites();
        SimulationValidator.ValidationResult result = SimulationValidator.validate(lat, lon, magnitude, depthKm, sites, outline);
        requestSimulationPreparation(settings, result.warnings());
    }

    public void requestSimulationPreparation(SimulationScenarioSettings settings, List<String> warnings) {
        if (simulationController != null) {
            simulationController.pause();
            simulationController.restart();
        }
        wasPlayingBeforeDeactivation = false;
        preparingReplay = true;
        preparationError = null;
        long generation = preparationGeneration.incrementAndGet();
        ApplicationMode targetMode = ApplicationMode.SIMULATION;
        updateControlStates();
        updateTimeDisplays();

        preparationFuture = CompletableFuture.supplyAsync(() -> {
            List<SimulationSite> sites = simulationSiteCatalog != null
                    ? simulationSiteCatalog.sites()
                    : SimulationSiteCatalog.loadDefault().sites();
            ScenarioInputs inputs = ScenarioInputs.forCustomScenario(settings, sites, travelTimeModel);
            PreparedReplay replay = replayPreparer.prepare(inputs, new ScenarioReferences(Map.of()), MmiMode.SIMULATED);
            EarthquakeEvent event = new EarthquakeEvent(
                    settings.scenarioId(), "calquake", settings.displayName(),
                    settings.createdUtc(), settings.epicenter(), settings.depthKm(), settings.magnitude(), "mw", "");
            List<ReferenceLocation> locations = simulationScenario != null
                    ? simulationScenario.locations()
                    : scenarioLoader.loadStarterSimulationScenario().locations();
            Scenario scenario = new Scenario(event, locations);
            ScenarioLoader.ScenarioBundle bundle = new ScenarioLoader.ScenarioBundle(
                    scenario, inputs, new ScenarioReferences(Map.of()));
            return new PreparedInstallation(bundle, replay, targetMode);
        }, preparationExecutor).whenComplete((installation, failure) -> Platform.runLater(() -> {
            if (generation != preparationGeneration.get() || currentMode != targetMode) return;
            if (failure != null) {
                preparingReplay = false;
                preparationError = unwrapCompletionFailure(failure);
                if (simSettingsStatusLabel != null) {
                    simSettingsStatusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #991B1B;");
                    simSettingsStatusLabel.setText("Preparation failed: " + preparationError.getMessage());
                }
                updateControlStates();
                return;
            }
            this.simulationInstalledSettings = settings;
            this.simulationDraftSettings = settings;
            this.simulationWarnings = warnings != null ? List.copyOf(warnings) : List.of();
            installPreparedReplay(installation);
        }));
    }

    private void installPreparedReplay(PreparedInstallation installation) {
        if (installation.mode() == ApplicationMode.REPLAY) {
            MonotonicClock clock = replayController != null ? replayController.clock() : MonotonicClock.system();
            ReplayEngine engine = ReplayEngine.createPrepared(
                    installation.bundle().scenario(), travelTimeModel, installation.replay());
            engine.setIntensityDisplayMode(IntensityDisplayMode.MAXIMUM_REACHED);
            ReplayController replacement = new ReplayController(
                    installation.bundle().scenario(), engine, clock, installation.replay().durationSeconds());
            this.replayPreparedReplay = installation.replay();
            this.replayScenario = installation.bundle().scenario();
            this.replayController = replacement;
            this.preparingReplay = false;
            this.preparationError = null;
            if (currentMode == ApplicationMode.REPLAY) {
                if (mapCanvasPane != null) {
                    mapCanvasPane.setMapScenario(getActiveMapScenario());
                    mapCanvasPane.renderFrame(replayController.currentFrame());
                }
                if (replayTimelineScrubber != null) {
                    replayTimelineScrubber.setMax(replayController.durationSeconds());
                    replayTimelineScrubber.setMajorTickUnit(Math.max(5.0, replayController.durationSeconds() / 4.0));
                }
                updateControlStates();
                updateTimeDisplays();
                updateLegendMeaning();
            }
        } else {
            MonotonicClock clock = simulationController != null ? simulationController.clock() : MonotonicClock.system();
            ReplayEngine engine = ReplayEngine.createPrepared(
                    installation.bundle().scenario(), travelTimeModel, installation.replay());
            IntensityDisplayMode displayMode = simulationInstalledSettings != null
                    ? simulationInstalledSettings.intensityDisplayMode()
                    : (simulationDraftSettings != null ? simulationDraftSettings.intensityDisplayMode() : IntensityDisplayMode.MAXIMUM_REACHED);
            engine.setIntensityDisplayMode(displayMode);
            ReplayController replacement = new ReplayController(
                    installation.bundle().scenario(), engine, clock, installation.replay().durationSeconds());
            this.simulationPreparedReplay = installation.replay();
            this.simulationScenario = installation.bundle().scenario();
            this.simulationController = replacement;
            this.draftStale = false;
            this.preparingReplay = false;
            this.preparationError = null;
            if (currentMode == ApplicationMode.SIMULATION) {
                if (mapCanvasPane != null) {
                    mapCanvasPane.setMapScenario(MapScenario.fromInputs(installation.replay().inputs()));
                    mapCanvasPane.renderFrame(simulationController.currentFrame());
                }
                if (simTimelineScrubber != null) {
                    simTimelineScrubber.setMax(simulationController.durationSeconds());
                    simTimelineScrubber.setMajorTickUnit(Math.max(5.0, simulationController.durationSeconds() / 4.0));
                }
                updateSimulationWarningBanner();
                if (simSettingsStatusLabel != null) {
                    if (!simulationWarnings.isEmpty()) {
                        simSettingsStatusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #B45309;");
                        simSettingsStatusLabel.setText("Installed with domain warnings. Ready to play.");
                    } else {
                        simSettingsStatusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #475569;");
                        simSettingsStatusLabel.setText("Ready");
                    }
                }
                updateControlStates();
                updateTimeDisplays();
                updateLegendMeaning();
            }
        }
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    void updateControlStates() {
        ReplayController ctrl = getController();
        if (ctrl == null) return;
        double elapsed = ctrl.elapsedSeconds();

        Button activePlayBtn = getPlayPauseButton();
        Slider activeScrubber = getTimelineScrubber();
        Label activeStateLbl = getControlStateLabel();

        // Lock Simulation settings fields during active playback
        if (currentMode == ApplicationMode.SIMULATION) {
            boolean isPlaying = ctrl.isPlaying();
            if (epicenterLatField != null) epicenterLatField.setDisable(isPlaying);
            if (epicenterLonField != null) epicenterLonField.setDisable(isPlaying);
            if (magnitudeField != null) magnitudeField.setDisable(isPlaying);
            if (depthField != null) depthField.setDisable(isPlaying);
            if (intensityDisplaySelector != null) intensityDisplaySelector.setDisable(isPlaying);
            if (applyButton != null) applyButton.setDisable(isPlaying || preparingReplay);
        }

        if (preparingReplay) {
            if (activePlayBtn != null) activePlayBtn.setDisable(true);
            if (activeScrubber != null) activeScrubber.setDisable(true);
            if (hudStateLabel != null) {
                hudStateLabel.setText("PREPARING");
                hudStateLabel.getStyleClass().setAll("status-badge-paused");
            }
            if (activeStateLbl != null) {
                activeStateLbl.setText("Preparing " + (currentMode == ApplicationMode.SIMULATION ? "simulation..." : "replay..."));
            }
            if (statusReplayLabel != null) {
                statusReplayLabel.setText("Preparing " + (currentMode == ApplicationMode.SIMULATION ? "simulation..." : "replay..."));
            }
            return;
        }

        if (preparationError != null) {
            if (activePlayBtn != null) activePlayBtn.setDisable(true);
            if (activeScrubber != null) activeScrubber.setDisable(true);
            if (hudStateLabel != null) {
                hudStateLabel.setText("ERROR");
                hudStateLabel.getStyleClass().setAll("status-badge-finished");
            }
            String message = preparationError.getMessage() != null
                    ? preparationError.getMessage() : preparationError.getClass().getSimpleName();
            if (activeStateLbl != null) activeStateLbl.setText("Preparation failed: " + message);
            if (statusReplayLabel != null) statusReplayLabel.setText("Preparation failed: " + message);
            return;
        }

        if (currentMode == ApplicationMode.SIMULATION && draftStale) {
            if (activePlayBtn != null) activePlayBtn.setDisable(true);
            if (activeScrubber != null) activeScrubber.setDisable(false);
            if (activeStateLbl != null) {
                activeStateLbl.setText("State: DRAFT MODIFIED (Apply required)");
            }
            if (statusReplayLabel != null) {
                statusReplayLabel.setText("Simulation: DRAFT MODIFIED (Apply required)");
            }
            return;
        }

        if (activeScrubber != null) activeScrubber.setDisable(false);

        if (ctrl.isPlaying()) {
            if (activePlayBtn != null) {
                activePlayBtn.setDisable(false);
                activePlayBtn.setText("⏸  Pause");
            }
            if (hudStateLabel != null) {
                hudStateLabel.setText("PLAYING");
                hudStateLabel.getStyleClass().setAll("status-badge-playing");
            }
            if (activeStateLbl != null) activeStateLbl.setText("State: PLAYING");
            if (statusReplayLabel != null) {
                statusReplayLabel.setText(String.format("%s: PLAYING (T + %.1f s)", currentMode.displayName(), elapsed));
            }
        } else if (ctrl.isPaused()) {
            if (activePlayBtn != null) {
                activePlayBtn.setDisable(false);
                activePlayBtn.setText("▶  Play");
            }
            if (hudStateLabel != null) {
                hudStateLabel.setText("PAUSED");
                hudStateLabel.getStyleClass().setAll("status-badge-paused");
            }
            if (elapsed == 0.0) {
                if (activeStateLbl != null) activeStateLbl.setText("State: PAUSED (Ready)");
                if (statusReplayLabel != null) {
                    statusReplayLabel.setText(String.format("%s: READY (0.00s / %.2fs)", currentMode.displayName(), ctrl.durationSeconds()));
                }
            } else {
                if (activeStateLbl != null) activeStateLbl.setText("State: PAUSED");
                if (statusReplayLabel != null) {
                    statusReplayLabel.setText(String.format("%s: PAUSED (T + %.1f s)", currentMode.displayName(), elapsed));
                }
            }
        } else if (ctrl.isFinished()) {
            if (activePlayBtn != null) {
                activePlayBtn.setDisable(true);
                activePlayBtn.setText("▶  Play");
            }
            if (hudStateLabel != null) {
                hudStateLabel.setText("FINISHED");
                hudStateLabel.getStyleClass().setAll("status-badge-finished");
            }
            if (activeStateLbl != null) activeStateLbl.setText("State: FINISHED (Require Restart)");
            if (statusReplayLabel != null) {
                statusReplayLabel.setText(String.format("%s: FINISHED (Require Restart)", currentMode.displayName()));
            }
        }
    }

    void updateTimeDisplays() {
        ReplayController ctrl = getController();
        if (ctrl == null) return;
        double elapsed = ctrl.elapsedSeconds();
        int minutes = (int) (elapsed / 60.0);
        int seconds = (int) (elapsed % 60.0);
        int centis = (int) Math.round((elapsed - Math.floor(elapsed)) * 100.0);
        if (centis >= 100) centis = 99;

        String formatted = String.format("00:%02d:%02d.%02d", minutes, seconds, centis);
        if (elapsedDigitsLabel != null) elapsedDigitsLabel.setText(formatted);
        double duration = ctrl.durationSeconds();
        if (elapsedSubLabel != null) {
            String modeContext = "";
            if (currentMode == ApplicationMode.SIMULATION) {
                IntensityDisplayMode displayMode = ctrl.intensityDisplayMode();
                modeContext = displayMode == IntensityDisplayMode.CURRENT_SHAKING
                        ? " • Current shaking"
                        : " • Max reached";
            }
            elapsedSubLabel.setText(String.format("T + %.1f s  (Max: %.1f s)%s", elapsed, duration, modeContext));
        }
        Label activeControlTimeLbl = getControlTimeLabel();
        if (activeControlTimeLbl != null) {
            activeControlTimeLbl.setText(String.format("Elapsed: %.2f s / %.2f s", elapsed, duration));
        }

        Slider activeScrubber = getTimelineScrubber();
        if (activeScrubber != null && !activeScrubber.isValueChanging()) {
            if (currentMode == ApplicationMode.SIMULATION) {
                updatingSimScrubber = true;
                try {
                    activeScrubber.setValue(elapsed);
                } finally {
                    updatingSimScrubber = false;
                }
            } else {
                updatingReplayScrubber = true;
                try {
                    activeScrubber.setValue(elapsed);
                } finally {
                    updatingReplayScrubber = false;
                }
            }
        }
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

    // Accessors
    public ApplicationMode getCurrentMode() {
        return currentMode;
    }

    public ToggleGroup getModeToggleGroup() {
        return modeToggleGroup;
    }

    public RadioMenuItem getSimulationMenuItem() {
        return simulationMenuItem;
    }

    public RadioMenuItem getReplayMenuItem() {
        return replayMenuItem;
    }

    public Scenario getScenario() {
        return getInstalledScenario();
    }

    public Scenario getInstalledScenario() {
        return currentMode == ApplicationMode.SIMULATION ? simulationScenario : replayScenario;
    }

    public Scenario getSimulationScenario() {
        return simulationScenario;
    }

    public Scenario getReplayScenario() {
        return replayScenario;
    }

    public CaliforniaOutline getOutline() {
        return outline;
    }

    public ReplayController getController() {
        return currentMode == ApplicationMode.SIMULATION ? simulationController : replayController;
    }

    public ReplayController getSimulationController() {
        return simulationController;
    }

    public ReplayController getReplayController() {
        return replayController;
    }

    public MapCanvasPane getMapCanvasPane() {
        return mapCanvasPane;
    }

    public MapScenario getActiveMapScenario() {
        if (currentMode == ApplicationMode.SIMULATION) {
            return MapScenario.fromInputs(simulationPreparedReplay.inputs());
        }
        ScenarioLoader.ScenarioBundle bundle = scenarioBundles.get(selectedReplayEvent);
        ScenarioReferences refs = bundle != null ? bundle.references() : new ScenarioReferences();
        return MapScenario.fromLegacyScenario(replayScenario, refs);
    }

    public SimulationSiteCatalog getSimulationSiteCatalog() {
        return simulationSiteCatalog;
    }

    public void setSimulationSiteCatalog(SimulationSiteCatalog simulationSiteCatalog) {
        this.simulationSiteCatalog = simulationSiteCatalog;
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

    public MenuItem getReplayMenuItemCompat() {
        return replayMenuItem;
    }

    public Menu getSettingsMenu() {
        return settingsMenu;
    }

    public Button getSettingsButton() {
        return settingsButton;
    }

    public ComboBox<String> getEventSelector() {
        return currentMode == ApplicationMode.REPLAY ? eventSelector : null;
    }

    public ComboBox<String> getReplayEventSelector() {
        return eventSelector;
    }

    public ComboBox<MmiMode> getMmiModeSelector() {
        return currentMode == ApplicationMode.REPLAY ? mmiModeSelector : null;
    }

    public ComboBox<MmiMode> getReplayMmiModeSelector() {
        return mmiModeSelector;
    }

    public MmiMode getSelectedMmiMode() {
        return selectedMmiMode;
    }

    public PreparedReplay getPreparedReplay() {
        return currentMode == ApplicationMode.SIMULATION ? simulationPreparedReplay : replayPreparedReplay;
    }

    public boolean isPreparingReplay() {
        return preparingReplay;
    }

    public Throwable getPreparationError() {
        return preparationError;
    }

    public long getPreparationGeneration() {
        return preparationGeneration.get();
    }

    public CompletableFuture<?> getPreparationFuture() {
        return preparationFuture;
    }

    public Button getPlayPauseButton() {
        return currentMode == ApplicationMode.SIMULATION ? simPlayPauseButton : replayPlayPauseButton;
    }

    public Button getRestartButton() {
        return currentMode == ApplicationMode.SIMULATION ? simRestartButton : replayRestartButton;
    }

    public Slider getTimelineScrubber() {
        return currentMode == ApplicationMode.SIMULATION ? simTimelineScrubber : replayTimelineScrubber;
    }

    public Label getHudTitleLabel() {
        return hudTitleLabel;
    }

    public Label getHudStateLabel() {
        return hudStateLabel;
    }

    public Label getControlStateLabel() {
        return currentMode == ApplicationMode.SIMULATION ? simControlStateLabel : replayControlStateLabel;
    }

    public Label getElapsedDigitsLabel() {
        return elapsedDigitsLabel;
    }

    public Label getElapsedSubLabel() {
        return elapsedSubLabel;
    }

    public Label getControlTimeLabel() {
        return currentMode == ApplicationMode.SIMULATION ? simControlTimeLabel : replayControlTimeLabel;
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

    public VBox getSimulationSidebar() {
        return simulationSidebar;
    }

    public VBox getReplaySidebar() {
        return replaySidebar;
    }

    public TextField getEpicenterLatField() {
        return epicenterLatField;
    }

    public TextField getEpicenterLonField() {
        return epicenterLonField;
    }

    public TextField getMagnitudeField() {
        return magnitudeField;
    }

    public TextField getDepthField() {
        return depthField;
    }

    public ComboBox<String> getIntensityDisplaySelector() {
        return intensityDisplaySelector;
    }

    public Button getApplyButton() {
        return applyButton;
    }

    public Button getSaveButton() {
        return saveButton;
    }

    public Button getImportButton() {
        return importButton;
    }

    public SimulationScenarioSettings getSimulationInstalledSettings() {
        return simulationInstalledSettings;
    }

    public SimulationScenarioSettings getSimulationDraftSettings() {
        return simulationDraftSettings;
    }

    public List<String> getSimulationWarnings() {
        return simulationWarnings;
    }

    public boolean isDraftStale() {
        return draftStale;
    }

    public Label getSimSettingsStatusLabel() {
        return simSettingsStatusLabel;
    }

    public VBox getSimWarningBanner() {
        return simWarningBanner;
    }

    public Label getSimWarningBannerLabel() {
        return simWarningBannerLabel;
    }

    public Label getSimLegendMeaningLabel() {
        return simLegendMeaningLabel;
    }

    public Label getReplayLegendMeaningLabel() {
        return replayLegendMeaningLabel;
    }

    void setStartupErrorForTesting(Throwable t) {
        this.startupError = t;
    }

    public static void main(String[] args) {
        launch(args);
    }

    private record PreparedInstallation(ScenarioLoader.ScenarioBundle bundle, PreparedReplay replay, ApplicationMode mode) {}

    private static final class PreparationThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "calquake-replay-preparation");
            thread.setDaemon(true);
            return thread;
        }
    }
}
