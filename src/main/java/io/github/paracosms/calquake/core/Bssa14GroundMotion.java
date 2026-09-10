package io.github.paracosms.calquake.core;

/** Base/no-basin BSSA14 deterministic median PGV implementation. */
public final class Bssa14GroundMotion {
    public static final String MODEL_ID = "BSSA14-PGV-RotD50-base-no-basin";
    public static final String VERSION = "OpenQuake-boore_2014-pgv-coefficients-2026-09-08";
    /** SHA-256 of the documented canonical PGV/PGA coefficient serialization. */
    public static final String COEFFICIENT_SHA256 =
            "31cdfae748b08d1093dc2e8a459cffcae9f38f273be6257e31d193b3abaad925";

    private static final Coefficients PGV = new Coefficients(
            5.037, 5.078, 4.849, 5.033, 1.073, -0.1536, 0.2252, 6.2,
            -1.243, 0.1489, -0.00344, 5.3, 0.0, -0.84, 1300.0,
            -0.1, -0.00844, 105.0, 272.0, 0.082, 0.080,
            0.644, 0.552, 0.401, 0.346);
    private static final Coefficients PGA = new Coefficients(
            0.4473, 0.4856, 0.2459, 0.4539, 1.431, 0.05053, -0.1662, 5.5,
            -1.134, 0.1917, -0.008088, 4.5, 0.0, -0.6, 1500.0,
            -0.15, -0.00701, 110.0, 270.0, 0.100, 0.070,
            0.695, 0.495, 0.398, 0.348);

    public Prediction predictPgv(double magnitude, double rakeDegrees, double rjbKm, double vs30) {
        validate(magnitude, rakeDegrees, rjbKm, vs30);
        double pgaRockG = Math.exp(event(PGA, magnitude, rakeDegrees) + path(PGA, magnitude, rjbKm));
        double lnPgv = event(PGV, magnitude, rakeDegrees) + path(PGV, magnitude, rjbKm)
                + site(PGV, vs30, pgaRockG);
        DomainStatus domain = magnitude >= 3.0 && magnitude <= 8.5
                && rjbKm <= 400.0 && vs30 >= 150.0 && vs30 <= 1500.0
                ? DomainStatus.IN_DOMAIN : DomainStatus.OUT_OF_DOMAIN;
        double tau = interpolateMagnitude(PGV.tau1, PGV.tau2, magnitude);
        double phi = intraEventPhi(PGV, magnitude, rjbKm, vs30);
        return new Prediction(lnPgv, Math.exp(lnPgv), Math.hypot(tau, phi), tau, phi,
                pgaRockG, domain, MODEL_ID, VERSION);
    }

    private static void validate(double magnitude, double rake, double rjb, double vs30) {
        if (!Double.isFinite(magnitude) || !Double.isFinite(rake)
                || !Double.isFinite(rjb) || !Double.isFinite(vs30)
                || rake < -180.0 || rake > 180.0 || rjb < 0.0 || vs30 <= 0.0) {
            throw new IllegalArgumentException("BSSA14 inputs must be finite and physically valid");
        }
    }

    private static double event(Coefficients c, double magnitude, double rake) {
        double style;
        double abs = Math.abs(rake);
        if (abs <= 30.0 || 180.0 - abs <= 30.0) {
            style = c.e1;
        } else if (rake > 30.0 && rake < 150.0) {
            style = c.e3;
        } else {
            style = c.e2;
        }
        double dm = magnitude - c.mh;
        return style + (magnitude <= c.mh ? c.e4 * dm + c.e5 * dm * dm : c.e6 * dm);
    }

    private static double path(Coefficients c, double magnitude, double rjb) {
        double r = Math.hypot(rjb, c.h);
        return (c.c1 + c.c2 * (magnitude - 4.5)) * Math.log(r)
                + (c.c3 + c.dc3) * (r - 1.0);
    }

    private static double site(Coefficients c, double vs30, double pgaRockG) {
        double cappedLinear = Math.min(vs30, c.vc);
        double linear = c.siteC * Math.log(cappedLinear / 760.0);
        double v = Math.min(vs30, 760.0);
        double f2 = c.f4 * (Math.exp(c.f5 * (v - 360.0)) - Math.exp(c.f5 * 400.0));
        double nonlinear = f2 * Math.log((pgaRockG + 0.1) / 0.1);
        return linear + nonlinear;
    }

    private static double interpolateMagnitude(double low, double high, double magnitude) {
        if (magnitude <= 4.5) return low;
        if (magnitude >= 5.5) return high;
        return low + (high - low) * (magnitude - 4.5);
    }

    private static double intraEventPhi(Coefficients c, double magnitude, double rjb, double vs30) {
        double phi = interpolateMagnitude(c.phi1, c.phi2, magnitude);
        if (rjb > c.r2) {
            phi += c.dfR;
        } else if (rjb > c.r1) {
            phi += c.dfR * Math.log(rjb / c.r1) / Math.log(c.r2 / c.r1);
        }
        if (vs30 <= 225.0) {
            phi -= c.dfV;
        } else if (vs30 <= 300.0) {
            phi -= c.dfV * Math.log(300.0 / vs30) / Math.log(300.0 / 225.0);
        }
        return phi;
    }

    public record Prediction(
            double naturalLogPgvCmPerSecond,
            double pgvCmPerSecond,
            double totalSigmaNaturalLog,
            double tauNaturalLog,
            double phiNaturalLog,
            double referenceRockPgaG,
            DomainStatus domainStatus,
            String modelId,
            String version
    ) {}

    private record Coefficients(
            double e0, double e1, double e2, double e3, double e4, double e5, double e6, double mh,
            double c1, double c2, double c3, double h, double dc3, double siteC, double vc,
            double f4, double f5, double r1, double r2, double dfR, double dfV,
            double phi1, double phi2, double tau1, double tau2
    ) {}
}
