package io.github.paracosms.calquake.core;

import io.github.paracosms.calquake.data.CaliforniaOutline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Validation and domain warning policy for CalQuake simulation mode.
 */
public final class SimulationValidator {

    public static final double BSSA14_MIN_MAGNITUDE = 3.0;
    public static final double BSSA14_MAX_MAGNITUDE = 8.5;
    public static final double BSSA14_MAX_RJB_KM = 400.0;

    public static final double ENVELOPE_MIN_MAGNITUDE = 2.0; // calibration is (2.0, 7.3]
    public static final double ENVELOPE_MAX_MAGNITUDE = 7.3;
    public static final double ENVELOPE_MAX_DISTANCE_KM = 200.0;

    public static final double HISTORICAL_BENCHMARK_MIN_DEPTH_KM = 8.0;
    public static final double HISTORICAL_BENCHMARK_MAX_DEPTH_KM = 18.2;

    public record ValidationResult(
            List<String> errors,
            List<String> warnings
    ) {
        public boolean isValid() {
            return errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public String warningSummary() {
            if (warnings.isEmpty()) return "";
            return "Outside the model's tested/calibrated range. This toy simulation may be wildly inaccurate: "
                    + String.join("; ", warnings);
        }
    }

    private SimulationValidator() {}

    public static ValidationResult validateRaw(
            String latText, String lonText, String magText, String depthText,
            List<SimulationSite> sites, CaliforniaOutline outline
    ) {
        List<String> errors = new ArrayList<>();
        if (latText == null || latText.isBlank()) errors.add("Latitude cannot be blank");
        if (lonText == null || lonText.isBlank()) errors.add("Longitude cannot be blank");
        if (magText == null || magText.isBlank()) errors.add("Magnitude cannot be blank");
        if (depthText == null || depthText.isBlank()) errors.add("Depth cannot be blank");

        if (!errors.isEmpty()) {
            return new ValidationResult(List.copyOf(errors), List.of());
        }

        double lat, lon, mag, depth;
        try {
            lat = Double.parseDouble(latText.trim());
        } catch (NumberFormatException e) {
            errors.add("Latitude must be a valid number");
            lat = Double.NaN;
        }
        try {
            lon = Double.parseDouble(lonText.trim());
        } catch (NumberFormatException e) {
            errors.add("Longitude must be a valid number");
            lon = Double.NaN;
        }
        try {
            mag = Double.parseDouble(magText.trim());
        } catch (NumberFormatException e) {
            errors.add("Magnitude must be a valid number");
            mag = Double.NaN;
        }
        try {
            depth = Double.parseDouble(depthText.trim());
        } catch (NumberFormatException e) {
            errors.add("Depth must be a valid number");
            depth = Double.NaN;
        }

        if (!errors.isEmpty()) {
            return new ValidationResult(List.copyOf(errors), List.of());
        }

        return validate(lat, lon, mag, depth, sites, outline);
    }

    public static ValidationResult validate(
            double lat, double lon, double mag, double depth,
            List<SimulationSite> sites, CaliforniaOutline outline
    ) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. Hard validation
        if (!Double.isFinite(lat) || lat < -90.0 || lat > 90.0) {
            errors.add("Latitude must be a finite number between -90 and 90");
        }
        if (!Double.isFinite(lon) || lon < -180.0 || lon > 180.0) {
            errors.add("Longitude must be a finite number between -180 and 180");
        }
        if (!Double.isFinite(depth) || depth < 0.0) {
            errors.add("Depth must be a finite non-negative number");
        }
        if (!Double.isFinite(mag)) {
            errors.add("Magnitude must be a finite number");
        }

        if (!errors.isEmpty()) {
            return new ValidationResult(List.copyOf(errors), List.of());
        }

        // 2. Non-blocking warnings
        GeoPoint epicenter = new GeoPoint(lat, lon);

        // Magnitude warnings
        if (mag < BSSA14_MIN_MAGNITUDE || mag > BSSA14_MAX_MAGNITUDE) {
            warnings.add(String.format("Magnitude Mw %.1f is outside BSSA14 nominal domain (3.0–8.5)", mag));
        }
        if (mag <= ENVELOPE_MIN_MAGNITUDE || mag > ENVELOPE_MAX_MAGNITUDE) {
            warnings.add(String.format("Magnitude Mw %.1f is outside envelope calibration range (2.0, 7.3]", mag));
        }

        // Depth warning
        if (depth < HISTORICAL_BENCHMARK_MIN_DEPTH_KM || depth > HISTORICAL_BENCHMARK_MAX_DEPTH_KM) {
            warnings.add(String.format("Depth %.1f km is outside historical arrival benchmark range (8.0–18.2 km)", depth));
        }

        // Geographic boundaries
        if (outline != null) {
            CaliforniaOutline.GeographicBoundingBox bounds = outline.geographicBounds();
            if (lon < bounds.minLongitude() || lon > bounds.maxLongitude()
                    || lat < bounds.minLatitude() || lat > bounds.maxLatitude()) {
                warnings.add("Epicenter is outside California boundaries");
            }
        }

        // Site distance, Rjb, and rupture geometry
        if (sites != null && !sites.isEmpty()) {
            List<String> farEnvelopeSites = new ArrayList<>();
            for (SimulationSite site : sites) {
                double dist = epicenter.distanceKmTo(site.coordinates());
                if (dist >= ENVELOPE_MAX_DISTANCE_KM) {
                    farEnvelopeSites.add(site.displayName());
                }
            }
            if (!farEnvelopeSites.isEmpty()) {
                warnings.add("Site(s) at or beyond envelope 200 km calibration distance: "
                        + String.join(", ", farEnvelopeSites));
            }

            try {
                EventSource dummySource = new EventSource(
                        "temp", "calquake", "temp", java.time.Instant.EPOCH, mag, "mw", epicenter, depth,
                        Optional.empty(),
                        Optional.of(SimulationAssumptionSet.CALQUAKE_CUSTOM_V1.mechanism()),
                        java.util.Map.of());
                RuptureGeometry rupture = RuptureGeometryProvider.generate(dummySource);

                List<String> farRjbSites = new ArrayList<>();
                for (SimulationSite site : sites) {
                    double rjb = rupture.rjbKm(site.coordinates());
                    if (rjb > BSSA14_MAX_RJB_KM) {
                        farRjbSites.add(site.displayName());
                    }
                }
                if (!farRjbSites.isEmpty()) {
                    warnings.add("Site(s) beyond BSSA14 400 km Rjb domain: " + String.join(", ", farRjbSites));
                }

                if (outline != null) {
                    CaliforniaOutline.GeographicBoundingBox bounds = outline.geographicBounds();
                    boolean ruptureOutOfBounds = false;
                    for (List<GeoPoint> part : rupture.surfaceProjectionParts()) {
                        for (GeoPoint pt : part) {
                            if (pt.longitude() < bounds.minLongitude() || pt.longitude() > bounds.maxLongitude()
                                    || pt.latitude() < bounds.minLatitude() || pt.latitude() > bounds.maxLatitude()) {
                                ruptureOutOfBounds = true;
                                break;
                            }
                        }
                        if (ruptureOutOfBounds) break;
                    }
                    if (ruptureOutOfBounds) {
                        warnings.add("Generated rupture extends outside California boundaries");
                    }
                }
            } catch (Exception e) {
                errors.add("Cannot generate finite rupture geometry: " + e.getMessage());
            }
        }

        return new ValidationResult(List.copyOf(errors), List.copyOf(warnings));
    }
}
