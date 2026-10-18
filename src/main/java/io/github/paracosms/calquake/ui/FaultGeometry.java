package io.github.paracosms.calquake.ui;

import io.github.paracosms.calquake.core.MercatorProjection;
import io.github.paracosms.calquake.data.CaliforniaFaultCatalog;

import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;

final class FaultGeometry {
    private FaultGeometry() {}

    // Coordinates are privately prepared and thereafter read-only; no UI code touches these arrays.
    record Trace(double[] coords, boolean solid, boolean closed,
                 double minX, double minY, double maxX, double maxY) {
        static Trace of(double[] coords, boolean solid) {
            if (coords.length < 4 || coords.length % 2 != 0) throw new IllegalArgumentException("Invalid trace");
            double minX = coords[0], maxX = coords[0], minY = coords[1], maxY = coords[1];
            for (int i = 2; i < coords.length; i += 2) {
                minX = Math.min(minX, coords[i]);
                maxX = Math.max(maxX, coords[i]);
                minY = Math.min(minY, coords[i + 1]);
                maxY = Math.max(maxY, coords[i + 1]);
            }
            boolean closed = coords.length >= 6 && coords[0] == coords[coords.length - 2]
                    && coords[1] == coords[coords.length - 1];
            return new Trace(coords, solid, closed, minX, minY, maxX, maxY);
        }

        boolean intersects(FaultRenderingService.Request request) {
            var t = request.transform();
            double s = t.scalePxPerKm(), x = t.originScreenXPx(), y = t.originScreenYPx();
            // Include antialiasing and round stroke caps outside the nominal viewport.
            return x + maxX * s >= -5 && x + minX * s <= request.widthPx() + 5
                    && y + maxY * s >= -5 && y + minY * s <= request.heightPx() + 5;
        }
    }

    static List<Trace> load() {
        var projection = MercatorProjection.californiaDefault();
        var traces = new ArrayList<Trace>();
        for (var fault : CaliforniaFaultCatalog.loadDefault().faults()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            for (var line : fault.polylines()) {
                double[] coords = new double[line.size() * 2];
                for (int i = 0; i < line.size(); i++) {
                    var p = projection.project(line.get(i));
                    coords[2 * i] = p.xKm();
                    coords[2 * i + 1] = p.yKm();
                }
                traces.add(Trace.of(coords, fault.isWellConstrained()));
            }
        }
        return List.copyOf(traces);
    }

    /** Iterative Douglas–Peucker; distance is to the finite segment, including backtracking bends. */
    static int simplify(double[] points, int n, double tolerance, boolean[] keep, int[] stack) {
        Arrays.fill(keep, 0, n, false);
        keep[0] = keep[n - 1] = true;
        int top = 0;
        stack[top++] = 0;
        stack[top++] = n - 1;
        while (top > 0) {
            int end = stack[--top], start = stack[--top], furthest = -1;
            double maxDistance = tolerance * tolerance;
            for (int i = start + 1; i < end; i++) {
                double distance = Line2D.ptSegDistSq(points[2 * start], points[2 * start + 1],
                        points[2 * end], points[2 * end + 1], points[2 * i], points[2 * i + 1]);
                if (distance > maxDistance) {
                    maxDistance = distance;
                    furthest = i;
                }
            }
            if (furthest >= 0) {
                keep[furthest] = true;
                stack[top++] = start;
                stack[top++] = furthest;
                stack[top++] = furthest;
                stack[top++] = end;
            }
        }
        int retained = 0;
        for (int i = 0; i < n; i++) if (keep[i]) retained++;
        return retained;
    }
}
