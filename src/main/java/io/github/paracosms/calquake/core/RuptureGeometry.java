package io.github.paracosms.calquake.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Surface projection of a finite rupture. Each part is either a trace/polyline
 * or a closed projected polygon. Rjb is the shortest horizontal distance to
 * the union of those parts.
 */
public record RuptureGeometry(
        List<List<GeoPoint>> surfaceProjectionParts,
        double topDepthKm,
        double bottomDepthKm,
        double strikeDegrees,
        double dipDegrees,
        String sourceId,
        String sourceSha256,
        boolean generated
) {
    private static final double KM_PER_DEGREE = Math.PI * GeoPoint.EARTH_RADIUS_KM / 180.0;

    public RuptureGeometry {
        Objects.requireNonNull(surfaceProjectionParts, "surfaceProjectionParts cannot be null");
        if (surfaceProjectionParts.isEmpty()) {
            throw new IllegalArgumentException("A rupture needs at least one surface-projection part");
        }
        List<List<GeoPoint>> copy = new ArrayList<>(surfaceProjectionParts.size());
        for (List<GeoPoint> part : surfaceProjectionParts) {
            Objects.requireNonNull(part, "Rupture parts cannot be null");
            if (part.size() < 2) {
                throw new IllegalArgumentException("Each rupture part needs at least two points");
            }
            copy.add(List.copyOf(part));
        }
        surfaceProjectionParts = List.copyOf(copy);
        if (!Double.isFinite(topDepthKm) || !Double.isFinite(bottomDepthKm)
                || topDepthKm < 0.0 || bottomDepthKm < topDepthKm) {
            throw new IllegalArgumentException("Rupture depths must be finite and ordered");
        }
        if (!Double.isFinite(strikeDegrees) || strikeDegrees < 0.0 || strikeDegrees >= 360.0) {
            throw new IllegalArgumentException("Strike must be within [0, 360) degrees");
        }
        if (!Double.isFinite(dipDegrees) || dipDegrees <= 0.0 || dipDegrees > 90.0) {
            throw new IllegalArgumentException("Dip must be within (0, 90] degrees");
        }
        sourceId = sourceId == null ? "" : sourceId.trim();
        sourceSha256 = sourceSha256 == null ? "" : sourceSha256.trim().toLowerCase();
    }

    /** Returns Joyner-Boore distance in kilometres. */
    public double rjbKm(GeoPoint site) {
        Objects.requireNonNull(site, "site cannot be null");
        double best = Double.POSITIVE_INFINITY;
        for (List<GeoPoint> part : surfaceProjectionParts) {
            List<Point2> local = part.stream().map(p -> projectAround(site, p)).toList();
            if (isClosed(part) && pointInsideOrigin(local)) {
                return 0.0;
            }
            for (int i = 1; i < local.size(); i++) {
                best = Math.min(best, distanceFromOriginToSegment(local.get(i - 1), local.get(i)));
            }
        }
        return best;
    }

    /** Stable canonical text used in replay signatures. */
    public String canonicalForm() {
        StringBuilder out = new StringBuilder();
        out.append(topDepthKm).append('|').append(bottomDepthKm).append('|')
                .append(strikeDegrees).append('|').append(dipDegrees).append('|')
                .append(sourceId).append('|').append(sourceSha256).append('|').append(generated);
        for (List<GeoPoint> part : surfaceProjectionParts) {
            out.append(';');
            for (GeoPoint point : part) {
                out.append(Double.toHexString(point.latitude())).append(',')
                        .append(Double.toHexString(point.longitude())).append('/');
            }
        }
        return out.toString();
    }

    private static boolean isClosed(List<GeoPoint> part) {
        return part.size() >= 4 && part.getFirst().distanceKmTo(part.getLast()) < 1.0e-6;
    }

    private static Point2 projectAround(GeoPoint origin, GeoPoint point) {
        double meanLatitude = Math.toRadians((origin.latitude() + point.latitude()) * 0.5);
        double x = (point.longitude() - origin.longitude()) * KM_PER_DEGREE * Math.cos(meanLatitude);
        double y = (point.latitude() - origin.latitude()) * KM_PER_DEGREE;
        return new Point2(x, y);
    }

    private static boolean pointInsideOrigin(List<Point2> polygon) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            Point2 a = polygon.get(i);
            Point2 b = polygon.get(j);
            boolean crosses = (a.y > 0.0) != (b.y > 0.0)
                    && 0.0 < (b.x - a.x) * (-a.y) / (b.y - a.y) + a.x;
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static double distanceFromOriginToSegment(Point2 a, Point2 b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0.0) {
            return Math.hypot(a.x, a.y);
        }
        double t = Math.max(0.0, Math.min(1.0, -(a.x * dx + a.y * dy) / lengthSquared));
        return Math.hypot(a.x + t * dx, a.y + t * dy);
    }

    private record Point2(double x, double y) {}
}
