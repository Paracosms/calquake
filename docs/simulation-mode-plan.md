# Simulation Mode implementation plan

Status: approved product direction, ready for implementation  
Plan date: 2026-09-10

## 1. Objective

Add a new top-level **Simulation** application mode for creating and playing an entirely custom earthquake scenario. Simulation appears above **Replay** in the `Mode` menu and is the default mode when CalQuake opens.

Simulation is an exploratory toy. It is not a forecast, emergency tool, hazard product, or scientifically validated prediction of a future earthquake. The UI must say this plainly while still reporting its model inputs and assumptions accurately.

The existing Replay mode remains responsible for the bundled Ridgecrest and Northridge historical events and its Recorded/Simulated MMI selector. Simulation is separate: it has no historical-event selector and never reads historical MMI, PGA, or PGV as predictor inputs.

## 2. Frozen product decisions

1. The top-level application modes are, in this order:
   - `Simulation`
   - `Replay`
2. `Simulation` is selected at application startup.
3. Simulation uses the five currently bundled city locations: Ridgecrest, Trona, Bakersfield, Los Angeles, and Fresno.
4. The city source must be modular so a later resource can add many more California cities without changing the simulation engine or renderer.
5. Users enter only:
   - epicenter latitude and longitude;
   - magnitude;
   - depth in kilometres.
6. Fault and site-condition values are automatic fixed assumptions, visibly disclosed in the Simulation settings panel. They are not additional required inputs in this release.
7. Model-domain violations do not block a simulation. Valid finite inputs run with warnings that results may be wildly inaccurate.
8. Mathematically impossible or uncomputable inputs still fail validation. Examples include non-finite numbers, coordinates outside their legal ranges, negative depth, generated non-finite rupture geometry, or a model calculation that cannot produce a finite result.
9. Settings cannot be edited while playback is running.
10. Simulation supports saving a scenario to a local file and importing one from a local file.
11. Completion is based on deterministic software behavior, transparent assumptions, warnings, input/reference isolation, persistence, and regression tests—not on a claim of predictive accuracy.

## 3. Terminology and mode hierarchy

Do not reuse `MmiMode` for the new top-level choice. Introduce a separate type such as:

```text
ApplicationMode
  SIMULATION
  REPLAY
```

`MmiMode.RECORDED` and `MmiMode.SIMULATED` remain Replay-only submodes.

Simulation also has an intensity presentation setting:

```text
IntensityDisplayMode
  MAXIMUM_REACHED
  CURRENT_SHAKING
```

The settings-panel labels should be user-facing phrases:

- `Maximum estimated MMI reached` — non-decreasing running maximum; default.
- `Current estimated shaking` — the envelope-derived estimate at the selected instant and therefore allowed to rise and fall.

Neither value is “instantaneous MMI” in a standardized scientific sense. Use the exact phrases above in labels, tooltips, and documentation.

## 4. Simulation experience

### 4.1 Startup

On a normal application launch:

1. Open in Simulation mode.
2. Show the California map and all five simulation cities.
3. Show an editable, valid starter scenario loaded from a versioned defaults resource.
4. Remain paused at `T + 0`.
5. Prepare the starter replay in the background. Enable Play only when preparation succeeds.

The starter is a custom scenario, not a disguised historical event. Give it a neutral title such as `Custom California Scenario` and do not show a USGS event identifier or historical origin time.

### 4.2 Mode menu

Replace the current single inert `Replay` menu item with mutually exclusive `RadioMenuItem` entries in a `ToggleGroup`:

```text
Mode
  Simulation   (first and selected by default)
  Replay
```

Switching modes must:

- pause and reset active playback;
- cancel or supersede stale background preparation;
- replace the right sidebar atomically;
- redraw the map from the newly installed mode state;
- preserve the latest in-memory Simulation draft and Replay selections independently;
- never install a late preparation result belonging to the other mode.

### 4.3 Simulation sidebar

The right sidebar in Simulation mode contains these sections in order:

1. **Simulation Controls**
   - Play/Pause;
   - Restart;
   - timeline scrubber;
   - elapsed time and preparation/playback state.
2. **Simulation Settings**
   - epicenter latitude;
   - epicenter longitude;
   - magnitude;
   - depth in km;
   - intensity display dropdown;
   - Apply/Prepare button;
   - inline validation and warning summary.
