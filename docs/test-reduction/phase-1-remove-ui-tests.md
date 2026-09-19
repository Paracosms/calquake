# Phase 1 — Remove all UI tests and make the suite headless

**Read [README.md](README.md) first — the guardrails there apply.**

## Goal

Eliminate every JavaFX-touching test. Running `./mvnw clean verify` currently constructs and shows real `Stage` windows, producing rapid window flashing. After this phase the test suite must not initialize the JavaFX toolkit at all.

Accepted loss: there will be **no automated app-startup or rendering coverage**. This is a deliberate decision — any surviving FX test reintroduces toolkit startup and the flashing. Manual check is `./mvnw javafx:run`.

## Scope (exclusive ownership)

- `src/test/java/io/github/paracosms/calquake/ui/**`
- `src/test/java/io/github/paracosms/calquake/testsupport/JavaFxTestHelper.java`
- `src/test/java/io/github/paracosms/calquake/testsupport/MmiIconGenerator.java`
- `.github/workflows/ci.yml`

Do not touch any other test file; later phases own those.

## Step 1 — Delete the JavaFX test files

| File | Lines | Tests | Note |
|---|---|---|---|
| `ui/CalQuakeAppIntegrationTest.java` | 1,157 | 17 | Constructs `new Stage()` in 17 places; ~45 `runOnFxThread` calls; toggles full screen. Primary source of the flashing. |
| `ui/MapCanvasPaneIntegrationTest.java` | 388 | 10 | Offscreen `Canvas.snapshot()` — no `Stage`, but still starts the FX toolkit. Includes a wall-clock throughput budget test (`<33.33 ms/frame`) that is flaky by nature. |
| `ui/RenderSnapshotGeneratorTest.java` | 167 | 1 | A screenshot generator, not a test. Writes PNGs to `target/render-snapshots`; asserts only "image has >50% visible pixels and ≥8 distinct colors". |
| `ui/MmiIconLoaderTest.java` | 95 | 5 | See warning below. |

Delete the now-empty `src/test/java/io/github/paracosms/calquake/ui/` directory.

### ⚠ `MmiIconLoaderTest` writes into the source tree

Its `@BeforeAll` (L21–30) regenerates PNG/SVG assets into **`src/main/resources/icons/mmi`** as a side effect of running tests. This is a build-time mutation disguised as a test.

Before deleting, confirm the 26 icon files are committed and clean:

```bash
git ls-files src/main/resources/icons/mmi | wc -l   # expect 26
git status --porcelain src/main/resources/icons/mmi # expect empty
```

If `git status` shows modifications, **check out the committed versions first** (`git checkout -- src/main/resources/icons/mmi`) so the regenerated copies are not what gets frozen. The committed icons are the artifact we keep.

## Step 2 — Delete the orphaned test helpers

- `testsupport/JavaFxTestHelper.java` (49 L) — the only place `Platform.startup` / `Platform.setImplicitExit` / `Platform.runLater` appear in tests. Used solely by the four files above.
- `testsupport/MmiIconGenerator.java` (123 L) — AWT-based icon generator, used only by `MmiIconLoaderTest` (plus its own `main`). **Delete it; keep the generated icons** it previously produced. Per the decision on record, the generator is not being relocated into `src/main`. If icons ever need regenerating, recover this file from git history.

Keep `testsupport/FakeMonotonicClock.java` — Phase 3 still needs it.

## Step 3 — Simplify CI

Edit `.github/workflows/ci.yml`:

1. Delete the whole `Install Linux GUI / Xvfb dependencies` step (the `apt-get install xvfb libxrender1 libxtst6 libxi6 libgl1 libglx-mesa0` block and its comment).
2. Replace the two OS-conditional run steps with a single unconditional verify, dropping the `xvfb-run --auto-servernum` wrapper. Keep the `windows-latest` / `ubuntu-latest` matrix and `fail-fast: false`.

The Windows step already runs `.\mvnw.cmd clean verify` bare. The simplest correct result is one step per OS with no GUI setup, or a single step using the wrapper script appropriate to the runner — either is fine as long as no display server is provisioned.

Do **not** add `-Dtestfx.headless`, `glass.platform=Monocle`, or `java.awt.headless` to `pom.xml`. There is no such config today and none is needed once no test touches JavaFX.

## Verification

```bash
# 1. No JavaFX anywhere in the test sources — must print nothing.
grep -rn "javafx" src/test/ || echo "CLEAN"

# 2. No FX toolkit bootstrapping — must print nothing.
grep -rn "Platform\.\|WritableImage\|new Stage(" src/test/ || echo "CLEAN"

# 3. No dangling references to deleted helpers — must print nothing.
grep -rn "JavaFxTestHelper\|MmiIconGenerator" src/ || echo "CLEAN"

# 4. Full build. Must compile and pass with NO windows appearing and no DISPLAY required.
./mvnw clean verify
```

Checks 1–3 printing `CLEAN` is the acceptance criterion for "headless". Run check 4 with `DISPLAY` unset to prove it: `env -u DISPLAY ./mvnw clean verify`.

Expected surefire totals after this phase: **30 test classes, 120 tests** (down from 34 / 153). Confirm no unrelated test started failing — if `MmiBenchmarkRunnerTest` or anything in `core/` breaks, that is a real finding; stop and report (Phase 2 deletes that test, but it must not *break* here).

## Commit

```
test: remove JavaFX UI tests and drop xvfb from CI

The four ui/ test classes started the JavaFX toolkit and opened real
Stages, making local `mvn verify` flash windows continuously. They
covered app wiring and rendering rather than simulation behavior, so
they are removed outright rather than replaced.

Also removes JavaFxTestHelper (their only toolkit entry point) and
MmiIconGenerator, whose @BeforeAll in MmiIconLoaderTest rewrote
committed assets under src/main/resources/icons/mmi during test runs.
The generated icons remain committed.

CI no longer needs xvfb or the GUI package install step.
```

## Definition of done

- [ ] 4 `ui/` files + `ui/` directory deleted.
- [ ] `JavaFxTestHelper.java` and `MmiIconGenerator.java` deleted; `FakeMonotonicClock.java` retained.
- [ ] `src/main/resources/icons/mmi` unchanged from `HEAD` (26 files, clean `git status`).
- [ ] Verification checks 1–3 print `CLEAN`.
- [ ] `env -u DISPLAY ./mvnw clean verify` passes.
- [ ] `ci.yml` has no xvfb and no GUI apt step; matrix preserved.
- [ ] `src/main/` untouched.
