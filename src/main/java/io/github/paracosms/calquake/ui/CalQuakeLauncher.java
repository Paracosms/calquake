package io.github.paracosms.calquake.ui;

/**
 * Standard launcher entry point for CalQuake.
 * <p>
 * Does not extend {@link javafx.application.Application}, allowing IDEs and launchers
 * to bootstrap JavaFX without requiring explicit {@code --module-path} VM options.
 */
public final class CalQuakeLauncher {

    private CalQuakeLauncher() {}

    public static void main(String[] args) {
        CalQuakeApp.main(args);
    }
}
