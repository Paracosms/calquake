package io.github.paracosms.calquake.testsupport;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data container for individual pick arrival evaluations and aggregate scientific benchmark metrics.
 */
public record ArrivalBenchmarkResult(
        String phaseFamily,
        List<PickEvaluation> evaluations,
        int totalCount,
        int passCount,
        double passRatePct,
        double maeSec,
        double biasSec,
        double p95Sec,
        double maxErrorSec,
        double wilsonLowerPct,
        double wilsonUpperPct,
        Map<String, BinStats> distanceBins,
        Map<String, BinStats> azimuthBins
) {
    public ArrivalBenchmarkResult {
        Objects.requireNonNull(phaseFamily, "phaseFamily cannot be null");
        Objects.requireNonNull(evaluations, "evaluations cannot be null");
        Objects.requireNonNull(distanceBins, "distanceBins cannot be null");
        Objects.requireNonNull(azimuthBins, "azimuthBins cannot be null");
        evaluations = List.copyOf(evaluations);
        distanceBins = Map.copyOf(distanceBins);
        azimuthBins = Map.copyOf(azimuthBins);
    }

    /**
     * Individual observed pick arrival evaluation against model prediction.
     */
    public record PickEvaluation(
            String network,
            String station,
            String channel,
            String phase,
            double distanceKm,
            double azimuthDeg,
            double observedTimeSec,
            double modelTimeSec,
            double residualSec,
            double toleranceSec,
            boolean passed,
            String arrivalPhaseName
    ) {
        public PickEvaluation {
            Objects.requireNonNull(network, "network cannot be null");
            Objects.requireNonNull(station, "station cannot be null");
            Objects.requireNonNull(channel, "channel cannot be null");
            Objects.requireNonNull(phase, "phase cannot be null");
            Objects.requireNonNull(arrivalPhaseName, "arrivalPhaseName cannot be null");
        }
    }

    /**
     * Statistical count and pass rate for a distance or azimuth bin.
     */
    public record BinStats(
            String binLabel,
            int totalCount,
            int passCount,
            double passRatePct,
            boolean isEmpty
    ) {
        public BinStats {
            Objects.requireNonNull(binLabel, "binLabel cannot be null");
        }

        public static BinStats empty(String label) {
            return new BinStats(label, 0, 0, 0.0, true);
        }

        public static BinStats of(String label, int total, int pass) {
            if (total == 0) {
                return empty(label);
            }
            return new BinStats(label, total, pass, 100.0 * pass / total, false);
        }
    }

    /**
     * Compute 95% Wilson score confidence interval [lower, upper] for k successes in n trials.
     */
    public static double[] wilsonScoreInterval(int k, int n) {
        if (n == 0) {
            return new double[]{0.0, 0.0};
        }
        double z = 1.959963984540054; // 95% two-sided normal quantile
        double p = (double) k / n;
        double z2 = z * z;
        double denom = 1.0 + z2 / n;
        double center = (p + z2 / (2.0 * n)) / denom;
        double factor = (z / denom) * Math.sqrt((p * (1.0 - p) / n) + (z2 / (4.0 * n * n)));

        double lower = Math.max(0.0, center - factor);
        double upper = Math.min(1.0, center + factor);
        return new double[]{lower * 100.0, upper * 100.0};
    }
}
