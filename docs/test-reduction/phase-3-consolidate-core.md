# Phase 3 — Consolidate the simulation regression core

**Read [README.md](README.md) first — the guardrails there apply. Guardrail 1 (never alter a pinned number) is the whole point of this phase.**
**Prerequisite:** Phases 1 and 2 merged.

## Goal

Collapse 24 test classes into **6 regression suites** that exist for one reason: if the simulation math changes, the build breaks. Everything that survives is either a frozen numeric output, a physical invariant, or a data-integrity hash.

**Input:** 20 files in `core/` + 4 catalog/outline files in `data/` = 2,483 lines, ~72 tests.
**Output:** 6 files, ~900 lines, ~48 tests.

## Scope (exclusive ownership)

- All of `src/test/java/io/github/paracosms/calquake/core/**` (20 files)
- `data/CaliforniaFaultCatalogTest.java`, `data/CaliforniaOutlineTest.java`, `data/CaliforniaVs30GridTest.java`, `data/FaultSectionCatalogTest.java`

**Do not touch `data/ScenarioLoaderTest.java`** — Phase 4 owns it. Do not touch `testsupport/` helpers (they are consumed as-is).

## Placement

Create the six suites in a new package **`io.github.paracosms.calquake.regression`** (`src/test/java/io/github/paracosms/calquake/regression/`). These classes deliberately cut across `core` and `data`, so mirroring production packages no longer makes sense. Delete the `core/` test directory and the four `data/` files once migration is complete.

## Migration method (follow this literally)

1. Work **one suite at a time**, in the order A → F below.
2. For each kept test: **copy the method body, its annotations, and its `@CsvSource`/`@ValueSource` tables verbatim** from the source file. Do not retype expected values. Do not reformat numbers. Do not widen or tighten a tolerance.
3. Merge the source files' `@BeforeAll`/`@BeforeEach` setup into one setup per suite. If two sources build the same model, build it once.
4. Keep original method names unless two collide; on collision, prefix with the concern (e.g. `pWave...`, `sWave...`). Preserving names keeps `git log -S` useful.
5. Iterate with `./mvnw test -Dtest=<NewSuiteName>` until green, then move to the next suite.
6. Delete a source file only after all of its kept tests pass in their new home.
7. **If a copied assertion fails, stop and report.** Do not adjust the expectation. Either the migration dropped required setup, or production behavior differs from what you assumed — both need a human.

---

## Suite A — `TravelTimeRegressionTest`

Sources: `HadleyKanamoriTauPModelTest` (103 L), `TravelTimeCurveTest` (221 L), `HomogeneousSphereOracleTest` (107 L).

### Keep

| From | Method (line) | Why |
|---|---|---|
| HadleyKanamori | `testVerticalTravelTimes` (L32) | Frozen 1.396825 s P / ×1.73 S at 8 km from the 5.5/5.5 + 2.5/6.3 layer model. Also pins `travelTimeSeconds(0)` == vertical. |
| HadleyKanamori | `testKinematicProportionality` (L51, 11 distances) | Ts/Tp = 1.73 ± 0.005 — the model's defining kinematic constraint. |
| HadleyKanamori | `testTravelTimeMonotonicity` (L63) | Physical invariant, cheap (61 steps). **This is the surviving monotonicity check** — see drops. |
| HadleyKanamori | `testBenchmarkRayPenetrationDepth` (L79) | Deepest pierce ≤ 16.5 km over 1–150 km. Guards against rays leaking into the mantle. **Sole owner of this assertion after this phase.** |
| TravelTimeCurve | `testCurveCoverage` (L41) | Frozen wavefront radii: P 59.92 / S 33.24 km @ 10 s, P 196.70 / S 106.25 km @ 30 s (±0.1), plus coverage bounds. Highest-value regression pin in the file. |
| TravelTimeCurve | `testPreVerticalArrivalAbsence` (L68) | Vertical P 1.3968 s / S 2.4165 s, `invertRadiusKm` empty before arrival and exactly 0.0 at it, `hasP`/`hasS` gating, P radius > S radius. |
| TravelTimeCurve | `testPInversionAccuracy` (L119, 19 values) + `testSInversionAccuracy` (L138, 18 values) | Round-trip: inverted radius re-fed to TauP reproduces elapsed time within 0.01 s. |
| HomogeneousSphere | `testHomogeneousSphereOracleAccuracy` (L46) | The **only** check validating TauP integration against closed-form physics rather than pinned numbers. **Reduce `@ValueSource` to `{1.0, 100.0, 300.0}`** (near/mid/far) — each case rebuilds a `TauModel`, so 11 cases is 11 rebuilds for no added signal. Keep the ±0.001 s tolerance. |

