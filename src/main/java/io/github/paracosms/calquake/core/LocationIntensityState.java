package io.github.paracosms.calquake.core;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Immutable, frame-local intensity state driven entirely by a prepared timeline. */
public record LocationIntensityState(
        SimulationSite site,
        OptionalDouble currentMmi,
        OptionalDouble finalMmi,
        OptionalDouble currentPgvCmPerSecond,
        OptionalDouble predictedPeakPgvCmPerSecond,
        Optional<MmiLegend.MmiBin> displayBin,
        Optional<MmiLegend.MmiBin> finalDisplayBin,
        IntensityStatus status,
        DomainStatus domainStatus,
        boolean sWaveArrived,
        double sArrivalTimeSeconds,
        double pArrivalTimeSeconds,
        double distanceKm,
        OptionalDouble rjbKm,
        ModelMetadata modelMetadata,
        IntensityDisplayMode displayMode
) {
    public LocationIntensityState {
        Objects.requireNonNull(site, "site cannot be null");
        currentMmi = currentMmi == null ? OptionalDouble.empty() : currentMmi;
        finalMmi = finalMmi == null ? OptionalDouble.empty() : finalMmi;
        currentPgvCmPerSecond = currentPgvCmPerSecond == null
                ? OptionalDouble.empty() : currentPgvCmPerSecond;
        predictedPeakPgvCmPerSecond = predictedPeakPgvCmPerSecond == null
                ? OptionalDouble.empty() : predictedPeakPgvCmPerSecond;
        displayBin = displayBin == null ? Optional.empty() : displayBin;
        finalDisplayBin = finalDisplayBin == null ? Optional.empty() : finalDisplayBin;
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(domainStatus, "domainStatus cannot be null");
        rjbKm = rjbKm == null ? OptionalDouble.empty() : rjbKm;
        Objects.requireNonNull(modelMetadata, "modelMetadata cannot be null");
        displayMode = displayMode == null ? IntensityDisplayMode.MAXIMUM_REACHED : displayMode;
        if (!Double.isFinite(pArrivalTimeSeconds) || !Double.isFinite(sArrivalTimeSeconds)
                || !Double.isFinite(distanceKm) || distanceKm < 0.0) {
            throw new IllegalArgumentException("Arrival times and distance must be finite");
        }
        if (status.hasDisplayValue() != currentMmi.isPresent()) {
            throw new IllegalArgumentException("Display status and current MMI presence disagree");
        }
    }

    public LocationIntensityState(
            SimulationSite site,
            OptionalDouble currentMmi,
            OptionalDouble finalMmi,
            OptionalDouble currentPgvCmPerSecond,
            OptionalDouble predictedPeakPgvCmPerSecond,
            Optional<MmiLegend.MmiBin> displayBin,
            Optional<MmiLegend.MmiBin> finalDisplayBin,
            IntensityStatus status,
            DomainStatus domainStatus,
            boolean sWaveArrived,
            double sArrivalTimeSeconds,
            double pArrivalTimeSeconds,
            double distanceKm,
            OptionalDouble rjbKm,
            ModelMetadata modelMetadata
    ) {
        this(site, currentMmi, finalMmi, currentPgvCmPerSecond, predictedPeakPgvCmPerSecond,
                displayBin, finalDisplayBin, status, domainStatus, sWaveArrived,
                sArrivalTimeSeconds, pArrivalTimeSeconds, distanceKm, rjbKm, modelMetadata,
                IntensityDisplayMode.MAXIMUM_REACHED);
    }

    public boolean isRevealed() {
        return status.hasDisplayValue() && currentMmi.isPresent();
    }

    public String city() { return site.displayName(); }
    public String geoid() { return site.id(); }
    public GeoPoint internalPoint() { return site.coordinates(); }

    /** Current raw MMI, or the prepared final value for legacy diagnostic callers. */
    public double mmiSourceDecimal() {
        return currentMmi.isPresent() ? currentMmi.getAsDouble()
                : finalMmi.orElseThrow(() -> new IllegalStateException("No MMI is available"));
    }

    public double mmiDisplayRounded() { return MmiLegend.roundToDisplay(mmiSourceDecimal()); }

    public String mmiRoman() {
        return displayBin.or(() -> finalDisplayBin).orElse(MmiLegend.BIN_NA).roman();
    }

    public String shakingDescription() {
        return displayBin.or(() -> finalDisplayBin).orElse(MmiLegend.BIN_NA).shakingDescriptor();
    }

    public String damageDescription() {
        return displayBin.or(() -> finalDisplayBin).orElse(MmiLegend.BIN_NA).damageDescriptor();
    }

    public String statusDescription() {
        if (status == IntensityStatus.NOT_ARRIVED) {
            return "Not arrived";
        }
        if (status == IntensityStatus.SHAKING_ENDED) {
            return "Shaking ended";
        }
        return shakingDescription();
    }

    public String colorHex() {
        return displayBin.or(() -> finalDisplayBin).orElse(MmiLegend.BIN_NA).colorHex();
    }
}
