package io.github.paracosms.calquake.regression;

import io.github.paracosms.calquake.core.HadleyKanamoriTauPModel;
import io.github.paracosms.calquake.testsupport.ArrivalBenchmarkResult;
import io.github.paracosms.calquake.testsupport.ArrivalBenchmarkRunner;
import io.github.paracosms.calquake.testsupport.ObservedArrival;
import io.github.paracosms.calquake.testsupport.ObservedArrivalsFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consolidates the Ridgecrest and Northridge observed arrival benchmarks into a single
 * regression gate against the Hadley-Kanamori TauP model.
 */
class ArrivalBenchmarkGateTest {

    private static HadleyKanamoriTauPModel model;
    private static ArrivalBenchmarkRunner ridgecrestRunner;
    private static ArrivalBenchmarkRunner northridgeRunner;

    @BeforeAll
    static void setUp() {
        model = HadleyKanamoriTauPModel.create();
        ridgecrestRunner = ArrivalBenchmarkRunner.forRidgecrest(model);
        northridgeRunner = ArrivalBenchmarkRunner.forNorthridge(model);
    }

    @Test
    @DisplayName("Ridgecrest P-Phase Arrival Benchmark: Validate >= 95% pass gate on frozen 78 picks")
    void testRidgecrestPPhaseArrivalBenchmark() {
        List<ObservedArrival> pPicks = ObservedArrivalsFixture.loadPPicks();
        ArrivalBenchmarkResult result = assertGate(
                ridgecrestRunner, "P", pPicks, 78, 75, 96.15, 0.445, -0.380, 0.01, 0.01, null);

        assertTrue(result.totalCount() >= 40, "Eligible P stations must be >= 40");
        assertTrue(result.passRatePct() >= 95.0,
                String.format("P pass rate %.2f%% must be >= 95.0%% (passed %d/%d)",
                        result.passRatePct(), result.passCount(), result.totalCount()));
    }

    @Test
    @DisplayName("Ridgecrest S-Phase Arrival Benchmark: Audit 16 picks and document unmet 75% pass rate")
    void testRidgecrestSPhaseArrivalBenchmark() {
        List<ObservedArrival> sPicks = ObservedArrivalsFixture.loadSPicks();
        Set<String> expectedFailures = Set.of("CCC", "B916", "TPO", "TEJ");
        ArrivalBenchmarkResult result = assertGate(
                ridgecrestRunner, "S", sPicks, 16, 12, 75.0, 1.435, null, 0.01, 0.0, expectedFailures);

        // Verify all 4 failing stations have negative residuals (1D predictions arrive earlier than picks)
        for (var pick : result.evaluations()) {
            if (!pick.passed()) {
                assertTrue(pick.residualSec() < 0.0,
                        String.format("Station %s residual must be negative: %.3f", pick.station(), pick.residualSec()));
            }
        }
    }

    @Test
    @DisplayName("Northridge P-Phase Arrival Benchmark: Validate >= 95% pass gate on 132 picks")
    void testNorthridgePPhaseArrivalBenchmark() {
        List<ObservedArrival> pPicks = ObservedArrivalsFixture.loadNorthridgePPicks();
        Set<String> expectedFailures = Set.of("LA00", "LA02", "DGR");
        ArrivalBenchmarkResult result = assertGate(
                northridgeRunner, "P", pPicks, 132, 129, 97.73, 0.4163, 0.0183, 0.005, 0.005, expectedFailures);

        assertTrue(result.totalCount() >= 40, "Eligible P stations must be >= 40");
        assertTrue(result.passRatePct() >= 95.0,
                String.format("P pass rate %.2f%% must be >= 95.0%% (passed %d/%d)",
                        result.passRatePct(), result.passCount(), result.totalCount()));
    }

    @Test
    @DisplayName("Northridge S-Phase Arrival Benchmark: Audit 5 picks and document unmet gate (40.00%)")
    void testNorthridgeSPhaseArrivalBenchmark() {
        List<ObservedArrival> sPicks = ObservedArrivalsFixture.loadNorthridgeSPicks();
        Set<String> expectedFailures = Set.of("LA00", "LA02", "LA04");
        ArrivalBenchmarkResult result = assertGate(
                northridgeRunner, "S", sPicks, 5, 2, 40.0, 2.1156, 1.4970, 0.005, 0.005, expectedFailures);

        // Verify passing stations: PAS, BAR
        Set<String> passingStations = result.evaluations().stream()
                .filter(ArrivalBenchmarkResult.PickEvaluation::passed)
                .map(ArrivalBenchmarkResult.PickEvaluation::station)
                .collect(Collectors.toSet());
        assertEquals(Set.of("PAS", "BAR"), passingStations, "Passing stations must be PAS and BAR");
    }

    private ArrivalBenchmarkResult assertGate(
            ArrivalBenchmarkRunner runner,
            String phase,
            List<ObservedArrival> picks,
            int expectedPickCount,
            int expectedPassCount,
            double expectedPassRate,
            double expectedMae,
            Double expectedBias,
            double maeTolerance,
            double biasTolerance,
            Set<String> expectedFailingStations
    ) {
        assertEquals(expectedPickCount, picks.size(), "Cohort must contain exactly " + expectedPickCount + " " + phase + " picks");
        ArrivalBenchmarkResult result = runner.runBenchmark(phase, picks);
        assertEquals(expectedPassCount, result.passCount(), "Exactly " + expectedPassCount + " of " + expectedPickCount + " " + phase + " picks must pass");
        assertEquals(expectedPassRate, result.passRatePct(), 0.01, phase + " pass rate must be " + expectedPassRate + "%");
        assertEquals(expectedMae, result.maeSec(), maeTolerance, phase + " MAE must remain near audited baseline");
        if (expectedBias != null) {
            assertEquals(expectedBias, result.biasSec(), biasTolerance, phase + " signed bias must remain near audited baseline");
        }
        if (expectedFailingStations != null) {
            Set<String> failingStations = result.evaluations().stream()
                    .filter(p -> !p.passed())
                    .map(ArrivalBenchmarkResult.PickEvaluation::station)
                    .collect(Collectors.toSet());
            assertEquals(expectedFailingStations, failingStations, "Failing stations must match audited set");
        }
        return result;
    }
}
