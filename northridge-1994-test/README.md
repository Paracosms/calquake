# 1994 Northridge Earthquake Event Dataset (`northridge-1994-test`)

This directory contains the complete curated, authenticated historical event bundle, raw source products, and derivative validation fixtures for the **1994-01-17 M 6.7 Northridge, California earthquake** (`ci3144585`), fulfilling all data and validation prerequisites specified in `.docs/replayAlpha.md`.

---

## 1. Event Origin & Identification

| Parameter | Value | Source / Standard |
| :--- | :--- | :--- |
| **Event ID** | `ci3144585` | USGS ComCat / SCEDC Primary Identifier |
| **Event Title** | M 6.7 - Northridge, California, earthquake | USGS Earthquake Hazards Program |
| **Origin UTC** | `1994-01-17T12:30:55.388Z` | Millisecond-precision ComCat / SCEDC origin |
| **Origin Epoch (ms)** | `758809855388` | Milliseconds since Unix epoch |
| **Local Time** | 1994-01-17 04:30:55.388 PST | UTC-8 (Pacific Standard Time) |
| **Latitude** | `34.2130° N` | Authoritative ComCat epicenter |
| **Longitude** | `-118.5370° W` | Authoritative ComCat epicenter |
| **Hypocentral Depth** | `18.20 km` | Reviewed crustal depth |
| **Magnitude** | `6.7 mw` | Moment magnitude |
| **Event Catalog URL** | [USGS ci3144585](https://earthquake.usgs.gov/earthquakes/eventpage/ci3144585) | USGS Event Page |

---

## 2. Accumulated & Downloaded File Inventory

All files required for historical replay, ShakeMap intensity sampling, and arrival benchmark validation are downloaded locally in this directory:

| Filename | Bytes | SHA-256 (Hex) | Description / Source |
| :--- | :--- | :--- | :--- |
| **`ci3144585_event.json`** | 79,567 | `74942fc29c303efccf40d61c38c65c358e4db3b36d36561b346ada86234caf9d` | Complete USGS ComCat GeoJSON detail payload for event `ci3144585` |
| **`ci3144585_scedc.phase`** | 13,583 | `3449b722758ef10b17d282b1056abe8693e73ec4d7e52f694e785fcb34047e9d` | SCEDC STP phase arrival file (180 raw arrival picks) from AWS Open Dataset |
| **`scedc_stations.xml`** | 448,765 | `25c7ede27a8aad0616f39d2059ae25101f186df7d7635be9b51d99496f763758` | FDSN StationXML for networks and stations in the phase file covering origin timestamp |
| **`grid.xml`** | 12,782,231 | `41fe303e1b4199df243c85884167d08278f55c6933b514ae1af689e95c76af43` | USGS Atlas ShakeMap intensity grid XML (`urn:usgs-product:atlas:shakemap:ci3144585:1594159786829`) |
| **`stationlist.json`** | 3,612,972 | `069dbde3116855b6c70cf68c1b41be9c8a26405d8783c442e2c7caeb8d1866ff` | USGS Atlas ShakeMap station list with 1,378 observations (185 seismic, 1,193 macroseismic) |
| **`ci3144585_raster.zip`** | 7,568,035 | `838259d9207fb06c932cf3ab991980b2ee5dab51e6108d7eb87fba1ab76768fb` | USGS Atlas ShakeMap GIS raster package (intensity overlay, contours, metadata) |
| **`california_places_2020.txt`**| 324,004 | `1c16b18074084aeb15ad486f7541d00c0398d20932f8ef4aa969845473a90726` | US Census 2020 Gazetteer internal coordinates and GEOIDs for California places |
| **`rupture.json`** | 916 | `0d5022b05c67b2f74811097209bc85f73694766a4a9a7dd0bb64341fa0bb337c` | USGS Atlas ShakeMap finite-fault rupture geometry GeoJSON |
| **`info.json`** | 4,923 | `695998371dfac0be44c5aedf67ac05b5f8ca9b2bedcf28ae549f2b68a9d64162` | USGS Atlas ShakeMap processing parameters, GMPE/GMICE configuration, and map limits |
| **`uncertainty.xml`** | 12,343,554 | `5f0ecc6b839f75cf5deac676d41fade1da397baacd9118192345cc0b704b2f7c` | USGS Atlas ShakeMap gridded standard deviation / uncertainty XML |
| **`quakeml.xml`** | 427,401 | `67e520322bdfac9f8ec108fcff508796cf18eca9fedf7e589bbd26e7023b517d` | ComCat reviewed QuakeML phase data product from Southern California Seismic Network (`CI`) |
| **`event.json`** | 339 | `5815310e15286a21c3806f696c863f35d78a262f842fa0ad6477209f8e66935f` | Normalized application event metadata record matching `ScenarioLoader` format |
| **`five_reference_locations.json`** | 4,606 | `aa0ab964432b3b44f4e8977359ee7bd82b0b5d22e22c1f0a64f417b818258e9c` | Sampled ShakeMap intensities and ground motions for the 5 reference cities from `grid.xml` |
| **`observed_picks_ci3144585.json`**| 106,115 | `54f3e69a4498612e9a9f0b200ca694aef06aee0fbd1884487617c5f89189f35e` | Filtered and deduplicated arrival picks fixture conforming to Stage 1 protocol |
| **`provenance_manifest.json`**| 4,587 | `701284cc8989967279dd2ecaadee4d7e877b1c53aaf63f60f1042a7bce6e8772` | Cryptographic manifest recording URLs, retrieval times, byte sizes, and checksums |

---

## 3. MMI Timing & Phase-Specific Shaking Investigation (`replayAlpha.md`)

Per `replayAlpha.md` requirements:
> *"Display each city's MMI rating when that city's S-wave arrival is reached, since the S wave represents the point at which the strongest shaking is expected in this visualization. Investigate whether the available data supports a live MMI update when the P wave arrives. If phase-specific P-wave MMI data is not available, keep the MMI reveal tied to the S-wave arrival and do not fabricate an earlier value."*

### Key Findings:
1. **Instrumental Ground Motion & ShakeMap Design**:
   - In both `grid.xml` and `stationlist.json`, the reported ground motion parameters (`PGA`, `PGV`, `PSA03`, `PSA10`, `PSA30`) represent peak amplitudes across the entirety of each recorded waveform.
   - Ground Motion to Intensity Conversion Equations (GMICE, Worden et al., 2012) map these total-record peaks to Instrumental Intensity (MMI).
   - The peak horizontal acceleration and velocity for regional active-crustal earthquakes in Southern California are systematically dominated by S-waves and transverse shear motion, not the initial compressional P-wave.
2. **Absence of Phase-Separated Intensity**:
   - The USGS Earthquake Hazards Program and SCEDC do **not** publish separate "P-wave MMI" fields. Neither `stationlist.json` nor `quakeml.xml` records time-stamped instantaneous MMI evolutions during P-wave passage.
3. **Replay Timing Rule**:
   - **Conclusion**: Because phase-specific P-wave MMI data does not exist in authoritative historical products, **city MMI reveals must remain strictly tied to the S-wave arrival**. Fabricating an arbitrary earlier P-wave intensity value is avoided, strictly upholding scientific integrity.

---

## 4. Five Reference Locations: Northridge ShakeMap Sampling

The 5 historical reference locations from Demo 0 were sampled at the nearest valid grid node in Northridge's `grid.xml` (resolution: 0.0167° ≈ 1.8 km):

| City | GEOID | Internal Lat / Lon | Sampled Node Lat / Lon | Offset | MMI (Source) | Rounded MMI | Roman | Shaking Descriptor | Damage Descriptor | Hex Swatch | PGA (%g) | PGV (cm/s) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Los Angeles** | 0644000 | 34.0194, -118.4108 | 34.0167, -118.4167 | 0.62 km | **7.2** | 7.2 | **VII** | Very strong | Moderate | `#ffc400` | 35.91 | 29.90 |
| **Bakersfield** | 0603526 | 35.3536, -119.0369 | 35.3500, -119.0333 | 0.52 km | **4.5** | 4.5 | **V** | Moderate | Very light | `#81ff8a` | 4.82 | 4.74 |
| **Ridgecrest** | 0660704 | 35.6285, -117.6640 | 35.6333, -117.6667 | 0.58 km | **5.3** | 5.3 | **V** | Moderate | Very light | `#81ff8a` | 4.20 | 5.17 |
| **Trona** | 0680515 | 35.8158, -117.3473 | 35.8167, -117.3500 | 0.26 km | **4.3** | 4.3 | **IV** | Light | None | `#7ffffa` | 2.06 | 3.04 |
| **Fresno** | 0627000 | 36.7827, -119.7934 | 36.7833, -119.8000 | 0.60 km | **3.0** | 3.0 | **II-III** | Weak | None | `#acdbff` | 0.80 | 1.70 |

*Note: For the 1994 Northridge earthquake (epicenter in the San Fernando Valley), Los Angeles experiences severe near-source shaking (MMI VII, 35.91%g PGA), whereas Ridgecrest and Trona experience moderate shaking (MMI 5.3 and 4.3).*

---

## 5. Arrival Pick Selection Protocol & Benchmark Evaluation

Following the exact Stage 1 protocol established in `frozen_selection_rules.md`:
1. **STP Quality Threshold**: `quality >= 0.5`
2. **Epicentral Distance Window**: `0.0 <= d <= 300.0 km`
3. **Active Station Epoch & Channel Matching**: Active epoch in `scedc_stations.xml` at `1994-01-17T12:30:55.388Z`.
4. **Deduplication**: Highest quality pick retained per `(station, phase)`.

### Pick Filtering Audit:
- **Total Raw Picks in SCEDC Phase File**: 180 (165 P, 15 S)
- **Excluded Picks**: 33 picks:
  - **28 picks** excluded due to `Quality < 0.5`: In the 1994 SCSN catalog (mix of analog short-period and early digital instruments), 28 picks (including 10 S picks) were tagged with quality 0.4 or 0.2 (STP weights 3 and 4).
  - **4 picks** excluded due to `Distance > 300 km`: `CI.IRS` (307.3 km), `CI.SGL` (311.3 km), `CI.RUN` (354.9 km), `NC.MMI` (373.1 km).
  - **1 pick** excluded due to lack of active StationXML metadata: `BK.PRI` (Parkfield, Northern California / UC Berkeley network `BK`).
- **Retained Final Cohort**:
  - **P-picks**: **132** picks (exceeds requirement of >= 40 picks)
  - **S-picks**: **5** picks

### Scientific Gate Benchmark Results (Hadley-Kanamori 1D TauP Model):
Evaluated using the formal acceptance criterion |T_model - T_obs| <= max(tol, 0.05 * T_obs):

- **P-Phase**:
  - **Pass Rate**: **97.73%** (129 / 132 passed) >= 95% -> **PASSED GATE**
  - **Mean Absolute Error (MAE)**: `0.4163 s`
  - **Signed Bias**: `+0.0183 s`
  - **3 Failing Stations**: `CI.LA02` (dist 19.9 km, residual 1.34 s), `CI.CO2` (dist 54.1 km, residual 1.25 s), `CI.EDW` (dist 78.4 km, residual -1.21 s).
- **S-Phase**:
  - **Pass Rate**: **40.00%** (2 / 5 passed: `CI.PAS` and `CI.BAR`) -> **UNMET GATE**
  - **Mean Absolute Error (MAE)**: `2.1156 s`
  - **Signed Bias**: `+1.4970 s`
  - *Honest reporting*: Per `replayAlpha.md`, adding Northridge does not imply that both phases pass an accuracy gate; the 1994 S-wave catalog limitations and strong 3D basin velocity anomalies in the Los Angeles basin account for the lower 1D S-wave fit.

---

## 6. External Links & Interactive Online Archives

For datasets, reports, and interactive web tools that cannot be bundled as static offline files, consult the following authoritative external resources:

1. **USGS ComCat Earthquake Summary**:
   - URL: [https://earthquake.usgs.gov/earthquakes/eventpage/ci3144585](https://earthquake.usgs.gov/earthquakes/eventpage/ci3144585)
   - Official USGS origin parameters, technical overview, ShakeMap interactive viewer, and impact statistics.
2. **CESMD (Center for Engineering Strong Motion Data)**:
   - URL: [https://www.strongmotioncenter.org/cgi-bin/CESMD/iqr_dist_DM2.pl?IQRID=Northridge_17Jan1994&SFlag=0&Flag=2](https://www.strongmotioncenter.org/cgi-bin/CESMD/iqr_dist_DM2.pl?IQRID=Northridge_17Jan1994&SFlag=0&Flag=2)
   - Processed accelerograms, spectral responses, and station geotechnical parameters from CDMG/CGS and USGS networks.
3. **SCEDC (Southern California Earthquake Data Center)**:
   - AWS Public Dataset bucket: `s3://scedc-pds/event_phases/1994/1994_017/3144585.phase`
   - SCEDC Web Services: [http://service.scedc.caltech.edu/fdsnws/station/1/](http://service.scedc.caltech.edu/fdsnws/station/1/)
4. **NISEE Northridge Earthquake Archive (UC Berkeley)**:
   - URL: [http://nisee.berkeley.edu/northridge/](http://nisee.berkeley.edu/northridge/)
   - Comprehensive reconnaissance photos, structural damage surveys, and engineering case studies.
5. **EERI (Earthquake Engineering Research Institute) Reconnaissance Report**:
   - URL: [https://www.eeri.org/projects/learning-from-earthquakes-lfe/lfe-reconnaissance-archive/1994-northridge-california-earthquake/](https://www.eeri.org/projects/learning-from-earthquakes-lfe/lfe-reconnaissance-archive/1994-northridge-california-earthquake/)
   - Multi-volume reconnaissance report covering seismology, ground motion, lifeline performance, and structural behavior.
6. **USGS Open-File Report 96-263**:
   - URL: [http://pubs.usgs.gov/of/1996/ofr-96-0263/](http://pubs.usgs.gov/of/1996/ofr-96-0263/)
   - "USGS Response to an Urban Earthquake: Northridge '94", detailing initial monitoring response and lessons learned.
