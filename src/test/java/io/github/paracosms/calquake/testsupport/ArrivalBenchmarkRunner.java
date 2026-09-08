package io.github.paracosms.calquake.testsupport;

import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.TravelTimeModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Runner that executes the frozen scientific arrival benchmark against observed picks
 * using a supplied TravelTimeModel, computing per-pick residuals and statistical aggregates.
 */
public final class ArrivalBenchmarkRunner {

    private final TravelTimeModel model;
    private final GeoPoint epicenter;
    private final double depthKm;

    public ArrivalBenchmarkRunner(TravelTimeModel model, GeoPoint epicenter, double depthKm) {
        this.model = Objects.requireNonNull(model, "model cannot be null");
        this.epicenter = Objects.requireNonNull(epicenter, "epicenter cannot be null");
        this.depthKm = depthKm;
    }

    public static ArrivalBenchmarkRunner forRidgecrest(TravelTimeModel model) {
        // Frozen CI epicenter: 35.7695 N, -117.5993333 W, depth 8.0 km
        return new ArrivalBenchmarkRunner(model, new GeoPoint(35.7695, -117.5993333), 8.0);
    }

    public ArrivalBenchmarkResult runBenchmark(String phaseFamily, List<ObservedArrival> picks) {
        Objects.requireNonNull(phaseFamily, "phaseFamily cannot be null");
        Objects.requireNonNull(picks, "picks cannot be null");

        boolean isP = phaseFamily.equalsIgnoreCase("P");
        List<ArrivalBenchmarkResult.PickEvaluation> evaluations = new ArrayList<>();
        List<Double> absoluteErrors = new ArrayList<>();
        double sumResidual = 0.0;
        double sumAbsError = 0.0;
        int passCount = 0;

        // Prepare distance bins: [0-50), [50-100), [100-150), [150-200), [200-250), [250-300]
        String[] distBinLabels = {"0-50 km", "50-100 km", "100-150 km", "150-200 km", "200-250 km", "250-300 km"};
        int[] distBinTotals = new int[distBinLabels.length];
        int[] distBinPasses = new int[distBinLabels.length];

        // Prepare azimuth bins: 8 octants
        String[] azBinLabels = {"N (337.5-22.5)", "NE (22.5-67.5)", "E (67.5-112.5)", "SE (112.5-157.5)",
                                "S (157.5-202.5)", "SW (202.5-247.5)", "W (247.5-292.5)", "NW (292.5-337.5)"};
        int[] azBinTotals = new int[azBinLabels.length];
        int[] azBinPasses = new int[azBinLabels.length];

        for (ObservedArrival pick : picks) {
            double distKm = pick.distanceKm();
            double azDeg = calculateInitialAzimuth(epicenter, pick.phaseCoordinates());

            Optional<TravelTimeModel.PhaseArrival> arrivalOpt = model.earliestArrival(phaseFamily, distKm, depthKm);
            if (arrivalOpt.isEmpty()) {
                throw new IllegalStateException("Failed to calculate arrival for " + pick.station() + " at " + distKm + " km");
            }
            TravelTimeModel.PhaseArrival arrival = arrivalOpt.get();
            double tMod = arrival.timeSeconds();
            double tObs = pick.observedTimeSec();
            double residual = tMod - tObs;
            double absErr = Math.abs(residual);

            // Frozen gate tolerance: max(1.0s, 0.05 * Tobs) for P; max(2.0s, 0.05 * Tobs) for S
            double tol = isP ? Math.max(1.0, 0.05 * tObs) : Math.max(2.0, 0.05 * tObs);
            boolean passed = absErr <= tol;

            if (passed) {
                passCount++;
            }
            sumResidual += residual;
            sumAbsError += absErr;
            absoluteErrors.add(absErr);

            // Tally distance bins
            int distIdx = getDistanceBinIndex(distKm);
            if (distIdx >= 0 && distIdx < distBinLabels.length) {
                distBinTotals[distIdx]++;
                if (passed) distBinPasses[distIdx]++;
            }

            // Tally azimuth bins
            int azIdx = getAzimuthBinIndex(azDeg);
            if (azIdx >= 0 && azIdx < azBinLabels.length) {
                azBinTotals[azIdx]++;
                if (passed) azBinPasses[azIdx]++;
            }

            evaluations.add(new ArrivalBenchmarkResult.PickEvaluation(
                    pick.network(),
                    pick.station(),
                    pick.channel(),
                    pick.phase(),
                    distKm,
                    azDeg,
                    tObs,
                    tMod,
                    residual,
                    tol,
                    passed,
                    arrival.phaseName()
            ));
        }

        int n = picks.size();
        double passRate = n > 0 ? (100.0 * passCount / n) : 0.0;
        double mae = n > 0 ? (sumAbsError / n) : 0.0;
        double bias = n > 0 ? (sumResidual / n) : 0.0;

        Collections.sort(absoluteErrors);
        double p95 = 0.0;
        double maxErr = 0.0;
        if (!absoluteErrors.isEmpty()) {
            int p95Idx = (int) Math.ceil(0.95 * absoluteErrors.size()) - 1;
            p95 = absoluteErrors.get(Math.max(0, p95Idx));
            maxErr = absoluteErrors.get(absoluteErrors.size() - 1);
        }

        double[] wilson = ArrivalBenchmarkResult.wilsonScoreInterval(passCount, n);

        Map<String, ArrivalBenchmarkResult.BinStats> distBins = new LinkedHashMap<>();
        for (int i = 0; i < distBinLabels.length; i++) {
            distBins.put(distBinLabels[i], ArrivalBenchmarkResult.BinStats.of(distBinLabels[i], distBinTotals[i], distBinPasses[i]));
        }

        Map<String, ArrivalBenchmarkResult.BinStats> azBins = new LinkedHashMap<>();
        for (int i = 0; i < azBinLabels.length; i++) {
            azBins.put(azBinLabels[i], ArrivalBenchmarkResult.BinStats.of(azBinLabels[i], azBinTotals[i], azBinPasses[i]));
        }

        return new ArrivalBenchmarkResult(
                phaseFamily,
                evaluations,
                n,
                passCount,
                passRate,
                mae,
                bias,
                p95,
                maxErr,
                wilson[0],
                wilson[1],
                distBins,
                azBins
        );
    }

