package io.github.paracosms.calquake.core;

import java.util.List;
import java.util.Objects;

/** Automatic future-scenario geometry using Wells & Coppersmith (1994) all-slip scaling. */
public final class RuptureGeometryProvider {
    public static final String MODEL_ID = "wells-coppersmith-1994-all-slip";

    private RuptureGeometryProvider() {}

    public static RuptureGeometry generate(EventSource source) {
        Objects.requireNonNull(source, "source cannot be null");
        Mechanism mechanism = source.mechanism().orElse(new Mechanism(
                0.0, 0.0, 90.0, "STRIKE_SLIP", "CalQuake documented custom-scenario default"));
        return generatePlanar(source.epicenter(), source.depthKm(), source.magnitude(), mechanism);
    }

    public static RuptureGeometry generatePlanar(
            GeoPoint epicenter, double depthKm, double magnitude, Mechanism mechanism) {
        Objects.requireNonNull(epicenter, "epicenter cannot be null");
        Objects.requireNonNull(mechanism, "mechanism cannot be null");
        if (!Double.isFinite(depthKm) || depthKm < 0.0) {
            throw new IllegalArgumentException("Depth must be non-negative and finite");
        }
        if (!Double.isFinite(magnitude)) {
            throw new IllegalArgumentException("Magnitude must be finite");
        }
        double lengthKm = Math.pow(10.0, -3.22 + 0.69 * magnitude);
        double downDipWidthKm = Math.pow(10.0, -1.01 + 0.32 * magnitude);
        if (!Double.isFinite(lengthKm) || !Double.isFinite(downDipWidthKm)
                || lengthKm <= 0.0 || downDipWidthKm <= 0.0) {
            throw new IllegalArgumentException("Magnitude produced invalid rupture dimensions");
        }

        double dipRad = Math.toRadians(mechanism.dipDegrees());
        double sinDip = Math.sin(dipRad);
        double cosDip = Math.cos(dipRad);

        double verticalExtent = downDipWidthKm * sinDip;
        double top = Math.max(0.0, depthKm - verticalExtent * 0.5);
        double bottom = top + verticalExtent;
        double hypocenterDownDip = (depthKm - top) / sinDip;

        double distUpDip = hypocenterDownDip * cosDip;
        double strike = mechanism.strikeDegrees();
        GeoPoint topMidpoint = destination(epicenter, strike - 90.0, distUpDip);

        double halfLength = lengthKm * 0.5;
        GeoPoint a = destination(topMidpoint, strike + 180.0, halfLength);
        GeoPoint b = destination(topMidpoint, strike, halfLength);

        double horizontalWidth = downDipWidthKm * cosDip;
        List<GeoPoint> projection;
        if (horizontalWidth < 1.0e-6) {
            projection = List.of(a, b);
        } else {
            GeoPoint c = destination(b, strike + 90.0, horizontalWidth);
            GeoPoint d = destination(a, strike + 90.0, horizontalWidth);
            projection = List.of(a, b, c, d, a);
        }
        return new RuptureGeometry(List.of(projection), top, bottom,
                mechanism.strikeDegrees(), mechanism.dipDegrees(), MODEL_ID, "", true);
    }

    static GeoPoint destination(GeoPoint start, double bearingDegrees, double distanceKm) {
        double angular = distanceKm / GeoPoint.EARTH_RADIUS_KM;
        double bearing = Math.toRadians((bearingDegrees % 360.0 + 360.0) % 360.0);
        double lat1 = Math.toRadians(start.latitude());
        double lon1 = Math.toRadians(start.longitude());
        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(angular)
                + Math.cos(lat1) * Math.sin(angular) * Math.cos(bearing));
        double lon2 = lon1 + Math.atan2(Math.sin(bearing) * Math.sin(angular) * Math.cos(lat1),
                Math.cos(angular) - Math.sin(lat1) * Math.sin(lat2));
        lon2 = (lon2 + 3.0 * Math.PI) % (2.0 * Math.PI) - Math.PI;
        return new GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2));
    }
}
