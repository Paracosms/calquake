package io.github.paracosms.calquake.core;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Historical/evaluation data associated with a site. Predictive model APIs do
 * not accept this type.
 */
public record ReferenceIntensity(
        String siteId,
        OptionalDouble shakeMapPeakMmi,
        Optional<MmiLegend.MmiBin> shakeMapDisplayBin,
        Optional<SamplingMetadata> samplingMetadata,
        Optional<GroundMotionTargets> groundMotionTargets,
        String provenance
) {
    public ReferenceIntensity {
        if (siteId == null || siteId.isBlank()) {
            throw new IllegalArgumentException("Reference site id cannot be null or blank");
        }
        siteId = siteId.trim();
        shakeMapPeakMmi = shakeMapPeakMmi == null ? OptionalDouble.empty() : shakeMapPeakMmi;
        shakeMapDisplayBin = shakeMapDisplayBin == null ? Optional.empty() : shakeMapDisplayBin;
        samplingMetadata = samplingMetadata == null ? Optional.empty() : samplingMetadata;
        groundMotionTargets = groundMotionTargets == null ? Optional.empty() : groundMotionTargets;
        provenance = provenance == null ? "" : provenance.trim();

        if (shakeMapPeakMmi.isPresent()) {
            double mmi = shakeMapPeakMmi.getAsDouble();
            if (!Double.isFinite(mmi)) {
                throw new IllegalArgumentException("ShakeMap peak MMI must be finite: " + mmi);
            }
            if (shakeMapDisplayBin.isEmpty()) {
                shakeMapDisplayBin = Optional.of(MmiLegend.findBin(Math.max(1.0, mmi)));
            }
        } else if (shakeMapDisplayBin.isPresent()) {
            throw new IllegalArgumentException("A ShakeMap display bin requires a peak MMI value");
        }
    }

    public static ReferenceIntensity shakeMapPeak(
            String siteId,
            double mmi,
            SamplingMetadata samplingMetadata,
            String provenance
    ) {
        return new ReferenceIntensity(
                siteId,
                OptionalDouble.of(mmi),
                Optional.of(MmiLegend.findBin(Math.max(1.0, mmi))),
                Optional.ofNullable(samplingMetadata),
                Optional.empty(),
                provenance
        );
    }

    /** Adapts the old combined resource model without exposing it to predictors. */
    public static ReferenceIntensity fromReferenceLocation(ReferenceLocation location) {
        return fromReferenceLocation(location, location.peakIntensity());
    }

    /** Adapts the old combined resource model while honoring a legacy intensity source override. */
    public static ReferenceIntensity fromReferenceLocation(
            ReferenceLocation location,
            ReferenceLocation.PeakIntensity peakIntensity
    ) {
        Objects.requireNonNull(location, "location cannot be null");
        Objects.requireNonNull(peakIntensity, "peakIntensity cannot be null");

        MmiLegend.MmiBin canonical = MmiLegend.findBin(peakIntensity.mmiSourceDecimal());
        MmiLegend.MmiBin preservedDisplay = new MmiLegend.MmiBin(
                peakIntensity.mmiRoman(),
                canonical.minMmi(),
                canonical.maxMmi(),
                peakIntensity.shakingDescription(),
                peakIntensity.damageDescription(),
                peakIntensity.colorHex(),
                canonical.textColorHex()
        );
        ReferenceLocation.SampledGridNode node = location.sampledGridNode();
        SamplingMetadata sampling = new SamplingMetadata(
                node.point(), node.offsetKm(),
                "Nearest valid ShakeMap grid node to site point", "legacy-combined-resource"
        );

        Optional<GroundMotionTargets> targets = Optional.empty();
        if (location.groundMotion() != null) {
            ReferenceLocation.GroundMotion gm = location.groundMotion();
            targets = Optional.of(new GroundMotionTargets(
                    OptionalDouble.of(gm.pgaPctG()),
                    OptionalDouble.of(gm.pgvCmS()),
                    "USGS_SHAKEMAP_GRID_SAMPLE"
            ));
        }

        return new ReferenceIntensity(
                location.geoid(),
                OptionalDouble.of(peakIntensity.mmiSourceDecimal()),
                Optional.of(preservedDisplay),
                Optional.of(sampling),
                targets,
                "USGS ShakeMap historical peak sampled at stored grid node"
        );
    }

    /** Sampling provenance for a historical ShakeMap target. */
    public record SamplingMetadata(
            GeoPoint sampledPoint,
            double offsetKm,
            String method,
            String sourceId
    ) {
        public SamplingMetadata {
            Objects.requireNonNull(sampledPoint, "sampledPoint cannot be null");
            if (!Double.isFinite(offsetKm) || offsetKm < 0.0) {
                throw new IllegalArgumentException("Sampling offset must be finite and non-negative: " + offsetKm);
            }
            method = method == null ? "" : method.trim();
            sourceId = sourceId == null ? "" : sourceId.trim();
        }
    }

    /** Optional evaluation amplitudes, kept separate from predictor site conditions. */
    public record GroundMotionTargets(
            OptionalDouble pgaPercentG,
            OptionalDouble pgvCentimetersPerSecond,
            String provenance
    ) {
        public GroundMotionTargets {
            pgaPercentG = validateNonNegative(pgaPercentG, "PGA");
            pgvCentimetersPerSecond = validateNonNegative(pgvCentimetersPerSecond, "PGV");
            provenance = provenance == null ? "" : provenance.trim();
        }

        private static OptionalDouble validateNonNegative(OptionalDouble value, String name) {
            OptionalDouble normalized = value == null ? OptionalDouble.empty() : value;
            if (normalized.isPresent()
                    && (!Double.isFinite(normalized.getAsDouble()) || normalized.getAsDouble() < 0.0)) {
                throw new IllegalArgumentException(name + " target must be finite and non-negative");
            }
            return normalized;
        }
    }
}