3. **Fixed Model Assumptions**
   - generated finite rupture: Wells & Coppersmith (1994), all-slip scaling;
   - mechanism: generic strike-slip;
   - rake: `0°`;
   - strike: `0°`;
   - dip: `90°`;
   - site condition for every city: reference rock, Vs30 `760 m/s`;
   - ground-motion and envelope model names/versions.
4. **Scenario File**
   - `Save to File…`;
   - `Import…`.
5. **MMI Legend**
6. **Toy/accuracy disclaimer**

The fixed assumptions are read-only in this release. They must not be hidden in a tooltip or described as measured local conditions.

The Replay sidebar remains the existing replay controls, event selector, Recorded/Simulated selector, and legend. The Ridgecrest/Northridge event selector must not be constructed or shown in Simulation mode.

### 4.4 Editing and preparation

- Input fields, intensity-display dropdown, Apply, Save, and Import are disabled while playback is actively running.
- Pausing re-enables them.
- Restart returns to `T + 0` without changing the scenario.
- Editing a prepared input marks the visible result as stale and disables Play until Apply/Prepare succeeds.
- Apply pauses and resets playback, validates the draft, shows warnings, and prepares the complete replay off the JavaFX thread.
- A failed preparation leaves the last successfully installed replay intact but clearly marked as not matching the current draft. It must never silently play stale results as if they belonged to the new settings.
- Timeline scrubbing remains a playback operation and may continue to work according to the existing controller behavior.

An optional map-click shortcut may populate epicenter latitude/longitude while paused. The numeric fields remain canonical, keyboard-accessible inputs and are the values persisted to disk.

## 5. Domain model

### 5.1 Custom scenario settings

Add an immutable type such as `SimulationScenarioSettings` containing:

```text
scenarioId
displayName
epicenter
magnitude
depthKm
intensityDisplayMode
assumptionSetId
```

Use a stable generated `scenarioId` for a newly created scenario and preserve it across save/import. The domain currently requires an `originUtc`; use a persisted synthetic creation instant for identity/provenance, but do not present it as the origin time of a real earthquake.

Conversion to predictor inputs must use `ScenarioInputs.forCustomScenario(...)` or a refined successor. Historical references must not be accepted or manufactured.

### 5.2 Fixed assumption set

Define a versioned assumption set rather than scattering constants through UI code:

```text
calquake-custom-v1
  mechanism = generic strike-slip
  rake = 0 degrees
  strike = 0 degrees
  dip = 90 degrees
  rupture scaling = wells-coppersmith-1994-all-slip
  site Vs30 = 760 m/s
  site provenance = DEFAULT
```

Resolved mechanism, rupture geometry, site condition, coefficient versions, and provenance must participate in `InputSignature` and be retained in `PreparedReplay` metadata. This makes assumptions visible and ensures cache invalidation if a future release changes them.

### 5.3 Modular city catalog

Create a simulation-only city resource and loader, for example:

```text
src/main/resources/data/simulation_sites.json
```

Each entry contains only predictor/presentation information:

```text
id
display_name
latitude
longitude
```

Initially populate it with the existing five cities and coordinates. Do not copy GEOIDs, sampled ShakeMap nodes, historical intensity, or ground-motion observations into this contract unless a later feature has a specific non-predictive use for them.

Expose the catalog behind an interface such as `SimulationSiteCatalog`. `ScenarioInputs`, preparation, map rendering, and tests consume `List<SimulationSite>` and must not assume there are exactly five entries. Layout must tolerate additional cities; later scale work may add decluttering or spatial filtering without changing the scientific pipeline.

### 5.4 Presentation-independent map state

`MapCanvasPane` currently depends on the historical `Scenario`/`ReferenceLocation` model. Replace that dependency with a reference-free map view input such as:

```text
MapScenario
  EventSource source
  List<SimulationSite> sites
  optional ScenarioReferences references
```

or bind the map directly to the installed `PreparedReplay.inputs()` plus optional reference overlays.

The renderer must always take displayed intensity/status from `FrameState`. It must never read a historical peak from a location object to render Simulation mode. Replay may attach references separately for inspection or comparison.

Keep temporary compatibility adapters only at legacy Replay boundaries; do not create fake `ReferenceLocation` instances for a custom simulation.

