# Instantaneous MMI implementation

CalQuake has two permanent modes. **Recorded** reveals a stored USGS ShakeMap peak-MMI city sample at the modeled S arrival. **Simulated** is a forward predictor: BSSA14 median PGV is multiplied by one normalized relative P/S time shape, converted with Worden 2012, and accumulated as the maximum MMI reached so far. Simulated prediction methods cannot accept `ScenarioReferences`; reference MMI/PGA/PGV is kept in a separate type and used only by Recorded preparation and offline evaluation.

## Live pipeline

All live preparation and playback is Java-only and offline:

```text
event + rupture + mechanism + site Vs30
  -> BSSA14 base/no-basin median PGV (RotD50, cm/s)
  -> combined relative P/S velocity envelope, normalized once
  -> running peak PGV
  -> Worden 2012 PGV-to-MMI
  -> running maximum MMI timeline
```

Preparation produces an immutable `PreparedReplay`. Frames are pure timeline lookups; they do not read files, run a model, use the network, or launch Python. Recorded duration is the latest valid city S arrival plus 10 seconds. Simulated duration is the greater of that value and the latest predicted envelope peak. Event/mode changes are prepared asynchronously and guarded by a monotonically increasing generation number before atomic installation.

## Pinned scientific choices

### BSSA14

- Model ID: `BSSA14-PGV-RotD50-base-no-basin`.
- Reference implementation revision: OpenQuake 3.21 `boore_2014.py`.
- Configuration: deterministic median, style-of-faulting enabled, no basin term.
- Predictor inputs: moment magnitude, rake, Rjb in km, and Vs30 in m/s. Rjb is calculated against the finite rupture's horizontal surface projection and is not epicentral or hypocentral distance.
- Output: natural-log PGV and PGV in cm/s. Reference-rock PGA is evaluated internally for the nonlinear site term. Natural-log total sigma, tau, and phi are retained as metadata.
- Canonical PGV/PGA coefficient serialization SHA-256: `31cdfae748b08d1093dc2e8a459cffcae9f38f273be6257e31d193b3abaad925`.
- Metadata domain marker: 3.0 <= M <= 8.5, Rjb <= 400 km, and 150 <= Vs30 <= 1500 m/s. Predictions outside this marker are still calculated and displayed, but remain tagged `OUT_OF_DOMAIN`.

The complete canonical coefficient serialization is frozen in `src/main/resources/data/scientific_inputs.json`; parity fixtures cover magnitude, style-of-faulting, distance, and site branches to `1e-6` in ln(PGV).

### Worden 2012 PGV conversion

With `x = log10(PGV)` and PGV in cm/s:

```text
MMI = 3.78 + 1.47 x,  x <= 0.53
MMI = 2.89 + 3.16 x,  x >  0.53
```

Magnitude/distance corrections are disabled for the pinned `WGRW12-equation-1-PGV` configuration. Non-positive or non-finite PGV has no MMI and is never passed to `log10`. Predictions remain unrounded; only badge lookup clamps to the existing legend's lower supported value. Canonical equation SHA-256: `b31b0c7e22b66931ab38aa1a9b21742aaff4d37a2199d0337267530d49a3cb95`.

### Deterministic relative P/S envelope

The `Cua-Heaton-derived-relative-PS-envelope/calquake-1` model adopts the Cua–Heaton phase-separated P/S envelope structure as its starting point. It is a transparent CalQuake hybrid, not a claim that BSSA14 itself predicts time history and not a verbatim reproduction of a published Cua–Heaton coefficient table. BSSA14 supplies the sole completed amplitude; these frozen CalQuake v1 equations supply relative timing only.

Let M be magnitude, R epicentral great-circle distance in km, V Vs30 in m/s, and `q = 1.12` for V < 464 m/s or `1.0` otherwise. For P:

