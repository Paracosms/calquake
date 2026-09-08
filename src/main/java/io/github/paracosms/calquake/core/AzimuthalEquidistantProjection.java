package io.github.paracosms.calquake.core;

import java.util.Objects;

/**
 * Epicenter-centered Azimuthal Equidistant Projection on a fixed 6,371 km sphere.
 * <p>
 * Forward projection formula:
 * <pre>
 *   x = d * sin(azimuth)
 *   y = -d * cos(azimuth)
 * </pre>
 * where:
 * <ul>
 *   <li>{@code d} is the great-circle epicentral distance in kilometers on the 6,371 km sphere.</li>
 *   <li>{@code azimuth} is the initial bearing from epicenter to target point.</li>
 *   <li>{@code +x} points East, {@code -x} points West.</li>
 *   <li>{@code -y} points North, {@code +y} points South (explicit screen-downwards orientation).</li>
 * </ul>
 * Under this projection, radial distances from the epicenter are preserved exactly,
 * and wavefronts expanding outward from the epicenter remain exact concentric circles.
 */
public final class AzimuthalEquidistantProjection {

    public static final double EARTH_RADIUS_KM = 6371.0;

    private final GeoPoint origin;
    private final double phi0; // latitude in radians
    private final double lam0; // longitude in radians
    private final double cosPhi0;
    private final double sinPhi0;

    public AzimuthalEquidistantProjection(GeoPoint origin) {
        this.origin = Objects.requireNonNull(origin, "origin cannot be null");
        this.phi0 = Math.toRadians(origin.latitude());
        this.lam0 = Math.toRadians(origin.longitude());
        this.cosPhi0 = Math.cos(phi0);
        this.sinPhi0 = Math.sin(phi0);
    }

    public static AzimuthalEquidistantProjection centeredAt(GeoPoint origin) {
        return new AzimuthalEquidistantProjection(origin);
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
     * @return ProjectedPoint in kilometers relative to epicenter
     */
    public ProjectedPoint project(GeoPoint point) {
        Objects.requireNonNull(point, "point cannot be null");

        if (point.equals(origin) ||
                (Math.abs(point.latitude() - origin.latitude()) < 1e-11 &&
                 Math.abs(point.longitude() - origin.longitude()) < 1e-11)) {
            return new ProjectedPoint(0.0, 0.0);
        }

        double phi = Math.toRadians(point.latitude());
        double lam = Math.toRadians(point.longitude());
        double dphi = phi - phi0;
        double dlam = lam - lam0;

        // Numerically stable haversine formula for spherical central angle Delta
        double sinHalfDphi = Math.sin(dphi / 2.0);
        double sinHalfDlam = Math.sin(dlam / 2.0);
        double h = sinHalfDphi * sinHalfDphi + cosPhi0 * Math.cos(phi) * sinHalfDlam * sinHalfDlam;
        h = Math.max(0.0, Math.min(1.0, h));
        double delta = 2.0 * Math.asin(Math.sqrt(h));

        if (delta < 1e-12) {
            return new ProjectedPoint(0.0, 0.0);
        }

        double distKm = EARTH_RADIUS_KM * delta;

        // Initial azimuth from origin to point
        double yAz = Math.sin(dlam) * Math.cos(phi);
        double xAz = cosPhi0 * Math.sin(phi) - sinPhi0 * Math.cos(phi) * Math.cos(dlam);
        double azimuth = Math.atan2(yAz, xAz);

        // x = d * sin(az), y = -d * cos(az)
        double x = distKm * Math.sin(azimuth);
        double y = -distKm * Math.cos(azimuth);

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
        double distKm = Math.hypot(xKm, yKm);
        if (distKm < 1e-12) {
            return origin;
        }

        double delta = distKm / EARTH_RADIUS_KM;
        // x = d * sin(az), y = -d * cos(az) => sin(az) = x/d, cos(az) = -y/d
        double azimuth = Math.atan2(xKm, -yKm);

        double sinDelta = Math.sin(delta);
        double cosDelta = Math.cos(delta);
        double cosAz = Math.cos(azimuth);
        double sinAz = Math.sin(azimuth);

        double sinPhi = sinPhi0 * cosDelta + cosPhi0 * sinDelta * cosAz;
        sinPhi = Math.max(-1.0, Math.min(1.0, sinPhi));
        double phi = Math.asin(sinPhi);

        double yLam = sinAz * sinDelta * cosPhi0;
        double xLam = cosDelta - sinPhi0 * sinPhi;
        double dlam = Math.atan2(yLam, xLam);
        double lam = lam0 + dlam;

        // Normalize longitude to [-180, 180]
        double degLat = Math.toDegrees(phi);
        double degLon = (Math.toDegrees(lam) + 540.0) % 360.0 - 180.0;

        return new GeoPoint(degLat, degLon);
    }

    public GeoPoint unproject(ProjectedPoint projectedPoint) {
        Objects.requireNonNull(projectedPoint, "projectedPoint cannot be null");
        return unproject(projectedPoint.xKm(), projectedPoint.yKm());
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
     * Uniform scale ensures circle wavefronts remain circular when rendered.
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