### Drop

| Method | Reason |
|---|---|
| `TravelTimeCurveTest.testDensePInversionScan` (L153), `testDenseSInversionScan` (L169) | Loop vertical→120 s in 0.5 s steps ≈ 480 TauP evaluations. Same property as the parameterized inversion tables, differing only in density. |
| `TravelTimeCurveTest.testRadiusMonotonicity` (L185) | 1,200 iterations × 2 inversions. Same invariant as `testTravelTimeMonotonicity` expressed in inverse coordinates; the cheap version is kept. |
| `TravelTimeCurveTest.testInvalidInputs` (L208) | NaN/negative → empty, null/bad-depth → NPE/IAE. Argument validation. |
| `HadleyKanamoriTauPModelTest.testInputValidation` (L98) | Same. |
| `HomogeneousSphereOracleTest.testZeroDistanceVerticalDelay` (L69) | h/v = 1.3333 s duplicates `testVerticalTravelTimes`. |
| `HomogeneousSphereOracleTest.testVariedDepthAndVelocity` (L84) | 3 depths × 3 velocities = 9 `TauModel` rebuilds; the single-parameter oracle already covers the closed-form agreement. |

**Result: ~8 methods.** TauP evaluations drop from roughly 800 to under 100 — this is the largest runtime win in the phase.

---

## Suite B — `ArrivalBenchmarkGateTest`

Sources: `ScientificGateArrivalBenchmarkTest` (143 L), `NorthridgeArrivalBenchmarkTest` (92 L).

**This is the most valuable test in the repository.** It runs the model against 231 real SCEDC picks across two events and pins pass rates, mean absolute error, bias, and the exact sets of failing stations. Treat every number here as sacred.

### Structure

One class, four methods, sharing the merged `@BeforeAll` and a single private assertion helper — e.g.
`assertGate(cohort, phase, expectedPickCount, expectedPassCount, expectedPassRate, expectedMae, expectedBias, Set<String> expectedFailingStations)`.

Do **not** force this into a `@ParameterizedTest`: the two cohorts carry different extra invariants (Ridgecrest S additionally asserts all failures have negative residuals; Northridge S asserts the passing set), and branching on cohort inside a parameterized body is worse than four explicit calls.

### Keep (verbatim constants)

| Method | Frozen expectations |
|---|---|
| Ridgecrest P (from L34) | 78 picks, 75 pass, 96.15 %, MAE 0.445 s, bias −0.380 s |
| Ridgecrest S (from L54) | 16 picks, 12 pass, 75.0 %, MAE 1.435 s, failing = {CCC, B916, TPO, TEJ}, all failing residuals negative |
| Northridge P (from L37) | 132 picks, 129 pass, 97.73 %, MAE 0.4163 s, bias +0.0183 s, failing = {LA00, LA02, DGR} |
| Northridge S (from L66) | 5 picks, 2 pass, 40.0 %, MAE 2.1156 s, bias +1.4970 s, passing = {PAS, BAR}, failing = {LA00, LA02, LA04} |

Keeps `testsupport/ArrivalBenchmarkRunner` (frozen epicenters and the `max(1.0|2.0, 0.05·Tobs)` tolerance gate), `ArrivalBenchmarkResult`, `ObservedArrival`, `ObservedArrivalsFixture` in use. Do not modify them.

### Drop

| Method | Reason |
|---|---|
| `testClcSourceConsistency` (L85) | Single-station forensic audit of CI.CLC (min mantle time, model 1.659 s vs observed 0.628 s). An investigation artifact from when the discrepancy was being chased, not a regression guard. |
| `testMaxRayPenetrationDepthAcrossCohort` (L115) | Exact duplicate of the penetration-depth assertion kept in Suite A. |

**Result: 4 methods.**

---

## Suite C — `GroundMotionAndIntensityRegressionTest`

