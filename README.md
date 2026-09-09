# CalQuake: California Earthquake Replay (WIP)

> **Offline seismic wavefront replay of the 2019 M 7.1 Ridgecrest Earthquake Sequence**  
> Built with Java 21, JavaFX 21, TauP 3.2.1, and USGS ShakeMap historical peak intensities.

![CalQuake Ridgecrest Test Demo](.\screenshots\ridgecrest-demo.png)

---

## Summary

CalQuake Demo 0 is a high-precision, offline seismic visualization desktop application that replays the first 120 seconds of the **2019-07-06 M 7.1 Ridgecrest, California earthquake** (`ci38457511`).

The application features:
- **Deterministic 0–120s Replay:** Monotonically clocked offline playback at 1&times; speed with Play, Pause, and Restart controls.
- **Physical Wavefront Modeling:** Expanding compressional P-wave (dashed cyan circle) and shear S-wave (solid orange circle) calculated via the published **Hadley–Kanamori (1977)** crustal velocity model and the **TauP 3.2.1** seismic ray engine.
- **Historical Peak Intensity Replay:** Displays verified **USGS ShakeMap peak MMI** values and color badges for five key California reference locations (Ridgecrest, Trona, Bakersfield, Los Angeles, and Fresno), continuously visible throughout playback.
- **Geodetic Accuracy:** Epicenter-centered azimuthal equidistant projection on a reference 6,371 km sphere ensuring circular wavefronts and exact radial distance scaling.
- **Self-Contained & Offline:** 100% bundled local assets with zero external runtime network dependencies.

---

## Prerequisites & Environment

| Component | Requirement | Notes |
| :--- | :--- | :--- |
| **Primary OS** | **Windows 10 / 11 (x86_64)** | Validated baseline; cross-platform profiles provided for macOS and Linux. |
| **Java Development Kit** | **JDK 21 LTS** | Recommended: [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21). |
| **Build Tool** | **Maven 3.9+** | Included Maven Wrapper (`mvnw` / `mvnw.cmd`) ensures reproducible builds. |

---

## Build Commands

### Windows (PowerShell)

```powershell
# 1. Clean build and run tests
.\mvnw.cmd clean verify

# 2. Launch the app
.\mvnw.cmd javafx:run
```

### Linux / macOS (Bash)

```bash
# 1. Grant execute permissions to wrapper (if needed)
chmod +x mvnw

# 2. Clean build and run tests
./mvnw clean verify

# 3. Launch the app
./mvnw javafx:run
```

---

## Attribution & Data Sources

- **USGS Earthquake Hazards Program**: Event catalog metadata and ComCat origin parameters for `ci38457511`.
- **Southern California Earthquake Data Center (SCEDC)**: Phase catalog arrival observations (`38457511.phase`) and seismic station metadata (`scedc_stations.xml`).
- **USGS ShakeMap Atlas**: ShakeMap v1 product `1594160054783` for instrumental ground motion and MMI grids.
- **U.S. Census Bureau**: 2020 Gazetteer Places and 2020 Cartographic Boundary shapefiles for California state outline.
- **Hadley, D. M., & Kanamori, H. (1977)**: *Seismic structure of the Transverse Ranges, California.* Geological Society of America Bulletin, 88(10), 1469-1478.
- **TauP Toolkit**: Crotwell, H. P., Owens, T. J., & Ritsema, J. (1999). *The TauP Toolkit: Flexible seismic travel-time and ray-path utilities.* Seismological Research Letters, 70(2), 154-160.
- **GeographicLib-Java**: Karney, C. F. F. (2013). *Algorithms for geodesics.* Journal of Geodesy, 87(1), 43-55.
