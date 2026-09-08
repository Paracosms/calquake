package io.github.paracosms.calquake.core;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.DistanceRay;
import edu.sc.seis.TauP.SeismicPhase;
import edu.sc.seis.TauP.SeismicPhaseFactory;
import edu.sc.seis.TauP.TauModel;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauModelLoader;
import edu.sc.seis.TauP.TauPException;
import edu.sc.seis.TauP.VelocityModel;
import edu.sc.seis.TauP.VelocityModelException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter encapsulating TauP 3.2.1 parameterized with the SCSN Hadley-Kanamori (1977)
 * 1D velocity model on a fixed 6,371 km sphere.
 * <p>
 * Velocity Model Layers:
 * <ul>
 *   <li>0.0 - 5.5 km: Vp = 5.50 km/s, Vs = 3.17919075 km/s (Vp / 1.73)</li>
 *   <li>5.5 - 16.0 km: Vp = 6.30 km/s, Vs = 3.64161850 km/s (Vp / 1.73)</li>
 *   <li>16.0 - 32.0 km: Vp = 6.70 km/s, Vs = 3.87283237 km/s (Vp / 1.73)</li>
 *   <li>32.0 - 6371.0 km (mantle): Vp = 7.80 km/s, Vs = 4.50867052 km/s (Vp / 1.73)</li>
 * </ul>
 * Discontinuities are preserved as step discontinuities via repeated depths.
 */
public final class HadleyKanamoriTauPModel implements TravelTimeModel {

    public static final String MODEL_RESOURCE = "/data/hadley_kanamori.nd";
    public static final String MODEL_NAME = "Hadley-Kanamori-1977";

    private static final List<String> P_PHASE_NAMES = List.of("p", "P", "Pn", "Pg", "Pb");
    private static final List<String> S_PHASE_NAMES = List.of("s", "S", "Sn", "Sg", "Sb");

    private final TauModel tauModel;
    private final Map<Double, DepthPhaseCache> depthPhaseCache = new ConcurrentHashMap<>();

    private record DepthPhaseCache(List<SeismicPhase> pPhases, List<SeismicPhase> sPhases) {}

    public HadleyKanamoriTauPModel() {
        this(loadTauModelFromResource(MODEL_RESOURCE));
    }

    public HadleyKanamoriTauPModel(TauModel tauModel) {
        this.tauModel = Objects.requireNonNull(tauModel, "tauModel cannot be null");
        // Pre-populate cache for standard Ridgecrest hypocentral depth (8.0 km)
        getOrCreateDepthCache(DEFAULT_SOURCE_DEPTH_KM);
    }

    public static HadleyKanamoriTauPModel create() {
        return new HadleyKanamoriTauPModel();
    }

    public TauModel getTauModel() {
        return tauModel;
    }

    @Override
    public double travelTimeSeconds(String phaseFamily, double distanceKm, double depthKm) {
        if (distanceKm == 0.0) {
            return verticalTravelTimeSeconds(phaseFamily, depthKm);
        }
        Optional<PhaseArrival> arrival = earliestArrival(phaseFamily, distanceKm, depthKm);
        if (arrival.isEmpty()) {
            throw new IllegalStateException("No arrival found for " + phaseFamily + " at distance " + distanceKm + " km and depth " + depthKm + " km");
        }
        return arrival.get().timeSeconds();
    }

    @Override
    public double verticalTravelTimeSeconds(String phaseFamily, double depthKm) {
        validateInputs(phaseFamily, 0.0, depthKm);
        boolean isP = isPPhase(phaseFamily);

        // Layer boundaries: 0.0, 5.5, 16.0, 32.0, 6371.0
        // P velocities: 5.5, 6.3, 6.7, 7.8
        // Vs = Vp / 1.73
        double time = 0.0;
        double currentDepth = depthKm;

        // Layer 1: 0 - 5.5 km
        // Layer 2: 5.5 - 16.0 km
        // Layer 3: 16.0 - 32.0 km
        // Layer 4: 32.0+ km
        double[][] layers = {
                {0.0, 5.5, 5.5},
                {5.5, 16.0, 6.3},
                {16.0, 32.0, 6.7},
                {32.0, 6371.0, 7.8}
        };

        for (double[] layer : layers) {
            double top = layer[0];
            double bot = layer[1];
            double vp = layer[2];
            double v = isP ? vp : (vp / 1.73);

            if (currentDepth <= top) {
                break;
            }
            double thicknessInLayer = Math.min(currentDepth, bot) - top;
            time += thicknessInLayer / v;
        }

        return time;
    }

