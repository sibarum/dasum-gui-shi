package sibarum.dasum.gui.core.text;

import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.render.DrawCommand;
import sibarum.dasum.gui.core.render.Rect;

/**
 * Bridges {@link AtlasData} (font metrics in em units, atlas bounds in atlas
 * pixels) to {@link DrawCommand.GlyphQuad} in screen pixels. Holds no state
 * beyond the atlas reference; one instance per atlas suffices.
 */
public final class GlyphLayout {

    private final AtlasData atlas;

    public GlyphLayout(AtlasData atlas) {
        this.atlas = atlas;
    }

    /**
     * Build a glyph quad placed so the glyph's baseline crosses {@code baselineY}
     * at the screen x of {@code baselineX} (plus the glyph's left bearing).
     * Returns {@code null} for whitespace and undefined codepoints.
     *
     * @param pixelSize per-em pixel size — multiplies plane bounds in em units
     */
    public DrawCommand.GlyphQuad build(int codepoint, float baselineX, float baselineY,
                                       float pixelSize, Color color) {
        GlyphData g = atlas.glyph(codepoint);
        if (g == null || g.planeBounds() == null || g.atlasBounds() == null) return null;

        Rect pb = g.planeBounds();
        Rect ab = g.atlasBounds();

        // Plane Y is Y-up relative to baseline; screen Y is Y-down.
        float screenLeft   = baselineX + pb.left()   * pixelSize;
        float screenRight  = baselineX + pb.right()  * pixelSize;
        float screenTop    = baselineY - pb.top()    * pixelSize;
        float screenBottom = baselineY - pb.bottom() * pixelSize;

        float w = atlas.info().width();
        float h = atlas.info().height();
        Rect uv = new Rect(ab.left() / w, ab.bottom() / h, ab.right() / w, ab.top() / h);

        return new DrawCommand.GlyphQuad(
            screenLeft, screenTop,
            screenRight - screenLeft, screenBottom - screenTop,
            uv, color
        );
    }

    /** Horizontal advance for a codepoint, in screen pixels at the given em pixel size. */
    public float advance(int codepoint, float pixelSize) {
        GlyphData g = atlas.glyph(codepoint);
        if (g == null) return 0f;
        return g.advance() * pixelSize;
    }
}
