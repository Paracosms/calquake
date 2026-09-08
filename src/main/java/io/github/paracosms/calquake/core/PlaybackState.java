package io.github.paracosms.calquake.core;

/**
 * Playback lifecycle states for the earthquake replay controller.
 */
public enum PlaybackState {
    /**
     * Playback is paused; elapsed time is preserved. Initial state on startup and after restart.
     */
    PAUSED,

    /**
     * Playback is actively progressing driven by monotonic clock differences.
     */
    PLAYING,

    /**
     * Playback has reached the maximum duration (120 s) and is halted. Requires restart to resume.
     */
    FINISHED
}
