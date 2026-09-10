package io.github.paracosms.calquake.core;

/**
 * Immutable geographic coordinate (latitude and longitude) in WGS84 decimal degrees.
 *
 * @param latitude  latitude in degrees between -90.0 and +90.0
 * @param longitude longitude in degrees between -180.0 and +180.0
 */
public record GeoPoint(double latitude, double longitude) {

    public GeoPoint {
        if (Double.isNaN(latitude) || Double.isInfinite(latitude)) {
            throw new IllegalArgumentException("Latitude must be a finite number: " + latitude);
        }
        if (Double.isNaN(longitude) || Double.isInfinite(longitude)) {
            throw new IllegalArgumentException("Longitude must be a finite number: " + longitude);
        }
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90.0 and 90.0 degrees: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180.0 and 180.0 degrees: " + longitude);
        }
    }

    public static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Calculates the great-circle epicentral distance in kilometers to another point on a 6,371 km sphere.
     *
     * @param other target point
     * @return great-circle distance in kilometers
     */
    public double distanceKmTo(GeoPoint other) {
        java.util.Objects.requireNonNull(other, "other cannot be null");
        if (this.equals(other) || (Math.abs(latitude - other.latitude) < 1e-11 && Math.abs(longitude - other.longitude) < 1e-11)) {
            return 0.0;
        }
        double phi1 = Math.toRadians(latitude);
        double lam1 = Math.toRadians(longitude);
        double phi2 = Math.toRadians(other.latitude);
        double lam2 = Math.toRadians(other.longitude);
        double dphi = phi2 - phi1;
        double dlam = lam2 - lam1;
        double sinHalfDphi = Math.sin(dphi / 2.0);
        double sinHalfDlam = Math.sin(dlam / 2.0);
        double h = sinHalfDphi * sinHalfDphi + Math.cos(phi1) * Math.cos(phi2) * sinHalfDlam * sinHalfDlam;
        h = Math.max(0.0, Math.min(1.0, h));
        return 2.0 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }
}
