package io.github.paracosms.calquake.core;

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
 * Validates the 1994 Northridge (ci3144585) earthquake arrival picks against the
 * Hadley-Kanamori 1D TauP travel-time model using the audited scientific gate methodology.
 * <p>
 * Evaluates 132 P-phase picks and 5 S-phase picks, honestly reporting that P-phase passes
 * the 95% gate while S-phase (40%) documents an unmet gate due to regional 3D basin delays.
 */
class NorthridgeArrivalBenchmarkTest {

    private static HadleyKanamoriTauPModel model;
    private static ArrivalBenchmarkRunner runner;

    @BeforeAll
    static void setUp() {
        model = HadleyKanamoriTauPModel.create();
        runner = ArrivalBenchmarkRunner.forNorthridge(model);
    }

    @Test
    @DisplayName("Northridge P-Phase Arrival Benchmark: Validate >= 95% pass gate on 132 picks")
    void testNorthridgePPhaseArrivalBenchmark() {
        List<ObservedArrival> pPicks = ObservedArrivalsFixture.loadNorthridgePPicks();
        assertEquals(132, pPicks.size(), "Northridge cohort must contain exactly 132 P picks");

        ArrivalBenchmarkResult result = runner.runBenchmark("P", pPicks);

        // Gate acceptance: >= 40 stations, >= 95% pass rate
        assertTrue(result.totalCount() >= 40, "Eligible P stations must be >= 40");
        assertTrue(result.passRatePct() >= 95.0,
                String.format("P pass rate %.2f%% must be >= 95.0%% (passed %d/%d)",
                        result.passRatePct(), result.passCount(), result.totalCount()));

        assertEquals(129, result.passCount(), "Exactly 129 of 132 P picks must pass");
        assertEquals(97.73, result.passRatePct(), 0.01, "P pass rate must be 97.73%");
        assertEquals(0.4163, result.maeSec(), 0.005, "P MAE must be near 0.4163 s");
        assertEquals(0.0183, result.biasSec(), 0.005, "P signed bias must be near +0.0183 s");

        // Verify the 3 failing stations identified during audit
        Set<String> failingStations = result.evaluations().stream()
                .filter(p -> !p.passed())
                .map(ArrivalBenchmarkResult.PickEvaluation::station)
                .collect(Collectors.toSet());

        Set<String> expectedFailures = Set.of("LA00", "LA02", "DGR");
        assertEquals(expectedFailures, failingStations, "Failing stations must match audited set: LA00, LA02, DGR");
    }

    @Test
    @DisplayName("Northridge S-Phase Arrival Benchmark: Audit 5 picks and document unmet gate (40.00%)")
    void testNorthridgeSPhaseArrivalBenchmark() {
        List<ObservedArrival> sPicks = ObservedArrivalsFixture.loadNorthridgeSPicks();
        assertEquals(5, sPicks.size(), "Northridge cohort must contain exactly 5 S picks");

        ArrivalBenchmarkResult result = runner.runBenchmark("S", sPicks);

        // Documented unmet gate: 2 of 5 pass = 40.00%
        assertEquals(2, result.passCount(), "Exactly 2 of 5 S picks pass");
        assertEquals(40.0, result.passRatePct(), 0.01, "S pass rate is 40.00%");
        assertEquals(2.1156, result.maeSec(), 0.005, "S MAE must be near 2.1156 s");
        assertEquals(1.4970, result.biasSec(), 0.005, "S signed bias must be near +1.4970 s");

        // Verify passing stations: PAS, BAR
        Set<String> passingStations = result.evaluations().stream()
                .filter(ArrivalBenchmarkResult.PickEvaluation::passed)
                .map(ArrivalBenchmarkResult.PickEvaluation::station)
                .collect(Collectors.toSet());
        assertEquals(Set.of("PAS", "BAR"), passingStations, "Passing stations must be PAS and BAR");

        // Verify failing stations: LA00, LA02, LA04
        Set<String> failingStations = result.evaluations().stream()
                .filter(p -> !p.passed())
                .map(ArrivalBenchmarkResult.PickEvaluation::station)
                .collect(Collectors.toSet());
        assertEquals(Set.of("LA00", "LA02", "LA04"), failingStations, "Failing stations must be LA00, LA02, LA04");
    }
}
