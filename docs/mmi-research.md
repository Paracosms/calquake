# Time-dependent MMI research for CalQuake

> Implementation note (2026-09-10): this document preserves the exploratory research history. The shipped product terminology and architecture are now **Recorded** and **Simulated**. Recorded means a historical USGS ShakeMap city peak revealed at modeled S arrival; Simulated means the BSSA14/envelope/Worden running-maximum prediction documented in [`instantaneous-mmi.md`](instantaneous-mmi.md). Earlier option names below are superseded.

Research date: 2026-09-09. This is a design recommendation, not an implemented or benchmarked model. Accuracy rankings below describe suitability and physical detail; they are not measured rankings on CalQuake's dataset.

**Selected implementation scope and naming.** The follow-up decision uses the shortlist numbering: **option 1 = Validation MMI (direct intensity equation)**, **option 2 = Simulated MMI (empirical ground motion plus envelopes)**, and option 3 = finite-fault waveform simulation (future extension). This numbering supersedes the initial six-alternative ordering. Both selected modes predict intensity; “Validation MMI” names the baseline, not observed ground truth or a model that has already passed validation. Observed/reference intensity remains separate. This document specifies the planned implementation; it does not claim that either mode or its UI already exists.

| Comparison | Option 1: Validation MMI | Option 2: Simulated MMI | Option 3: finite-fault waveform simulation |
|---|---|---|---|
| Best fit | Simplest predictive baseline | Recommended production direction | Later detailed waveform backend |
| Method | Allen–Wald–Worden hypocentral IPE plus approximate timing | BSSA14 PGA/PGV, calibrated P/S envelope shapes, Worden GMICE | Established BBP method, then the same running-peak/GMICE stage |
| City behavior | Simple rise toward predicted final MMI | Statistically informed increases as P/S shaking develops | Irregular increases from synthetic pulses and rupture contributions |
| Estimated final-MMI accuracy rating | 6/10 | 7/10 | 7/10 initially; greater physical detail is not guaranteed to reduce MMI error |
| Relative implementation difficulty | 3/10 | 6/10 | 9/10 |
| Timing realism | Low; assumed progression | Moderate; empirical shape, independently validated | Highest of these candidates; exact historical pulses still uncertain |
| Required earthquake inputs | Magnitude, epicenter, depth | Magnitude, epicenter, depth, mechanism/rake, supplied or generated rupture geometry | Magnitude, fault position/dimensions/orientation, hypocenter on fault |
| Additional engine inputs | City coordinates, timing parameters | Site Vs30, derived rupture distances, envelope parameters; seed only if variability is sampled | Regional velocity/attenuation model, site properties, seed, method-dependent slip/stress/rupture-speed/rise-time inputs |
| Custom-sim user controls | Location, magnitude, depth | Location, magnitude, depth, fault preset; automatic site lookup | Fault/scenario preset and source parameters, with advanced rupture controls |
| New data/software | Published coefficients; current data can start evaluation | Rupture/configuration files, site data, processed waveform subset | Simulator dependencies, regional model packages, source files and waveform validation data |
| Runtime | Small Java calculation | Per-scenario preparation followed by inexpensive timeline lookup | Separate waveform generation followed by cached playback |
| Principal limitation | Simplified timing and site treatment | Proposed hybrid requires calibration and validation | High setup effort and sensitivity to uncertain source/Earth-model inputs |

The accuracy ratings are provisional engineering judgments, not accuracy percentages, achieved test results, or numerical error estimates. Their ordering may change after held-out tests. Difficulty is relative to this Java/JavaFX repository and includes integration and validation. No rating is an acceptance threshold.

**Final verdict.** Build option 1 as the reproducible baseline and option 2 as the main simulation mode. Compare their final-MMI and threshold-timing performance on the same held-out targets. Retain waveform replay as reference data and option 3 as an extension. More sophisticated methods require more engine assumptions, but geographic lookup and fault presets can keep custom-simulation controls manageable.

**Recommendation.** Build a ground-motion prediction layer, a separate time-history/envelope layer, and a shared running-peak intensity layer. Start with an empirical model and retain an inexpensive direct-intensity baseline. Use historical recordings as validation targets; use the same prediction path for historical backtests and custom scenarios.

