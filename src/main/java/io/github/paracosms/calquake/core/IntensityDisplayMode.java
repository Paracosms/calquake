package io.github.paracosms.calquake.core;

import java.util.Objects;

/**
 * Intensity presentation mode options for Simulation mode.
 */
public enum IntensityDisplayMode {
    MAXIMUM_REACHED("Maximum estimated MMI reached"),
    CURRENT_SHAKING("Current estimated shaking");

    private final String label;

    IntensityDisplayMode(String label) {
        this.label = Objects.requireNonNull(label);
    }

    public String label() {
        return label;
    }

    public static IntensityDisplayMode fromLabel(String label) {
        if (label != null) {
            for (IntensityDisplayMode mode : values()) {
                if (mode.label.equalsIgnoreCase(label) || mode.name().equalsIgnoreCase(label)) {
                    return mode;
                }
            }
        }
        return MAXIMUM_REACHED;
    }
}
