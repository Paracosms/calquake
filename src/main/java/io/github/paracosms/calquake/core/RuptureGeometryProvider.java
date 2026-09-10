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
        double magnitude = source.magnitude();
        double lengthKm = Math.pow(10.0, -3.22 + 0.69 * magnitude);
        double downDipWidthKm = Math.pow(10.0, -1.01 + 0.32 * magnitude);
        if (!Double.isFinite(lengthKm) || !Double.isFinite(downDipWidthKm)
                || lengthKm <= 0.0 || downDipWidthKm <= 0.0) {
            throw new IllegalArgumentException("Magnitude produced invalid rupture dimensions");
        }

        double halfLength = lengthKm * 0.5;
        double horizontalWidth = downDipWidthKm * Math.cos(Math.toRadians(mechanism.dipDegrees()));
        GeoPoint a = destination(source.epicenter(), mechanism.strikeDegrees() + 180.0, halfLength);
        GeoPoint b = destination(source.epicenter(), mechanism.strikeDegrees(), halfLength);
        List<GeoPoint> projection;
        if (horizontalWidth < 1.0e-6) {
            projection = List.of(a, b);
        } else {
            GeoPoint c = destination(b, mechanism.strikeDegrees() + 90.0, horizontalWidth);
            GeoPoint d = destination(a, mechanism.strikeDegrees() + 90.0, horizontalWidth);
            projection = List.of(a, b, c, d, a);
        }
        double halfVerticalWidth = downDipWidthKm * Math.sin(Math.toRadians(mechanism.dipDegrees())) * 0.5;
        double top = Math.max(0.0, source.depthKm() - halfVerticalWidth);
        double bottom = source.depthKm() + halfVerticalWidth;
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
