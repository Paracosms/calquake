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
 *   <li>At 120.0 seconds, stops, enters FINISHED state, and requires Restart.</li>
 *   <li>Keeps event origin UTC separate from the monotonic playback clock.</li>
 * </ul>
 */
public final class ReplayController {

    public static final double MAX_REPLAY_SECONDS = 120.0;

    private final Scenario scenario;
    private final ReplayEngine engine;
    private final MonotonicClock clock;

    private PlaybackState state = PlaybackState.PAUSED;
    private double elapsedSeconds = 0.0;
    private long lastClockNanos = 0L;

    public ReplayController(Scenario scenario, ReplayEngine engine, MonotonicClock clock) {
        this.scenario = Objects.requireNonNull(scenario, "scenario cannot be null");
        this.engine = Objects.requireNonNull(engine, "engine cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public ReplayController(Scenario scenario, ReplayEngine engine) {
        this(scenario, engine, MonotonicClock.system());
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
            if (elapsedSeconds >= MAX_REPLAY_SECONDS) {
                elapsedSeconds = MAX_REPLAY_SECONDS;
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

            if (elapsedSeconds >= MAX_REPLAY_SECONDS) {
                elapsedSeconds = MAX_REPLAY_SECONDS;
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
