package sibarum.dasum.gui.vis.plot;

import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.vis.math.Vec3;
import sibarum.dasum.gui.vis.scene.Layer;
import sibarum.dasum.gui.vis.scene.LineLayer;
import sibarum.dasum.gui.vis.scene.TextLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The world-space rectangle a plot occupies plus the two {@link Axis}es that
 * map data into it. This is the single source of the data→world transform:
 * series, fields and tick labels all route through {@link #toWorld} so they
 * stay registered to the same frame.
 *
 * <p>The frame lives in the world XY plane at {@code z = 0}. {@code (wx0, wy0)}
 * is the lower-left corner, {@code (wx1, wy1)} the upper-right; the x axis
 * maps across {@code [wx0, wx1]} and the y axis up {@code [wy0, wy1]}.
 *
 * @param wx0 world x of the left edge
 * @param wy0 world y of the bottom edge
 * @param wx1 world x of the right edge
 * @param wy1 world y of the top edge
 * @param x   horizontal data axis
 * @param y   vertical data axis
 */
public record PlotFrame(float wx0, float wy0, float wx1, float wy1, Axis x, Axis y) {

    public PlotFrame {
        if (!(wx1 > wx0)) throw new IllegalArgumentException("wx1 must be > wx0");
        if (!(wy1 > wy0)) throw new IllegalArgumentException("wy1 must be > wy0");
        if (x == null || y == null) throw new IllegalArgumentException("axes != null");
    }

    public float worldX(double dataX) { return (float) x.dataToWorld(dataX, wx0, wx1); }
    public float worldY(double dataY) { return (float) y.dataToWorld(dataY, wy0, wy1); }

    /** Map a data point to its world position in the frame's XY plane. */
    public Vec3 toWorld(double dataX, double dataY) {
        return new Vec3(worldX(dataX), worldY(dataY), 0f);
    }

    /** Lower-left world corner (z = 0) — for camera framing. */
    public Vec3 worldMin() { return new Vec3(wx0, wy0, 0f); }
    /** Upper-right world corner (z = 0) — for camera framing. */
    public Vec3 worldMax() { return new Vec3(wx1, wy1, 0f); }

    /**
     * Build the frame chrome — border, optional gridlines, and tick labels —
     * in painter's order (grid, then border, then labels). Layers reuse the
     * standard primitives so they batch with everything else in the scene.
     */
    public List<Layer> chrome(PlotStyle style) {
        Ticks.TickSet xt = Ticks.forAxis(x, style.targetXTicks());
        Ticks.TickSet yt = Ticks.forAxis(y, style.targetYTicks());

        List<Layer> layers = new ArrayList<>(4);

        // Gridlines (interior, dim) — one LineLayer holds every segment.
        if (style.grid()) {
            List<Float> seg = new ArrayList<>();
            for (double v : xt.values()) {
                float wx = worldX(v);
                addSegment(seg, wx, wy0, wx, wy1);
            }
            for (double v : yt.values()) {
                float wy = worldY(v);
                addSegment(seg, wx0, wy, wx1, wy);
            }
            if (!seg.isEmpty()) layers.add(new LineLayer(toArray(seg), uniform(style.gridColor(), seg.size())));
        }

        // Border rectangle (axis colour).
        List<Float> border = new ArrayList<>();
        addSegment(border, wx0, wy0, wx1, wy0);
        addSegment(border, wx1, wy0, wx1, wy1);
        addSegment(border, wx1, wy1, wx0, wy1);
        addSegment(border, wx0, wy1, wx0, wy0);
        layers.add(new LineLayer(toArray(border), uniform(style.axisColor(), border.size())));

        // Tick labels — one TextLayer per label.
        float h = style.labelHeightWorld();
        float gap = h * 0.5f;
        for (int i = 0; i < xt.count(); i++) {
            float wx = worldX(xt.values()[i]);
            layers.add(new TextLayer(xt.labels()[i],
                new Vec3(wx, wy0 - gap - h, 0f), h, style.labelColor())
                .withAlign(TextLayer.HAlign.CENTER));
        }
        for (int i = 0; i < yt.count(); i++) {
            float wy = worldY(yt.values()[i]);
            layers.add(new TextLayer(yt.labels()[i],
                new Vec3(wx0 - gap, wy - h * 0.35f, 0f), h, style.labelColor())
                .withAlign(TextLayer.HAlign.RIGHT));
        }
        return layers;
    }

    private static void addSegment(List<Float> out, float x0, float y0, float x1, float y1) {
        out.add(x0); out.add(y0); out.add(0f);
        out.add(x1); out.add(y1); out.add(0f);
    }

    private static float[] toArray(List<Float> in) {
        float[] a = new float[in.size()];
        for (int i = 0; i < a.length; i++) a[i] = in.get(i);
        return a;
    }

    /** RGB triple repeated for every vertex (LineLayer wants RGB per endpoint). */
    private static float[] uniform(Color c, int floatCount) {
        float[] cols = new float[floatCount];
        for (int i = 0; i < floatCount; i += 3) {
            cols[i] = c.r(); cols[i + 1] = c.g(); cols[i + 2] = c.b();
        }
        return cols;
    }
}
