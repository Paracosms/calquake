package io.github.paracosms.calquake.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Immutable deterministic per-site prefix-maximum timeline. */
public final class IntensityTimeline {
    private final SimulationSite site;
    private final double pArrivalSeconds;
    private final double sArrivalSeconds;
    private final double surfaceDistanceKm;
    private final OptionalDouble rjbKm;
    private final OptionalDouble finalMmi;
    private final OptionalDouble predictedPeakPgv;
    private final Optional<MmiLegend.MmiBin> finalDisplayBin;
    private final DomainStatus domainStatus;
    private final ModelMetadata modelMetadata;
    private final List<Sample> samples;
    private final MmiMode mmiMode;

    public IntensityTimeline(
            SimulationSite site,
            double pArrivalSeconds,
            double sArrivalSeconds,
            double surfaceDistanceKm,
            OptionalDouble rjbKm,
            OptionalDouble finalMmi,
            OptionalDouble predictedPeakPgv,
            Optional<MmiLegend.MmiBin> finalDisplayBin,
            DomainStatus domainStatus,
            ModelMetadata modelMetadata,
            List<Sample> samples,
            MmiMode mmiMode
    ) {
        this.site = Objects.requireNonNull(site, "site cannot be null");
        if (!Double.isFinite(pArrivalSeconds) || !Double.isFinite(sArrivalSeconds)
                || !Double.isFinite(surfaceDistanceKm) || surfaceDistanceKm < 0.0) {
            throw new IllegalArgumentException("Timeline arrival and distance values must be finite");
        }
        this.pArrivalSeconds = pArrivalSeconds;
        this.sArrivalSeconds = sArrivalSeconds;
        this.surfaceDistanceKm = surfaceDistanceKm;
        this.rjbKm = rjbKm == null ? OptionalDouble.empty() : rjbKm;
        this.finalMmi = finalMmi == null ? OptionalDouble.empty() : finalMmi;
        this.predictedPeakPgv = predictedPeakPgv == null ? OptionalDouble.empty() : predictedPeakPgv;
        this.finalDisplayBin = finalDisplayBin == null ? Optional.empty() : finalDisplayBin;
        this.domainStatus = Objects.requireNonNull(domainStatus, "domainStatus cannot be null");
        this.modelMetadata = Objects.requireNonNull(modelMetadata, "modelMetadata cannot be null");
        this.mmiMode = mmiMode == null ? MmiMode.RECORDED : mmiMode;
        Objects.requireNonNull(samples, "samples cannot be null");
        if (samples.isEmpty() || samples.getFirst().elapsedSeconds() != 0.0) {
            throw new IllegalArgumentException("Timeline must start at exactly T+0");
        }
        this.samples = List.copyOf(samples);
        validateSamples(this.samples);
    }

    public IntensityTimeline(
            SimulationSite site,
            double pArrivalSeconds,
            double sArrivalSeconds,
            double surfaceDistanceKm,
            OptionalDouble rjbKm,
            OptionalDouble finalMmi,
            OptionalDouble predictedPeakPgv,
            Optional<MmiLegend.MmiBin> finalDisplayBin,
            DomainStatus domainStatus,
            ModelMetadata modelMetadata,
            List<Sample> samples
    ) {
        this(site, pArrivalSeconds, sArrivalSeconds, surfaceDistanceKm, rjbKm, finalMmi,
                predictedPeakPgv, finalDisplayBin, domainStatus, modelMetadata, samples, MmiMode.RECORDED);
    }

    public static IntensityTimeline recorded(
            SimulationSite site,
            double pArrival,
            double sArrival,
            double distanceKm,
            double mmi,
            MmiLegend.MmiBin displayBin,
            ModelMetadata metadata
    ) {
        return new IntensityTimeline(site, pArrival, sArrival, distanceKm,
                OptionalDouble.empty(), OptionalDouble.of(mmi), OptionalDouble.empty(),
                Optional.of(displayBin), DomainStatus.IN_DOMAIN, metadata,
                List.of(Sample.notArrived(0.0), Sample.available(sArrival, mmi, OptionalDouble.empty())));
    }

    public LocationIntensityState stateAt(double elapsedSeconds) {
        return stateAt(elapsedSeconds, IntensityDisplayMode.MAXIMUM_REACHED);
    }

