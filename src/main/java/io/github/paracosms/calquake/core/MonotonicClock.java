package io.github.paracosms.calquake.core;

/**
 * Monotonic nanosecond time provider for driving replay progression.
 * Allows deterministic clock injection and testing without real-time sleeps.
 */
@FunctionalInterface
public interface MonotonicClock {

    /**
     * Returns the current value of the running Java Virtual Machine's high-resolution
     * time source, in nanoseconds.
     *
     * @return current monotonic time in nanoseconds
     */
    long nanoTime();

    /**
     * Standard system monotonic clock backed by {@link System#nanoTime()}.
     */
    static MonotonicClock system() {
        return System::nanoTime;
    }
}
