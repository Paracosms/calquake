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
}
