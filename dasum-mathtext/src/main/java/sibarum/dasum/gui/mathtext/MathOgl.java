package sibarum.dasum.gui.mathtext;

import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.mathtext.LaidOut.GlyphRun;
import sibarum.dasum.gui.mathtext.LaidOut.Rule;
import sibarum.dasum.gui.vis.math.Vec3;
import sibarum.dasum.gui.vis.scene.Layer;
import sibarum.dasum.gui.vis.scene.LineLayer;
import sibarum.dasum.gui.vis.scene.TextLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenGL backend: the same tree-walk of a {@link LaidOut} as {@link MathSvg}, but into dasum scene
 * {@link Layer}s — a {@link TextLayer} per glyph run (in the {@code math} FontGroup) and a
 * {@link LineLayer} per rule. Positions come straight from the shared layout, so the on-screen render
 * matches the SVG up to scale. The window/scene host places the returned layers.
 */
public final class MathOgl {

    private MathOgl() {}

    /**
     * Turn a laid-out expression into scene layers at {@code pxPerEm}, with its top-left at
     * {@code (originX, originY)} in world coords (y down). {@code color} paints glyphs and rules;
     * {@code constants.fontGroup()} selects the atlas.
     */
    public static List<Layer> toLayers(LaidOut m, MathConstants constants, Color color,
                                       float pxPerEm, float originX, float originY) {
        List<Layer> layers = new ArrayList<>();
        float baseline = originY + (float) (m.ascent() * pxPerEm);
        for (LaidOut.Draw d : m.draws()) {
            switch (d) {
                case GlyphRun g -> layers.add(new TextLayer(
                        g.glyphs(),
                        new Vec3(originX + (float) (g.x() * pxPerEm), baseline + (float) (g.y() * pxPerEm), 0f),
                        (float) (g.size() * pxPerEm), color)
                        .withFontGroup(constants.fontGroup())
                        .withAlign(TextLayer.HAlign.LEFT));
                case Rule r -> {
                    float x1 = originX + (float) (r.x() * pxPerEm);
                    float x2 = x1 + (float) (r.width() * pxPerEm);
                    float yc = baseline + (float) ((r.y() + r.height() / 2) * pxPerEm);
                    float[] seg = {x1, yc, 0f, x2, yc, 0f};
                    layers.add(new LineLayer(seg, rgb(seg.length, color)));
                }
            }
        }
        return layers;
    }

    /** An RGB-per-endpoint colour array matching a {@link LineLayer} endpoints buffer. */
    private static float[] rgb(int len, Color c) {
        float[] cols = new float[len];
        for (int i = 0; i + 2 < len; i += 3) { cols[i] = c.r(); cols[i + 1] = c.g(); cols[i + 2] = c.b(); }
        return cols;
    }
}
