package io.github.paracosms.calquake.core;

import io.github.paracosms.calquake.data.FaultSectionCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic fault resolver that associates an entered hypocenter with the most plausible
 * nearby fault section from an offline catalog.
 */
public final class AutomaticFaultResolver {

    public static final String RESOLVER_ID = "automatic-fault-resolver-v1";
    public static final double MAX_DISTANCE_KM = 10.0;
    public static final double COMPARABLE_TOLERANCE_KM = 1.0;
    public static final double STRIKE_WINDOW_KM = 10.0;

    private static final double KM_PER_DEGREE = Math.PI * GeoPoint.EARTH_RADIUS_KM / 180.0;

    private final FaultSectionCatalog catalog;

    public AutomaticFaultResolver(FaultSectionCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog cannot be null");
    }

    public static AutomaticFaultResolver defaultResolver() {
        return new AutomaticFaultResolver(FaultSectionCatalog.loadDefault());
    }

    public FaultSectionCatalog catalog() {
        return catalog;
    }

    /**
     * Resolves fault association for given epicenter, depth, and magnitude.
     */
    public FaultResolution resolve(GeoPoint epicenter, double depthKm, double magnitude) {
        Objects.requireNonNull(epicenter, "epicenter cannot be null");
        if (!Double.isFinite(depthKm) || depthKm < 0.0) {
            throw new IllegalArgumentException("Depth must be non-negative and finite: " + depthKm);
        }
        if (!Double.isFinite(magnitude)) {
            throw new IllegalArgumentException("Magnitude must be finite: " + magnitude);
        }

        List<CandidateDistance> eligible = new ArrayList<>();

        for (FaultSection section : catalog.sections()) {
            if (!boundsOverlap(section, epicenter, MAX_DISTANCE_KM)) {
                continue;
            }
            SectionDistanceResult distResult = computeSectionDistance(section, epicenter, depthKm);
            if (distResult.minDistanceKm <= MAX_DISTANCE_KM) {
                eligible.add(new CandidateDistance(section, distResult));
            }
        }

        if (eligible.isEmpty()) {
            Mechanism genericMech = new Mechanism(0.0, 0.0, 90.0, "STRIKE_SLIP", "Generic strike-slip fallback");
            RuptureGeometry rupture = RuptureGeometryProvider.generatePlanar(epicenter, depthKm, magnitude, genericMech);
            return FaultResolution.generic(genericMech, rupture, "No suitable nearby fault section within 10 km");
        }

        // Find minimum distance
        double minDistance = eligible.stream()
                .mapToDouble(c -> c.result.minDistanceKm)
                .min()
                .orElseThrow();

        // Identify comparable set within 1 km of minimum
        double cutoff = minDistance + COMPARABLE_TOLERANCE_KM;
        List<CandidateDistance> comparable = eligible.stream()
                .filter(c -> c.result.minDistanceKm <= cutoff)
                .toList();

        boolean isAmbiguous = comparable.size() > 1;

        // Select lowest stable section ID within comparable set
        CandidateDistance selected = comparable.stream()
                .min(Comparator.comparingLong((CandidateDistance c) -> c.section.sectionId())
                        .thenComparingDouble(c -> c.result.minDistanceKm))
                .orElseThrow();

        FaultSection chosenSection = selected.section;
        SectionDistanceResult chosenResult = selected.result;

        // Derive local strike from ~10 km window along trace centered on closest point
        double strike = deriveLocalStrike(chosenSection, chosenResult.alongTraceKm, chosenResult.closestSegmentIndex);

        // Mechanism style from rake
        String style = classifyStyle(chosenSection.rakeDegrees());
        String provenance = "NSHM23: " + chosenSection.sectionName() + " (" + chosenSection.sectionId() + ")";
        Mechanism mechanism = new Mechanism(
                chosenSection.rakeDegrees(), strike, chosenSection.dipDegrees(), style, provenance);

        RuptureGeometry rupture = RuptureGeometryProvider.generatePlanar(epicenter, depthKm, magnitude, mechanism);

        return FaultResolution.faultInformed(
                chosenSection.sectionId(), chosenSection.sectionName(), chosenResult.minDistanceKm,
                isAmbiguous, mechanism, rupture);
    }

