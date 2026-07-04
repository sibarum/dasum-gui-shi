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
     * Returns {@code null} for whitespace.
     *
     * <p>A codepoint the font can't render falls back to the atlas's baked
     * missing-glyph box (see {@link AtlasData#notdef()}) — unless it's a
     * non-printing codepoint (whitespace/control/format), which stays
     * invisible, or the atlas predates the feature, in which case the old
     * "render nothing" behaviour is preserved.
     *
     * @param pixelSize per-em pixel size — multiplies plane bounds in em units
     */
    public DrawCommand.GlyphQuad build(int codepoint, float baselineX, float baselineY,
                                       float pixelSize, Color color) {
        return build(codepoint, baselineX, baselineY, pixelSize, color, 0f);
    }

    /**
     * As {@link #build(int, float, float, float, Color)}, with an SDF edge
     * shift in screen pixels — positive dilates (bold / outline underlay),
     * negative erodes (lighter). See {@link DrawCommand.GlyphQuad#edgePx}.
     */
    public DrawCommand.GlyphQuad build(int codepoint, float baselineX, float baselineY,
                                       float pixelSize, Color color, float edgePx) {
        GlyphData g = resolve(codepoint);
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
            uv, color, edgePx
        );
    }

    /** Horizontal advance for a codepoint, in screen pixels at the given em pixel size. */
    public float advance(int codepoint, float pixelSize) {
        GlyphData g = resolve(codepoint);
        if (g == null) return 0f;
        return g.advance() * pixelSize;
    }

    /**
     * Map a codepoint to the glyph that should represent it: the font's own
     * glyph when present, otherwise the baked missing-glyph box for a
     * printable-but-unrenderable codepoint. Non-printing codepoints and
     * atlases without a baked box resolve to {@code null} (unchanged
     * "render nothing / zero advance" behaviour).
     */
    private GlyphData resolve(int codepoint) {
        GlyphData g = atlas.glyph(codepoint);
        if (g != null) return g;
        if (!isRenderableMiss(codepoint)) return null;
        // A printable character the atlas doesn't have — surface it (deduped)
        // with a stack trace, then fall back to the baked missing-glyph box.
        MissingGlyphLog.report(atlas, codepoint);
        return atlas.notdef();
    }

    /**
     * Whether an absent codepoint should show the missing-glyph box. True for
     * anything a user would expect to see ink for; false for whitespace and
     * non-printing categories (control, format, surrogate, separators), which
     * legitimately have no visible form and must not turn into boxes.
     */
    private static boolean isRenderableMiss(int codepoint) {
        if (Character.isWhitespace(codepoint)) return false;
        return switch (Character.getType(codepoint)) {
            case Character.CONTROL, Character.FORMAT, Character.SURROGATE,
                 Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR,
                 Character.PARAGRAPH_SEPARATOR -> false;
            default -> true;
        };
    }
}
