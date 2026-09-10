package io.github.paracosms.calquake.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Controller managing replay state and time progression.
 * <p>
 * Progression rules:
 * <ul>
 *   <li>Owns an injected fakeable monotonic clock; derives elapsed time strictly from clock differences,
 *       never from accumulated frame counts.</li>
 *   <li>Default behavior: starts PAUSED at 0.0 s.</li>
 *   <li>Pause preserves elapsed time; resume continues it.</li>
 *   <li>Restart returns to 0.0 s and pauses.</li>
 *   <li>At the configured replay duration, stops, enters FINISHED state, and requires Restart.</li>
 *   <li>Keeps event origin UTC separate from the monotonic playback clock.</li>
 * </ul>
 */
public final class ReplayController {
    private final Scenario scenario;
    private final ReplayEngine engine;
    private final MonotonicClock clock;
    private final double durationSeconds;

    private PlaybackState state = PlaybackState.PAUSED;
    private double elapsedSeconds = 0.0;
    private long lastClockNanos = 0L;

    public ReplayController(Scenario scenario, ReplayEngine engine, MonotonicClock clock) {
        this(scenario, engine, clock,
                Objects.requireNonNull(engine, "engine cannot be null")
                        .preparedReplay().durationSeconds());
    }

    /**
     * Creates a controller with a scenario-specific replay duration.
     *
     * @param scenario replay scenario
     * @param engine deterministic replay engine
     * @param clock monotonic playback clock
     * @param durationSeconds positive, finite replay duration in seconds
     */
    public ReplayController(
            Scenario scenario,
            ReplayEngine engine,
            MonotonicClock clock,
            double durationSeconds
    ) {
        this.scenario = Objects.requireNonNull(scenario, "scenario cannot be null");
        this.engine = Objects.requireNonNull(engine, "engine cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0.0) {
            throw new IllegalArgumentException(
                    "durationSeconds must be a positive finite number: " + durationSeconds);
        }
        this.durationSeconds = durationSeconds;
    }

    public ReplayController(Scenario scenario, ReplayEngine engine) {
        this(scenario, engine, MonotonicClock.system());
    }

    /**
     * Creates a controller with a scenario-specific replay duration and the system clock.
     */
    public ReplayController(Scenario scenario, ReplayEngine engine, double durationSeconds) {
        this(scenario, engine, MonotonicClock.system(), durationSeconds);
    }

    /**
     * Start or resume playback. If currently FINISHED, requires {@link #restart()} first.
     */
    public void play() {
        if (state == PlaybackState.FINISHED) {
            return;
        }
        if (state == PlaybackState.PAUSED) {
            state = PlaybackState.PLAYING;
            lastClockNanos = clock.nanoTime();
        }
    }

    /**
     * Pause playback. Preserves the current elapsed time.
     */
    public void pause() {
        if (state == PlaybackState.PLAYING) {
            long now = clock.nanoTime();
            double deltaSec = (now - lastClockNanos) / 1_000_000_000.0;
            if (deltaSec > 0.0) {
                elapsedSeconds += deltaSec;
            }
            if (elapsedSeconds >= durationSeconds) {
                elapsedSeconds = durationSeconds;
                state = PlaybackState.FINISHED;
            } else {
                state = PlaybackState.PAUSED;
            }
        }
    }

    /**
     * Toggles between play and pause. No-op if FINISHED.
     */
    public void togglePlayPause() {
        if (state == PlaybackState.PLAYING) {
            pause();
        } else if (state == PlaybackState.PAUSED) {
            play();
        }
    }

    /**
     * Restarts replay to 0.0 seconds and returns to PAUSED state.
     */
    public void restart() {
        elapsedSeconds = 0.0;
        state = PlaybackState.PAUSED;
        lastClockNanos = clock.nanoTime();
    }

    /**
     * Seeks playback to a specific elapsed time in seconds.
     * Preserves paused or playing state, clamping time to the configured replay duration.
     * If previously FINISHED and the target is before the end, transitions to PAUSED.
     *
     * @param targetSeconds target elapsed time in seconds
     */
    public void seek(double targetSeconds) {
        if (Double.isNaN(targetSeconds) || Double.isInfinite(targetSeconds)) {
            throw new IllegalArgumentException("Target seconds must be a finite number: " + targetSeconds);
        }
        if (targetSeconds < 0.0) {
            targetSeconds = 0.0;
        }
        if (targetSeconds > durationSeconds) {
            targetSeconds = durationSeconds;
        }
        this.elapsedSeconds = targetSeconds;
        this.lastClockNanos = clock.nanoTime();

        if (this.elapsedSeconds >= durationSeconds) {
            this.elapsedSeconds = durationSeconds;
            this.state = PlaybackState.FINISHED;
        } else if (this.state == PlaybackState.FINISHED) {
            this.state = PlaybackState.PAUSED;
        }
    }

    /**
     * Advances playback time based on monotonic clock difference since the previous tick.
     * Always returns the current {@link FrameState}.
     *
     * @return current frame state
     */
    public FrameState tick() {
        if (state == PlaybackState.PLAYING) {
            long now = clock.nanoTime();
            double deltaSec = (now - lastClockNanos) / 1_000_000_000.0;
            lastClockNanos = now;

            if (deltaSec > 0.0) {
                elapsedSeconds += deltaSec;
            }

            if (elapsedSeconds >= durationSeconds) {
                elapsedSeconds = durationSeconds;
                state = PlaybackState.FINISHED;
            }
        }
        return currentFrame();
    }

    /**
     * Queries the current frame state without advancing time.
     */
    public FrameState currentFrame() {
        return engine.frameAt(scenario, elapsedSeconds);
    }

    /**
     * Computes the simulated event UTC timestamp at the current elapsed replay time.
     * Derived by adding elapsed duration to origin UTC, keeping origin UTC separate
     * from the monotonic clock.
     */
    public Instant simulatedUtc() {
        long nanos = (long) Math.round(elapsedSeconds * 1_000_000_000.0);
        return scenario.event().originUtc().plus(Duration.ofNanos(nanos));
    }

    public PlaybackState state() {
        return state;
    }

    public double elapsedSeconds() {
        return elapsedSeconds;
    }

    /**
     * Returns the configured replay duration used for seeking, clamping, and completion.
     */
    public double durationSeconds() {
        return durationSeconds;
    }

    public boolean isPaused() {
        return state == PlaybackState.PAUSED;
    }

    public boolean isPlaying() {
        return state == PlaybackState.PLAYING;
    }

    public boolean isFinished() {
        return state == PlaybackState.FINISHED;
    }

    public Scenario scenario() {
        return scenario;
    }

    public ReplayEngine engine() {
        return engine;
    }

    public MonotonicClock clock() {
        return clock;
    }
}
