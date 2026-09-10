package io.github.paracosms.calquake.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeSet;

/** Java-only BSSA14 PGV + empirical P/S envelope + Worden 2012 preparation. */
public final class SimulatedMmiModel implements IntensityModel {
    public static final double DEFAULT_TIMELINE_STEP_SECONDS = 0.05;
    public static final double CONVERGENCE_STEP_SECONDS = 0.025;

    private final TravelTimeModel travelTimeModel;
    private final Bssa14GroundMotion groundMotion;
    private final EmpiricalEnvelopeModel envelope;
    private final Worden2012Gmice gmice;
    private final double timelineStepSeconds;

    public SimulatedMmiModel(TravelTimeModel travelTimeModel) {
        this(travelTimeModel, new Bssa14GroundMotion(), new EmpiricalEnvelopeModel(),
                new Worden2012Gmice(), DEFAULT_TIMELINE_STEP_SECONDS);
    }

    public SimulatedMmiModel(
            TravelTimeModel travelTimeModel,
            Bssa14GroundMotion groundMotion,
            EmpiricalEnvelopeModel envelope,
            Worden2012Gmice gmice,
            double timelineStepSeconds
    ) {
        this.travelTimeModel = Objects.requireNonNull(travelTimeModel);
        this.groundMotion = Objects.requireNonNull(groundMotion);
        this.envelope = Objects.requireNonNull(envelope);
        this.gmice = Objects.requireNonNull(gmice);
        if (!Double.isFinite(timelineStepSeconds) || timelineStepSeconds <= 0.0) {
            throw new IllegalArgumentException("Timeline step must be positive and finite");
        }
        this.timelineStepSeconds = timelineStepSeconds;
    }

    @Override
    public MmiMode mode() { return MmiMode.SIMULATED; }

    @Override
    public PreparedIntensityResult prepare(ScenarioInputs inputs) {
        Objects.requireNonNull(inputs, "inputs cannot be null");
        EventSource source = inputs.event();
        RuptureGeometry rupture = source.ruptureGeometry().orElseThrow(() ->
                new IllegalArgumentException("Simulated mode requires rupture geometry for " + source.id()));
        Mechanism mechanism = source.mechanism().orElseThrow(() ->
                new IllegalArgumentException("Simulated mode requires a mechanism/rake for " + source.id()));
        ModelMetadata metadata = new ModelMetadata(
                "simulated-bssa14-envelope-worden2012", "1",
                Map.of(
                        "groundMotionModel", Bssa14GroundMotion.MODEL_ID,
                        "groundMotionVersion", Bssa14GroundMotion.VERSION,
                        "groundMotionCoefficientHash", Bssa14GroundMotion.COEFFICIENT_SHA256,
                        "envelopeModel", EmpiricalEnvelopeModel.MODEL_ID,
                        "envelopeVersion", EmpiricalEnvelopeModel.VERSION,
                        "envelopeCoefficientHash", EmpiricalEnvelopeModel.COEFFICIENT_SHA256,
                        "gmice", Worden2012Gmice.MODEL_ID,
                        "gmiceCoefficientHash", Worden2012Gmice.COEFFICIENT_SHA256,
                        "timelineStepSeconds", Double.toString(timelineStepSeconds)));

        List<IntensityTimeline> timelines = new ArrayList<>();
        double latestPeak = 0.0;
        int extrapolated = 0;
        for (SimulationSite site : inputs.sites()) {
            SiteCondition condition = site.siteCondition().orElseThrow(() ->
                    new IllegalArgumentException("Simulated mode requires Vs30 for site '" + site.id() + "'"));
            double distance = source.epicenter().distanceKmTo(site.coordinates());
            double p = travelTimeModel.travelTimeSeconds("P", distance, source.depthKm());
            double s = travelTimeModel.travelTimeSeconds("S", distance, source.depthKm());
            if (!Double.isFinite(p) || !Double.isFinite(s) || p < 0.0 || s < p) {
                throw new IllegalArgumentException("Invalid modeled arrivals for site '" + site.id() + "'");
            }
            double rjb = rupture.rjbKm(site.coordinates());
            Bssa14GroundMotion.Prediction prediction = groundMotion.predictPgv(
                    source.magnitude(), mechanism.rakeDegrees(), rjb, condition.vs30MetersPerSecond());
            EmpiricalEnvelopeModel.Parameters parameters = envelope.parameters(
                    source.magnitude(), distance, condition.vs30MetersPerSecond());
            DomainStatus domain = prediction.domainStatus() == DomainStatus.OUT_OF_DOMAIN
                    || parameters.domainStatus() == DomainStatus.OUT_OF_DOMAIN
                    ? DomainStatus.OUT_OF_DOMAIN : DomainStatus.IN_DOMAIN;
            if (domain == DomainStatus.OUT_OF_DOMAIN) extrapolated++;
            PreparedSite prepared = prepareSite(site, p, s, distance, rjb, prediction, parameters, domain, metadata);
            timelines.add(prepared.timeline());
            latestPeak = Math.max(latestPeak, prepared.peakTimeSeconds());
        }
        return new PreparedIntensityResult(timelines, latestPeak, metadata,
                Map.of("outOfDomainSiteCount", Integer.toString(extrapolated),
                        "referenceIsolation", "ScenarioReferences are not accepted by this model"));
    }

