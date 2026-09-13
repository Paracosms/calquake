package io.github.paracosms.calquake.ui;

import javafx.scene.media.AudioClip;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages earthquake sound notifications during Simulation mode replay:
 * <ul>
 *   <li>T + 6.0s: EarthquakeDetected.mp3</li>
 *   <li>T + 9.0s: EarthquakeLight.mp3</li>
 *   <li>T + 14.0s: EarthquakeStrong.mp3 (if MW >= 5.0)</li>
 * </ul>
 */
public class SimulationSoundManager {
    private static final Logger LOGGER = Logger.getLogger(SimulationSoundManager.class.getName());

    public static final double DETECTED_TRIGGER_SECONDS = 6.0;
    public static final double LIGHT_TRIGGER_SECONDS = 9.0;
    public static final double STRONG_TRIGGER_SECONDS = 12.0;
    public static final double STRONG_MIN_MAGNITUDE = 5.0;
    public static final double SEEK_THRESHOLD_SECONDS = 1.5;

    public enum SimulationSound {
        DETECTED("earthquakeDetected.mp3"),
        LIGHT("earthquakeLight.mp3"),
        STRONG("earthquakeStrong.mp3");

        private final String defaultFileName;

        SimulationSound(String defaultFileName) {
            this.defaultFileName = defaultFileName;
        }

        public String defaultFileName() {
            return defaultFileName;
        }
    }

    @FunctionalInterface
    public interface ClipPlayer {
        void play(SimulationSound sound);
    }

    private final ClipPlayer customPlayer;
    private final List<Consumer<SimulationSound>> listeners = new CopyOnWriteArrayList<>();
    private final List<SimulationSound> playedHistory = Collections.synchronizedList(new ArrayList<>());

    private boolean playedDetected = false;
    private boolean playedLight = false;
    private boolean playedStrong = false;
    private double lastElapsedSeconds = 0.0;
    private boolean muted = false;

    // Real audio clips for default player
    private AudioClip detectedClip;
    private AudioClip lightClip;
    private AudioClip strongClip;

    public SimulationSoundManager() {
        this(null);
    }

    public SimulationSoundManager(ClipPlayer customPlayer) {
        this.customPlayer = customPlayer;
        if (customPlayer == null) {
            loadClipsSafely();
        }
    }

    private void loadClipsSafely() {
        this.detectedClip = loadClip(SimulationSound.DETECTED);
        this.lightClip = loadClip(SimulationSound.LIGHT);
        this.strongClip = loadClip(SimulationSound.STRONG);
    }

    private AudioClip loadClip(SimulationSound sound) {
        try {
            URI uri = resolveAudioUri(sound);
            if (uri != null) {
                return new AudioClip(uri.toString());
            } else {
                LOGGER.log(Level.FINE, () -> "Audio file not found for: " + sound.defaultFileName());
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, () -> "Could not initialize AudioClip for " + sound + ": " + t.getMessage());
        }
        return null;
    }

    public static URI resolveAudioUri(SimulationSound sound) {
        String baseName = sound.defaultFileName();
        String pascalName = Character.toUpperCase(baseName.charAt(0)) + baseName.substring(1);

        String[] relativePaths = {
                "sounds/" + baseName,
                "sounds/" + pascalName,
                baseName,
                pascalName
        };

        for (String rel : relativePaths) {
            File file = new File(rel);
            if (file.exists() && file.isFile()) {
                return file.toURI();
            }
        }

        String[] resourcePaths = {
                "/sounds/" + baseName,
                "/sounds/" + pascalName,
                "/" + baseName,
                "/" + pascalName
        };

        for (String res : resourcePaths) {
            URL url = SimulationSoundManager.class.getResource(res);
            if (url != null) {
                try {
                    return url.toURI();
                } catch (Exception ignored) {
                }
            }
        }

        return null;
    }

