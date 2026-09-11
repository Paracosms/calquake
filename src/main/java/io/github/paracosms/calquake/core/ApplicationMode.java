package io.github.paracosms.calquake.core;

/**
 * The top-level application modes for CalQuake.
 */
public enum ApplicationMode {
    SIMULATION("Simulation"),
    REPLAY("Replay");

    private final String displayName;

    ApplicationMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
