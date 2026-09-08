package io.github.paracosms.calquake.core;

import java.util.Objects;

/**
 * Immutable domain representation of a reference location (e.g. Census place)
 * with sampled USGS ShakeMap historical peak ground motion and intensity.
 *
 * @param city             display city name (e.g. "Ridgecrest")
 * @param officialName     Census official name (e.g. "Ridgecrest city")
 * @param geoid            Census GEOID identifier (e.g. "0660704")
 * @param ansicode         Census ANSI/FIPS code
 * @param lsad             legal/statistical area description code
 * @param internalPoint    Census internal point coordinates
 * @param sampledGridNode  nearest ShakeMap grid node and offset
 * @param peakIntensity    peak Modified Mercalli Intensity values and styling
 * @param groundMotion     detailed instrumental ground motion parameters (optional/can be null)
 */
public record ReferenceLocation(
        String city,
        String officialName,
        String geoid,
        String ansicode,
        String lsad,
        GeoPoint internalPoint,
        SampledGridNode sampledGridNode,
        PeakIntensity peakIntensity,
        GroundMotion groundMotion
) {

    public ReferenceLocation {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("City name cannot be null or blank");
        }
        if (geoid == null || geoid.isBlank()) {
            throw new IllegalArgumentException("GEOID cannot be null or blank");
        }
        Objects.requireNonNull(internalPoint, "internalPoint cannot be null");
        Objects.requireNonNull(sampledGridNode, "sampledGridNode cannot be null");
        Objects.requireNonNull(peakIntensity, "peakIntensity cannot be null");
        if (officialName == null) {
            officialName = city;
        }
        if (ansicode == null) {
            ansicode = "";
        }
        if (lsad == null) {
            lsad = "";
        }
    }

    /**
     * Nearest sampled ShakeMap grid node and offset from internal point.
     *
     * @param point    coordinates of the sampled grid node
     * @param offsetKm distance offset in kilometers from Census internal point
     */
    public record SampledGridNode(GeoPoint point, double offsetKm) {
        public SampledGridNode {
            Objects.requireNonNull(point, "Grid node point cannot be null");
            if (Double.isNaN(offsetKm) || Double.isInfinite(offsetKm) || offsetKm < 0.0) {
                throw new IllegalArgumentException("Offset km must be a finite non-negative number: " + offsetKm);
            }
        }
    }

    /**
     * Modified Mercalli Intensity values, classification, and presentation styling.
     *
     * @param mmiSourceDecimal   raw decimal MMI from ShakeMap grid (1.0 - 10.0)
     * @param mmiDisplayRounded  display rounded MMI
     * @param mmiRoman           Roman numeral representation (e.g. "VII", "II-III")
     * @param shakingDescription shaking description (e.g. "Very strong")
     * @param damageDescription  damage description (e.g. "Moderate")
     * @param colorHex           hex color code (e.g. "#ffc400")
     */
    public record PeakIntensity(
            double mmiSourceDecimal,
            double mmiDisplayRounded,
            String mmiRoman,
            String shakingDescription,
            String damageDescription,
            String colorHex
    ) {
        public PeakIntensity {
            if (Double.isNaN(mmiSourceDecimal) || Double.isInfinite(mmiSourceDecimal) || mmiSourceDecimal < 1.0 || mmiSourceDecimal > 10.0) {
                throw new IllegalArgumentException("MMI source decimal must be between 1.0 and 10.0: " + mmiSourceDecimal);
            }
            if (mmiRoman == null) {
                mmiRoman = "";
            }
            if (shakingDescription == null) {
                shakingDescription = "";
            }
            if (damageDescription == null) {
                damageDescription = "";
            }
            if (colorHex == null || colorHex.isBlank()) {
                colorHex = "#ffffff";
            }
        }
    }

    /**
     * Instrumental ground motion measurements at the sampled node.
     *
     * @param pgaPctG   Peak Ground Acceleration (%g)
     * @param pgvCmS    Peak Ground Velocity (cm/s)
     * @param psa03PctG 0.3 s Peak Spectral Acceleration (%g)
     * @param psa10PctG 1.0 s Peak Spectral Acceleration (%g)
     * @param psa30PctG 3.0 s Peak Spectral Acceleration (%g)
     * @param svelMS    Shear wave velocity Vs30 (m/s)
     */
    public record GroundMotion(
            double pgaPctG,
            double pgvCmS,
            double psa03PctG,
            double psa10PctG,
            double psa30PctG,
            double svelMS
    ) {
        public GroundMotion {
            if (Double.isNaN(pgaPctG) || pgaPctG < 0.0) throw new IllegalArgumentException("pgaPctG must be non-negative");
            if (Double.isNaN(pgvCmS) || pgvCmS < 0.0) throw new IllegalArgumentException("pgvCmS must be non-negative");
            if (Double.isNaN(psa03PctG) || psa03PctG < 0.0) throw new IllegalArgumentException("psa03PctG must be non-negative");
            if (Double.isNaN(psa10PctG) || psa10PctG < 0.0) throw new IllegalArgumentException("psa10PctG must be non-negative");
            if (Double.isNaN(psa30PctG) || psa30PctG < 0.0) throw new IllegalArgumentException("psa30PctG must be non-negative");
            if (Double.isNaN(svelMS) || svelMS < 0.0) throw new IllegalArgumentException("svelMS must be non-negative");
        }
    }
}
