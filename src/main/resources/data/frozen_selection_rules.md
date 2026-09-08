# Stage 1: Frozen Selection Rules & Provenance Specification

This document records the exact, immutable filtering protocols, deduplication rules, and resolution decisions for the **Demo 0** 2019 Ridgecrest mainshock dataset.

## 1. Frozen Event Origin & Metadata

- **Event ID**: `ci38457511`
- **Network**: `CI` (Southern California Seismic Network / Caltech)
- **Origin UTC**: `2019-07-06T03:19:53.040Z` (milliseconds preserved)
- **Epoch Milliseconds**: `1562383193040`
- **Longitude**: `-117.5993333`
- **Latitude**: `35.7695`
- **Hypocentral Depth**: `8.0 km`
- **Magnitude**: `7.1 mw`

## 2. Arrival Pick Selection Protocol

The phase observations are drawn from `ridgecrest-2019-test/ci38457511_scedc.phase` (95 raw picks: 79 P and 16 S).

### Selection Criteria
1. **STP Quality Threshold**:
   $$\text{quality} \ge 0.5$$
   All picks below 0.5 are excluded (0 picks in this dataset fell below 0.5).
2. **Epicentral Distance Window**:
   $$0.0 \le d \le 300.0\text{ km}$$
   Calculated distance from epicenter to station must fall within 0 to 300 km.
3. **Active Station Epoch & Channel Matching**:
   The `(network, station, channel, location)` tuple must have an active epoch covering the origin timestamp (`2019-07-06T03:19:53.040Z`) in `scedc_stations.xml`.
   Location code `'--'` is normalized to blank `''`.
4. **Deduplication**:
   At most one pick per `(station, phase)` pair is retained. Ties are broken by highest quality first, followed by alphabetical channel name. (No duplicates existed in the raw phase file).
5. **Absolute Pick Time Recovery**:
   Pick absolute UTC timestamp is computed from the phase file header origin plus reported travel time:
   $$t_{\text{pick, UTC}} = t_{\text{header origin, UTC}} + \Delta t_{\text{phase}}$$
   Observed elapsed time $T_{\text{obs}}$ is calculated as:
   $$T_{\text{obs}} = t_{\text{pick, UTC}} - t_{\text{frozen origin, UTC}}$$
6. **Station Elevation Policy**:
   Station elevations from the phase file are preserved exactly as reported (e.g. `CI.DAW` at 0.0 m vs 1477.4 m in station XML). No synthetic elevation timing corrections are applied.

### Excluded Pick Audit
- **Pick**: `NN.WLDB.HHZ.--`, Phase `P`, Quality `0.8`, Distance `72.88 km`, $T_{\text{obs}} = 12.339\text{ s}$
- **Resolution**: Excluded because `NN.WLDB` has no active epoch or entry in `scedc_stations.xml`.
- **Resulting Cohort**:
  - **P picks**: **78** retained (exceeds requirement $\ge 40$)
  - **S picks**: **16** retained (exceeds requirement $\ge 15$)
  - **Total retained**: **94 picks**

## 3. Five Reference Locations & ShakeMap Sampling Protocol

Reference locations are defined by US Census 2020 Gazetteer internal points (`INTPTLAT`, `INTPTLONG`) matching designated GEOIDs in `ridgecrest-2019-test/california_places_2020.txt`:

| Location | GEOID | Internal Lat | Internal Lon | Nearest Node Lat | Nearest Node Lon | Offset (km) | Source MMI | Rounded MMI | Worden et al. Bin | Shaking | Color Hex |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Ridgecrest** | 0660704 | 35.628542 | -117.663992 | 35.6333 | -117.6667 | 0.5829 | **7.2** | 7.2 | VII | Very strong | `#ffc400` |
| **Trona** | 0680515 | 35.815821 | -117.347348 | 35.8167 | -117.3500 | 0.2583 | **6.9** | 6.9 | VII | Very strong | `#ffc400` |
| **Bakersfield** | 0603526 | 35.353593 | -119.036921 | 35.3500 | -119.0333 | 0.5172 | **3.9** | 3.9 | IV | Light | `#7ffffa` |
| **Los Angeles** | 0644000 | 34.019394 | -118.410825 | 34.0167 | -118.4167 | 0.6188 | **3.8** | 3.8 | IV | Light | `#7ffffa` |
| **Fresno** | 0627000 | 36.782684 | -119.793359 | 36.7833 | -119.8000 | 0.5954 | **3.1** | 3.1 | II-III | Weak | `#acdbff` |

## 4. California Outline Geometry

- **Source**: US Census Bureau 2020 Cartographic Boundary shapefile `cb_2020_us_state_20m.zip` (STATEFP: `06`, STUSPS: `CA`).
- **Structure**: 6 closed polygon rings (5 Channel Islands + 1 mainland ring; 468 vertices total).
- **Bounding Box**: Longitude `[-124.409591, -114.139055]`, Latitude `[32.534156, 42.009247]`.

## 5. Artifact Directory Locations

- **Provenance Manifest**: `src/main/resources/data/provenance_manifest.json`
- **Observed Picks Fixture**: `src/test/resources/fixtures/observed_picks_ci38457511.json`
- **Reference Locations**: `src/main/resources/data/five_reference_locations.json`
- **MMI Palette**: `src/main/resources/data/mmi_legend.json`
- **California Outline**: `src/main/resources/data/california_outline.json`
