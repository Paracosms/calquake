# Test suite reduction — agent handoff packet

Four phases, each a self-contained implementation plan for one agent. Execute **in order**; each phase assumes the previous one is merged.

| Phase | Doc | Owns (exclusive) | Net lines | Net tests |
|---|---|---|---|---|
| 1 | [phase-1-remove-ui-tests.md](phase-1-remove-ui-tests.md) | `src/test/.../ui/**`, `JavaFxTestHelper`, `MmiIconGenerator`, `.github/workflows/ci.yml` | −1,979 | −33 |
| 2 | [phase-2-delete-trivial-tests.md](phase-2-delete-trivial-tests.md) | 5 whole test files (list is exhaustive) | −335 | −17 |
| 3 | [phase-3-consolidate-core.md](phase-3-consolidate-core.md) | all of `core/`, plus the 4 catalog/outline tests in `data/` | ≈ −1,580 | ≈ −55 |
| 4 | [phase-4-trim-scenario-loader.md](phase-4-trim-scenario-loader.md) | `data/ScenarioLoaderTest.java` only | ≈ −685 | ≈ −18 |

Baseline (measured on `simulation` @ `d3fbe12`): **34 test classes, 6,209 lines, 153 `@Test`/`@ParameterizedTest` methods.**
Target end state: **7 test classes, ~1,600 lines, ~50 tests, zero JavaFX in the test suite.**

## Why this is happening

The vast majority of these tests were written to confirm a feature worked immediately after it was implemented. They are not regression guards. What actually needs protecting long-term is:

1. **Simulation math stays put** — travel times, ground motion, GMICE, MMI timelines, geodesy, rupture distance.
2. **The observed-pick benchmark gate** — model output vs. 231 real SCEDC picks across two events. This is the single highest-value test in the repo.
3. **Bundled data files don't silently change** — provenance-manifest and catalog SHA-256 checks.

Everything else is negotiable, and the UI tests are actively harmful: running `mvn verify` locally opens and flashes real JavaFX windows.

## Non-negotiable guardrails (apply to every phase)

1. **Never alter a pinned numeric value or tolerance.** When moving an assertion, copy it verbatim — do not retype numbers, do not "clean up" a tolerance, do not recompute an expected value. The whole point is that drift in simulation output breaks the build.
2. **If a migrated assertion fails, stop.** That is a real finding about the production code or about the migration, not a number to adjust. Report it and wait.
3. **Do not modify anything under `src/main/`.** Test-only helpers are fair game; production code is out of scope for all four phases.
4. **Delete, don't `@Disabled`.** Git history is the archive.
5. **Do not touch `northridge-1994-test/` or `ridgecrest-2019-test/`.** No test loads them, but they are the provenance inputs backing the two Replay options and are cited by `src/main/resources/data/frozen_selection_rules.md`.
6. **Branch:** work on `simulation`, one commit per phase. Never commit to `main`.
7. **Verify with `./mvnw clean verify`** before committing. After Phase 1 the suite is fully headless, so this needs no display and no `xvfb`.