Sources: `Bssa14GroundMotionTest` (23 L), `Worden2012GmiceTest` (26 L), `EmpiricalEnvelopeTest` (31 L), `MmiLegendAndIntensitySourceTest` (170 L, partial).

### Keep

| From | Method (line) | Why |
|---|---|---|
| Bssa14 | `matchesPinnedIndependentNaturalLogPgvFixtures` (L17) | Pinned independent ln(PGV) fixtures to 1e-12 plus `exp()` consistency. Highest value-per-line in the suite — copy the `@CsvSource` table character for character. |
| Worden | `usesPgvCentimetersPerSecondAndPinnedBreakpoint` (L13) | MMI(1 cm/s) = 3.78, the breakpoint, MMI(10) = 6.05. |
| Worden | `zeroAndInvalidAmplitudeNeverEvaluateALogarithm` (L21) | Numerical-safety guard against `log(0) → −Inf` poisoning MMI. This is about GMICE numerics, not argument validation — keep it. |
| EmpiricalEnvelope | `envelopeHasOnsetRisePeakDecayAndNonnegativeCombination` (L12) | Envelope shape invariant. |
| EmpiricalEnvelope | `retainsOutOfCalibrationDomainMetadataWithoutSuppressingValues` (L26) | Out-of-domain flagging. **After Phase 2 deleted `ScenarioTest`, this is the only remaining coverage of BSSA14 domain flagging — it must survive.** |
| MmiLegend | `testLegendBinsAndBoundaries` (L73, 23 rows) | Worden bin boundaries and exact hex colors. |
| MmiLegend | `testDisplayRounding` (L42, 7 rows) | 1-decimal HALF_UP rounding. |

### Constraint

`MmiLegendAndIntensitySourceTest` has a `@BeforeAll` that builds a full `ReplayEngine` and loads the default scenario. The two kept tests operate on legend/rounding logic and should not need it. **This suite must have no `ReplayEngine` setup** — if you find a kept test genuinely requires it, that test belongs in Suite D instead; move it rather than importing the engine here.

### Drop

| Method | Reason |
|---|---|
| `MmiLegend.testNaBin` (L81) | null/NaN/−0.5 → "N/A"/`#808080`. Display fallback. If you want it cheaply, fold an N/A row into `testLegendBinsAndBoundaries` rather than keeping a separate method. |
| `MmiLegend.testIntensityTimeInvariance` (L96) | MMI constant across 7 times for recorded mode (7.2/VII/`#ffc400`). Those constants are pinned in Phase 4's scenario fidelity test, and time-invariance is implied by the S-reveal test moving to Suite D. |

### Moves out

`MmiLegend.testSWaveArrivalMmiRevealTiming` (L112) → **Suite D**. It asserts arrival ordering (Ridgecrest < Trona < Bakersfield < LA < Fresno) and that reveal flips exactly at each arrival — replay pipeline behavior, not legend behavior.

**Result: ~7 methods.**

---

## Suite D — `ReplayPipelineRegressionTest`

Sources: `SimulatedMmiTimelineTest` (134 L), `RecordedReplayPreparerTest` (63 L), `ReplayDeterminismAndSyntheticScenarioTest` (127 L), `PrecomputedWavefrontsTest` (87 L), `ReplayControllerTest` (229 L → 3), `InputSignatureAndImmutabilityTest` (164 L → 1), plus the S-reveal test from Suite C's sources.

This is the end-to-end guard: scenario in, timeline out, numbers stable. Uses `testsupport/FakeMonotonicClock`.

### Keep