    /**
     * Called on each animation tick during simulation playback.
     *
     * @param elapsedSeconds current elapsed playback time in seconds
     * @param magnitude magnitude (MW) of the current simulation scenario
     * @param isPlaying whether playback is actively progressing
     */
    public void onPlaybackTick(double elapsedSeconds, double magnitude, boolean isPlaying) {
        if (!isPlaying) {
            if (elapsedSeconds < lastElapsedSeconds) {
                updateArmingState(elapsedSeconds);
            }
            lastElapsedSeconds = elapsedSeconds;
            return;
        }

        if (elapsedSeconds < lastElapsedSeconds) {
            // Time moved backward; update arming state and do not play sounds
            updateArmingState(elapsedSeconds);
            lastElapsedSeconds = elapsedSeconds;
            return;
        }

        if (lastElapsedSeconds < DETECTED_TRIGGER_SECONDS && elapsedSeconds >= DETECTED_TRIGGER_SECONDS && !playedDetected) {
            playedDetected = true;
            triggerSound(SimulationSound.DETECTED);
        }

        if (lastElapsedSeconds < LIGHT_TRIGGER_SECONDS && elapsedSeconds >= LIGHT_TRIGGER_SECONDS && !playedLight) {
            playedLight = true;
            triggerSound(SimulationSound.LIGHT);
        }

        if (lastElapsedSeconds < STRONG_TRIGGER_SECONDS && elapsedSeconds >= STRONG_TRIGGER_SECONDS && !playedStrong) {
            playedStrong = true;
            if (magnitude >= STRONG_MIN_MAGNITUDE) {
                triggerSound(SimulationSound.STRONG);
            }
        }

        lastElapsedSeconds = elapsedSeconds;
    }

    /**
     * Handles seeking to a target time.
     *
     * @param targetSeconds destination elapsed seconds
     */
    public void onSeek(double targetSeconds) {
        updateArmingState(targetSeconds);
        lastElapsedSeconds = targetSeconds;
    }

    /**
     * Updates arming flags so triggers before {@code elapsedSeconds} are disarmed,
     * and triggers after {@code elapsedSeconds} are re-armed.
     */
    public void updateArmingState(double elapsedSeconds) {
        playedDetected = elapsedSeconds >= DETECTED_TRIGGER_SECONDS;
        playedLight = elapsedSeconds >= LIGHT_TRIGGER_SECONDS;
        playedStrong = elapsedSeconds >= STRONG_TRIGGER_SECONDS;
    }

    /**
     * Resets sound manager state to T = 0 and stops any playing audio.
     */
    public void reset() {
        stop();
        playedDetected = false;
        playedLight = false;
        playedStrong = false;
        lastElapsedSeconds = 0.0;
        playedHistory.clear();
    }

    /**
     * Stops any currently playing audio clips.
     */
    public void stop() {
        try {
            if (detectedClip != null && detectedClip.isPlaying()) {
                detectedClip.stop();
            }
            if (lightClip != null && lightClip.isPlaying()) {
                lightClip.stop();
            }
            if (strongClip != null && strongClip.isPlaying()) {
                strongClip.stop();
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Error stopping audio clips", t);
        }
    }

    private void triggerSound(SimulationSound sound) {
        playedHistory.add(sound);

        if (!muted) {
            if (customPlayer != null) {
                try {
                    customPlayer.play(sound);
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "Custom clip player error for " + sound, t);
                }
            } else {
                playDefaultClip(sound);
            }
        }

        for (Consumer<SimulationSound> listener : listeners) {
            try {
                listener.accept(sound);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Sound listener error for " + sound, t);
            }
        }
    }

    private void playDefaultClip(SimulationSound sound) {
        try {
            AudioClip clip = switch (sound) {
                case DETECTED -> detectedClip;
                case LIGHT -> lightClip;
                case STRONG -> strongClip;
            };
            if (clip != null) {
                clip.play();
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, () -> "Error playing sound " + sound + ": " + t.getMessage());
        }
    }

    public void addSoundListener(Consumer<SimulationSound> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
    }

    public void removeSoundListener(Consumer<SimulationSound> listener) {
        listeners.remove(listener);
    }

    public List<SimulationSound> getPlayedHistory() {
        return Collections.unmodifiableList(new ArrayList<>(playedHistory));
    }

    public boolean isPlayedDetected() {
        return playedDetected;
    }

    public boolean isPlayedLight() {
        return playedLight;
    }

    public boolean isPlayedStrong() {
        return playedStrong;
    }

    public double getLastElapsedSeconds() {
        return lastElapsedSeconds;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }
}
