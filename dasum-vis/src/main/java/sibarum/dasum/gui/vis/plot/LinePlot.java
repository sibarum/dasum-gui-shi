package sibarum.dasum.gui.vis.plot;

import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.vis.scene.Layer;
import sibarum.dasum.gui.vis.scene.LineLayer;
import sibarum.dasum.gui.vis.scene.TriangleLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a 2D line/curve chart as a stack of scene {@link Layer}s: frame
 * chrome (border, grid, tick labels) under one layer per series. Straight
 * series become a crisp 1px {@link LineLayer}; smoothed series are resampled
 * via {@link CurveResample} first; thick series ({@code thicknessWorld > 0})
 * are tessellated into a {@link TriangleLayer} ribbon.
 *
 * <p>Stateless and pure — the returned list is ready to wrap in a
 * {@code SceneSnapshot} and publish from any thread.
 */
public final class LinePlot {

    private LinePlot() {}

    /** Curve smoothness: segments emitted between adjacent control points. */
    public static final int CURVE_SEGMENTS_PER_SPAN = 16;

    /**
     * A linear frame fitted to the combined data extent of {@code series}
     * (with 5% padding), placed on the world rectangle
     * {@code [wx0, wx1] × [wy0, wy1]}.
     */
    public static PlotFrame autoFrame(float wx0, float wy0, float wx1, float wy1, List<Series> series) {
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Series s : series) {
            for (double v : s.xs()) { minX = Math.min(minX, v); maxX = Math.max(maxX, v); }
            for (double v : s.ys()) { minY = Math.min(minY, v); maxY = Math.max(maxY, v); }
        }
        if (minX > maxX) { minX = 0; maxX = 1; minY = 0; maxY = 1; } // empty guard
        return new PlotFrame(wx0, wy0, wx1, wy1,
            Axis.autoRange(minX, maxX, 0.05), Axis.autoRange(minY, maxY, 0.05));
    }

    /** Frame chrome plus one layer per series, in draw order. */
    public static List<Layer> build(PlotFrame frame, List<Series> series, PlotStyle style) {
        List<Layer> layers = new ArrayList<>(frame.chrome(style));
        for (Series s : series) layers.add(seriesLayer(frame, s));
        return layers;
    }

    private static Layer seriesLayer(PlotFrame frame, Series s) {
        double[] xs = s.xs(), ys = s.ys();
        if (s.style() == Series.Style.CURVE) {
            CurveResample.Polyline p = CurveResample.catmullRom(xs, ys, CURVE_SEGMENTS_PER_SPAN);
            xs = p.xs(); ys = p.ys();
        }
        // Project to world (z = 0) once.
        int n = xs.length;
        float[] wx = new float[n], wy = new float[n];
        for (int i = 0; i < n; i++) { wx[i] = frame.worldX(xs[i]); wy[i] = frame.worldY(ys[i]); }

        return s.thicknessWorld() > 0f
            ? ribbon(wx, wy, s.thicknessWorld(), s.color())
            : polyline(wx, wy, s.color());
    }

    /** Thin 1px polyline: consecutive world points become line segments. */
    private static LineLayer polyline(float[] wx, float[] wy, Color c) {
        int segs = wx.length - 1;
        float[] ep = new float[segs * 6];
        float[] col = new float[segs * 6];
        for (int i = 0; i < segs; i++) {
            int o = i * 6;
            ep[o]     = wx[i];     ep[o + 1] = wy[i];     ep[o + 2] = 0f;
            ep[o + 3] = wx[i + 1]; ep[o + 4] = wy[i + 1]; ep[o + 5] = 0f;
            putRgb(col, o, c); putRgb(col, o + 3, c);
        }
        return new LineLayer(ep, col);
    }

    /**
     * Thick polyline tessellated into a quad ribbon (two triangles per
     * segment, square-cut joints). Each segment is offset by half the stroke
     * width along its 2D normal in the XY plane.
     */
    private static TriangleLayer ribbon(float[] wx, float[] wy, float thickness, Color c) {
        int segs = wx.length - 1;
        float half = thickness * 0.5f;
        float[] v = new float[segs * 18];   // 2 triangles * 3 verts * 3 floats
        float[] col = new float[segs * 18];
        for (int i = 0; i < segs; i++) {
            float ax = wx[i],     ay = wy[i];
            float bx = wx[i + 1], by = wy[i + 1];
            float dx = bx - ax, dy = by - ay;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            float nx, ny;
            if (len > 1e-6f) { nx = -dy / len * half; ny = dx / len * half; }
            else { nx = 0f; ny = half; }

            // Quad corners: a+n, a-n, b-n, b+n  → triangles (0,1,2) and (0,2,3).
            float[] qx = { ax + nx, ax - nx, bx - nx, bx + nx };
            float[] qy = { ay + ny, ay - ny, by - ny, by + ny };
            int[] order = { 0, 1, 2, 0, 2, 3 };
            int o = i * 18;
            for (int k = 0; k < 6; k++) {
                int idx = order[k];
                v[o + k * 3]     = qx[idx];
                v[o + k * 3 + 1] = qy[idx];
                v[o + k * 3 + 2] = 0f;
                putRgb(col, o + k * 3, c);
            }
        }
        return new TriangleLayer(v, col);
    }

    private static void putRgb(float[] arr, int off, Color c) {
        arr[off] = c.r(); arr[off + 1] = c.g(); arr[off + 2] = c.b();
    }
}