| From | Method (line) | Why |
|---|---|---|
| SimulatedMmiTimeline | `simulatedTimelineIsProgressiveDeterministicAndPeakNormalized` (L19, ×2 events) | Progressive monotone MMI, peak-normalized final PGV→MMI via GMICE, not revealed at t=0, rise-then-fall of CURRENT_SHAKING. The core sim integration test. |
| SimulatedMmiTimeline | `halvingTimelineStepMeetsFinalAndThresholdConvergencePolicy` (L98, ×2 events) | Halving the step keeps final MMI and the 3.5/4.5/5.5 threshold crossings within 0.1 s. A numerical-convergence policy — expensive (prepares each replay twice) but irreplaceable. |
| RecordedReplayPreparer | `historicalPeakAppearsExactlyAtSAndDurationIsLatestSPlusTen` (L16, ×2 events) | Recorded-mode timing contract. |
| RecordedReplayPreparer | `recordedReplayWorksWithAbsentFaultMechanismAndVs30` (L42) | Recorded bundles ignore mechanism/Vs30 and still match the full bundle's arrivals and final MMI. **Note: L41 uses a fully-qualified `@org.junit.jupiter.api.Test`; normalize to `@Test` with the import.** |
| ReplayDeterminism | `testFrameStateDeterminism` (L24) | `FrameState` equality for identical inputs. |
| ReplayDeterminism | `testSyntheticScenario` (L42) | Hand-built 3-city scenario: frame fields at t=0, MMI 8.1/VIII, vertical P/S ±1e-4, gating at 2/3.5/6 s. Only test exercising a fully synthetic scenario. |
| PrecomputedWavefronts | `scenarioFactoryPrecomputesThroughExplicitNonDefaultDuration` (L46) | 180 s duration precomputes ≥185 s with P/S present at 180 s. |
| ReplayController | `elapsedTimeTracksClockRatherThanTickFrequency` (L47) | Playback must be wall-clock driven, not tick-count driven. |
| ReplayController | `pauseResumeExcludesInactiveTimeAcrossRepeatedCycles` (L67) | Pause accounting. |
| ReplayController | `explicitShortDurationControlsTickPauseSeekAndFinish` (L175) | Covers tick + pause + seek + finish in one deterministic 12.5 s run. |
| InputSignature | `everyScientificInputDimensionChangesTheSignature` (L26) + its private `assertChanged` helper (L148) | Cache-correctness guard: a changed scientific input must invalidate the cache. If this test does not already assert the negative case (**changing display mode must NOT change the signature**), fold that single assertion in from `customScenarioSettings...` (L88). |
| MmiLegend | `testSWaveArrivalMmiRevealTiming` (L112) | Moved in from Suite C's sources; needs the `ReplayEngine` setup that lives here. |

### Drop

| Method | Reason |
|---|---|
| `SimulatedMmiTimeline.referencesCannotAffectPredictionsOrSimulatedCacheSignature` (L80) | Signature isolation is covered by the kept `InputSignature` test; also duplicated by `InputSignature` L136. |
| `RecordedReplayPreparer.recordedModeExplicitlyRequiresReferences` (L34) | IAE on missing references. |
| `PrecomputedWavefronts.legacyScenarioFactoryRetainsDefaultDuration` (L59) | Asserts the legacy factory equals an explicit 120 s — a migration-era check. |
| `PrecomputedWavefronts.durationAwareFactoryRejectsNonPositiveOrNonFiniteDuration` (L74) | Argument validation. |
| `ReplayController`: `startsPausedWithInitialFrame` (L31), `replayEndRequiresRestartBeforePlaybackCanContinue` (L92), `pausingAtEndBoundaryAlsoFinishesReplay` (L118), `simulatedUtcUsesEventOriginAndReplayElapsedTime` (L128), `seekUpdatesElapsedTimeAndTransitionsState` (L140), `explicitLongDurationDoesNotFinishAtLegacyBoundary` (L199), `explicitDurationMustBePositiveAndFinite` (L217) | State-machine transitions pinned alongside the controller as it was written. Seek and finish are covered by `explicitShortDuration...`; the 125 s-vs-120 s boundary test is a migration artifact. |
| `InputSignature`: `simulatedSiteNeedsNoHistoricalReference` (L20), `preparedReplayTimelinesAndFramesAreImmutable` (L70), `customScenarioSettings...` (L88, except the one folded assertion), `testRecordedSignatureIgnoresFaultMechanismVs30AndScientificManifest` (L136) | Immutability (`UnsupportedOperationException` ×4) and duplicate signature-isolation checks. |

**Result: ~13 methods.** This suite is the slowest survivor (it prepares full replays for two events, twice over for the convergence test). That cost is justified — it is what "the sim math stays put" actually means.

---

## Suite E — `GeometryRegressionTest`

Sources: `MercatorProjectionTest` (151 L), `GeoPointTest` (69 L → 1), `RuptureDistanceTest` (75 L), `data/CaliforniaOutlineTest` (66 L → 1).

### Keep

