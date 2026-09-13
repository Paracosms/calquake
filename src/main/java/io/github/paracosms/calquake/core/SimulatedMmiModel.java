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
        for (double t = 0.0; t <= supportEnd + timelineStepSeconds; t += timelineStepSeconds) {
            sampleTimes.add(t);
        }
        sampleTimes.add(peakTime);
        sampleTimes.add(supportEnd);

        List<IntensityTimeline.Sample> samples = new ArrayList<>(sampleTimes.size());
        double runningPgv = 0.0;
        double runningMmi = Double.NEGATIVE_INFINITY;

        IntensityStatus revealedStatus = (domain == DomainStatus.OUT_OF_DOMAIN)
                ? IntensityStatus.OUT_OF_DOMAIN
                : IntensityStatus.AVAILABLE;

        for (double t : sampleTimes) {
            double raw = envelope.rawEnvelope(t, p, s, parameters);
            double currentVelocity = prediction.pgvCmPerSecond() * raw / maximumRaw;
            runningPgv = Math.max(runningPgv, currentVelocity);
            OptionalDouble runningMmiOpt = gmice.tryFromPgvCmPerSecond(runningPgv);
            if (runningMmiOpt.isPresent()) {
                runningMmi = Math.max(runningMmi, runningMmiOpt.getAsDouble());
            }

            // Maximum reached curve
            IntensityStatus maxStatus;
            OptionalDouble maxMmi;
            OptionalDouble maxPgv;
            if (runningPgv > 0.0 && Double.isFinite(runningMmi)) {
                maxStatus = revealedStatus;
                maxMmi = OptionalDouble.of(runningMmi);
                maxPgv = OptionalDouble.of(runningPgv);
            } else {
                maxStatus = IntensityStatus.NOT_ARRIVED;
                maxMmi = OptionalDouble.empty();
                maxPgv = OptionalDouble.empty();
            }

            // Current shaking curve (envelope-derived)
            IntensityStatus currentStatus;
            OptionalDouble currentMmi;
            OptionalDouble currentPgv;

            if (t < p || currentVelocity <= 0.0) {
                if (t > peakTime) {
                    currentStatus = IntensityStatus.SHAKING_ENDED;
                } else {
                    currentStatus = IntensityStatus.NOT_ARRIVED;
                }
                currentMmi = OptionalDouble.empty();
                currentPgv = OptionalDouble.empty();
            } else {
                OptionalDouble currMmiOpt = gmice.tryFromPgvCmPerSecond(currentVelocity);
                boolean pastPeak = (t > peakTime);
                boolean belowThreshold = currMmiOpt.isEmpty() || currMmiOpt.getAsDouble() < 1.0;
                boolean ended = pastPeak && (belowThreshold || t >= supportEnd);

                if (ended) {
                    currentStatus = IntensityStatus.SHAKING_ENDED;
                    currentMmi = OptionalDouble.empty();
                    currentPgv = OptionalDouble.empty();
                } else {
                    currentStatus = revealedStatus;
                    double mmiVal = currMmiOpt.isPresent() ? currMmiOpt.getAsDouble() : 1.0;
                    currentMmi = OptionalDouble.of(mmiVal);
                    currentPgv = OptionalDouble.of(currentVelocity);
                }
            }

            samples.add(new IntensityTimeline.Sample(t, maxStatus, maxMmi, maxPgv,
                    currentStatus, currentMmi, currentPgv));
        }
        double finalMmi = gmice.fromPgvCmPerSecond(prediction.pgvCmPerSecond());
        IntensityTimeline timeline = new IntensityTimeline(site, p, s, surfaceDistance,
                OptionalDouble.of(rjb), OptionalDouble.of(finalMmi),
                OptionalDouble.of(prediction.pgvCmPerSecond()),
                Optional.of(MmiLegend.findBin(Math.max(1.0, finalMmi), MmiMode.SIMULATED)),
                domain, metadata, samples, MmiMode.SIMULATED);
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
