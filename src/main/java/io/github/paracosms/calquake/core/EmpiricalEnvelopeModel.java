package io.github.paracosms.calquake.core;

/**
 * Deterministic Cua-Heaton-derived P/S envelope shape. BSSA14 supplies the
 * amplitude; this model supplies only relative rise, plateau, and two-term coda.
 */
public final class EmpiricalEnvelopeModel {
    public static final String MODEL_ID = "Cua-Heaton-derived-relative-PS-envelope";
    public static final String VERSION = "calquake-1";
    /** SHA-256 of the documented canonical CalQuake v1 envelope equations. */
    public static final String COEFFICIENT_SHA256 =
            "0c4d70e60c2092f294a185121a568ed9a9ce8735f2631fb53b709d9b8075f52c";
    public static final double CALIBRATION_MAX_DISTANCE_KM = 200.0;
    public static final double CALIBRATION_MIN_MAGNITUDE = 2.0;
    public static final double CALIBRATION_MAX_MAGNITUDE = 7.3;

    public Parameters parameters(double magnitude, double epicentralDistanceKm, double vs30) {
        if (!Double.isFinite(magnitude) || !Double.isFinite(epicentralDistanceKm)
                || !Double.isFinite(vs30) || epicentralDistanceKm < 0.0 || vs30 <= 0.0) {
            throw new IllegalArgumentException("Envelope inputs must be finite and physically valid");
        }
        double soilStretch = vs30 < 464.0 ? 1.12 : 1.0;
        Body p = new Body(
                Math.max(0.35, 0.12 * magnitude + 0.002 * epicentralDistanceKm) * soilStretch,
                Math.max(0.40, 0.25 * magnitude + 0.003 * epicentralDistanceKm) * soilStretch,
                Math.max(1.0, 0.45 * magnitude + 0.010 * epicentralDistanceKm) * soilStretch,
                Math.max(3.0, 1.35 * magnitude + 0.030 * epicentralDistanceKm) * soilStretch,
                0.18);
        Body s = new Body(
                Math.max(0.75, 0.25 * magnitude + 0.004 * epicentralDistanceKm) * soilStretch,
                Math.max(1.0, 0.65 * magnitude + 0.008 * epicentralDistanceKm) * soilStretch,
                Math.max(2.0, 1.10 * magnitude + 0.015 * epicentralDistanceKm) * soilStretch,
                Math.max(6.0, 3.30 * magnitude + 0.045 * epicentralDistanceKm) * soilStretch,
                1.0);
        DomainStatus status = magnitude > CALIBRATION_MIN_MAGNITUDE
                && magnitude <= CALIBRATION_MAX_MAGNITUDE
                && epicentralDistanceKm < CALIBRATION_MAX_DISTANCE_KM
                ? DomainStatus.IN_DOMAIN : DomainStatus.OUT_OF_DOMAIN;
        return new Parameters(p, s, status);
    }

    public double rawEnvelope(double elapsedSeconds, double pArrival, double sArrival, Parameters parameters) {
        return Math.max(0.0, body(elapsedSeconds - pArrival, parameters.p()))
                + Math.max(0.0, body(elapsedSeconds - sArrival, parameters.s()));
    }

    public double supportEnd(double pArrival, double sArrival, Parameters parameters) {
        return Math.max(pArrival + parameters.p().riseSeconds() + parameters.p().plateauSeconds()
                        + 8.0 * parameters.p().slowDecaySeconds(),
                sArrival + parameters.s().riseSeconds() + parameters.s().plateauSeconds()
                        + 8.0 * parameters.s().slowDecaySeconds());
    }

    public double predictedPeakTime(double pArrival, double sArrival, Parameters parameters) {
        double end = supportEnd(pArrival, sArrival, parameters);
        double bestTime = pArrival;
        double best = -1.0;
        for (double t = Math.max(0.0, pArrival); t <= end; t += 0.025) {
            double value = rawEnvelope(t, pArrival, sArrival, parameters);
            if (value > best) {
                best = value;
                bestTime = t;
            }
        }
        return bestTime;
    }

    private static double body(double sinceArrival, Body body) {
        if (sinceArrival <= 0.0) return 0.0;
        if (sinceArrival < body.riseSeconds()) {
            double x = sinceArrival / body.riseSeconds();
            double sine = Math.sin(Math.PI * x * 0.5);
            return body.relativeAmplitude() * sine * sine;
        }
        double sincePeak = sinceArrival - body.riseSeconds();
        if (sincePeak <= body.plateauSeconds()) return body.relativeAmplitude();
        double coda = sincePeak - body.plateauSeconds();
        return body.relativeAmplitude() * (0.75 * Math.exp(-coda / body.fastDecaySeconds())
                + 0.25 * Math.exp(-coda / body.slowDecaySeconds()));
    }

    public record Body(double riseSeconds, double plateauSeconds, double fastDecaySeconds,
                       double slowDecaySeconds, double relativeAmplitude) {
        public Body {
            if (!(riseSeconds > 0.0 && plateauSeconds >= 0.0 && fastDecaySeconds > 0.0
                    && slowDecaySeconds > 0.0 && relativeAmplitude >= 0.0)) {
                throw new IllegalArgumentException("Envelope body parameters must be non-negative");
            }
        }
    }

    public record Parameters(Body p, Body s, DomainStatus domainStatus) {}
}
