package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.GeoPoint;
import io.github.paracosms.calquake.core.MercatorProjection;
import io.github.paracosms.calquake.core.MercatorProjection.ViewportTransform;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.scene.image.Image;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Viewport-only, asynchronous image cache. All methods are called on the FX thread. */
final class StreetViewTiles implements AutoCloseable {
    static final int SOURCE_WIDTH = 2048;
    static final int SOURCE_HEIGHT = 2304;
    // Decoded pixels, not compressed JPEG bytes. Includes previews and pending images.
    static final long MAX_CACHED_PIXELS = 32L * 1024 * 1024;
    private static final int PREVIEW_WIDTH = 512;

    record Key(int row, int col, int width) {
        long pixels() { return (long) width * (width * SOURCE_HEIGHT / SOURCE_WIDTH); }
    }

    record Tile(int row, int col, double x, double y, double width, double height) {}

    private final Map<Key, Entry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Runnable onChange;
    private long cachedPixels;
    private boolean closed;

    StreetViewTiles(Runnable onChange) {
        this.onChange = onChange;
    }

    static List<Tile> visibleTiles(MercatorProjection projection, ViewportTransform transform,
                                   double viewportWidth, double viewportHeight) {
        var nw = projection.project(new GeoPoint(MapCanvasPane.STREET_VIEW_NORTH, MapCanvasPane.STREET_VIEW_WEST));
        var se = projection.project(new GeoPoint(MapCanvasPane.STREET_VIEW_SOUTH, MapCanvasPane.STREET_VIEW_EAST));
        var topLeft = transform.toScreen(nw);
        double width = (se.xKm() - nw.xKm()) * transform.scalePxPerKm() / MapCanvasPane.STREET_VIEW_GRID_COLS;
        double height = (se.yKm() - nw.yKm()) * transform.scalePxPerKm() / MapCanvasPane.STREET_VIEW_GRID_ROWS;
        List<Tile> tiles = new ArrayList<>();
        for (int row = 0; row < MapCanvasPane.STREET_VIEW_GRID_ROWS; row++) {
            for (int col = 0; col < MapCanvasPane.STREET_VIEW_GRID_COLS; col++) {
                double x = topLeft.xPx() + col * width;
                double y = topLeft.yPx() + row * height;
                if (x < viewportWidth && y < viewportHeight && x + width > 0 && y + height > 0) {
                    tiles.add(new Tile(row, col, x, y, width, height));
                }
            }
        }
        return tiles;
    }

    static int requestedWidth(List<Tile> tiles, double displayScale) {
        if (tiles.isEmpty()) return PREVIEW_WIDTH;
        double required = tiles.getFirst().width() * displayScale;
        int width = PREVIEW_WIDTH;
        while (width < SOURCE_WIDTH && width < required) width *= 2;
        // A very large/HiDPI viewport must still fit in the cache without eviction/reload loops.
        while (width > PREVIEW_WIDTH && workingSetPixels(tiles.size(), width) > MAX_CACHED_PIXELS) width /= 2;
        return width;
    }

    static long workingSetPixels(int tileCount, int width) {
        long preview = new Key(0, 0, PREVIEW_WIDTH).pixels();
        return tileCount * (width == PREVIEW_WIDTH ? preview : preview + new Key(0, 0, width).pixels());
    }

    void prepare(List<Tile> tiles, int width) {
        if (closed) return;
        Set<Key> needed = new HashSet<>();
        for (Tile tile : tiles) {
            needed.add(new Key(tile.row(), tile.col(), PREVIEW_WIDTH));
            needed.add(new Key(tile.row(), tile.col(), width));
        }
        // Cancel obsolete queued loads promptly during pan/zoom; retain finished images as fallbacks.
        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var item = iterator.next();
            if (!needed.contains(item.getKey()) && !item.getValue().complete) {
                discard(item.getKey(), item.getValue());
                iterator.remove();
            }
        }
        long additional = needed.stream().filter(key -> !cache.containsKey(key)).mapToLong(Key::pixels).sum();
        iterator = cache.entrySet().iterator();
        while (cachedPixels + additional > MAX_CACHED_PIXELS && iterator.hasNext()) {
            var item = iterator.next();
            if (!needed.contains(item.getKey())) {
                discard(item.getKey(), item.getValue());
                iterator.remove();
            }
        }
        // Request previews first, so a new viewport gets a usable map before detail finishes.
        for (Tile tile : tiles) request(new Key(tile.row(), tile.col(), PREVIEW_WIDTH));
        for (Tile tile : tiles) request(new Key(tile.row(), tile.col(), width));
    }

    Image imageFor(Tile tile, int width) {
        Entry best = null;
        int bestWidth = 0;
        for (int candidate = PREVIEW_WIDTH; candidate <= SOURCE_WIDTH; candidate *= 2) {
            Entry entry = cache.get(new Key(tile.row(), tile.col(), candidate));
            if (entry != null && entry.complete && !entry.image.isError()) {
                // Prefer enough detail; use an available lower level while a sharper one loads.
                if (best == null || (bestWidth < width && candidate > bestWidth)) {
                    best = entry;
                    bestWidth = candidate;
                }
            }
        }
        return best == null ? null : best.image;
    }

    private void request(Key key) {
        if (cache.containsKey(key)) return;
        var resource = StreetViewTiles.class.getResource(String.format(MapCanvasPane.STREET_VIEW_RESOURCE_FORMAT, key.row(), key.col()));
        if (resource == null) return;
        Image image = new Image(resource.toExternalForm(), key.width(), 0, true, true, true);
        Entry entry = new Entry(image);
        cache.put(key, entry);
        cachedPixels += key.pixels();
        image.progressProperty().addListener(entry.listener);
        image.errorProperty().addListener(entry.listener);
        entry.loaded();
    }

    private void discard(Key key, Entry entry) {
        entry.detach();
        entry.image.cancel();
        cachedPixels -= key.pixels();
    }

    private final class Entry {
        final Image image;
        final InvalidationListener listener = ignored -> loaded();
        boolean complete;

        Entry(Image image) { this.image = image; }

        void loaded() {
            if (complete || (image.getProgress() < 1 && !image.isError())) return;
            complete = true;
            detach();
            if (image.isError()) System.err.println("Unable to load Street View tile: " + image.getException());
            Platform.runLater(() -> { if (!closed) onChange.run(); });
        }

        void detach() {
            image.progressProperty().removeListener(listener);
            image.errorProperty().removeListener(listener);
        }
    }

    void cancelPending() {
        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var item = iterator.next();
            if (!item.getValue().complete) {
                discard(item.getKey(), item.getValue());
                iterator.remove();
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        cache.forEach(this::discard);
        cache.clear();
    }
}
