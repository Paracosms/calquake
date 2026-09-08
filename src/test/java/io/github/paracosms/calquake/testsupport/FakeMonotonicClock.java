package io.github.paracosms.calquake.testsupport;

import io.github.paracosms.calquake.core.MonotonicClock;

/**
 * Controllable monotonic clock for deterministic testing of replay timing,
 * pause/resume, and boundary conditions without thread sleeps.
 */
public final class FakeMonotonicClock implements MonotonicClock {

    private long currentNanos;

    public FakeMonotonicClock() {
        this(0L);
    }

    public FakeMonotonicClock(long initialNanos) {
        this.currentNanos = initialNanos;
    }

    @Override
    public long nanoTime() {
        return currentNanos;
    }

    public void advanceNanos(long nanos) {
        if (nanos < 0) {
            throw new IllegalArgumentException("Monotonic time cannot move backwards: " + nanos);
        }
        this.currentNanos += nanos;
    }

    public void advanceMillis(long millis) {
        advanceNanos(millis * 1_000_000L);
    }

    public void advanceSeconds(double seconds) {
        advanceNanos((long) Math.round(seconds * 1_000_000_000.0));
    }

    public void setNanos(long nanos) {
        this.currentNanos = nanos;
    }
}