| From | Method (line) | Why |
|---|---|---|
| Mercator | `testOriginProjectsToZeroZero` (L34) | Origin → (0,0) and round-trip. |
| Mercator | `testCardinalDirectionsAndScreenAxes` (L51) | Sign conventions — a silent flip here would invert the map. |
| Mercator | `testReferenceLocationsRoundTrip` (L90, 7 cities) | Round-trip ≤1e-6° at real California latitudes. Cheap; keep. |
| Mercator | `testGeodesicCircleGeneration` (L101) | 48 circle points all 150 km ± 0.05 from the epicenter. This is the wavefront ring geometry. |
| Mercator | `testViewportTransformPreservesAspectRatio` (L123) | 1:1 pixel scale and centering. |
| GeoPoint | `testDistanceKmTo` (L44) | Self 0, pole-to-pole πR, quarter-circle (π/2)R, Ridgecrest→LA 200–220 km. The geodesic foundation everything else rests on. |
| RuptureDistance | `rjbIsZeroInsideProjectionAndUsesNearestEdgeOutside` (L14) | Rjb = 0 inside, R·π/180 for 1° outside. |
| RuptureDistance | `bundledRupturesProduceFiniteDistinctRjb` (L25) | Both bundled ruptures give finite, distinct Rjb. |
| RuptureDistance | `correctedPlanarRupturePreservesDimensionsAndContainsHypocenter` (L39) | Preserves W·sin(dip), clamps top depth at 0, contains hypocenter, exterior Rjb > 50 km, vertical projection is a 2-point segment. |
| CaliforniaOutline | `testProjectRingsAndBoundingBox` (L35) | Projected bbox −545.92 / 596.11 / −722.45 / 604.80 km. **Fold in** the "6 rings / 468 vertices" assertions from `testLoadDefaultOutline` (L19) so the data-shape pin survives without a second method. |

### Drop

`GeoPointTest.testBoundaryValidCoordinates` (L18), `testOutOfBoundsCoordinates` (L31), `testNanAndInfinityCoordinates` (L36); `CaliforniaOutlineTest.testLoadDefaultOutline` (L19, after folding) and `testRejectsInvalidOutline` (L59) — all coordinate/argument validation.

**Result: ~8 methods.**

---

## Suite F — `SiteAndFaultResolutionRegressionTest`

Sources: `SiteConditionResolverTest` (90 L), `AutomaticFaultResolverTest` (154 L), `data/CaliforniaVs30GridTest` (57 L), `data/FaultSectionCatalogTest` (49 L), `data/CaliforniaFaultCatalogTest` (52 L).

### Keep

| From | Method (line) | Why |
|---|---|---|
| SiteCondition | `testResolutionPrecedence` (L18) | MEASURED (450) > MAPPED_PROXY (520) > raster > DEFAULT (760) offshore. Precedence errors would silently change ground motion everywhere. |
| SiteCondition | `testCustomScenarioResolvesRealisticVs30` (L54) | Per-city Vs30 resolution, records `vs30DatasetId`/`vs30Checksum`, LA ≠ SF. |
| AutomaticFaultResolver | `testBoundedPatchDistancesAndDepthMismatch` (L41, `@CsvSource` 4 rows) | Patch distance ± 0.5 km, section id 10, FAULT_INFORMED vs GENERIC on depth mismatch. |
| AutomaticFaultResolver | `testCandidateSelectionAmbiguityAndTieBreaking` (L59) | Tie-break to lowest sectionId within 1 km + ambiguity flag. |
| AutomaticFaultResolver | `testDeriveLocalStrikeAndDipSideOrientation` (L99) | Local strike 0°/180° by dip side ± 2°. |
| AutomaticFaultResolver | `testExplicitInputsPrecedenceThroughCustomCompletion` (L115) | SUPPLIED precedence, derived dip 55°, resolver metadata/versionIds. |
| CaliforniaVs30Grid | `testLoadDefaultGrid` (L17) | Grid 1320×1260, west −125 / north 42.5, dataset id, and the pinned samples (Ridgecrest 277.0, LA 304.5, SF 239.7, Bakersfield 246.0, ocean/out-of-bounds empty). |
| FaultSectionCatalog | `testBundledCatalogLoadsAndValidatesIntegrity` (L18) | 664 sections, dataset id, pinned SHA-256 `80e695cc…`, per-section invariants. A real data-integrity guard — keep the hash exactly. |
| CaliforniaFaultCatalog | `testLoadDefaultCatalog` (L16) — **trimmed** | See below. |