    private static boolean boundsOverlap(FaultSection section, GeoPoint epicenter, double bufferKm) {
        double maxDipHoriz = section.horizontalDipWidthKm();
        double totalBufferKm = maxDipHoriz + bufferKm;
        double latBufferDeg = totalBufferKm / KM_PER_DEGREE;
        double lonBufferDeg = totalBufferKm / (KM_PER_DEGREE * Math.cos(Math.toRadians(epicenter.latitude())));

        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;

        for (GeoPoint p : section.trace()) {
            minLat = Math.min(minLat, p.latitude());
            maxLat = Math.max(maxLat, p.latitude());
            minLon = Math.min(minLon, p.longitude());
            maxLon = Math.max(maxLon, p.longitude());
        }

        return epicenter.latitude() >= minLat - latBufferDeg
                && epicenter.latitude() <= maxLat + latBufferDeg
                && epicenter.longitude() >= minLon - lonBufferDeg
                && epicenter.longitude() <= maxLon + lonBufferDeg;
    }

    static SectionDistanceResult computeSectionDistance(FaultSection section, GeoPoint epicenter, double depthKm) {
        List<GeoPoint> trace = section.trace();
        double bestDist = Double.POSITIVE_INFINITY;
        int bestSegment = 0;
        double alongTraceToBest = 0.0;

        double cumulativeTraceDist = 0.0;
        double dipRad = Math.toRadians(section.dipDegrees());
        double sinDip = Math.sin(dipRad);
        double cosDip = Math.cos(dipRad);
        double patchWidth = section.downDipWidthKm();
        double upDepth = section.upperDepthKm();
        double catDipAzimuth = dipDirectionAzimuth(section.dipDirection());

        for (int i = 0; i < trace.size() - 1; i++) {
            GeoPoint p1 = trace.get(i);
            GeoPoint p2 = trace.get(i + 1);

            double meanLatRad = Math.toRadians((p1.latitude() + p2.latitude()) * 0.5);
            double dx = (p2.longitude() - p1.longitude()) * KM_PER_DEGREE * Math.cos(meanLatRad);
            double dy = (p2.latitude() - p1.latitude()) * KM_PER_DEGREE;
            double segLen = Math.hypot(dx, dy);

            if (segLen < 1.0e-6) {
                continue;
            }

            // Unit vector along trace segment s
            double sx = dx / segLen;
            double sy = dy / segLen;

            // Perpendicular horizontal dip sides
            // Right: (sy, -sx), Left: (-sy, sx)
            double rnx = sy;
            double rny = -sx;
            double lnx = -sy;
            double lny = sx;

            // Unit vector for catalog dip direction
            double catRad = Math.toRadians(catDipAzimuth);
            double catX = Math.sin(catRad); // East
            double catY = Math.cos(catRad); // North

            double hnx, hny;
            if (section.dipDegrees() >= 89.999 || "Vertical".equalsIgnoreCase(section.dipDirection())) {
                hnx = 0.0;
                hny = 0.0;
            } else {
                double rDot = rnx * catX + rny * catY;
                double lDot = lnx * catX + lny * catY;
                if (rDot >= lDot) {
                    hnx = rnx;
                    hny = rny;
                } else {
                    hnx = lnx;
                    hny = lny;
                }
            }

            // Down-dip unit vector d
            double dx_comp = cosDip * hnx;
            double dy_comp = cosDip * hny;
            double dz_comp = sinDip;

            // Hypocenter offset q from p1
            double qx = (epicenter.longitude() - p1.longitude()) * KM_PER_DEGREE * Math.cos(Math.toRadians(p1.latitude()));
            double qy = (epicenter.latitude() - p1.latitude()) * KM_PER_DEGREE;
            double qz = depthKm - upDepth;

            // Orthogonal projections
            double u = qx * sx + qy * sy;
            double v = qx * dx_comp + qy * dy_comp + qz * dz_comp;

            // Clamp to patch bounds
            double uClamped = Math.max(0.0, Math.min(segLen, u));
            double vClamped = Math.max(0.0, Math.min(patchWidth, v));

            // Closest point coordinates relative to p1
            double px = uClamped * sx + vClamped * dx_comp;
            double py = uClamped * sy + vClamped * dy_comp;
            double pz = vClamped * dz_comp;

            // Distance from hypocenter to patch
            double rx = qx - px;
            double ry = qy - py;
            double rz = qz - pz;
            double dist = Math.sqrt(rx * rx + ry * ry + rz * rz);

            if (dist < bestDist) {
                bestDist = dist;
                bestSegment = i;
                alongTraceToBest = cumulativeTraceDist + uClamped;
            }

            cumulativeTraceDist += segLen;
        }

        return new SectionDistanceResult(bestDist, bestSegment, alongTraceToBest, cumulativeTraceDist);
    }

