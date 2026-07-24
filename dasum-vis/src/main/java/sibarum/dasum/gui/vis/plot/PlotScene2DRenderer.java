package sibarum.dasum.gui.vis.plot;

import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.vis.math.Vec3;
import sibarum.dasum.gui.vis.scene.BlendMode;
import sibarum.dasum.gui.vis.scene.Layer;
import sibarum.dasum.gui.vis.scene.LineLayer;
import sibarum.dasum.gui.vis.scene.TextLayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders a {@link PlotScene2D} to OpenGL {@link Layer}s — the on-screen sibling of
 * {@link SvgPlotWriter}. Both consume the same semantic IR, so the meaning of each annotation (a
 * marker glyph per feature, a half-opacity line per asymptote, overlap-thinned labels) is defined
 * ONCE here rather than duplicated per backend.
 *
 * <p>Curves + chrome route through the existing {@link LinePlot#build}, so drawn curves look exactly
 * as they always have. The reliable enclosure band is a web-facing affordance (hidden by default in
 * SVG) and is intentionally NOT drawn on screen.
 */
public final class PlotScene2DRenderer {

    private PlotScene2DRenderer() {}

    private static final Color MARK_COLOR = new Color(0.98f, 0.85f, 0.30f, 1f);   // amber markers
    private static final Color LABEL_COLOR = new Color(0.95f, 0.95f, 0.98f, 1f);
    private static final Color ASYM_COLOR = new Color(0.95f, 0.45f, 0.45f, 1f);   // reddish, half-opacity

    /** Marker half-size, world units. */
    private static final float GLYPH = 0.06f;
    /** Label text height, world units. */
    private static final float LABEL_HEIGHT = 0.24f;
    /** Dark corona around a label (screen px) — legible over curve/grid without a solid plate. */
    private static final float LABEL_OUTLINE_PX = 2.2f;
    private static final Color LABEL_OUTLINE = new Color(0.03f, 0.04f, 0.06f, 0.9f);
    /** A glyph is taller than wide: label width ≈ chars · height · aspect (overlap estimate). */
    private static final float LABEL_CHAR_ASPECT = 0.55f;
    /** A small world-space breathing gap kept between two labels that would otherwise touch. */
    private static final float LABEL_MIN_GAP = 0.08f;

    /**
     * Build the layer list: chrome + curves ({@link LinePlot#build}), then a {@code +} glyph and a
     * thinned label per feature, and a half-opacity vertical line and thinned x-label per asymptote.
     * Labels are dropped when they would overlap an already-placed one — a dense cluster (tan(1/x)
     * near 0) keeps every line/glyph but only readable labels.
     */
    public static List<Layer> toLayers(PlotScene2D scene, PlotStyle style) {
        PlotFrame frame = scene.frame();
        List<Layer> layers = new ArrayList<>(LinePlot.build(frame, scene.curves(), style));
        LabelPlacer placed = new LabelPlacer();

        for (PlotScene2D.Feature f : scene.features()) {
            Vec3 a = frame.toWorld(f.x(), f.y());
            Color mark = f.color() != null ? f.color() : MARK_COLOR;   // colour-code to the owning plot
            float[] seg = {a.x() - GLYPH, a.y(), 0f, a.x() + GLYPH, a.y(), 0f,
                           a.x(), a.y() - GLYPH, 0f, a.x(), a.y() + GLYPH, 0f};
            layers.add(new LineLayer(seg, filledColor(seg.length, mark)));
            float lx = a.x() + GLYPH * 1.5f, ly = a.y() + GLYPH * 1.5f;
            Color labelCol = f.color() != null ? f.color() : LABEL_COLOR;
            if (placed.tryPlace(f.label(), lx, ly)) layers.add(label(f.label(), lx, ly, labelCol));
        }

        // Left-to-right, so a cluster's surviving labels are a spread-out subset (greedy by x).
        List<PlotScene2D.Asymptote> asy = new ArrayList<>(scene.asymptotes());
        asy.sort(Comparator.comparingDouble(PlotScene2D.Asymptote::x));
        for (PlotScene2D.Asymptote a : asy) {
            Color asymCol = a.color() != null ? a.color() : ASYM_COLOR;   // tint to the owning plot
            float wx = frame.worldX(a.x());
            float[] seg = {wx, frame.wy0(), 0f, wx, frame.wy1(), 0f};
            // Keep the half-opacity blend so a tinted asymptote still reads as an asymptote, not a curve.
            layers.add(new LineLayer(seg, filledColor(seg.length, asymCol))
                    .withBlend(BlendMode.ALPHA).withOpacity(0.5f));
            float lx = wx + GLYPH, ly = frame.wy1() - LABEL_HEIGHT;
            if (placed.tryPlace(a.label(), lx, ly)) layers.add(label(a.label(), lx, ly, asymCol));
        }
        return layers;
    }

    /** A left-aligned MSDF label with a dark outline/corona for legibility. */
    private static TextLayer label(String text, float wx, float wy, Color color) {
        return new TextLayer(text, new Vec3(wx, wy, 0f), LABEL_HEIGHT, color)
                .withAlign(TextLayer.HAlign.LEFT)
                .withOutline(LABEL_OUTLINE, LABEL_OUTLINE_PX);
    }

    /** An RGB-per-endpoint colour array matching a {@link LineLayer} endpoints buffer. */
    private static float[] filledColor(int len, Color c) {
        float[] cols = new float[len];
        for (int i = 0; i + 2 < len; i += 3) { cols[i] = c.r(); cols[i + 1] = c.g(); cols[i + 2] = c.b(); }
        return cols;
    }

    private static float labelWidth(String text) {
        return text.length() * LABEL_HEIGHT * LABEL_CHAR_ASPECT;
    }

    /**
     * Greedy overlap-avoiding label placement (world-space box test). An accepted label reserves its
     * box; a later candidate intersecting any reserved box is rejected — its feature still shows a
     * line/glyph, just no text. Keeps a dense cluster's labels readable instead of a smear.
     */
    private static final class LabelPlacer {
        private final List<float[]> boxes = new ArrayList<>();   // {x0, y0, x1, y1} world units

        boolean tryPlace(String text, float x, float y) {
            float x1 = x + labelWidth(text) + LABEL_MIN_GAP, y1 = y + LABEL_HEIGHT;
            for (float[] b : boxes) {
                if (x < b[2] && x1 > b[0] && y < b[3] && y1 > b[1]) return false;
            }
            boxes.add(new float[]{x, y, x1, y1});
            return true;
        }
    }
}