### Required dedupe

San Francisco Vs30 ≈ 239.7 is currently asserted in **both** `CaliforniaVs30GridTest` (L41–42) and `SiteConditionResolverTest` (L44–45). In the merged suite, declare the expected value **once** as a private constant and reference it from both tests. Do not retype the literal, and do not delete the raster-path assertion from the precedence test — the two assertions mean different things (grid lookup vs. resolver falling through to the grid).

### Required trim

`CaliforniaFaultCatalogTest.testLoadDefaultCatalog` currently iterates **every fault and every polyline point** (thousands of points) checking finiteness and California bounds. Replace with:
- catalog size > 1000 (verbatim from source),
- San Andreas / Hayward / Garlock present (verbatim),
- finite + in-bounds spot-checks over a **bounded sample** — e.g. the first and last vertex of the first 25 faults.

Keep the same bounds constants the original used.

### Drop

`FaultSectionCatalogTest.testCatalogRejectsCorruptOrMissingResource` (L39) — IllegalState/IAE on a corrupt resource.

**Result: ~9 methods.**

---

## Verification

```bash
# Old test packages gone, six new suites present.
ls src/test/java/io/github/paracosms/calquake/          # expect: data, regression, testsupport
ls src/test/java/io/github/paracosms/calquake/regression/ # expect 6 files
ls src/test/java/io/github/paracosms/calquake/data/      # expect ONLY ScenarioLoaderTest.java

# No dangling references to deleted classes.
grep -rn "TravelTimeCurveTest\|HadleyKanamoriTauPModelTest\|HomogeneousSphereOracleTest\|ScientificGateArrivalBenchmarkTest\|NorthridgeArrivalBenchmarkTest\|ReplayControllerTest\|InputSignatureAndImmutabilityTest\|MmiLegendAndIntensitySourceTest" src/ || echo "CLEAN"

# testsupport helpers still all in use (5 files, none orphaned).
for h in ArrivalBenchmarkRunner ArrivalBenchmarkResult ObservedArrival ObservedArrivalsFixture FakeMonotonicClock; do
  echo -n "$h: "; grep -rl "$h" src/test/java/io/github/paracosms/calquake/regression/ | wc -l
done

./mvnw clean verify
```

Every helper in the last check must report ≥1. If `FakeMonotonicClock` reports 0, you dropped too many `ReplayController` tests. If `ArrivalBenchmarkRunner` reports 0, Suite B is wrong.

Expected totals after this phase: **7 test classes** (6 regression + `ScenarioLoaderTest`), **~70 tests** (Phase 4 then removes ~18 more). Wall-clock should drop substantially, driven mostly by Suite A's TauP reduction.

## Commit

```
test: consolidate simulation tests into six regression suites

Replaces 24 test classes with six suites in a new `regression` package,
each defined by what must not drift: travel times, the observed-pick
arrival gate, ground motion and intensity conversion, the replay
pipeline, geometry, and site/fault resolution.

All pinned values and tolerances are carried over verbatim. Removed
coverage was argument validation, state-machine transitions recorded
alongside the code that introduced them, and redundant checks of the
same invariant (dense inversion scans superseding parameterized tables,
duplicated ray-penetration and Vs30 assertions, two monotonicity tests
in inverse coordinates).
```

## Definition of done

- [ ] 6 suites exist under `regression/`; all 20 `core/` files and the 4 named `data/` files deleted; `core/` directory removed.
- [ ] `data/ScenarioLoaderTest.java` untouched.
- [ ] Every pinned number and tolerance in the surviving tests is byte-identical to its source (spot-check Suite B's four cohorts and Suite A's frozen radii against `git show HEAD~1`).
- [ ] All five `testsupport/` helpers still referenced.
- [ ] SF Vs30 expectation declared once in Suite F.
- [ ] `CaliforniaFaultCatalog` check no longer iterates all polyline points.
- [ ] Suite C does not construct a `ReplayEngine`.
- [ ] `./mvnw clean verify` passes with no assertion values changed.
- [ ] `src/main/` untouched.
