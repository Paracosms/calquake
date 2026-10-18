package io.github.paracosms.calquake.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Pure offscreen Java2D rendering: no JavaFX objects or scene-graph operations. */
final class FaultRasterizer {
    private static final BasicStroke SOLID = new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke DASHED = new BasicStroke(1.1f, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND, 10, new float[]{4, 3}, 0);
    private static final Color SOLID_COLOR = new Color(54, 166, 166, 166);
    private static final Color DASHED_COLOR = new Color(54, 166, 166, 102);

    private FaultRasterizer() {}

    static BufferedImage render(FaultRenderingService.Request request, List<FaultGeometry.Trace> traces,
                                BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) return null;
        double density = request.displayScale();
        var image = new BufferedImage((int) Math.ceil(request.widthPx() * density),
                (int) Math.ceil(request.heightPx() * density), BufferedImage.TYPE_INT_ARGB_PRE);
        var graphics = image.createGraphics();
        var transform = request.transform();
        double scale = transform.scalePxPerKm(), ox = transform.originScreenXPx(), oy = transform.originScreenYPx();
        double[] screen = new double[512];
        boolean[] keep = new boolean[256];
        int[] stack = new int[512];
        var path = new Path2D.Double();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.scale(density, density);
            // Preserve per-trace transparency and the original dashed-then-solid paint order.
            for (boolean solid : new boolean[]{false, true}) {
                graphics.setStroke(solid ? SOLID : DASHED);
                graphics.setColor(solid ? SOLID_COLOR : DASHED_COLOR);
                for (var trace : traces) {
                    if (cancelled.getAsBoolean()) return null;
                    if (trace.solid() != solid || !trace.intersects(request)) continue;
                    int n = trace.coords().length / 2;
                    if (keep.length < n) {
                        screen = new double[n * 2];
                        keep = new boolean[n];
                        stack = new int[n * 2];
                    }
                    for (int i = 0; i < n; i++) {
                        screen[2 * i] = ox + trace.coords()[2 * i] * scale;
                        screen[2 * i + 1] = oy + trace.coords()[2 * i + 1] * scale;
                    }
                    if (solid && !trace.closed()) {
                        // Half a physical display pixel, including high-DPI monitors.
                        FaultGeometry.simplify(screen, n, 0.5 / density, keep, stack);
                    } else {
                        Arrays.fill(keep, 0, n, true);
                    }
                    path.reset();
                    path.moveTo(screen[0], screen[1]);
                    for (int i = 1; i < n; i++) {
                        if (keep[i]) path.lineTo(screen[2 * i], screen[2 * i + 1]);
                    }
                    graphics.draw(path);
                }
            }
        } finally {
            graphics.dispose();
        }
        return cancelled.getAsBoolean() ? null : image;
    }
}