    private PreparedSite prepareSite(
            SimulationSite site,
            double p,
            double s,
            double surfaceDistance,
            double rjb,
            Bssa14GroundMotion.Prediction prediction,
            EmpiricalEnvelopeModel.Parameters parameters,
            DomainStatus domain,
            ModelMetadata metadata
    ) {
        double supportEnd = envelope.supportEnd(p, s, parameters);
        TreeSet<Double> evaluationTimes = breakpoints(p, s, parameters);
        for (double t = 0.0; t <= supportEnd; t += CONVERGENCE_STEP_SECONDS) evaluationTimes.add(t);

        double maximumRaw = 0.0;
        double peakTime = p;
        for (double t : evaluationTimes) {
            double raw = envelope.rawEnvelope(t, p, s, parameters);
            if (raw > maximumRaw) {
                maximumRaw = raw;
                peakTime = t;
            }
        }
        if (!(maximumRaw > 0.0) || !Double.isFinite(maximumRaw)) {
            throw new IllegalStateException("Degenerate empirical envelope for site '" + site.id() + "'");
        }

        TreeSet<Double> sampleTimes = breakpoints(p, s, parameters);
        for (double t = 0.0; t <= peakTime + timelineStepSeconds; t += timelineStepSeconds) sampleTimes.add(t);
        sampleTimes.add(peakTime);
        List<IntensityTimeline.Sample> samples = new ArrayList<>(sampleTimes.size());
        double runningPgv = 0.0;
        double runningMmi = Double.NEGATIVE_INFINITY;
        for (double t : sampleTimes) {
            double raw = envelope.rawEnvelope(t, p, s, parameters);
            double velocity = prediction.pgvCmPerSecond() * raw / maximumRaw;
            runningPgv = Math.max(runningPgv, velocity);
            OptionalDouble mmi = gmice.tryFromPgvCmPerSecond(runningPgv);
            if (mmi.isPresent()) runningMmi = Math.max(runningMmi, mmi.getAsDouble());
            if (runningPgv > 0.0 && Double.isFinite(runningMmi)) {
                samples.add(IntensityTimeline.Sample.available(t, runningMmi, OptionalDouble.of(runningPgv)));
            } else {
                samples.add(IntensityTimeline.Sample.notArrived(t));
            }
        }
        double finalMmi = gmice.fromPgvCmPerSecond(prediction.pgvCmPerSecond());
        IntensityTimeline timeline = new IntensityTimeline(site, p, s, surfaceDistance,
                OptionalDouble.of(rjb), OptionalDouble.of(finalMmi),
                OptionalDouble.of(prediction.pgvCmPerSecond()),
                Optional.of(MmiLegend.findBin(Math.max(1.0, finalMmi))), domain, metadata, samples);
        return new PreparedSite(timeline, peakTime);
    }

    private static TreeSet<Double> breakpoints(
            double p, double s, EmpiricalEnvelopeModel.Parameters parameters) {
        TreeSet<Double> times = new TreeSet<>();
        times.add(0.0);
        addBodyBreakpoints(times, p, parameters.p());
        addBodyBreakpoints(times, s, parameters.s());
        return times;
    }

    private static void addBodyBreakpoints(TreeSet<Double> times, double arrival,
                                            EmpiricalEnvelopeModel.Body body) {
        times.add(arrival);
        times.add(arrival + body.riseSeconds());
        times.add(arrival + body.riseSeconds() + body.plateauSeconds());
    }

    private record PreparedSite(IntensityTimeline timeline, double peakTimeSeconds) {}
}