```text
rise    = max(0.35, 0.12 M + 0.002 R) q
plateau = max(0.40, 0.25 M + 0.003 R) q
fast    = max(1.00, 0.45 M + 0.010 R) q
slow    = max(3.00, 1.35 M + 0.030 R) q
relative amplitude = 0.18
```

For S:

```text
rise    = max(0.75, 0.25 M + 0.004 R) q
plateau = max(1.00, 0.65 M + 0.008 R) q
fast    = max(2.00, 1.10 M + 0.015 R) q
slow    = max(6.00, 3.30 M + 0.045 R) q
relative amplitude = 1.00
```

Each body is zero before its TauP/Hadley–Kanamori arrival, rises as `A sin²(pi t / (2 rise))`, stays at A for the plateau, then follows `A [0.75 exp(-t/fast) + 0.25 exp(-t/slow)]`. The nonnegative P and S bodies are added and the completed combination is normalized exactly once to BSSA14 PGV. The timeline step is 0.05 s; normalization evaluates at 0.025 s plus all onsets, end-of-rise points, and plateau ends. Step-halving tests require final-MMI change below 0.01 and matched threshold-time change below 0.1 s. Equation SHA-256: `0c4d70e60c2092f294a185121a568ed9a9ce8735f2631fb53b709d9b8075f52c`.

The inherited Cua–Heaton calibration marker is 2 < M <= 7.3 and epicentral distance < 200 km. The current duration equations are deliberately simple and are the largest scientific limitation of the time-development display; out-of-domain cases are retained in benchmark metadata.

## Frozen historical inputs

`scientific_inputs.json` is the runtime manifest. Ridgecrest uses the archived USGS ShakeMap Atlas multi-segment surface trace, strike-slip rake 0 degrees, and rupture SHA-256 `19d6e93ff3cf2f848dfa314398341fa92e876564098894f2551656fb7ee2675a`. Northridge uses the archived finite-fault surface polygon, reverse rake 90 degrees, and rupture SHA-256 `0d5022b05c67b2f74811097209bc85f73694766a4a9a7dd0bb64341fa0bb337c`.

Bundled city values labeled SVEL are used only after verifying the ShakeMap Atlas grid definition: time-averaged shear-wave velocity in the upper 30 m. They are therefore stored as Vs30 in m/s with `MAPPED_PROXY` provenance. Bundled historical events fail Simulated preparation if rupture, mechanism, or site condition is absent. The future custom-scenario domain API instead generates a rupture with the pinned Wells–Coppersmith (1994) all-slip magnitude scaling and tags a 760 m/s Vs30 fallback as `DEFAULT`.

## Benchmark and limitations

Run:

```powershell
.\mvnw.cmd -Dtest=MmiBenchmarkRunnerTest test
```

Outputs are written to `target/mmi-benchmark/`. Exact-station PGV targets and station-derived MMI are a distinct cohort from city ShakeMap samples. Every row retains an input signature, coefficient hashes, model/provenance IDs, Rjb, Vs30, prediction, target, residual, status, and domain marker. MAE, RMSE, signed bias, and fraction within one MMI unit are reported per event/reference class.

The compact fixture has no processed waveform running-peak curves. Threshold target times at MMI 3.5, 4.5, and 5.5, and therefore signed/absolute errors, are emitted as null with an explicit censored outcome; predicted crossings remain reported. Station summary components are also not asserted to be BSSA14 RotD50, so PGV residuals must be interpreted with that component mismatch. ShakeMap comparisons are useful but not wholly independent because their archived rupture products are also runtime inputs.

Primary references: [OpenQuake BSSA14 source](https://github.com/gem/oq-engine/blob/v3.21.0/openquake/hazardlib/gsim/boore_2014.py), [Worden et al. 2012](https://doi.org/10.1785/0120110154), [Cua and Heaton 2009 report](https://resolver.caltech.edu/CaltechEERL:EERL-2009-05), and [Wells and Coppersmith 1994](https://doi.org/10.1785/BSSA0840040974).