**Define the quantity first.** MMI describes shaking effects. Instrumental MMI estimates those effects statistically from ground motion. The proposed display is “Maximum estimated MMI so far.” Applying a peak-motion conversion to a partial record is an application convention that must be validated; it does not establish a standardized instantaneous MMI scale. Worden et al. (2012) derive peak-motion/intensity relationships; PGV has the smallest errors among the individual measures they investigated. [USGS publication](https://pubs.usgs.gov/publication/70044008).

For a fixed event and conversion:

```text
peakVelocitySoFar(t) = peak metric of horizontal velocity samples through t
mmiSoFar(t) = GMICE(peakVelocitySoFar(t), fixed event/site context)
display(t) = max over valid mmiSoFar samples through t
```

GMICE means ground-motion-to-intensity conversion equation. Fix the horizontal-component convention and units; the expression above deliberately does not equate vector magnitude, maximum component, geometric mean, and RotD50. Those metrics differ. PGV-first, with PGA fallback chosen per usable record, is a practical starting policy. Current ShakeMap documentation prefers PGV over PGA when available. This does not imply that every historical ShakeMap product used precisely the same configuration. [ShakeMap processing manual](https://ghsc.code-pages.usgs.gov/esi/shakemap/docs2020/manual4_0/tg_processing.html).

Before the signal arrives, show a neutral “Not arrived” state; distinguish that from missing data and from measured weak shaking. Do not take log(0), fabricate MMI 0, add P and S intensities, or sum MMI from subfaults. If a model predicts an amplitude envelope rather than a waveform, label its running maximum as an envelope-based estimate. A short-window “shaking now” effect may decrease, while the maximum badge remains at its highest value.

**What is already in this repository.**

| Asset | Verified contents | Useful for | Missing information |
|---|---|---|---|
| `src/main/resources/data/event.json` | Ridgecrest M7.1, 8 km depth, origin and coordinates | Historical source and timing inputs | Complete rupture evolution |
| `five_reference_locations.json` | City points, peak MMI, PGA, PGV, spectra, SVEL | Display references and initial site data | Waveforms and peak timestamps |
| `ridgecrest-2019-test/grid.xml` | MMI/PGA/PGV/spectra/SVEL grid; finite-source product | Historical reference field | Time evolution; independent raw observations |
| `ridgecrest-2019-test/stationlist.json` | 943 seismic and 4,177 macroseismic entries; 543 seismic entries within 300 km epicentral distance, before quality filtering | Many more validation locations than the five cities | Waveform samples |
| `observed_picks_ci38457511.json` | Frozen 78 P and 16 S picks | Arrival benchmark | Motion amplitudes and duration |
| TauP/Hadley–Kanamori model | Existing travel-time and wavefront machinery | Onset estimates | Full ground-motion amplitudes and scattering |

Counts were obtained by parsing the local JSON. Instrumental entries include channel amplitudes, flags, station coordinates, Vs30, and explicit distance fields such as Rjb/Rrup. The same entries also contain predictions: those must not be mistaken for measured amplitudes. Macroseismic entries contain reported intensity; at least the inspected example has `nresp = -1`, so usable response-count metadata must be checked before weighting.

The five MMI values (7.2, 6.9, 3.9, 3.8, 3.1) are nearest-grid-node samples at Census internal points, not citywide maxima or five direct sensor measurements. Offsets are approximately 0.26–0.62 km. Preserve this definition for comparison. If a future product means maximum anywhere inside a city, evaluate multiple sites inside the city and apply the same spatial aggregation to its reference data.

The local event catalog lists frozen Atlas `rupture.json`, `info.json`, and `uncertainty.xml` URLs under product timestamp `1594160054783`. Their contents were not retrieved during this research. Retrieve them before claiming exact compatibility with that product's GMPE/GMICE configuration. Verify SVEL's original definition before promoting it to a canonical Vs30 field.

**Alternative: reveal the existing ShakeMap peaks — easiest visual implementation, outside the selected predictive modes.**

Keep each city's known peak and reveal it at an arrival, or ramp toward it using an assumed envelope. No additional data is necessary. Final agreement with the supplied peak is guaranteed by construction, so it is a replay/display check, not a prediction result. Timing remains assumed. For custom scenarios, the user must provide each site's final intensity or another model must generate it. This option alone therefore does not meet the shared predictive-algorithm objective.

**Option 1 / Validation MMI — direct intensity prediction equation plus timing.**

Use an established active-crustal IPE such as the hypocentral-distance form of Allen, Wald and Worden (2012). The published OpenQuake implementation offers both rupture-distance and hypocentral-distance variants; the inspected implementation does not include a site-amplification input. Predict the final MMI, then apply an explicitly approximate timing envelope. [OpenQuake IPE source](https://docs.openquake.org/oq-engine/3.6/_modules/openquake/hazardlib/gsim/allen_2012_ipe.html).

Custom inputs: magnitude, epicenter, depth, destination coordinates, and a selected/default timing model. Use the hypocentral variant with hypocentral distance rather than silently substituting that distance into the rupture-distance equation. A rupture-distance variant additionally requires fault geometry. This is an excellent baseline, and can be implemented compactly in Java. It gives limited control over local soil effects and does not independently predict when peaks occur. More elaborate models are not guaranteed to beat this baseline on final MMI.

**Option 2 / Simulated MMI — empirical motion envelopes and GMICE.**

Predict acceleration/velocity amplitude and its evolution, then compute maximum estimated MMI through the common reducer. Two useful candidates share this architecture:

- Start with Cua–Heaton empirical P/S envelopes as an integrated amplitude-and-timing candidate.
- Evaluate a hybrid with NGA-West2 BSSA14 peak PGA/PGV and a separately calibrated empirical envelope shape. This hybrid is a proposed composition, not a published end-to-end algorithm with established CalQuake accuracy.

Cua–Heaton models the P and S contributions using rise, duration, and decay parameters with magnitude, distance, and site dependence. Its envelope dataset is Southern California, within 200 km, with magnitudes above 2 through 7.3. CalQuake's LA and Fresno points lie beyond that distance range; extending temporal parameters there requires explicit validation. [Cua–Heaton technical report](https://authors.library.caltech.edu/records/9cc84-ht018).

BSSA14's base implementation uses magnitude, rake, Vs30, and Rjb, and supports PGA/PGV. Rjb is distance to the surface projection of the rupture, not hypocentral distance. Its horizontal metric is RotD50. [OpenQuake BSSA14 implementation](https://docs.openquake.org/oq-engine/3.21/manual/_modules/openquake/hazardlib/gsim/boore_2014.html).

For the hybrid, choose an explicitly defined envelope combination, normalize its total peak to the predicted motion peak, and validate the time shape. Do not independently normalize both P and S to the total-event peak and then add them. A GMM supplies peak statistics, not waveform phases or rise times. Do not repeatedly reapply its site-amplification calculation to instantaneous amplitudes. An envelope for velocity also does not supply a physically consistent acceleration record simply by copying its shape.

Custom inputs: magnitude, epicenter, depth, selected sites, and site category for a simple envelope model. For BSSA14, add fault mechanism/rake, a supplied or generated rupture footprint for Rjb, and site Vs30. Use geographic site lookup and fault presets to keep the UI small. Regional envelope parameters can be defaults. A seed is optional if sampling variability; retain a deterministic median mode.

The engine needs more assumptions than the basic UI needs controls. Persist generated rupture dimensions, site-data provenance, model versions, and every default. For a large nearby event such as Ridgecrest, geometry is more consequential than another visual easing function. This option supports offline Java playback after inexpensive scenario preparation. It captures statistical trends, but not the exact sequence of pulses at an individual historical station.

**Reference method: recorded waveform replay — best fidelity at instrumented historical sites.**

Use processed horizontal velocity/acceleration records, align their start times to the frozen origin, and derive running peaks. This is the strongest reference for the timing of intensity increases at recording locations. Mapping a station to a nearby city still introduces spatial error. With multiple stations, interpolate appropriately aligned amplitude/envelope information or residuals; do not naively average phase-misaligned raw traces.

Custom inputs: a waveform for every target site, or a separate synthetic waveform generator. The shared intensity reducer can be identical, but waveform replay is not itself a shared earthquake prediction algorithm. This makes it especially useful as the benchmark/reference method alongside either selected mode or future option 3.

CESMD hosts processed Ridgecrest sequence data: 22,375 records from 131 events spanning M3.6–7.1, with downloadable intensity/duration metadata and time series. The mainshock ASDF/HDF archive alone is listed as 3.55 GB. Start with selected stations rather than the complete archive. [Dataset](https://www.strongmotioncenter.org/specialstudies/rekoske_2019ridgecrest/), [time-series inventory](https://www.strongmotioncenter.org/specialstudies/rekoske_2019ridgecrest/timeseries/).

SCEDC also exposes waveform, station-response, and availability services. Raw records need instrument correction and quality control; reliable velocity extraction requires suitable filtering and baseline treatment. Final offline processing may use future samples, so do not describe such replay as a causal live estimator. [SCEDC services](https://service.scedc.caltech.edu/).

**Option 3 — stochastic or hybrid finite-fault simulation: strongest practical waveform extension.**

Generate synthetic records, then use exactly the recorded-waveform intensity reducer. Stochastic simulation constructs spectra and random phases with duration constraints. Relevant physical parameters include stress parameter, attenuation, spreading, and site amplification. [Boore stochastic-method overview](https://www.usgs.gov/publications/simulation-ground-motion-using-stochastic-method), [parameter discussion](https://pubs.usgs.gov/publication/70147405).

Use an established simulator instead of implementing one inside the JavaFX renderer. SCEC's Broadband Platform includes rupture generation, low/high-frequency synthesis, site effects, and validation tools. Many established BBP methods use layered 1D structures; BBP should not be described generically as full 3D physics. [SCEC BBP](https://southern.scec.org/software/bbp).

Custom inputs: magnitude; fault position, dimensions, strike, dip, rake, and top depth; hypocenter location on the fault; site coordinates and site properties; regional velocity/attenuation model; random seed. Depending on the selected method, supply or generate slip, rupture speed, rise time, stress parameter, and other method-specific settings. BBP's source format documents the basic fault and hypocenter fields. [BBP input formats](https://github.com/SCECcode/bbp/wiki/File-Format-Guide).

Fault/region presets can fill many of these fields, but defaults create uncertainty rather than information about the actual earthquake. Expect slower scenario preparation, more downloads, native/Python dependencies, and ensemble evaluation. A plausible synthetic waveform is not an exact historical reconstruction, and one seed is not an adequate accuracy benchmark. After precomputation, playback can remain lightweight and offline.

**Long-term alternative: 3D physics with broadband treatment — highest physical-detail ceiling.**

Model the source and propagation through a spatially varying Earth model, optionally combining resolved lower frequencies with stochastic higher frequencies. This can represent basin/path effects that a simple envelope cannot. Published SCEC work explicitly combines 3D simulation with broadband methods rather than assuming low-frequency motion suffices for all strong-motion measures. [SCEC 3D broadband work](https://seismosoc.secure-platform.com/a/gallery/rounds/43/details/12632).

Custom inputs: the finite-fault inputs above; a 3D Vp/Vs/density/attenuation model and computational domain; resolved source-time/slip representation, mesh and boundary choices, and relevant site/topographic treatment. Dynamic-rupture formulations need additional stress/friction assumptions. These are principally backend/scientific configuration, not reasonable everyday form fields.

This is the highest-cost research direction, often requiring substantial compute and preprocessing. Inaccurate source or Earth-model assumptions can erase its theoretical advantage. Do not promise superior final-MMI accuracy without comparison; reserve it for a later simulation backend or imported precomputed scenarios.

**Data acquisition priority.**

1. Extract a compact benchmark from existing station amplitudes and felt reports; retain flags, units, horizontal components, distances, and site provenance. Current counts are inventory counts, not validated sample sizes.
2. Retrieve the frozen rupture/configuration/uncertainty products identified in the local event catalog. Keep their versions and hashes.
3. Obtain a geographically and geologically varied subset of processed waveforms near and far from the source, including the five city regions where coverage permits. Inspect absolute timing, gaps, clipping, free-field status, and processing metadata. Get enough pre-event baseline and post-arrival coda; do not assume 120 seconds captures every final peak.
4. For custom locations, use a regional Vs30 dataset or a California subset of the USGS mosaic. Vs30 characterizes shallow site conditions; it is not the deep propagation model. [USGS Vs30 resources](https://earthquake.usgs.gov/data/vs30/).
5. Add separate earthquake sequences for external validation. The 131 Ridgecrest events help but share geography and stations; they do not substitute for broad out-of-region/event testing.

**Validation that supports defensible resume claims.**

Keep three evaluations separate:

| Evaluation | Target | Suggested metrics | Meaning |
|---|---|---|---|
| Ground-motion prediction | Quality-controlled instrumental PGA/PGV at exact stations | Log residual bias/scatter, ratios, stratification by distance and Vs30 | Tests source/path/site model |
| Final intensity | Held-out reported intensity; separately, instrumental-MMI and ShakeMap references | MMI MAE/RMSE, signed bias, fraction within one MMI unit, threshold confusion | Tests intensity estimates, with each target's provenance stated |
| Time development | Processed recorded running-peak curves | Time to IV/V/VI, threshold misses/false crossings, time to 90% of final PGV, curve discrepancy | Tests the envelope/history layer |

Do not feed final ShakeMap MMI or station peaks into the predictor and claim that agreement with those same quantities validates prediction. Likewise, converting PGV with Worden and comparing with station MMI derived from that PGV is a conversion consistency check. ShakeMap fields combine observations and models, so map agreement is useful but not independent sensor truth. [USGS ShakeMap interpolation research](https://www.usgs.gov/publications/a-revised-ground-motion-and-intensity-interpolation-scheme-shakemap).

Match locations, units, component definitions, filters, and usable frequency bands. Do not compare BSSA14 RotD50 predictions directly with maximum-component records without a documented compatible treatment. Score unrounded MMI; round only for display. Preserve uncertainty rather than hiding it behind decimal formatting.

Freeze train/tuning/test splits by event; use spatial blocking when fitting station corrections, and add a separate sequence for generalization. Keep all sites for one test earthquake out of event-specific tuning. For synthetic ensembles report coverage and distribution errors, not only the best realization. Set acceptance criteria before examining test results; this research establishes no achieved error or guaranteed accuracy target.

A future resume statement could read: “Built deterministic, time-dependent earthquake intensity simulations; evaluated on N held-out events and K stations, achieving X MMI MAE and Y-second median threshold-crossing error.” Fill in the numbers only after measurement, and explicitly name whether the reference is felt intensity, instrumental intensity, or ShakeMap.

**Integration plan for the existing code.**

`ReplayEngine` currently pre-resolves `fixedIntensities`; `IntensitySource` has no time argument. `LocationIntensityState` stores a historical `PeakIntensity`, and `MapCanvasPane` draws badges from `loc.peakIntensity()` on the static canvas. Merely computing a changing MMI in the engine will therefore not animate the existing badges.

Introduce an immutable per-scenario intensity timeline, with a pure `stateAt(time)` lookup. Keep optional observed/reference values separate from predicted values so that a custom scenario need not contain historical MMI. Bind each prepared timeline and travel-time cache to the complete scenario inputs. The current alternate-scenario `frameAt(targetScenario, t)` path retains the original wavefront cache, so scenario changes require deliberate reconstruction/invalidation.

Precompute running peaks at full numerical/waveform resolution, then decimate for display while preserving peaks and threshold times. Never estimate the maximum only from JavaFX frame samples. Prefix maxima make pause, restart, scrubbing, skipped frames, and seeded replay deterministic. Persist model and processing provenance with each result.

Move intensity badges to a layer rendered from `FrameState`; keep the geographic base map static. Extend frame state with value, arrival/data status, uncertainty/provenance, and optional historical reference. Make the replay horizon scenario-dependent before claiming final-peak completeness. Update tests that intentionally assert constant historical badges, and retain tests for deterministic time lookup, unit conversion, threshold preservation, missing data, and scenario isolation.

Use a scientific reference implementation offline for model parity and data preparation, then choose a small Java implementation or a local preprocessing backend. Heavy waveform generation belongs outside the JavaFX animation loop. The immediate research prototype should compare the direct IPE, the integrated envelope candidate, and BSSA14 plus a calibrated envelope before choosing the production default.

**Required Event Selector UI.**

Add two mutually exclusive radio buttons with exactly these visible labels: **Validation MMI** and **Simulated MMI**. They belong inside the existing Event Selector `group-box`, below “Select earthquake sequence:” and the existing earthquake dropdown. They are MMI modes, not earthquake names; preserve the separate earthquake selector.

```text
┌ Event Selector ────────────────────┐
│ Select earthquake sequence:       │
│ [ Ridgecrest                   ▾ ]│
│                                  │
│ ○ Validation MMI   ● Simulated MMI │
└───────────────────────────────────┘
```

Implement in `CalQuakeApp.buildEventSelectorBox()` with a `ToggleGroup` and two `RadioButton`s. Use `MmiMode.VALIDATION` and `MmiMode.SIMULATED` as toggle data, rather than branching on label text. Proposed default: Simulated MMI once that mode is implemented and its required resources are available. During staged development, default to Validation MMI and leave Simulated MMI visibly disabled until implemented; do not run the baseline under the simulated label. In a completed release, show a resource/setup error rather than silently substituting algorithms.

Use an `HBox` that fits the current sidebar; switch to a vertical layout if necessary at narrow supported sizes. Give controls stable IDs (`validation-mmi-option`, `simulated-mmi-option`), keyboard navigation, and accessible names. Tooltips can say “Baseline intensity estimate” and “Ground-motion simulation estimate.” A badge tooltip/readout should identify “Maximum estimated MMI so far” and the active mode. Historical ShakeMap comparisons, when shown, must be explicitly identified as references.

Changing mode preserves the chosen earthquake but pauses and resets playback to T+0. Prepare the replacement engine/timeline off the JavaFX thread, then swap the engine and controller together and render their initial frame. While preparing, prevent Play from using stale results. Ignore superseded preparation results after rapid selections. Restart keeps the selected mode; mode changes do not require an approval dialog. An event change also resets playback and prepares a new scenario, preserving the mode where supported. The existing dropdown lists Northridge, but only the Ridgecrest dataset has been verified here: do not label Ridgecrest calculations as another event if that selection lacks actual scenario data.

**Shared domain and data preparation for both modes.**

The following are proposed types and responsibilities; names can be adjusted to fit the repository without changing the contracts.

| Proposed component | Responsibility |
|---|---|
| `MmiMode` | Stable selection and serialization of the two algorithms |
| `SimulationSite` | Site ID, coordinates, optional site properties and their provenance; requires no historical intensity |
| `ReferenceIntensity` | Optional observations/ShakeMap samples used only for comparison |
| `MmiModelConfig` | Coefficient/model versions, timing defaults, supported domain, integration step, optional seed |
| `IntensityModel.prepare(inputs)` | Produce immutable per-site timelines without accessing reference intensities |
| `IntensityTimeline.stateAt(t)` | Pure lookup of estimated MMI, status, and optional available uncertainty |
| `PreparedReplay` | Bind scenario, mode, complete input signature, timeline, wavefronts, and duration |
| `IntensityBenchmarkRunner` | Run both predictors at frozen test sites and export paired scientific metrics |

Keep historical loading backward compatible through an adapter: derive simulation sites from the existing `ReferenceLocation`, and store its sampled grid/peak values separately as references. The current `ReferenceLocation` constructor requires historical peaks and a sampled node, so simply reusing it for custom sites would force fabricated observations. The predictor API must never require those fields. Initially retain the legacy fixed `IntensitySource` only for historical fixture/import verification; route both selected UI modes through the new prepared-model interface.

Freeze event origin, magnitude type, coordinate convention, and units. Use the existing travel-time model with epicentral surface distance and depth for P/S arrivals. Separately compute each amplitude model's required distances: Rhypo for option 1, Rjb for BSSA14, and the chosen GMICE/envelope distance definitions. Keep those fields distinct. For a point-source baseline, document any `sqrt(epicentralDistance² + depth²)` Rhypo approximation and its supported range; validate it against an independent geometric calculation. Do not substitute a single generic distance into every model.

Convert historical resources into compact, versioned inputs and separate reference fixtures. Retrieve rupture geometry for Ridgecrest before running the BSSA14 benchmark near the fault. Existing station Rjb values can test geometry calculations but do not supply a general geometry model for custom cities. Obtain site properties through a documented provider, with measured/proxy/default status. Pin all inputs and processing recipes in a manifest with hashes, retrieval URLs, model IDs, component conventions, and calibration split IDs.

**Option 1 implementation: Validation MMI.**

1. Implement a pure `Allen2012HypocentralIpe` that returns the unrounded predicted final intensity and the model's applicable uncertainty. Freeze the hypocentral variant, coefficient source, magnitude convention, distance units, and valid domain. Use the source implementation already cited above as an independent numerical reference, verifying its comments against the underlying publication where needed.
2. Supply only magnitude and Rhypo to the peak predictor. Event location/depth/site coordinates derive Rhypo and arrivals. Model observations and ShakeMap peaks must never enter the calculation. Preserve out-of-domain flags and unbounded diagnostic values; constrain only the displayed intensity to the supported legend range, with explicit saturation metadata.
3. Add a deterministic `BaselineIntensityProgression`. This is a visualization/timing assumption, not an IPE capability. A concrete initial proposal is a weighted pair of smooth ramps beginning at the P/S arrivals:

```text
smooth(u) = x*x*(3 - 2*x), where x = clamp(u, 0, 1)
progress(t) = alpha * smooth((t - tP) / riseP)
            + (1 - alpha) * smooth((t - tS) / riseS)
displayMmi(t) = 1 + (displayFinalMmi - 1) * progress(t)
```

Before tP, emit `NOT_ARRIVED` instead of an intensity. As an explicitly illustrative baseline configuration, start with `alpha=0.15`, `riseP=2 s`, and `riseS=8 s`. These numbers are proposed animation defaults, not published seismological estimates. Persist them, report them with timing results, and tune only on a declared training split if tuning is undertaken. Do not claim they model actual P/S energy fractions. Apply a prefix maximum or prove the analytic progression is monotone, and retain the analytic final prediction separately from its display ramp.

4. Make final intensity available to the benchmark independently of playback. Derive the required horizon from the arrival and rise times; a 120-second cutoff is a partial replay if a site has not finished. If a required arrival is unavailable, record a timing-unavailable status rather than inventing a zero-second arrival or forcing the city to its final value.
5. Integrate the prepared baseline timeline into `ReplayEngine.frameAt(t)` and the selected-mode controller. New scenarios regenerate distance/arrival/timeline caches. A scenario without reference values must work identically.

Custom-simulation contract: magnitude, epicenter, depth, and target sites are required; timing parameters come from the saved baseline configuration. No fault geometry, site map, waveform, or observed final MMI is required for this specific IPE variant.

**Option 1 tests and evidence.**

| Proposed test | Cases and expected result |
|---|---|
| `Allen2012HypocentralIpeTest` | Freeze externally generated expected predictions across supported magnitude/distance values, including the 50 km distance-term boundary, very short distance, and invalid input. Compare unrounded means and uncertainty with the independent reference; proposed absolute tolerance 1e-6 MMI. |
| `BaselineIntensityProgressionTest` | Check immediately before/at/after P and S, completed ramps, repeat/out-of-order queries, and missing arrivals. No increase before P; completed display equals the display-bounded IPE peak; prefix maxima never decrease. |
| `PredictionInputIsolationTest` | Changing/removing reference MMI and station peak observations cannot change the baseline prediction or timeline. |
| Final-MMI benchmark | Predict at instrument and felt-report locations. Compare directly with reported intensity; separately compare with instrumental-MMI and ShakeMap references. Do not claim baseline PGA/PGV skill because it produces no PGA/PGV. |
| Temporal benchmark | Compare MMI threshold-crossing times with recorded-waveform-derived intensity. Label baseline rise defaults as assumed. Timing results are not validated by passing the existing P/S arrival test. |

The baseline needs no new raw waveform data to begin evaluating final-MMI error. Until waveforms are available, mark temporal scientific validation as pending rather than treating ramp-unit-test success as evidence of physical timing accuracy.

**Option 2 implementation: Simulated MMI.**

1. Implement `RuptureGeometry` and `SiteConditionProvider`. For historical backtests load the event's frozen geometry and independently sourced site data; for a custom event use user geometry or a documented fault preset with a published dimension-scaling relation. Persist the selected relation and generated dimensions. Confirm the requested hypocenter lies consistently on/within the generated rupture, and reject inconsistent geometry. Validate derived Rjb with reference sites near the fault, above its projection, and far away.
2. Implement a deterministic BSSA14 median PGA/PGV predictor for the selected regional/base variant. Start with the base no-basin configuration to bound scope. Correctly apply magnitude/mechanism, path, and nonlinear site terms, including the reference-rock PGA used by the site calculation. Freeze coefficients and test numerical parity against a pinned independent implementation. Keep the predictor's logarithmic output and physical-unit conversion explicit; do not exponentiate twice or confuse natural logarithms with log10.
3. Define one `Worden2012Gmice` implementation with a pinned revision, amplitude units, horizontal-component treatment, magnitude/distance correction policy, valid range, and low-amplitude behavior. Convert acceleration from g or percent-g into the units required by that implementation; retain velocity in its documented units. Store raw PGA/PGV predictions for validation even when only MMI is displayed. The historical legend colors do not constitute the conversion equation.
4. Implement `EmpiricalEnvelopeModel` using the cited Cua–Heaton P/S shape parameterization as the starting candidate. Port and verify rise/duration/coda definitions and the exact distance/site conventions; do not infer coefficients from chart colors. In the BSSA14 hybrid, obtain the total peak from BSSA14 and use calibrated relative amplitudes/shapes for timing. Treat this hybrid as its own versioned model. Use event-independent configuration at prediction time; do not fit a test site's timeline to its known peak time or amplitude.
5. Define the envelope combination explicitly. One initial candidate is the nonnegative sum of P and S amplitude envelopes followed by a single normalization of the combined envelope. This is a statistical envelope approximation, not coherent vector-wave addition. Evaluate this combination against training waveforms before freezing it. Do not independently scale both phases to the full-event predicted peak. If the combined envelope is degenerate or has no supported timing, return a model status rather than dividing by zero.

```text
rawEnvelope(t) = combine(pEnvelope(t), sEnvelope(t))
shape(t) = rawEnvelope(t) / maximum(rawEnvelope over complete model support)
velocityEnvelope(t) = predictedPGV * shape(t)
pgvSoFar(t) = maximum(velocityEnvelope through t)
mmiSoFar(t) = Worden2012(pgvSoFar(t), fixed conversion context)
```

For median PGV-first mode, the completed envelope's maximum must equal the BSSA14 PGV; the completed MMI must equal its frozen GMICE conversion within numerical tolerance. These are pipeline consistency checks, not accuracy claims. Normalizing a fully prepared synthetic envelope uses the model's own predicted peak and is suitable for offline simulation; it must not use an observed test-event peak.

6. Compute PGA envelopes separately if needed, with acceleration-specific parameters. A velocity envelope does not define an acceleration waveform. Choose PGV versus PGA fallback once per site/record under a documented validity policy; avoid switching conversion branches on each frame. Apply an explicit noise/validity threshold to imported recorded data. Both measures, when compared to recordings, require compatible horizontal-component definitions; raw maximum-component station summaries are not interchangeable with BSSA14 RotD50. Establish a documented conversion/metric adapter and propagate its uncertainty before claiming end-to-end GMICE compatibility.
7. Precompute a timeline at a fixed numerical step; proposed initial envelope step is 0.05 s, subject to convergence tests at 0.025 s. Include arrival/rise/peak breakpoints explicitly or evaluate analytic peaks so narrow extrema cannot be missed. Stop only after every modeled site has passed its relevant envelope peak; retain a documented coda/end policy and mark truncated sites. For waveform-derived references, calculate peaks at native sample resolution before producing compact display data.
8. Retain a deterministic median mode for the initial implementation. If sampled variability is added later, sample event/site residuals consistently and keep each realization fixed through replay; do not draw fresh randomness per frame. Carry GMM and GMICE uncertainty with appropriate propagation or mark combined uncertainty unavailable. Do not present the GMM's ground-motion sigma as an MMI error bar.

Custom-simulation contract: require magnitude, epicenter, depth, target sites, and mechanism/rupture specification. The UI may obtain mechanism and dimensions from a fault preset, and Vs30 from geographic lookup. Envelope coefficients and regional model selection belong in versioned configuration. If data are missing, either use a declared supported default with provenance or return a clear unavailable state. Do not secretly use historical site peaks as a fallback.

**Option 2 tests and evidence.**

| Proposed test | Cases and expected result |
|---|---|
| `RuptureDistanceTest` | Analytic simple faults and external reference geometries; Rjb zero inside surface projection; dip/orientation/top-depth conventions correct; distinguish Rjb, Rhypo, and epicentral distance. |
| `Bssa14GroundMotionTest` | Independent golden outputs for PGA/PGV across supported magnitudes, mechanisms, distances and site velocities, including piecewise boundaries and nonlinear sites. Proposed tolerance 1e-6 in natural-log motion for a matching double-precision implementation. |
| `Worden2012GmiceTest` | Golden values at both sides of conversion breakpoints and magnitude/distance corrections; unit equivalence; invalid/zero amplitudes; branch continuity as defined by the selected implementation. Proposed tolerance 1e-6 MMI. |
| `EmpiricalEnvelopeTest` | Correct onset, rise, peak, and decay; finite outputs; unit-maximum normalization; no double-counting of the full-event peak; independent parameter fixtures. Flag LA/Fresno beyond the original 200 km envelope calibration range. |
| `SimulatedMmiTimelineTest` | Native/model-resolution peaks survive display decimation; final PGV matches predicted PGV; final MMI matches GMICE; running maximum remains unchanged after shaking decays. Step halving changes MMI by less than a proposed 0.01 unit and matched threshold times by less than 0.1 s, or the resolution must be improved. |
| `PredictionInputIsolationTest` | Replace/remove all historical peaks and reference MMI while retaining legitimate source/site inputs; simulated outputs stay unchanged. Changing magnitude, geometry or site inputs invalidates relevant cached results. |
| Ground-motion benchmark | Score predicted PGA/PGV at quality-controlled stations using compatible components. Report distance/site/mechanism strata and usable counts. |
| Final-MMI and temporal benchmarks | Use the same target cohorts and reference definitions as option 1; export paired errors plus waveform-derived timing metrics. Do not choose an envelope variant by test-set performance. |

The proposed tolerances above concern software parity and numerical resolution, not achievable earthquake-prediction accuracy. Inspect reference precision before freezing tolerances. Release comparisons must report scientific error separately.

**Common benchmark protocol: how to test both modes fairly.**

1. Build separate immutable input and target fixtures. Inputs contain event, sites, geometry where applicable, site properties, and model configuration. Targets contain channel measurements, reported intensity, optional ShakeMap samples, and processed waveform-derived curves. Record exclusions, preprocessing/component policies, and absolute timing alignment. Do not use `stationlist.json` prediction fields as observations.
2. Freeze train/tuning/test event IDs and any spatial blocks before calibration. With Ridgecrest mainshock alone, report a single-event case study; there is no honest event holdout within one earthquake. Additional sequence events permit event splits, and an independent sequence tests broader generalization. Keep the existing frozen arrival cohort and its thresholds unchanged.
3. Execute both predictors on the same common evaluable sites. Publish full coverage/exclusion counts for each model as well, so a model cannot appear better by skipping difficult sites. Compute final predictions over complete model horizons; score any common shorter replay window separately with truncation flags.
4. Report final-MMI MAE, RMSE, signed bias and fraction within one unit separately for reported, instrumental-derived, and ShakeMap targets. Score raw unrounded model outputs with an explicit out-of-domain policy; log display clipping separately. Use paired errors when comparing the two modes, stratify by distance and site condition, and estimate uncertainty by event-level resampling when enough independent events exist.
5. Freeze numerical intensity thresholds. To match the existing legend's category onsets, proposed threshold-crossing levels for IV/V/VI are 3.5/4.5/5.5, not silently 4/5/6. Store the numeric thresholds in reports. For crossings in both prediction and reference, report signed and absolute timing errors. Separately count missed and false crossings; do not discard them from an apparent timing-success rate. Distinguish right-censored recordings from true non-crossings.
6. Score option 2's PGA/PGV and, where useful, time to 90% of final PGV. Mark these quantities N/A for option 1. A normalized MMI progression comparison is possible for both modes but must be named distinctly from PGV progression.
7. Export `target/mmi-benchmark/site-results.csv`, `summary.json`, and `report.md` with model IDs, input hashes, split IDs, mode, event/site IDs, predictions, targets, residuals, crossings, coverage and statuses. CSV is an output of the future runner, not a new artifact generated by this documentation task.
8. Define scientific improvement gates before seeing held-out results. A proposed decision rule is lower paired final-MMI MAE for option 2 without a material timing/coverage regression, assessed with uncertainty and per-event results. Set the numerical regression margins using training/validation data and freeze them. If no improvement is demonstrated, retain option 2 as a richer temporal model and report that result honestly; the provisional 6/10 and 7/10 ratings must not replace evidence.

**UI, replay and integration tests for both modes.**

Parameterize shared tests over `MmiMode.values()`. Extend the existing fake-clock and JavaFX test helpers rather than adding real-time sleeps.

- In `MmiModeSelectorTest`, assert both exact labels are inside the Event Selector box below the sequence selector, exactly one mode is selected, the dropdown still selects an earthquake, and controls remain readable at supported sizes. Test keyboard selection and selection-state/accessibility behavior.
- In `MmiModeSwitchIntegrationTest`, select each mode while paused, playing and finished. Verify the event is preserved, playback pauses at zero, city state resets, and the new mode actually selects a different model implementation. Test rapid switches and failed preparation so stale timelines cannot win.
- In `ReplayDeterminismAndSyntheticScenarioTest`, query the same timestamp directly, after many small ticks, and after a skipped-frame interval. The intensity must match. Repeat after restart and with two custom scenarios differing in depth/source/site inputs. No timeline or wavefront cache may leak between scenarios.
- In render tests, use deliberately different synthetic timelines for the two model adapters. Verify neutral badges before arrival, later intensity/color changes from `FrameState`, final maxima remaining after envelope decay, and correct redraw after resize. Retain geographic/static-layer tests. Rewrite tests that require fixed historical badges during every frame only where the new requirement supersedes them; preserve source-data and legend verification.
- Verify no-reference custom scenarios, missing Vs30/rupture inputs, unsupported model domain, missing P/S arrivals, and replay truncation have distinct, truthful states. The current historical `PeakIntensity` validator cannot represent all of these states, so use the new status/value representation rather than placeholder values.
- After mode preparation, playback must use bundled/prepared resources and perform no network or waveform-generation work in `AnimationTimer`. Verify the configured simulation horizon replaces the current hardcoded 120-second labels and controller limit consistently.

**Build sequence and completion criteria.**

| Stage | Deliverable | Required check before proceeding |
|---|---|---|
| A | Domain separation, mode selector, prepared timelines, frame-driven badges | Fake timelines pass both-mode UI/replay tests; historical fixture and arrival tests still pass |
| B | Validation MMI predictor and explicit baseline timing config | Independent IPE parity, deterministic ramp behavior, no-observation-input test, first final-MMI benchmark |
| C | Rupture/site providers, BSSA14, Worden conversion | Geometry and scientific component parity; unit/component conventions documented |
| D | Simulated MMI envelopes and completed shared selector | Envelope parity/normalization/convergence, same-engine custom scenario support, mode switching and horizon tests |
| E | Waveform reference subset and paired evaluation | Frozen splits, reproducible final-MMI/timing reports for both modes, limitations and coverage published |

Run the relevant newly added test classes while developing, then use the repository's existing Windows command `./mvnw.cmd verify` for full integration/regression verification. Launch with `./mvnw.cmd javafx:run` for the UI walkthrough: choose Ridgecrest, run Validation MMI, switch to Simulated MMI, run again, pause/restart/resize, and verify the chosen event and mode throughout. Scientific benchmark preparation should be explicit and offline after download, not hidden inside ordinary tests. Large external datasets remain outside the bundled unit-test fixtures; include compact reproducible examples in the test resources.

Completion means both exact UI options select their documented predictors; both modes progressively update city maxima, remain deterministic, and accept valid custom inputs without historical answers; software checks pass; and the benchmark distinguishes measured outcomes from pending temporal/generalization validation. It does not require either mode to reproduce the five historical grid values exactly or achieve an invented accuracy score.
