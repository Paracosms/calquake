# Phase 4 — Trim the scenario loader test

**Read [README.md](README.md) first — the guardrails there apply.**
**Prerequisite:** Phases 1–3 merged.

## Goal

`data/ScenarioLoaderTest.java` is 843 lines and 22 tests — the second-largest test file in the repo and, after Phase 1, the largest. Roughly half of it is tables of malformed-JSON strings asserting exception messages, and the other half re-validates the same bundle through six serializer format variants.

Collapse it to **4 tests, ~160 lines**, keeping only what guards bundled data and round-trip fidelity.

## Scope (exclusive ownership)

`src/test/java/io/github/paracosms/calquake/data/ScenarioLoaderTest.java` — this file and nothing else.

## Placement

Phase 3 created `io.github.paracosms.calquake.regression`. Put the result there as **`regression/ScenarioDataRegressionTest.java`** and delete the `data/` test directory. Final test-tree layout should be exactly `regression/` (7 classes) + `testsupport/` (5 helpers).

## The four survivors

### 1. `bundledScenarioFidelity` — two methods sharing a helper

Merged from `testLoadDefaultScenarioFidelity` (L37, Ridgecrest) and `testLoadNorthridgeScenarioFidelity` (L268). As in Phase 3's Suite B, use **two explicit methods calling one private helper** rather than a `@ParameterizedTest`, since the expected values differ wholesale.

**Load each scenario by event name** ("Ridgecrest" / "Northridge") so that name-based lookup is exercised here — that is what lets you delete `testLoadScenarioByEventName` (L333). These two bundles are the app's only two Replay options, so their fidelity is worth pinning.

Each source test currently makes ~80 assertions. **Keep:**
- event id, origin instant, epicenter lat/lon, depth, magnitude (Ridgecrest 7.1 / 8.0 km; Northridge 6.7 / 18.2 km)
- the 5 reference locations: name, GEOID, latitude, longitude
- per-location MMI decimal value and PGA %g

**Drop from the fidelity assertions:**
- roman numeral and hex color per location — Phase 3's `GroundMotionAndIntensityRegressionTest.testLegendBinsAndBoundaries` owns the MMI→roman/hex mapping across all 23 bins. Asserting it again per city is redundant.
- census internal-point and grid-node plumbing, and `offsetKm` — provenance bookkeeping, not simulation output.
- title/display-string assertions.

### 2. `derivativeFixturesMatchProvenanceManifest`

From L127, **keep verbatim**, along with its private `assertResourceHash` helper (L143). It SHA-256s four classpath resources against `/data/provenance_manifest.json`. This is a genuine regression guard: it fails loudly if a bundled data file changes without the manifest being updated. Do not weaken it.

### 3. `starterSimulationBundleAndSettings`

Merge `testLoadStarterSimulationBundleWithCatalog` (L411), `testLoadStarterSimulationSettings` (L432), and the useful half of `testSimulationSiteCatalogLoadDefault` (L351) into one method:
- starter bundle loads with its catalog, 22 sites, no reference locations
- the pinned starter settings (35.5 / −118.5 / 6.5 / 10.0 / `custom-california-scenario-v1`)
- the 22-city catalog count, Ridgecrest coordinates, and one `findById` / `requireByDisplayName` lookup

Copy all values verbatim.

### 4. `serializerRoundTripPreservesScenarioCitiesAndSignature`

One round-trip test replacing six. Merge `testSimulationScenarioSerializerRoundTripAndAtomicWrite` (L445), `testSimulationScenarioWithBundledCitiesRoundTrip` (L591), and `testSimulationSiteSerializerRoundTrip` (L763) into a single method that:
- serializes a scenario **that includes bundled cities**, writes it (exercising the atomic write), reads it back
- asserts the round-tripped scenario equals the original and that the **input signature is unchanged** (the cache-correctness property)
- round-trips the site catalog through `SimulationSiteSerializer`

Doing it with bundled cities present covers both the with-cities and base paths in one pass.

## Drop entirely