## 6. Intensity presentation modes

The current prepared Simulated timeline stores a running maximum. Extend the prepared site result so one preparation can answer both presentation settings without rerunning BSSA14, TauP, or envelope preparation.

For each site and prepared sample time retain, or be able to derive deterministically:

```text
currentPgv(t) = predictedBssa14Pgv * normalizedEnvelope(t)
currentEstimatedMmi(t) = Worden2012(currentPgv(t)) when currentPgv > 0
maximumPgvReached(t) = max currentPgv(u), u <= t
maximumEstimatedMmiReached(t) = max currentEstimatedMmi(u), u <= t
```

Rules:

- Before modeled arrival, show `Not arrived`, not MMI 0.
- `MAXIMUM_REACHED` never decreases.
- `CURRENT_SHAKING` may decrease and eventually show a neutral `Shaking ended` or below-display-threshold state rather than preserving a strong badge.
- Switching the dropdown while paused should update the frame immediately from already prepared data and should not require scientific recomputation.
- The MMI legend remains common, but the panel/HUD must state which display meaning is active.
- The current-shaking value is an envelope-derived illustrative estimate. It must not be called a measured intensity or actual waveform.

If retaining both curves substantially increases sample memory when the city catalog expands, store the normalized envelope/current PGV and derive the two MMI views in constant time during frame lookup. Do not rerun travel-time or ground-motion models per animation frame.

## 7. Validation and warning policy

### 7.1 Hard validation errors

Reject only inputs or generated states that cannot be represented or computed safely:

- blank required fields;
- non-numeric, NaN, or infinite values;
- latitude outside `[-90, 90]`;
- longitude outside `[-180, 180]`;
- negative depth;
- generated invalid/non-finite rupture geometry;
- missing P/S arrivals required by the selected model;
- non-finite model output or a degenerate envelope.

Magnitude remains any finite numeric value at the form boundary. Extremely implausible magnitudes may later fail safely if the fixed model cannot produce finite geometry or motion.

### 7.2 Non-blocking warnings

Warn but allow preparation for:

- magnitude outside BSSA14's nominal `3.0–8.5` domain;
- magnitude outside the envelope's original `(2.0, 7.3]` calibration range;
- a city at or beyond the envelope's `200 km` calibration distance;
- Rjb beyond BSSA14's `400 km` domain;
- depth outside the range covered by CalQuake's historical arrival benchmarks;
- epicenter outside California or far outside the visible map;
- generated rupture extending outside the map;
- unusually shallow, deep, small, or large scenarios;
- raw predicted MMI outside the display legend range.

Warnings appear in three places:

1. An inline summary in Simulation Settings before Apply.
2. A persistent warning banner/badge after a warned replay is installed.
3. Per-site status or inspector detail where only some cities are outside a distance domain.

Use direct wording, for example: `Outside the model's tested/calibrated range. This toy simulation may be wildly inaccurate.` Do not use green success styling or a “validated” label for custom scenarios.

Warnings and domain status are part of the prepared result/provenance so save/import, cache hits, and rendering cannot lose them.

## 8. Save and import

### 8.1 File contract

Use a versioned UTF-8 JSON format with a dedicated extension filter such as `*.calquake.json`. Initial shape:

```json
{
  "schema_version": 1,
  "type": "calquake-simulation-scenario",
  "scenario_id": "...",
  "name": "Custom California Scenario",
  "created_utc": "...",
  "epicenter": {
    "latitude": 0.0,
    "longitude": 0.0
  },
  "magnitude": 6.0,
  "depth_km": 10.0,
  "intensity_display_mode": "MAXIMUM_REACHED",
  "assumption_set": "calquake-custom-v1"
}
```

Persist only user settings and stable assumption-set identity as authoritative inputs. Generated geometry and resolved assumptions may be included in an audit section, but import must regenerate them from the recognized versioned assumption set and verify any saved audit values rather than trusting them as hidden user inputs.

### 8.2 Save behavior

- Save is available only while paused.
- Save the current valid draft, not an unrelated previously prepared scenario.
- Use a JavaFX `FileChooser` and atomic write pattern: write a sibling temporary file, then replace the requested destination.
- Never overwrite a destination without the normal file chooser confirmation behavior.
- Report success/failure in the application status area without terminating the app.

