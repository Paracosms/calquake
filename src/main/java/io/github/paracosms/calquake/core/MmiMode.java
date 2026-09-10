package io.github.paracosms.calquake.core;

/** The two permanent replay intensity modes exposed by CalQuake. */
public enum MmiMode {
    RECORDED("Recorded"),
    SIMULATED("Simulated");

    private final String displayName;

    MmiMode(String displayName) {
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