| Method (line) | Reason |
|---|---|
| `testRejectsMalformedEventJson` (L223, ~8 strings) | Table of malformed JSON → IAE. |
| `testRejectsMalformedLocationsJson` (L263, ~8 strings) | Same. |
| `testSimulationScenarioSerializerValidationErrors` (L496) | Unknown type, unsupported schema version, 3 more — exception-message pinning. This is the single largest block in the file (~95 lines). |
| `testSimulationSiteSerializerValidationErrors` (L817, 7 cases) | Same. |
| `testSimulationSiteCatalogCustomAndValidation` (L373) | Duplicate-id and bad-latitude rejection. |
| `testSimulationScenarioWithInvalidBundledCitiesThrows` (L684, ×3) | Same shape. |
| `testLoadEventFromProvenanceManifestFormat` (L155) | Accepts an alternative event JSON shape — input-format tolerance, not simulation behavior. |
| `testSimulationScenarioWithoutBundledCitiesOptional` (L643) | Covered by survivor 4 plus the starter bundle (which has no references). |
| `testSimulationScenarioWithAlternativeCitiesObjectFormat` (L655) | Second accepted cities encoding; format tolerance. |
| `testLoadScenarioByEventName` (L333) | Absorbed into survivor 1 by loading both bundles by name. |
| `testSimulationSiteSerializerFileIo` (L788) | File IO covered by survivor 4's atomic write. |
| `testLoadRecordedScenarioBundleIsolation` (L749) | Asserts recorded bundles carry no rupture/mechanism/manifest/Vs30. Phase 3 kept `recordedReplayWorksWithAbsentFaultMechanismAndVs30` in `ReplayPipelineRegressionTest`, which verifies the same isolation *behaviorally* (identical arrivals and final MMI without those inputs). **Confirm that test exists before deleting this one.** |

## Verification

```bash
# Phase 3's survivor must be present before you delete this one's coverage.
grep -rn "recordedReplayWorksWithAbsentFaultMechanismAndVs30" src/test/java/io/github/paracosms/calquake/regression/

# Final layout: regression/ (7 classes) + testsupport/ (5 helpers), no data/ or core/.
ls src/test/java/io/github/paracosms/calquake/
ls src/test/java/io/github/paracosms/calquake/regression/

# Provenance hash guard must still be present and unweakened.
grep -n "provenance_manifest\|sha256\|SHA-256" src/test/java/io/github/paracosms/calquake/regression/ScenarioDataRegressionTest.java

# Nothing references the old class.
grep -rn "ScenarioLoaderTest" src/ docs/ README.md || echo "CLEAN"

./mvnw clean verify
```

## Final state check for the whole effort

```bash
find src/test -name "*.java" | wc -l                                  # expect 12 (7 tests + 5 helpers)
grep -rcE '^\s*@(Test|ParameterizedTest)' src/test --include=*.java | awk -F: '{s+=$2} END {print s}'  # expect ~50
grep -rn "javafx" src/test/ || echo "NO JAVAFX"
```

Baseline was 34 test classes / 6,209 lines / 153 tests. Target is **7 classes / ~1,600 lines / ~50 tests / zero JavaFX**. Report the actual numbers you land on, plus before/after `./mvnw clean verify` wall-clock if you can measure both.

## Commit

```
test: reduce ScenarioLoader coverage to data and round-trip guards

Collapses 22 tests into four: per-event bundled scenario fidelity
(loaded by name, trimmed to event and location values that the legend
suite does not already cover), the provenance-manifest SHA-256 guard,
the starter simulation bundle and settings, and a single serializer
round-trip carrying bundled cities.

Removed coverage was tables of malformed-JSON strings asserting
exception messages, and five additional serializer format variants
re-verifying the same round-trip semantics.
```

## Definition of done

- [ ] `regression/ScenarioDataRegressionTest.java` exists with 4 tests; `data/` test directory deleted.
- [ ] Provenance-manifest SHA-256 test carried over verbatim, including its helper.
- [ ] All kept values byte-identical to the source (`git show HEAD~1 -- '*ScenarioLoaderTest.java'` to compare).
- [ ] Recorded-bundle isolation confirmed still covered in `ReplayPipelineRegressionTest` before its loader-level test was dropped.
- [ ] Final tree is `regression/` + `testsupport/` only.
- [ ] `./mvnw clean verify` passes; final counts reported.
- [ ] `src/main/` untouched.
