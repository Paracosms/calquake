package io.github.paracosms.calquake.regression;

import io.github.paracosms.calquake.core.*;
import io.github.paracosms.calquake.data.ScenarioLoader;
import io.github.paracosms.calquake.data.SimulationSiteCatalog;
import io.github.paracosms.calquake.testsupport.FakeMonotonicClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ReplayPipelineRegressionTest {

    private Scenario scenario;
    private FakeMonotonicClock clock;
    private ReplayController controller;
    private ReplayEngine engine;

    private static final TravelTimeModel LINEAR_MODEL = new TravelTimeModel() {
        @Override
        public double travelTimeSeconds(String phaseFamily, double distanceKm, double depthKm) {
            return verticalTravelTimeSeconds(phaseFamily, depthKm)
                    + distanceKm / velocityKmPerSecond(phaseFamily);
        }

        @Override
        public double verticalTravelTimeSeconds(String phaseFamily, double depthKm) {
            return depthKm / velocityKmPerSecond(phaseFamily);
        }

        @Override
        public Optional<PhaseArrival> earliestArrival(
                String phaseFamily,
                double distanceKm,
                double depthKm
        ) {
            return Optional.of(new PhaseArrival(
                    phaseFamily,
                    travelTimeSeconds(phaseFamily, distanceKm, depthKm),
                    0.0,
                    distanceKm,
                    depthKm));
        }

        private double velocityKmPerSecond(String phaseFamily) {
            return "P".equalsIgnoreCase(phaseFamily) ? 4.0 : 2.0;
        }
    };

    @BeforeEach
    void setUp() {
        scenario = new ScenarioLoader().loadDefaultScenario();
        engine = ReplayEngine.create(scenario, HadleyKanamoriTauPModel.create());
        clock = new FakeMonotonicClock(1_000_000_000L);
        controller = new ReplayController(scenario, engine, clock);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ridgecrest", "Northridge"})
    void simulatedTimelineIsProgressiveDeterministicAndPeakNormalized(String eventName) {
        ScenarioLoader.ScenarioBundle bundle = new ScenarioLoader().loadScenarioBundle(eventName);
        ReplayPreparer preparer = new ReplayPreparer(new HadleyKanamoriTauPModel());
        PreparedReplay replay = preparer.prepare(bundle.inputs(), bundle.references(), MmiMode.SIMULATED);
        assertEquals(MmiMode.SIMULATED, replay.mode());
        assertTrue(replay.references().isEmpty());
        Worden2012Gmice gmice = new Worden2012Gmice();
        for (IntensityTimeline timeline : replay.timelinesBySiteId().values()) {
            double previousMmi = Double.NEGATIVE_INFINITY;
            for (IntensityTimeline.Sample sample : timeline.samples()) {
                if (sample.mmi().isPresent()) {
                    assertTrue(sample.mmi().getAsDouble() + 1.0e-12 >= previousMmi);
                    previousMmi = sample.mmi().getAsDouble();
                }
            }
            double peakPgv = timeline.predictedPeakPgv().orElseThrow();
            LocationIntensityState finalState = timeline.stateAt(replay.durationSeconds());
            assertEquals(peakPgv, finalState.currentPgvCmPerSecond().orElseThrow(), 1.0e-9);
            assertEquals(gmice.fromPgvCmPerSecond(peakPgv), finalState.currentMmi().orElseThrow(), 1.0e-9);
            assertFalse(timeline.stateAt(0.0).isRevealed());

            // Stage D dual presentation checks on the same timeline:
            // 1. MAXIMUM_REACHED is monotonically non-decreasing
            // 2. CURRENT_SHAKING rises and falls (envelope-derived)
            // 3. Both are deterministic at identical requested timestamps
            double previousMaxMmi = Double.NEGATIVE_INFINITY;
            double peakCurrentMmi = Double.NEGATIVE_INFINITY;
            boolean sawRise = false;
            boolean sawFall = false;
            for (IntensityTimeline.Sample sample : timeline.samples()) {
                double t = sample.elapsedSeconds();
                LocationIntensityState maxState = timeline.stateAt(t, IntensityDisplayMode.MAXIMUM_REACHED);
                LocationIntensityState currState = timeline.stateAt(t, IntensityDisplayMode.CURRENT_SHAKING);

                assertEquals(maxState, timeline.stateAt(t), "Default stateAt(t) must equal MAXIMUM_REACHED");

                if (maxState.currentMmi().isPresent()) {
                    assertTrue(maxState.currentMmi().getAsDouble() + 1.0e-12 >= previousMaxMmi,
                            "Maximum reached curve must never decrease");
                    previousMaxMmi = maxState.currentMmi().getAsDouble();
                }

                if (currState.currentMmi().isPresent()) {
                    double currVal = currState.currentMmi().getAsDouble();
                    if (currVal > peakCurrentMmi) {
                        sawRise = true;
                        peakCurrentMmi = currVal;
                    } else if (currVal < peakCurrentMmi - 0.05) {
                        sawFall = true;
                    }
                } else if (sawRise) {
                    assertEquals(IntensityStatus.SHAKING_ENDED, currState.status());
                }
            }
            assertTrue(sawRise, "Current shaking must rise to peak");
            assertTrue(sawFall, "Current shaking must fall after peak");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ridgecrest", "Northridge"})
    void halvingTimelineStepMeetsFinalAndThresholdConvergencePolicy(String eventName) {
        ScenarioInputs inputs = new ScenarioLoader().loadScenarioInputs(eventName);
        HadleyKanamoriTauPModel travelTimes = new HadleyKanamoriTauPModel();
        PreparedIntensityResult coarse = new SimulatedMmiModel(
                travelTimes, new Bssa14GroundMotion(), new EmpiricalEnvelopeModel(),
                new Worden2012Gmice(), 0.05).prepare(inputs);
        PreparedIntensityResult fine = new SimulatedMmiModel(
                travelTimes, new Bssa14GroundMotion(), new EmpiricalEnvelopeModel(),
                new Worden2012Gmice(), 0.025).prepare(inputs);

        Map<String, IntensityTimeline> fineBySite = fine.timelines().stream()
                .collect(java.util.stream.Collectors.toMap(t -> t.site().id(), t -> t));
        for (IntensityTimeline coarseTimeline : coarse.timelines()) {
            IntensityTimeline fineTimeline = fineBySite.get(coarseTimeline.site().id());
            assertEquals(coarseTimeline.finalMmi().orElseThrow(),
                    fineTimeline.finalMmi().orElseThrow(), 0.01);
            for (double threshold : new double[]{3.5, 4.5, 5.5}) {
                OptionalDouble coarseCrossing = firstCrossing(coarseTimeline, threshold);
                OptionalDouble fineCrossing = firstCrossing(fineTimeline, threshold);
                assertEquals(coarseCrossing.isPresent(), fineCrossing.isPresent(),
                        coarseTimeline.site().id() + " threshold " + threshold);
                if (coarseCrossing.isPresent()) {
                    assertEquals(fineCrossing.getAsDouble(), coarseCrossing.getAsDouble(), 0.1,
                            coarseTimeline.site().id() + " threshold " + threshold);
                }
            }
        }
    }

    private static OptionalDouble firstCrossing(IntensityTimeline timeline, double threshold) {
        return timeline.samples().stream()
                .filter(sample -> sample.mmi().isPresent()
                        && sample.mmi().getAsDouble() >= threshold)
                .mapToDouble(IntensityTimeline.Sample::elapsedSeconds)
                .findFirst();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ridgecrest", "Northridge"})
    void historicalPeakAppearsExactlyAtSAndDurationIsLatestSPlusTen(String eventName) {
        ScenarioLoader loader = new ScenarioLoader();
        ScenarioLoader.ScenarioBundle bundle = loader.loadScenarioBundle(eventName);
        PreparedReplay replay = new RecordedReplayPreparer(new HadleyKanamoriTauPModel())
                .prepare(bundle.inputs(), bundle.references());
        double latestS = replay.timelinesBySiteId().values().stream()
                .mapToDouble(IntensityTimeline::sArrivalSeconds).max().orElseThrow();
        assertEquals(latestS + 10.0, replay.durationSeconds(), 1.0e-9);
        for (IntensityTimeline timeline : replay.timelinesBySiteId().values()) {
            assertTrue(timeline.stateAt(timeline.sArrivalSeconds() - 1.0e-6).currentMmi().isEmpty());
            double expected = bundle.references().require(timeline.site().id()).shakeMapPeakMmi().orElseThrow();
            assertEquals(expected, timeline.stateAt(timeline.sArrivalSeconds()).currentMmi().orElseThrow(), 0.0);
            assertEquals(expected, timeline.stateAt(replay.durationSeconds()).currentMmi().orElseThrow(), 0.0);
        }
    }

    @Test
    void recordedReplayWorksWithAbsentFaultMechanismAndVs30() {
        ScenarioLoader loader = new ScenarioLoader();
        ScenarioLoader.ScenarioBundle recordedBundle = loader.loadRecordedScenarioBundle("Ridgecrest");
        PreparedReplay replayFromRecorded = new RecordedReplayPreparer(new HadleyKanamoriTauPModel())
                .prepare(recordedBundle.inputs(), recordedBundle.references());
        assertNotNull(replayFromRecorded);

        // Compare with bundle loaded with full scientific inputs
        ScenarioLoader.ScenarioBundle fullBundle = loader.loadScenarioBundle("Ridgecrest");
        PreparedReplay replayFromFull = new RecordedReplayPreparer(new HadleyKanamoriTauPModel())
                .prepare(fullBundle.inputs(), fullBundle.references());

        assertEquals(replayFromFull.durationSeconds(), replayFromRecorded.durationSeconds(), 1.0e-9);
        for (String siteId : replayFromRecorded.timelinesBySiteId().keySet()) {
            var tRecorded = replayFromRecorded.timelinesBySiteId().get(siteId);
            var tFull = replayFromFull.timelinesBySiteId().get(siteId);
            assertEquals(tFull.sArrivalSeconds(), tRecorded.sArrivalSeconds(), 1.0e-9);
            assertEquals(tFull.pArrivalSeconds(), tRecorded.pArrivalSeconds(), 1.0e-9);
            assertEquals(tFull.finalMmi().orElseThrow(), tRecorded.finalMmi().orElseThrow(), 1.0e-9);
        }
    }

    @Test
    @DisplayName("Verify determinism: identical inputs and time yield identical FrameState")
    void testFrameStateDeterminism() {
        ScenarioLoader loader = new ScenarioLoader();
        Scenario defaultScenario = loader.loadDefaultScenario();
        HadleyKanamoriTauPModel defaultModel = HadleyKanamoriTauPModel.create();
        ReplayEngine replayEngine = ReplayEngine.create(defaultScenario, defaultModel);

        double[] times = {0.0, 1.0, 1.396825, 2.0, 2.4165, 5.0, 15.0, 30.0, 60.0, 120.0};

        for (double t : times) {
            FrameState f1 = replayEngine.frameAt(defaultScenario, t);
            FrameState f2 = replayEngine.frameAt(defaultScenario, t);

            assertEquals(f1, f2, "FrameState must be strictly equal for identical input at t=" + t);
        }
    }

    @Test
    @DisplayName("Verify second tiny synthetic scenario with distinct location, depth, and time")
    void testSyntheticScenario() {
        // Synthetic M6.5 earthquake in Northern California / East Bay
        String syntheticId = "nc73999999";
        Instant syntheticOrigin = Instant.parse("2026-06-01T12:00:00.000Z");
        GeoPoint syntheticEpicenter = new GeoPoint(37.8000, -122.2500); // Oakland / Hayward Fault
        double syntheticDepthKm = 15.0; // 15 km depth vs Ridgecrest's 8 km
        double syntheticMag = 6.5;

        EarthquakeEvent syntheticEvent = new EarthquakeEvent(
                syntheticId, "nc", "Synthetic Hayward Fault M6.5",
                syntheticOrigin, syntheticEpicenter, syntheticDepthKm, syntheticMag, "mw", "https://example.org/synthetic"
        );

        // Synthetic reference locations
        GeoPoint sfPoint = new GeoPoint(37.7749, -122.4194);
        ReferenceLocation sf = new ReferenceLocation(
                "San Francisco", "San Francisco city", "0667000", "02411786", "25",
                sfPoint,
                new ReferenceLocation.SampledGridNode(sfPoint, 0.1),
                new ReferenceLocation.PeakIntensity(8.1, 8.1, "VIII", "Severe", "Moderate/heavy", "#ff8500"),
                null
        );

        GeoPoint sjPoint = new GeoPoint(37.3382, -121.8863);
        ReferenceLocation sj = new ReferenceLocation(
                "San Jose", "San Jose city", "0668000", "02411788", "25",
                sjPoint,
                new ReferenceLocation.SampledGridNode(sjPoint, 0.2),
                new ReferenceLocation.PeakIntensity(5.8, 5.8, "VI", "Strong", "Light", "#fffa00"),
                null
        );

        GeoPoint sacPoint = new GeoPoint(38.5816, -121.4944);
        ReferenceLocation sac = new ReferenceLocation(
                "Sacramento", "Sacramento city", "0664000", "02411780", "25",
                sacPoint,
                new ReferenceLocation.SampledGridNode(sacPoint, 0.3),
                new ReferenceLocation.PeakIntensity(4.2, 4.2, "IV", "Light", "None", "#7ffffa"),
                null
        );

        Scenario syntheticScenario = new Scenario(syntheticEvent, List.of(sf, sj, sac));

        // Create an engine using only the generic scenario contract.
        HadleyKanamoriTauPModel taupModel = HadleyKanamoriTauPModel.create();
        ReplayEngine synEngine = ReplayEngine.create(syntheticScenario, taupModel);

        FrameState initFrame = synEngine.frameAt(syntheticScenario, 0.0);
        assertEquals(0.0, initFrame.elapsedSeconds(), 1e-9);
        assertEquals(syntheticEpicenter, initFrame.epicenter());
        assertEquals(3, initFrame.locationIntensities().size());
        assertEquals("San Francisco", initFrame.locationIntensities().get(0).city());
        assertEquals(8.1, initFrame.locationIntensities().get(0).mmiSourceDecimal(), 1e-9);
        assertEquals("VIII", initFrame.locationIntensities().get(0).mmiRoman());

        // Verify vertical travel time for 15.0 km depth in Hadley-Kanamori model:
        // Layer 1 (0 - 5.5 km): 5.5 / 5.5 = 1.000 s
        // Layer 2 (5.5 - 15.0 km): 9.5 / 6.3 = 1.5079365 s
        // Expected vertical P: ~2.5079 s
        // Expected vertical S: ~2.5079 * 1.73 = ~4.3387 s
        double expectedVertP = (5.5 / 5.5) + (9.5 / 6.3);
        double expectedVertS = expectedVertP * 1.73;

        TravelTimeCurve pCurve = synEngine.wavefronts().pCurve();
        TravelTimeCurve sCurve = synEngine.wavefronts().sCurve();
        assertEquals(expectedVertP, pCurve.verticalTimeSeconds(), 1e-4);
        assertEquals(expectedVertS, sCurve.verticalTimeSeconds(), 1e-4);

        // At t = 2.0 s (before synthetic vertical P arrival), no P or S front
        WavefrontRadii radii2s = synEngine.wavefronts().radiiAt(2.0);
        assertFalse(radii2s.hasP());
        assertFalse(radii2s.hasS());

        // At t = 3.5 s (after P vertical arrival but before S vertical arrival)
        WavefrontRadii radii35s = synEngine.wavefronts().radiiAt(3.5);
        assertTrue(radii35s.hasP());
        assertFalse(radii35s.hasS());
        assertTrue(radii35s.pRadiusKm() > 0.0);

        // At t = 6.0 s (both P and S have arrived)
        WavefrontRadii radii6s = synEngine.wavefronts().radiiAt(6.0);
        assertTrue(radii6s.hasP());
        assertTrue(radii6s.hasS());
        assertTrue(radii6s.pRadiusKm() > radii6s.sRadiusKm());
    }

    @Test
    void scenarioFactoryPrecomputesThroughExplicitNonDefaultDuration() {
        Scenario defaultScen = new ScenarioLoader().loadDefaultScenario();

        PrecomputedWavefronts wavefronts =
                PrecomputedWavefronts.forScenario(defaultScen, LINEAR_MODEL, 180.0);

        assertTrue(wavefronts.pCurve().maxTimeSeconds() >= 185.0);
        assertTrue(wavefronts.sCurve().maxTimeSeconds() >= 185.0);
        assertTrue(wavefronts.radiiAt(180.0).hasP());
        assertTrue(wavefronts.radiiAt(180.0).hasS());
    }

    @Test
    void elapsedTimeTracksClockRatherThanTickFrequency() {
        controller.play();
        clock.advanceSeconds(10.0);
        FrameState singleTickFrame = controller.tick();
        assertEquals(10.0, singleTickFrame.elapsedSeconds(), 1e-6);
        assertTrue(singleTickFrame.frontRadii().hasP());
        assertTrue(singleTickFrame.frontRadii().hasS());

        controller.restart();
        controller.play();
        for (int i = 0; i < 600; i++) {
            clock.advanceSeconds(1.0 / 60.0);
            controller.tick();
        }

        assertEquals(10.0, controller.elapsedSeconds(), 1e-4,
                "One long tick and 600 short ticks must represent the same elapsed time");
    }

    @Test
    void pauseResumeExcludesInactiveTimeAcrossRepeatedCycles() {
        double expectedElapsed = 0.0;
        controller.play();

        for (double activeSeconds : new double[]{2.0, 3.5, 1.5}) {
            clock.advanceSeconds(activeSeconds);
            controller.pause();
            expectedElapsed += activeSeconds;
            assertEquals(expectedElapsed, controller.elapsedSeconds(), 1e-6);
            assertTrue(controller.isPaused());

            clock.advanceSeconds(20.0);
            controller.tick();
            assertEquals(expectedElapsed, controller.elapsedSeconds(), 1e-6,
                    "Inactive wall-clock time must not advance the replay");
            controller.play();
        }

        clock.advanceSeconds(1.0);
        controller.tick();
        assertEquals(8.0, controller.elapsedSeconds(), 1e-6);
        assertTrue(controller.isPlaying());
    }

    @Test
    void explicitShortDurationControlsTickPauseSeekAndFinish() {
        ReplayEngine shortEngine = ReplayEngine.create(scenario, HadleyKanamoriTauPModel.create());
        ReplayController shortReplay = new ReplayController(scenario, shortEngine, clock, 12.5);

        assertEquals(12.5, shortReplay.durationSeconds(), 1e-9);

        shortReplay.play();
        clock.advanceSeconds(20.0);
        FrameState finalFrame = shortReplay.tick();

        assertTrue(shortReplay.isFinished());
        assertEquals(12.5, shortReplay.elapsedSeconds(), 1e-9);
        assertEquals(12.5, finalFrame.elapsedSeconds(), 1e-9);

        shortReplay.seek(4.0);
        assertTrue(shortReplay.isPaused());
        assertEquals(4.0, shortReplay.elapsedSeconds(), 1e-9);

        shortReplay.seek(99.0);
        assertTrue(shortReplay.isFinished());
        assertEquals(12.5, shortReplay.elapsedSeconds(), 1e-9);
    }

    @Test
    void everyScientificInputDimensionChangesTheSignature() {
        ScenarioInputs base = new ScenarioLoader().loadScenarioInputs("Ridgecrest");
        String signature = InputSignature.compute(base, MmiMode.SIMULATED);

        assertChanged(signature, withEvent(base, event(base, 7.2, base.event().depthKm(),
                base.event().epicenter(), base.event().ruptureGeometry(), base.event().mechanism())));
        assertChanged(signature, withEvent(base, event(base, base.event().magnitude(), 8.5,
                base.event().epicenter(), base.event().ruptureGeometry(), base.event().mechanism())));
        assertChanged(signature, withEvent(base, event(base, base.event().magnitude(), base.event().depthKm(),
                new GeoPoint(35.8, -117.7), base.event().ruptureGeometry(), base.event().mechanism())));

        RuptureGeometry rupture = base.event().ruptureGeometry().orElseThrow();
        RuptureGeometry changedRupture = new RuptureGeometry(
                rupture.surfaceProjectionParts(), rupture.topDepthKm(), rupture.bottomDepthKm(),
                rupture.strikeDegrees(), rupture.dipDegrees(), rupture.sourceId(),
                rupture.sourceSha256() + "-changed", rupture.generated());
        assertChanged(signature, withEvent(base, event(base, base.event().magnitude(), base.event().depthKm(),
                base.event().epicenter(), Optional.of(changedRupture), base.event().mechanism())));

        Mechanism mechanism = base.event().mechanism().orElseThrow();
        Mechanism changedMechanism = new Mechanism(
                mechanism.rakeDegrees() + 1.0, mechanism.strikeDegrees(), mechanism.dipDegrees(),
                mechanism.style(), mechanism.provenance());
        assertChanged(signature, withEvent(base, event(base, base.event().magnitude(), base.event().depthKm(),
                base.event().epicenter(), base.event().ruptureGeometry(), Optional.of(changedMechanism))));

        List<SimulationSite> sites = new ArrayList<>(base.sites());
        SimulationSite first = sites.getFirst();
        SiteCondition condition = first.siteCondition().orElseThrow();
        sites.set(0, new SimulationSite(first.id(), first.displayName(), first.coordinates(),
                new SiteCondition(condition.vs30MetersPerSecond() + 1.0,
                        condition.provenance(), condition.sourceId())));
        assertChanged(signature, new ScenarioInputs(base.event(), sites,
                base.travelTimeConfiguration(), base.scientificConfiguration()));

        LinkedHashMap<String, String> versions = new LinkedHashMap<>(
                base.scientificConfiguration().versionIds());
        versions.put("groundMotion", "deliberately-different-model-version");
        assertChanged(signature, new ScenarioInputs(base.event(), base.sites(),
                base.travelTimeConfiguration(), new ScientificConfiguration(
                versions, base.scientificConfiguration().numericParameters())));

        // Folded negative assertion from customScenarioSettings:
        // Changing intensity display mode must not alter scientific InputSignature
        TravelTimeModel ttModel = new HadleyKanamoriTauPModel();
        List<SimulationSite> catalogSites = SimulationSiteCatalog.loadDefault().sites();
        SimulationScenarioSettings defaultSettings = SimulationScenarioSettings.createDefault();
        ScenarioInputs settingsInputs = ScenarioInputs.forCustomScenario(defaultSettings, catalogSites, ttModel);
        String baseSettingsSig = InputSignature.compute(settingsInputs, MmiMode.SIMULATED);
        SimulationScenarioSettings diffMode = defaultSettings.withIntensityDisplayMode(IntensityDisplayMode.CURRENT_SHAKING);
        ScenarioInputs modeInputs = ScenarioInputs.forCustomScenario(diffMode, catalogSites, ttModel);
        String modeSig = InputSignature.compute(modeInputs, MmiMode.SIMULATED);
        assertEquals(baseSettingsSig, modeSig, "Changing intensity display mode must not alter scientific InputSignature");
    }

    @Test
    @DisplayName("Verify city MMI rating reveal occurs strictly upon S-wave arrival")
    void testSWaveArrivalMmiRevealTiming() {
        // At t = 0.0 s, no location has received S wave
        FrameState f0 = engine.frameAt(0.0);
        for (LocationIntensityState loc : f0.locationIntensities()) {
            assertFalse(loc.sWaveArrived(), loc.city() + " must not have S-wave arrived at t=0");
            assertFalse(loc.isRevealed(), loc.city() + " must not be revealed at t=0");
        }

        // Check arrival times are ordered by distance from Ridgecrest epicenter:
        // Ridgecrest (~16.8 km, ~5.8s), Trona (~23 km, ~7.7s), Bakersfield (~138 km, ~40s),
        // Los Angeles (~207 km, ~59s), Fresno (~228 km, ~64s)
        LocationIntensityState rc = f0.locationIntensities().get(0);
        LocationIntensityState trona = f0.locationIntensities().get(1);
        LocationIntensityState bakersfield = f0.locationIntensities().get(2);
        LocationIntensityState la = f0.locationIntensities().get(3);
        LocationIntensityState fresno = f0.locationIntensities().get(4);

        double tRc = rc.sArrivalTimeSeconds();
        double tTrona = trona.sArrivalTimeSeconds();
        double tBakersfield = bakersfield.sArrivalTimeSeconds();
        double tLa = la.sArrivalTimeSeconds();
        double tFresno = fresno.sArrivalTimeSeconds();

        assertTrue(tRc < tTrona);
        assertTrue(tTrona < tBakersfield);
        assertTrue(tBakersfield < tLa);
        assertTrue(tLa < tFresno);

        // Before Ridgecrest S arrival: none revealed
        FrameState fBeforeRc = engine.frameAt(tRc - 0.1);
        assertFalse(fBeforeRc.locationIntensities().get(0).isRevealed());

        // After Ridgecrest S arrival: only Ridgecrest revealed
        FrameState fAfterRc = engine.frameAt(tRc + 0.1);
        assertTrue(fAfterRc.locationIntensities().get(0).isRevealed(), "Ridgecrest revealed");
        assertFalse(fAfterRc.locationIntensities().get(1).isRevealed(), "Trona not revealed");

        // After Trona S arrival: Ridgecrest & Trona revealed
        FrameState fAfterTrona = engine.frameAt(tTrona + 0.1);
        assertTrue(fAfterTrona.locationIntensities().get(0).isRevealed());
        assertTrue(fAfterTrona.locationIntensities().get(1).isRevealed());
        assertFalse(fAfterTrona.locationIntensities().get(2).isRevealed());

        // After Bakersfield S arrival: Ridgecrest, Trona, and Bakersfield revealed
        FrameState fAfterBakersfield = engine.frameAt(tBakersfield + 0.1);
        assertTrue(fAfterBakersfield.locationIntensities().get(2).isRevealed());
        assertFalse(fAfterBakersfield.locationIntensities().get(3).isRevealed());

        // Before Fresno S arrival: Fresno not yet revealed
        FrameState fBeforeFresno = engine.frameAt(tFresno - 0.1);
        assertFalse(fBeforeFresno.locationIntensities().get(4).isRevealed(), "Fresno not revealed before its arrival");

        // After Fresno S arrival: all 5 locations revealed
        FrameState fAfterFresno = engine.frameAt(tFresno + 0.1);
        for (LocationIntensityState loc : fAfterFresno.locationIntensities()) {
            assertTrue(loc.isRevealed(), loc.city() + " must be revealed after its arrival");
        }
    }

    private static void assertChanged(String baseSignature, ScenarioInputs changed) {
        assertNotEquals(baseSignature, InputSignature.compute(changed, MmiMode.SIMULATED));
    }

    private static ScenarioInputs withEvent(ScenarioInputs base, EventSource event) {
        return new ScenarioInputs(event, base.sites(), base.travelTimeConfiguration(),
                base.scientificConfiguration());
    }

    private static EventSource event(
            ScenarioInputs base, double magnitude, double depth, GeoPoint epicenter,
            Optional<RuptureGeometry> rupture, Optional<Mechanism> mechanism) {
        EventSource event = base.event();
        return new EventSource(event.id(), event.network(), event.title(), event.originUtc(), magnitude,
                event.magnitudeType(), epicenter, depth, rupture, mechanism, event.metadata());
    }
}