    public LocationIntensityState stateAt(double elapsedSeconds, IntensityDisplayMode displayMode) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0.0) {
            throw new IllegalArgumentException("Elapsed seconds must be finite and non-negative");
        }
        Objects.requireNonNull(displayMode, "displayMode cannot be null");
        int low = 0;
        int high = samples.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (samples.get(mid).elapsedSeconds() <= elapsedSeconds) low = mid;
            else high = mid - 1;
        }
        Sample sample = samples.get(low);

        boolean isCurrent = (displayMode == IntensityDisplayMode.CURRENT_SHAKING);
        IntensityStatus status = isCurrent ? sample.currentStatus() : sample.status();
        OptionalDouble mmi = isCurrent ? sample.currentMmi() : sample.mmi();
        OptionalDouble pgv = isCurrent ? sample.currentPgvCmPerSecond() : sample.pgvCmPerSecond();

        Optional<MmiLegend.MmiBin> bin = (status.hasDisplayValue() && mmi.isPresent())
                ? Optional.of(MmiLegend.findBin(Math.max(1.0, mmi.getAsDouble()), mmiMode))
                : Optional.empty();

        return new LocationIntensityState(site, mmi, finalMmi, pgv,
                predictedPeakPgv, bin, finalDisplayBin, status, domainStatus,
                elapsedSeconds >= sArrivalSeconds, sArrivalSeconds, pArrivalSeconds,
                surfaceDistanceKm, rjbKm, modelMetadata, displayMode);
    }

    private static void validateSamples(List<Sample> values) {
        double previousTime = -1.0;
        double previousMmi = Double.NEGATIVE_INFINITY;
        double previousPgv = Double.NEGATIVE_INFINITY;
        for (Sample sample : values) {
            Objects.requireNonNull(sample, "Timeline samples cannot contain null");
            if (sample.elapsedSeconds() <= previousTime) {
                throw new IllegalArgumentException("Timeline sample times must strictly increase");
            }
            if (sample.mmi().isPresent() && sample.mmi().getAsDouble() + 1.0e-12 < previousMmi) {
                throw new IllegalArgumentException("Timeline MMI must be a prefix maximum");
            }
            if (sample.pgvCmPerSecond().isPresent()
                    && sample.pgvCmPerSecond().getAsDouble() + 1.0e-12 < previousPgv) {
                throw new IllegalArgumentException("Timeline PGV must be a prefix maximum");
            }
            previousTime = sample.elapsedSeconds();
            if (sample.mmi().isPresent()) previousMmi = sample.mmi().getAsDouble();
            if (sample.pgvCmPerSecond().isPresent()) previousPgv = sample.pgvCmPerSecond().getAsDouble();
        }
    }

    public SimulationSite site() { return site; }
    public double pArrivalSeconds() { return pArrivalSeconds; }
    public double sArrivalSeconds() { return sArrivalSeconds; }
    public double surfaceDistanceKm() { return surfaceDistanceKm; }
    public OptionalDouble rjbKm() { return rjbKm; }
    public OptionalDouble finalMmi() { return finalMmi; }
    public OptionalDouble predictedPeakPgv() { return predictedPeakPgv; }
    public DomainStatus domainStatus() { return domainStatus; }
    public ModelMetadata modelMetadata() { return modelMetadata; }
    public List<Sample> samples() { return samples; }
    public MmiMode mmiMode() { return mmiMode; }

    public record Sample(
            double elapsedSeconds,
            IntensityStatus status,
            OptionalDouble mmi,
            OptionalDouble pgvCmPerSecond,
            IntensityStatus currentStatus,
            OptionalDouble currentMmi,
            OptionalDouble currentPgvCmPerSecond
    ) {
        public Sample {
            if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0.0) {
                throw new IllegalArgumentException("Sample time must be finite and non-negative");
            }
            Objects.requireNonNull(status, "status cannot be null");
            mmi = mmi == null ? OptionalDouble.empty() : mmi;
            pgvCmPerSecond = pgvCmPerSecond == null ? OptionalDouble.empty() : pgvCmPerSecond;
            if (status.hasDisplayValue() != mmi.isPresent()) {
                throw new IllegalArgumentException("Sample status and MMI presence disagree");
            }
            Objects.requireNonNull(currentStatus, "currentStatus cannot be null");
            currentMmi = currentMmi == null ? OptionalDouble.empty() : currentMmi;
            currentPgvCmPerSecond = currentPgvCmPerSecond == null ? OptionalDouble.empty() : currentPgvCmPerSecond;
            if (currentStatus.hasDisplayValue() != currentMmi.isPresent()) {
                throw new IllegalArgumentException("Sample currentStatus and currentMmi presence disagree");
            }
        }

        public Sample(double elapsedSeconds, IntensityStatus status,
                      OptionalDouble mmi, OptionalDouble pgvCmPerSecond) {
            this(elapsedSeconds, status, mmi, pgvCmPerSecond, status, mmi, pgvCmPerSecond);
        }

        public static Sample notArrived(double time) {
            return new Sample(time, IntensityStatus.NOT_ARRIVED,
                    OptionalDouble.empty(), OptionalDouble.empty(),
                    IntensityStatus.NOT_ARRIVED, OptionalDouble.empty(), OptionalDouble.empty());
        }

        public static Sample available(double time, double mmi, OptionalDouble pgv) {
            return new Sample(time, IntensityStatus.AVAILABLE, OptionalDouble.of(mmi), pgv,
                    IntensityStatus.AVAILABLE, OptionalDouble.of(mmi), pgv);
        }

        public static Sample dual(double time,
                                  IntensityStatus maxStatus, OptionalDouble maxMmi, OptionalDouble maxPgv,
                                  IntensityStatus currentStatus, OptionalDouble currentMmi, OptionalDouble currentPgv) {
            return new Sample(time, maxStatus, maxMmi, maxPgv, currentStatus, currentMmi, currentPgv);
        }
    }
}
