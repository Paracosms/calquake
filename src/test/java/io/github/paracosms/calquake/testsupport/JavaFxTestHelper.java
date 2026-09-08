package io.github.paracosms.calquake.testsupport;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;

public final class JavaFxTestHelper {

    private static volatile boolean initialized = false;

    private JavaFxTestHelper() {}

    public static synchronized void initToolkit() {
        if (!initialized) {
            try {
                Platform.startup(() -> {});
                Platform.setImplicitExit(false);
                initialized = true;
            } catch (IllegalStateException e) {
                Platform.setImplicitExit(false);
                initialized = true;
            }
        }
    }

    public static void runOnFxThread(Runnable action) throws Exception {
        initToolkit();
        CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] error = new Throwable[1];

        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error[0] = t;
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        if (error[0] != null) {
            if (error[0] instanceof Exception e) {
                throw e;
            }
            throw new RuntimeException(error[0]);
        }
    }
}
