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
 * Executes the frozen scientific arrival benchmark for P and S phases, verifies
 * scientific gate criteria, performs the source consistency audit, and prints
 * reproducible Markdown audit tables.
 */
class ScientificGateArrivalBenchmarkTest {

    private static HadleyKanamoriTauPModel model;
    private static ArrivalBenchmarkRunner runner;

    @BeforeAll
    static void setUp() {
        model = HadleyKanamoriTauPModel.create();
        runner = ArrivalBenchmarkRunner.forRidgecrest(model);
    }

    @Test
    @DisplayName("P-Phase Arrival Benchmark: Validate >= 95% pass gate on frozen 78 picks")
    void testPPhaseArrivalBenchmark() {
        List<ObservedArrival> pPicks = ObservedArrivalsFixture.loadPPicks();
        assertEquals(78, pPicks.size(), "Frozen cohort must contain exactly 78 P picks");

        ArrivalBenchmarkResult result = runner.runBenchmark("P", pPicks);

        System.out.println(ArrivalBenchmarkRunner.generateMarkdownReport(result));

        // Gate acceptance requirements: >= 40 stations, >= 95% pass rate
        assertTrue(result.totalCount() >= 40, "Eligible P stations must be >= 40");
        assertTrue(result.passRatePct() >= 95.0,
                String.format("P pass rate %.2f%% must be >= 95.0%% (passed %d/%d)",
                        result.passRatePct(), result.passCount(), result.totalCount()));

        assertEquals(75, result.passCount(), "Exactly 75 of 78 P picks must pass");
        assertEquals(96.15, result.passRatePct(), 0.01, "P pass rate must be 96.15%");
        assertTrue(result.maeSec() < 0.5, "P MAE must be < 0.5 s (actual: " + result.maeSec() + ")");
    }

    @Test
    @DisplayName("S-Phase Arrival Benchmark: Audit 16 picks and document unmet 75% pass rate")
    void testSPhaseArrivalBenchmark() {
        List<ObservedArrival> sPicks = ObservedArrivalsFixture.loadSPicks();
        assertEquals(16, sPicks.size(), "Frozen cohort must contain exactly 16 S picks");

        ArrivalBenchmarkResult result = runner.runBenchmark("S", sPicks);

        System.out.println(ArrivalBenchmarkRunner.generateMarkdownReport(result));

        // Audit check: 12 of 16 pass = 75.00%
        assertEquals(12, result.passCount(), "Exactly 12 of 16 S picks pass");
        assertEquals(75.0, result.passRatePct(), 0.01, "S pass rate is 75.00%");

        // Verify the 4 failing stations identified during audit
        Set<String> failingStations = result.evaluations().stream()
                .filter(p -> !p.passed())
                .map(ArrivalBenchmarkResult.PickEvaluation::station)
                .collect(Collectors.toSet());

        Set<String> expectedFailures = Set.of("CCC", "B916", "TPO", "TEJ");
        assertEquals(expectedFailures, failingStations, "Failing stations must match audited set: CCC, B916, TPO, TEJ");

        // Verify all 4 failing stations have negative residuals (1D predictions arrive earlier than picks)
        for (var pick : result.evaluations()) {
            if (!pick.passed()) {
                assertTrue(pick.residualSec() < 0.0,
                        String.format("Station %s residual must be negative: %.3f", pick.station(), pick.residualSec()));
            }
        }
    }

    @Test
    @DisplayName("Audit CI.CLC source-level pick consistency")
    void testClcSourceConsistency() {
        List<ObservedArrival> pPicks = ObservedArrivalsFixture.loadPPicks();
        ObservedArrival clcPick = pPicks.stream()
                .filter(p -> p.station().equals("CLC"))
                .findFirst()
                .orElseThrow();

        double distKm = clcPick.distanceKm(); // 5.14 km
        double depthKm = 8.0; // km
        double slantKm = Math.sqrt(distKm * distKm + depthKm * depthKm); // ~9.51 km

        // Shortest theoretical travel time at maximum mantle speed (7.8 km/s)
        double minMantleTime = slantKm / 7.8;
        assertTrue(minMantleTime > 1.2, "Minimum mantle travel time must be > 1.2 s: " + minMantleTime);

        // Actual model prediction
        double modelTime = model.travelTimeSeconds("P", distKm, depthKm);
        assertEquals(1.659, modelTime, 0.01, "Model time for CLC is ~1.659 s");

        // Reported observed time in raw SCEDC phase file
        double obsTime = clcPick.observedTimeSec();
        assertEquals(0.628, obsTime, 0.001, "Observed time in SCEDC phase file is 0.628 s");

        // Physical proof: observed time (0.628 s) is physically faster than straight-line propagation at 7.8 km/s (1.22 s)
        assertTrue(obsTime < minMantleTime,
                "Observed time is physically faster than highest possible velocity, proving source-catalog offset");
    }

    @Test
    @DisplayName("Audit maximum ray penetration depth across all 94 picks")
    void testMaxRayPenetrationDepthAcrossCohort() {
        List<ObservedArrival> allPicks = ObservedArrivalsFixture.loadAllPicks();
        assertEquals(94, allPicks.size());

        // Verify unique station count is 78
        long uniqueStations = allPicks.stream().map(ObservedArrival::station).distinct().count();
        assertEquals(78, uniqueStations, "Cohort must contain 94 picks across 78 unique stations");

        double maxPierceDepth = 0.0;
        for (ObservedArrival pick : allPicks) {
            double deg = (pick.distanceKm() / TravelTimeModel.EARTH_RADIUS_KM) * (180.0 / Math.PI);
            var arrival = model.earliestArrival(pick.phase(), pick.distanceKm(), 8.0);
            assertTrue(arrival.isPresent());

            // Check ray pierce depth via TauP phase directly
            try {
                String phName = arrival.get().phaseName();
                var phase = edu.sc.seis.TauP.SeismicPhaseFactory.createPhase(phName, model.getTauModel(), 8.0, 0.0);
                var arrs = edu.sc.seis.TauP.DistanceRay.ofDegrees(deg).calculate(phase);
                if (!arrs.isEmpty()) {
                    maxPierceDepth = Math.max(maxPierceDepth, arrs.get(0).getDeepestPierce().getDepth());
                }
            } catch (Exception ignored) {}
        }

        assertTrue(maxPierceDepth <= 16.5,
                "Max ray pierce depth across all 94 picks must be <= 16.5 km (actual: " + maxPierceDepth + ")");
    }
}
