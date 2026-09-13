package io.github.paracosms.calquake.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Standard conformal spherical Mercator projection with a fixed geographic origin.
 * <p>
 * Forward projection formula:
 * <pre>
 *   x = R * (lon - lon0)
 *   y = -R * (ln(tan(pi/4 + lat/2)) - y0_raw)
 * </pre>
 * where:
 * <ul>
 *   <li>{@code R} is the 6,371 km fixed sphere radius.</li>
 *   <li>{@code +x} points East, {@code -x} points West.</li>
 *   <li>{@code -y} points North, {@code +y} points South (explicit screen-downwards orientation).</li>
 *   <li>{@code (lat0, lon0)} is the fixed geographic center (default: 37.0°N, -119.5°W).</li>
 * </ul>
 * Under this projection:
 * <ul>
 *   <li>The California landmass and reference cities remain strictly stationary across scenarios.</li>
 *   <li>True North is strictly vertical everywhere on the map.</li>
 *   <li>Meridians and parallels form an orthogonal grid.</li>
 * </ul>
 */
public final class MercatorProjection {

    public static final double EARTH_RADIUS_KM = 6371.0;
    public static final double DEFAULT_CENTER_LATITUDE = 37.0;
    public static final double DEFAULT_CENTER_LONGITUDE = -119.5;

    private static final MercatorProjection CALIFORNIA_DEFAULT =
            new MercatorProjection(new GeoPoint(DEFAULT_CENTER_LATITUDE, DEFAULT_CENTER_LONGITUDE));

    private final GeoPoint origin;
    private final double lam0Rad;
    private final double y0Raw;

    public MercatorProjection(GeoPoint origin) {
        this.origin = Objects.requireNonNull(origin, "origin cannot be null");
        double phi0Rad = Math.toRadians(origin.latitude());
        this.lam0Rad = Math.toRadians(origin.longitude());
        this.y0Raw = Math.log(Math.tan(Math.PI / 4.0 + phi0Rad / 2.0));
    }

    public static MercatorProjection californiaDefault() {
        return CALIFORNIA_DEFAULT;
    }

    public static MercatorProjection centeredAt(GeoPoint origin) {
        return new MercatorProjection(origin);
    }

    public GeoPoint origin() {
        return origin;
    }

    /**
     * 2D Cartesian point in kilometers in the projection plane.
     */
    public record ProjectedPoint(double xKm, double yKm) {
        public double distanceFromOriginKm() {
            return Math.hypot(xKm, yKm);
        }
    }

    /**
     * Projects a geographic coordinate into 2D projection space (kilometers).
     *
     * @param point geographic point to project
     * @return ProjectedPoint in kilometers relative to origin
     */
    public ProjectedPoint project(GeoPoint point) {
        Objects.requireNonNull(point, "point cannot be null");

        double phiRad = Math.toRadians(point.latitude());
        double lamRad = Math.toRadians(point.longitude());

        // Clamp latitude to avoid infinity at poles
        phiRad = Math.max(-1.4844, Math.min(1.4844, phiRad)); // ~85.05 degrees

        double x = EARTH_RADIUS_KM * (lamRad - lam0Rad);
        double yRaw = Math.log(Math.tan(Math.PI / 4.0 + phiRad / 2.0));
        double y = -EARTH_RADIUS_KM * (yRaw - y0Raw);

        return new ProjectedPoint(x, y);
    }

    /**
     * Inverts projected coordinates back to geographic latitude and longitude.
     *
     * @param xKm East coordinate in km
     * @param yKm South coordinate in km (-y is North)
     * @return original GeoPoint on the 6,371 km sphere
     */
    public GeoPoint unproject(double xKm, double yKm) {
        double lamRad = lam0Rad + xKm / EARTH_RADIUS_KM;
        double yRaw = y0Raw - yKm / EARTH_RADIUS_KM;
        double phiRad = 2.0 * Math.atan(Math.exp(yRaw)) - Math.PI / 2.0;

        double degLat = Math.toDegrees(phiRad);
        double degLon = (Math.toDegrees(lamRad) + 540.0) % 360.0 - 180.0;

        return new GeoPoint(degLat, degLon);
    }

    public GeoPoint unproject(ProjectedPoint projectedPoint) {
        Objects.requireNonNull(projectedPoint, "projectedPoint cannot be null");
        return unproject(projectedPoint.xKm(), projectedPoint.yKm());
    }

