# Phase 2 — Delete implementation-era test files

**Read [README.md](README.md) first — the guardrails there apply.**
**Prerequisite:** Phase 1 merged.

## Goal

Remove five test files whose contents are entirely constructor/validation/getter checks or self-tests of test fixtures. These were written to confirm code existed; none of them guards simulation behavior.

This phase is **whole-file deletions only — no editing, no merging.** Every method-level keep/drop decision lives in Phase 3 and Phase 4, where the files are being rewritten anyway. If you find yourself editing a file, you are outside your scope.

## Scope (exclusive ownership)

Exactly these five files, and nothing else:

| File | Lines | Tests | Why it goes |
|---|---|---|---|
| `benchmark/MmiBenchmarkRunnerTest.java` | 23 | 1 | Runs the entire MMI benchmark harness and asserts that `site-results.csv` / `summary.json` / `report.md` appear under `target/mmi-benchmark` with expected cohort markers. It is a report generator, not a regression test, and it is slow. `README.md` already documents invoking the harness manually. Delete the `benchmark/` test directory too. |
| `core/EarthquakeEventTest.java` | 59 | 5 | 100% constructor rejection checks: blank id, null origin, null epicenter, negative/non-finite depth, non-finite magnitude. |
| `core/ReferenceLocationTest.java` | 49 | 3 | Same shape: invalid location fields, `offsetKm` < 0/NaN, invalid MMI. |
| `core/ScenarioTest.java` | 163 | 6 | Case-insensitive lookup, `locations()` unmodifiable, null/empty rejection, assumption-set defaults, record getters, and a table of validator warning/error message strings. The one behavior here with scientific weight — flagging inputs outside the BSSA14 calibration domain — stays covered by `EmpiricalEnvelopeTest.retainsOutOfCalibrationDomainMetadataWithoutSuppressingValues`, which survives into Phase 3. |
| `testsupport/ObservedArrivalsFixtureTest.java` | 41 | 2 | Tests a *test helper's* JSON parsing (first-pick field fidelity, and that all picks meet the selection criteria). The fixture's correctness is already implied by the arrival benchmark gate, which consumes it and asserts exact pass rates. |

**Total: −335 lines, −17 tests.**

### Do NOT delete

`testsupport/ObservedArrivalsFixture.java`, `ObservedArrival.java`, `ArrivalBenchmarkRunner.java`, `ArrivalBenchmarkResult.java`, `FakeMonotonicClock.java` — all are still consumed by tests that survive Phase 3.

`MmiBenchmarkRunner` under `src/main/` stays; only its *test* is deleted. It remains invocable manually.

## Notes on `README.md`

`README.md` line ~78 documents `.\mvnw.cmd -Dtest=MmiBenchmarkRunnerTest test`. That invocation will no longer work after this phase. Update that line to invoke the harness's `main` directly (or the documented equivalent), or remove the line — your call, but do not leave the README pointing at a deleted test class. Check how `MmiBenchmarkRunner` in `src/main/` is entered before rewriting the instruction; **do not modify the runner itself.**

## Verification

```bash
# No references to deleted classes anywhere — must print nothing.
grep -rn "MmiBenchmarkRunnerTest\|EarthquakeEventTest\|ReferenceLocationTest\|ScenarioTest\|ObservedArrivalsFixtureTest" src/ docs/ README.md || echo "CLEAN"

# Surviving helpers still referenced.
grep -rln "ObservedArrivalsFixture\|ArrivalBenchmarkRunner\|FakeMonotonicClock" src/test/

./mvnw clean verify
```

Careful with the first grep: `ScenarioTest` is a substring of nothing else here, but `ScenarioLoaderTest` must still exist — confirm `src/test/java/io/github/paracosms/calquake/data/ScenarioLoaderTest.java` is present and untouched (Phase 4 owns it).

Expected totals after this phase: **25 test classes, 103 tests.**

## Commit

```
test: delete implementation-era validation tests

Removes five test classes that verified code existed rather than
guarding behavior: three pure constructor-rejection suites
(EarthquakeEvent, ReferenceLocation, Scenario), a self-test of the
observed-picks test fixture, and MmiBenchmarkRunnerTest, which ran the
full benchmark harness to assert that report artifacts were written.

The MMI benchmark harness itself is unchanged and still runnable
manually; README updated accordingly.
```

## Definition of done

- [ ] Exactly the five listed files deleted; `benchmark/` test directory removed.
- [ ] No other test file modified (`git diff --stat` shows only deletions plus the README line).
- [ ] `ScenarioLoaderTest.java` still present and unmodified.
- [ ] Surviving `testsupport/` helpers intact.
- [ ] README no longer references `MmiBenchmarkRunnerTest`.
- [ ] `./mvnw clean verify` passes; 103 tests run.
- [ ] `src/main/` untouched.