    public static String generateMarkdownReport(ArrivalBenchmarkResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Arrival Benchmark Report: ").append(result.phaseFamily()).append(" Phase\n\n");
        sb.append(String.format("- **Total Picks**: %d\n", result.totalCount()));
        sb.append(String.format("- **Pass Count**: %d / %d (%.2f%%)\n", result.passCount(), result.totalCount(), result.passRatePct()));
        sb.append(String.format("- **95%% Wilson Score Interval**: [%.2f%%, %.2f%%]\n", result.wilsonLowerPct(), result.wilsonUpperPct()));
        sb.append(String.format("- **Mean Absolute Error (MAE)**: %.3f s\n", result.maeSec()));
        sb.append(String.format("- **Mean Signed Bias**: %+.3f s\n", result.biasSec()));
        sb.append(String.format("- **95th Percentile Error (P95)**: %.3f s\n", result.p95Sec()));
        sb.append(String.format("- **Maximum Absolute Error**: %.3f s\n\n", result.maxErrorSec()));

        sb.append("#### Distance Coverage\n\n");
        sb.append("| Distance Bin | Total Picks | Passed | Pass Rate | Status |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- |\n");
        for (var entry : result.distanceBins().values()) {
            if (entry.isEmpty()) {
                sb.append(String.format("| %s | 0 | 0 | N/A | *Empty* |\n", entry.binLabel()));
            } else {
                sb.append(String.format("| %s | %d | %d | %.1f%% | Active |\n",
                        entry.binLabel(), entry.totalCount(), entry.passCount(), entry.passRatePct()));
            }
        }
        sb.append("\n");

        sb.append("#### Azimuth Coverage (Octants)\n\n");
        sb.append("| Azimuth Octant | Total Picks | Passed | Pass Rate | Status |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- |\n");
        for (var entry : result.azimuthBins().values()) {
            if (entry.isEmpty()) {
                sb.append(String.format("| %s | 0 | 0 | N/A | *Empty* |\n", entry.binLabel()));
            } else {
                sb.append(String.format("| %s | %d | %d | %.1f%% | Active |\n",
                        entry.binLabel(), entry.totalCount(), entry.passCount(), entry.passRatePct()));
            }
        }
        sb.append("\n");

        sb.append("#### Per-Pick Evaluation Table\n\n");
        sb.append("| # | Station | Dist (km) | Az (°) | T_obs (s) | T_model (s) | Residual (s) | Tol (s) | Pass/Fail | Branch |\n");
        sb.append("| ---: | :--- | ---: | ---: | ---: | ---: | ---: | ---: | :---: | :---: |\n");
        int idx = 1;
        for (var pick : result.evaluations()) {
            sb.append(String.format("| %d | %s.%-4s | %6.2f | %5.1f | %6.3f | %6.3f | %+6.3f | %5.3f | %s | %s |\n",
                    idx++,
                    pick.network(),
                    pick.station(),
                    pick.distanceKm(),
                    pick.azimuthDeg(),
                    pick.observedTimeSec(),
                    pick.modelTimeSec(),
                    pick.residualSec(),
                    pick.toleranceSec(),
                    pick.passed() ? "PASS" : "**FAIL**",
                    pick.arrivalPhaseName()
            ));
        }

        return sb.toString();
    }

    private static int getDistanceBinIndex(double distKm) {
        if (distKm < 50.0) return 0;
        if (distKm < 100.0) return 1;
        if (distKm < 150.0) return 2;
        if (distKm < 200.0) return 3;
        if (distKm < 250.0) return 4;
        if (distKm <= 300.0) return 5;
        return -1;
    }

    private static int getAzimuthBinIndex(double azDeg) {
        // Normalize to [0, 360)
        double az = (azDeg % 360.0 + 360.0) % 360.0;
        // Octants: 8 bins of 45 deg centered on 0, 45, 90, 135, 180, 225, 270, 315
        // Shift by 22.5 deg: (az + 22.5) / 45
        int idx = (int) Math.floor((az + 22.5) / 45.0) % 8;
        return idx;
    }

    private static double calculateInitialAzimuth(GeoPoint from, GeoPoint to) {
        double phi1 = Math.toRadians(from.latitude());
        double lam1 = Math.toRadians(from.longitude());
        double phi2 = Math.toRadians(to.latitude());
        double lam2 = Math.toRadians(to.longitude());
        double dlam = lam2 - lam1;

        double y = Math.sin(dlam) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dlam);
        double az = Math.toDegrees(Math.atan2(y, x));
        return (az % 360.0 + 360.0) % 360.0;
    }
}