    @Override
    public Optional<PhaseArrival> earliestArrival(String phaseFamily, double distanceKm, double depthKm) {
        validateInputs(phaseFamily, distanceKm, depthKm);

        if (distanceKm == 0.0) {
            double vertTime = verticalTravelTimeSeconds(phaseFamily, depthKm);
            String name = isPPhase(phaseFamily) ? "p" : "s";
            return Optional.of(new PhaseArrival(name, vertTime, 0.0, 0.0, depthKm));
        }

        DepthPhaseCache cache = getOrCreateDepthCache(depthKm);
        List<SeismicPhase> phases = isPPhase(phaseFamily) ? cache.pPhases() : cache.sPhases();

        // Central angle in degrees on the 6,371.0 km sphere
        double degrees = (distanceKm / EARTH_RADIUS_KM) * (180.0 / Math.PI);
        DistanceRay ray = DistanceRay.ofDegrees(degrees);

        List<Arrival> allArrivals = new ArrayList<>();
        for (SeismicPhase phase : phases) {
            List<Arrival> calculated = ray.calculate(phase);
            if (calculated != null && !calculated.isEmpty()) {
                allArrivals.addAll(calculated);
            }
        }

        if (allArrivals.isEmpty()) {
            return Optional.empty();
        }

        Arrival earliest = Arrival.getEarliestArrival(allArrivals);
        return Optional.of(new PhaseArrival(
                earliest.getName(),
                earliest.getTime(),
                earliest.getRayParam(),
                distanceKm,
                depthKm
        ));
    }

    private DepthPhaseCache getOrCreateDepthCache(double depthKm) {
        return depthPhaseCache.computeIfAbsent(depthKm, d -> {
            List<SeismicPhase> pPhases = createPhases(P_PHASE_NAMES, d);
            List<SeismicPhase> sPhases = createPhases(S_PHASE_NAMES, d);
            return new DepthPhaseCache(pPhases, sPhases);
        });
    }

    private List<SeismicPhase> createPhases(List<String> names, double depthKm) {
        List<SeismicPhase> list = new ArrayList<>();
        for (String name : names) {
            try {
                SeismicPhase phase = SeismicPhaseFactory.createPhase(name, tauModel, depthKm, 0.0);
                list.add(phase);
            } catch (TauModelException e) {
                // Ignore phases not supported by model geometry
            }
        }
        return Collections.unmodifiableList(list);
    }

    private static void validateInputs(String phaseFamily, double distanceKm, double depthKm) {
        Objects.requireNonNull(phaseFamily, "phaseFamily cannot be null");
        if (!phaseFamily.equalsIgnoreCase("P") && !phaseFamily.equalsIgnoreCase("S")) {
            throw new IllegalArgumentException("Unknown phase family: '" + phaseFamily + "'. Must be 'P' or 'S'.");
        }
        if (Double.isNaN(distanceKm) || Double.isInfinite(distanceKm) || distanceKm < 0.0) {
            throw new IllegalArgumentException("Distance must be a finite non-negative number: " + distanceKm);
        }
        if (Double.isNaN(depthKm) || Double.isInfinite(depthKm) || depthKm < 0.0) {
            throw new IllegalArgumentException("Depth must be a finite non-negative number: " + depthKm);
        }
    }

    private static boolean isPPhase(String phaseFamily) {
        return phaseFamily.trim().equalsIgnoreCase("P");
    }

    private static TauModel loadTauModelFromResource(String resourcePath) {
        try (InputStream is = HadleyKanamoriTauPModel.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Missing velocity model resource: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                VelocityModel vMod = VelocityModel.readNDFile(reader, MODEL_NAME);
                vMod.setRadiusOfEarth(EARTH_RADIUS_KM);
                return TauModelLoader.createTauModel(vMod);
            }
        } catch (IOException | TauPException e) {
            throw new RuntimeException("Failed to load TauModel from " + resourcePath, e);
        }
    }
}
