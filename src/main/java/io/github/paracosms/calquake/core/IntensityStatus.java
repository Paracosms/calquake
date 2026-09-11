package io.github.paracosms.calquake.core;

/** Availability and diagnostic status of a site's intensity at a replay instant. */
public enum IntensityStatus {
    NOT_ARRIVED,
    AVAILABLE,
    MISSING_INPUT,
    MODEL_ERROR,
    OUT_OF_DOMAIN,
    TRUNCATED,
    SHAKING_ENDED;

    /** Whether this status may carry a calculated value that should be displayed. */
    public boolean hasDisplayValue() {
        return this == AVAILABLE || this == OUT_OF_DOMAIN || this == TRUNCATED;
    }
}