    /**
     * Generates a closed ring of projected points along a great-circle surface distance
     * for physically exact wavefront rendering on the Mercator projection plane.
     *
     * @param center    epicenter origin of the wavefront
     * @param radiusKm  ground distance in kilometers
     * @param numPoints number of perimeter sample points
     * @return list of projected points forming the wavefront polygon
     */
    public List<ProjectedPoint> geodesicCirclePoints(GeoPoint center, double radiusKm, int numPoints) {
        Objects.requireNonNull(center, "center cannot be null");
        if (radiusKm <= 0.0 || numPoints < 3) {
            return List.of();
        }

        double phi1 = Math.toRadians(center.latitude());
        double lam1 = Math.toRadians(center.longitude());
        double delta = radiusKm / EARTH_RADIUS_KM;
        double sinPhi1 = Math.sin(phi1);
        double cosPhi1 = Math.cos(phi1);
        double sinDelta = Math.sin(delta);
        double cosDelta = Math.cos(delta);

        List<ProjectedPoint> points = new ArrayList<>(numPoints);
        double step = (2.0 * Math.PI) / numPoints;
        for (int i = 0; i < numPoints; i++) {
            double theta = i * step;
            double sinPhi2 = sinPhi1 * cosDelta + cosPhi1 * sinDelta * Math.cos(theta);
            sinPhi2 = Math.max(-1.0, Math.min(1.0, sinPhi2));
            double phi2 = Math.asin(sinPhi2);
            double yLam = Math.sin(theta) * sinDelta * cosPhi1;
            double xLam = cosDelta - sinPhi1 * sinPhi2;
            double lam2 = lam1 + Math.atan2(yLam, xLam);
            double degLat = Math.toDegrees(phi2);
            double degLon = (Math.toDegrees(lam2) + 540.0) % 360.0 - 180.0;
            points.add(project(new GeoPoint(degLat, degLon)));
        }
        return Collections.unmodifiableList(points);
    }

    /**
     * Bounding box in projection kilometer space.
     */
    public record BoundingBox(double minXKm, double minYKm, double maxXKm, double maxYKm) {
        public double widthKm() {
            return maxXKm - minXKm;
        }

        public double heightKm() {
            return maxYKm - minYKm;
        }

        public double centerXKm() {
            return (minXKm + maxXKm) / 2.0;
        }

        public double centerYKm() {
            return (minYKm + maxYKm) / 2.0;
        }
    }

    /**
     * 2D Screen pixel coordinate.
     */
    public record ScreenPoint(double xPx, double yPx) {}

    /**
     * Linear viewport transformation preserving 1:1 aspect ratio.
     * Uniform scale ensures conformal shapes remain undistorted when rendered.
     */
    public record ViewportTransform(
            double scalePxPerKm,
            double originScreenXPx,
            double originScreenYPx
    ) {
        public ScreenPoint toScreen(double xKm, double yKm) {
            return new ScreenPoint(
                    originScreenXPx + xKm * scalePxPerKm,
                    originScreenYPx + yKm * scalePxPerKm
            );
        }

        public ScreenPoint toScreen(ProjectedPoint p) {
            return toScreen(p.xKm(), p.yKm());
        }

        public double toScreenRadius(double radiusKm) {
            return radiusKm * scalePxPerKm;
        }

        public ProjectedPoint toProjected(double screenXPx, double screenYPx) {
            return new ProjectedPoint(
                    (screenXPx - originScreenXPx) / scalePxPerKm,
                    (screenYPx - originScreenYPx) / scalePxPerKm
            );
        }

        public ProjectedPoint toProjected(ScreenPoint sp) {
            return toProjected(sp.xPx(), sp.yPx());
        }
    }

    /**
     * Creates a ViewportTransform that fits the given bounding box into a screen viewport
     * while strictly preserving 1:1 aspect ratio.
     *
     * @param bounds       bounding box in projected km
     * @param screenWidth  viewport width in pixels
     * @param screenHeight viewport height in pixels
     * @param marginPx     padding margin around bounding box in pixels
     * @return configured ViewportTransform
     */
    public ViewportTransform createViewportTransform(BoundingBox bounds, double screenWidth, double screenHeight, double marginPx) {
        Objects.requireNonNull(bounds, "bounds cannot be null");
        double availW = screenWidth - 2.0 * marginPx;
        double availH = screenHeight - 2.0 * marginPx;
        if (availW <= 0 || availH <= 0) {
            throw new IllegalArgumentException("Viewport size too small for specified margin");
        }

        // Uniform scale factor to preserve 1:1 aspect ratio
        double scale = Math.min(availW / bounds.widthKm(), availH / bounds.heightKm());

        // Center the bounding box in the viewport
        double screenCenterX = screenWidth / 2.0;
        double screenCenterY = screenHeight / 2.0;

        // Position of projection (0, 0) on screen
        double originScreenX = screenCenterX - bounds.centerXKm() * scale;
        double originScreenY = screenCenterY - bounds.centerYKm() * scale;

        return new ViewportTransform(scale, originScreenX, originScreenY);
    }
}