### 8.3 Import behavior

- Import is available only while paused.
- Parse and validate into a new immutable draft before mutating visible state.
- Reject unknown `type`, unsupported future schema versions, missing required values, and malformed JSON with a specific error.
- An import failure leaves the current draft and installed replay unchanged.
- A successful import switches to Simulation mode if necessary, installs the draft, shows warnings, and prepares it asynchronously.
- Do not accept historical observations or executable/plugin configuration from the file.

Create a dedicated serializer/loader rather than extending the historical `ScenarioLoader` with conditionals that conflate the two formats.

## 9. Preparation, caching, and playback

Reuse the existing prepared-replay lifecycle:

1. Validate the draft and collect warnings.
2. Resolve `calquake-custom-v1` assumptions.
3. Load the current simulation city catalog.
4. Build reference-free `ScenarioInputs`.
5. Prepare Simulated intensity timelines and wavefronts on the existing background executor.
6. Install only if both preparation generation and active `ApplicationMode` still match.
7. Construct a fresh scenario-bound engine/controller with the prepared duration.

Input signatures must change when any of these change:

- epicenter;
- magnitude;
- depth;
- city list or coordinates;
- resolved mechanism/rupture;
- Vs30 assumption;
- scientific model/configuration version.

The intensity display dropdown should not invalidate the scientific preparation if both display curves are already available. It is presentation state and is persisted for user convenience, but should not create duplicate expensive cache entries.

Playback remains deterministic and offline. No network, file read, geometry generation, or scientific-model work may occur inside `AnimationTimer`.

## 10. Implementation sequence

### Stage A — Top-level application mode shell

- Add `ApplicationMode`.
- Convert the Mode menu to ordered radio items.
- Make Simulation the startup default.
- Split sidebar construction into Simulation and Replay variants.
- Preserve Replay behavior behind the Replay selection.
- Add state-transition tests before changing scientific preparation.

Exit gate: the app opens in a paused Simulation shell; switching to Replay restores the existing Ridgecrest/Northridge workflow; repeated switching cannot mix controllers or sidebar controls.

### Stage B — Reference-free map and modular cities

- Add `simulation_sites.json` and `SimulationSiteCatalog`.
- Move map presentation from `Scenario.locations()` to `SimulationSite`/prepared inputs.
- Keep historical references as an optional Replay overlay.
- Remove renderer assumptions about exactly five sites and fixed historical fields.

Exit gate: a synthetic `ScenarioInputs` with no `ScenarioReferences` renders all configured sites and frame-supplied statuses without constructing any `ReferenceLocation`.

### Stage C — Custom settings and assumptions

- Add `SimulationScenarioSettings` and `calquake-custom-v1` resolution.
- Build the right-side settings and fixed-assumptions panels.
- Add draft/installed/stale state handling.
- Implement hard validation and non-blocking warnings.
- Lock settings during playback.

Exit gate: changing each of epicenter, magnitude, and depth produces a distinct input signature and replay; warned values can run; invalid values cannot corrupt the installed scenario.

### Stage D — Dual intensity presentation

- Extend timeline/prepared state with current-envelope and maximum-reached values.
- Add `IntensityDisplayMode` and paused-time switching.
- Update badges, status text, tooltips, and legend context.
- Preserve running-maximum behavior for existing historical Simulated replay.

Exit gate: current shaking rises and falls; maximum reached never decreases; both are deterministic at the same requested timestamp; switching presentation does not rerun preparation.

### Stage E — Save/import

- Add the versioned JSON DTO and serializer/loader.
- Add JavaFX file chooser actions.
- Use atomic save and transactional import semantics.
- Preserve scenario identity and selected display mode.

Exit gate: a save/import round trip reproduces the same canonical settings, input signature, warnings, duration, and sampled outputs.

### Stage F — Integration, documentation, and polish

- Add persistent toy/accuracy language and domain warnings.
- Update README screenshots, usage, terminology, and scope.
- Update startup/error UI and accessibility names.
- Run full verification and a manual Windows UI walkthrough.

Exit gate: all completion criteria below pass with no regression to bundled Replay mode.

## 11. Required tests

### 11.1 Mode and lifecycle