    private static double deriveLocalStrike(FaultSection section, double alongTraceKm, int closestSegmentIndex) {
        List<GeoPoint> trace = section.trace();
        double totalLength = 0.0;
        double[] segLengths = new double[trace.size() - 1];
        for (int i = 0; i < trace.size() - 1; i++) {
            segLengths[i] = trace.get(i).distanceKmTo(trace.get(i + 1));
            totalLength += segLengths[i];
        }

        if (totalLength < 1.0e-4) {
            return 0.0;
        }

        double halfWindow = STRIKE_WINDOW_KM * 0.5;
        double sStart = Math.max(0.0, alongTraceKm - halfWindow);
        double sEnd = Math.min(totalLength, alongTraceKm + halfWindow);

        GeoPoint pStart = interpolateAlongTrace(trace, segLengths, sStart);
        GeoPoint pEnd = interpolateAlongTrace(trace, segLengths, sEnd);

        double strike = pStart.bearingTo(pEnd);
        if (Double.isNaN(strike)) {
            // Fall back to segment bearing
            GeoPoint p1 = trace.get(closestSegmentIndex);
            GeoPoint p2 = trace.get(closestSegmentIndex + 1);
            strike = p1.bearingTo(p2);
        }

        // Orient strike so strike + 90 represents chosen dip side
        if (section.dipDegrees() < 89.999 && !"Vertical".equalsIgnoreCase(section.dipDirection())) {
            double dipAzimuth = dipDirectionAzimuth(section.dipDirection());
            double rightSideBearing = (strike + 90.0) % 360.0;
            double diff = Math.abs(normalizeDegrees(rightSideBearing - dipAzimuth));
            if (diff > 90.0) {
                strike = (strike + 180.0) % 360.0;
            }
        } else {
            // For vertical faults, normalize deterministically into [0, 180)
            strike = strike % 180.0;
            if (strike < 0.0) strike += 180.0;
        }

        return strike;
    }

    private static GeoPoint interpolateAlongTrace(List<GeoPoint> trace, double[] segLengths, double distanceKm) {
        double current = 0.0;
        for (int i = 0; i < segLengths.length; i++) {
            double len = segLengths[i];
            if (current + len >= distanceKm || i == segLengths.length - 1) {
                double t = len > 1.0e-6 ? Math.max(0.0, Math.min(1.0, (distanceKm - current) / len)) : 0.0;
                GeoPoint a = trace.get(i);
                GeoPoint b = trace.get(i + 1);
                double lat = a.latitude() + t * (b.latitude() - a.latitude());
                double lon = a.longitude() + t * (b.longitude() - a.longitude());
                return new GeoPoint(lat, lon);
            }
            current += len;
        }
        return trace.getLast();
    }

    private static double dipDirectionAzimuth(String dipDir) {
        String dir = dipDir.toUpperCase().trim();
        return switch (dir) {
            case "N", "NORTH" -> 0.0;
            case "NNE", "NORTH-NORTHEAST", "NORTH_NORTHEAST" -> 22.5;
            case "NE", "NORTHEAST" -> 45.0;
            case "ENE", "EAST-NORTHEAST", "EAST_NORTHEAST" -> 67.5;
            case "E", "EAST" -> 90.0;
            case "ESE", "EAST-SOUTHEAST", "EAST_SOUTHEAST" -> 112.5;
            case "SE", "SOUTHEAST" -> 135.0;
            case "SSE", "SOUTH-SOUTHEAST", "SOUTH_SOUTHEAST" -> 157.5;
            case "S", "SOUTH" -> 180.0;
            case "SSW", "SOUTH-SOUTHWEST", "SOUTH_SOUTHWEST" -> 202.5;
            case "SW", "SOUTHWEST" -> 225.0;
            case "WSW", "WEST-SOUTHWEST", "WEST_SOUTHWEST" -> 247.5;
            case "W", "WEST" -> 270.0;
            case "WNW", "WEST-NORTHWEST", "WEST_NORTHWEST" -> 292.5;
            case "NW", "NORTHWEST" -> 315.0;
            case "NNW", "NORTH-NORTHWEST", "NORTH_NORTHWEST" -> 337.5;
            default -> 90.0;
        };
    }

    private static double normalizeDegrees(double deg) {
        deg = (deg % 360.0 + 360.0) % 360.0;
        if (deg > 180.0) deg -= 360.0;
        return deg;
    }

    private static String classifyStyle(double rake) {
        double abs = Math.abs(rake);
        if (abs <= 30.0 || 180.0 - abs <= 30.0) {
            return "STRIKE_SLIP";
        } else if (rake > 30.0 && rake < 150.0) {
            return "REVERSE";
        } else {
            return "NORMAL";
        }
    }

    record SectionDistanceResult(
            double minDistanceKm,
            int closestSegmentIndex,
            double alongTraceKm,
            double totalTraceLengthKm
    ) {}

    private record CandidateDistance(FaultSection section, SectionDistanceResult result) {}
}
