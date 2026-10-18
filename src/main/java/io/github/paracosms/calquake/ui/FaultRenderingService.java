package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.MercatorProjection.ViewportTransform;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** UI-thread-owned coordinator. Only geometry preparation and rasterization run on the worker. */
final class FaultRenderingService implements AutoCloseable {
    enum State { IDLE, LOADING, READY, ERROR }

    // Style is fixed for the lifetime of the service; these are all variable cache inputs.
    record Request(ViewportTransform transform, double widthPx, double heightPx, double displayScale) {
        Request {
            Objects.requireNonNull(transform);
            if (!positive(widthPx) || !positive(heightPx) || !positive(displayScale)
                    || !positive(transform.scalePxPerKm())
                    || !Double.isFinite(transform.originScreenXPx())
                    || !Double.isFinite(transform.originScreenYPx())
                    || Math.ceil(widthPx * displayScale) * Math.ceil(heightPx * displayScale) > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid fault viewport dimensions or transform");
            }
        }

        private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }
    }

    record Raster(Request request, Image image) {}

    @FunctionalInterface
    interface Renderer {
        BufferedImage render(Request request, List<FaultGeometry.Trace> traces, BooleanSupplier cancelled);
    }

    private final ExecutorService worker;
    private final Consumer<Runnable> dispatch;
    private final Supplier<List<FaultGeometry.Trace>> loader;
    private final Renderer renderer;
    private Runnable onChange = () -> {};
    private volatile long generation;
    private volatile boolean disposed;
    private List<FaultGeometry.Trace> geometry; // Worker-owned; retained across visibility changes.
    private Request requested;
    private Raster currentRaster;
    private State state = State.IDLE;
    private RuntimeException lastError;
    private boolean busy;

    FaultRenderingService() {
        this(Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "fault-render-worker");
            thread.setDaemon(true);
            return thread;
        }), Platform::runLater, FaultGeometry::load, FaultRasterizer::render);
    }

    // The service owns the supplied worker, including shutdown. Injection keeps tests deterministic.
    FaultRenderingService(ExecutorService worker, Consumer<Runnable> dispatch,
                          Supplier<List<FaultGeometry.Trace>> loader, Renderer renderer) {
        this.worker = worker;
        this.dispatch = dispatch;
        this.loader = loader;
        this.renderer = renderer;
    }

    void setOnChange(Runnable listener) { onChange = Objects.requireNonNull(listener); }
    State state() { return state; }
    RuntimeException lastError() { return lastError; }
    Raster currentRaster() { return currentRaster; }

    String statusMessage() {
        return switch (state) {
            case LOADING -> currentRaster == null ? "Loading faults…" : "";
            case ERROR -> "Faults unavailable — click to retry";
            default -> "";
        };
    }

    void requestRender(Request request) {
        if (disposed || request.equals(requested)) return;
        requested = request;
        generation++;
        lastError = null;
        state = matchesCache() ? State.READY : State.LOADING;
        if (!busy && state == State.LOADING) start();
        onChange.run();
    }

    void retry() {
        if (disposed || state != State.ERROR || requested == null) return;
        generation++;
        lastError = null;
        state = State.LOADING;
        if (!busy) start();
        onChange.run();
    }

    void cancelActiveRender() {
        if (disposed) return;
        generation++;
        requested = null;
        state = State.IDLE;
        lastError = null;
        onChange.run();
    }

    private boolean matchesCache() {
        return currentRaster != null && currentRaster.request().equals(requested);
    }

    private void start() {
        busy = true;
        long ticket = generation;
        Request request = requested;
        worker.execute(() -> {
            BufferedImage pixels = null;
            RuntimeException failure = null;
            try {
                // A hidden layer may finish preparing reusable geometry, but never publishes a raster.
                if (!disposed && geometry == null) geometry = List.copyOf(loader.get());
                if (!disposed && ticket == generation) {
                    pixels = renderer.render(request, geometry,
                            () -> disposed || ticket != generation || Thread.currentThread().isInterrupted());
                }
            } catch (RuntimeException e) {
                failure = e;
            } finally {
                if (disposed) geometry = null;
            }
            BufferedImage result = pixels;
            RuntimeException error = failure;
            // Wait for UI consumption before starting another job: at most one queued pixel buffer.
            if (!disposed) dispatch.accept(() -> finish(ticket, request, result, error));
        });
    }

    private void finish(long ticket, Request request, BufferedImage pixels, RuntimeException failure) {
        busy = false;
        if (disposed) {
            geometry = null;
            return;
        }
        if (ticket == generation && requested != null) {
            try {
                if (failure != null) throw failure;
                if (pixels == null) throw new IllegalStateException("Fault renderer returned no image");
                WritableImage image = new WritableImage(pixels.getWidth(), pixels.getHeight());
                image.getPixelWriter().setPixels(0, 0, pixels.getWidth(), pixels.getHeight(),
                        PixelFormat.getIntArgbPreInstance(),
                        ((DataBufferInt) pixels.getRaster().getDataBuffer()).getData(), 0, pixels.getWidth());
                currentRaster = new Raster(request, image);
                state = State.READY;
            } catch (RuntimeException e) {
                lastError = e;
                state = State.ERROR;
            }
        }
        if (requested != null && !matchesCache() && state != State.ERROR) start();
        onChange.run();
    }

    @Override
    public void close() {
        disposed = true;
        generation++;
        requested = null;
        currentRaster = null;
        onChange = () -> {};
        worker.shutdownNow();
        if (!busy) geometry = null;
    }
}