- Mode menu order is exactly Simulation then Replay.
- Simulation is selected by default.
- Simulation has no Ridgecrest/Northridge selector.
- Replay retains both historical events and Recorded/Simulated choices.
- Mode changes pause/reset playback and reject stale preparation results.
- Draft state and Replay selections survive round-trip mode switching.

### 11.2 Inputs and warnings

- Latitude/longitude boundaries, finite values, and negative depth validation.
- Magnitude, distance, Rjb, and depth warnings at both sides of each documented boundary.
- Warned scenarios prepare and play.
- Non-finite/degenerate computations return a clear unavailable/error state.
- Fixed assumptions and provenance are present and visible.
- Inputs are disabled only while actively playing and restored on pause/finish/restart as specified.

### 11.3 Domain and isolation

- A custom simulation prepares with no `ScenarioReferences`.
- Adding, removing, or changing historical fixtures cannot affect its output.
- Each meaningful predictor input changes the signature.
- Presentation-mode changes do not require a new scientific cache entry.
- City catalog size/order is not hardcoded to five.

### 11.4 Timeline and rendering

- Before arrival is neutral.
- Current shaking may rise and fall with the prepared envelope.
- Maximum reached is monotonic.
- Direct timestamp lookup equals many small ticks and skipped-frame playback.
- Final maximum equals the existing BSSA14/Worden completed result within current numerical tolerances.
- Map badges always use `FrameState`, never historical location peaks.
- Raw out-of-range MMI remains available diagnostically while display clipping stays truthful.

### 11.5 Save/import

- Canonical round trip.
- Unknown type and schema rejection.
- Missing, malformed, non-finite, and out-of-range coordinate handling.
- Warnings survive regeneration.
- Failed import leaves current state untouched.
- Imported assumption-set versions are resolved explicitly; unknown versions fail safely.
- File operations are unavailable during active playback.

### 11.6 Regression verification

- Existing Ridgecrest and Northridge Recorded replay tests.
- Existing historical Simulated MMI scientific parity and benchmark tests.
- Replay event/mode switching, preparation race, scrubber, resize, and render tests.
- Full `./mvnw.cmd verify` on Windows.

## 12. Manual acceptance walkthrough

1. Launch CalQuake and confirm Simulation is selected by default.
2. Confirm all five cities appear and no historical event dropdown is visible.
3. Inspect the fixed fault and Vs30 assumptions and toy disclaimer.
4. Enter a plausible California epicenter, magnitude, and depth; Apply and play.
5. While playing, confirm settings, Apply, Save, and Import are disabled.
6. Pause, switch between current shaking and maximum reached, and verify the visual difference without a preparation delay.
7. Enter an out-of-domain but finite scenario, confirm prominent warnings, choose Apply, and verify it still runs or fails only with a clear numerical/model error.
8. Save the scenario, change its values, import the saved file, and confirm the prior result is reproduced.
9. Switch to Replay and confirm Ridgecrest/Northridge plus Recorded/Simulated work unchanged.
10. Rapidly switch modes and settings during preparation and confirm no stale result becomes visible.

## 13. Completion criteria

Simulation Mode is complete when:

- it is the first and default top-level mode;
- it accepts only epicenter, magnitude, and depth as required user inputs;
- it uses the modular current-city catalog and fixed, visible fault/site assumptions;
- its current-shaking/maximum-reached dropdown behaves truthfully;
- all valid finite scenarios are attempted, with prominent warnings outside model domains;
- settings cannot change during playback;
- save/import is versioned, transactional, and reproducible;
- custom prediction is structurally isolated from historical reference answers;
- stale asynchronous results cannot cross scenario or mode boundaries;
- Replay behavior remains intact;
- automated and manual verification pass;
- the UI never represents the result as scientifically validated, suitable for safety decisions, or an actual future-earthquake prediction.

## 14. Explicitly deferred

- Adding or editing observation sites in the UI.
- Full California city coverage and label decluttering.
- Continuous intensity grids or heatmaps.
- Editable mechanism, strike, dip, rupture, or Vs30.
- Geographic Vs30 lookup.
- Stochastic realizations and uncertainty bands.
- Live USGS imports or any network dependency.
- Waveform synthesis, finite-fault rupture evolution, directivity, basin effects, or 3D propagation.
- Scientific acceptance gates beyond retaining the existing benchmark/regression evidence and truthful limitations.
