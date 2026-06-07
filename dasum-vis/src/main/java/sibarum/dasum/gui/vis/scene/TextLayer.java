package sibarum.dasum.gui.vis.scene;

import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.vis.math.Vec3;

/**
 * A single line of MSDF text inside the scene — axis tick labels, point
 * annotations, captions. Glyphs are laid out in <b>world units</b>
 * ({@code heightWorld} = the font's em height in world space) with the
 * baseline passing through {@code anchor}; horizontal placement follows
 * {@code align} (CENTER puts the line's midpoint on the anchor).
 *
 * <p>{@code billboard} text always faces the camera: glyph geometry is
 * uploaded once in a camera-independent local frame and oriented in the
 * vertex shader, so orbiting the camera does NOT re-upload anything —
 * the per-layer identity cache stays effective. Non-billboard text lies
 * in the world XY plane (the natural choice for 2D/ortho scenes).
 *
 * <p>Crispness comes from the MSDF distance field and adapts to screen
 * density automatically; extremely small on-screen sizes are bounded by
 * the atlas's distance range like all framework text.
 *
 * @param text        the line to render ({@code '\n'} is not handled —
 *                    one layer per line)
 * @param fontGroup   registered font-group name; see {@link FontGroups}
 * @param anchor      world-space baseline anchor
 * @param heightWorld em height in world units (> 0)
 * @param color       glyph colour (alpha multiplies with {@code opacity})
 * @param align       horizontal alignment of the line on the anchor
 * @param billboard   face the camera instead of lying in the XY plane
 * @param blend       fixed-function blend mode for this layer
 * @param opacity     uniform layer opacity in [0, 1]
 */
public record TextLayer(
    String text,
    String fontGroup,
    Vec3 anchor,
    float heightWorld,
    Color color,
    HAlign align,
    boolean billboard,
    BlendMode blend,
    float opacity
) implements Layer {

    public enum HAlign { LEFT, CENTER, RIGHT }

    public TextLayer {
        if (text == null) throw new IllegalArgumentException("text != null");
        if (fontGroup == null) throw new IllegalArgumentException("fontGroup != null");
        if (anchor == null) throw new IllegalArgumentException("anchor != null");
        if (!(heightWorld > 0f)) throw new IllegalArgumentException("heightWorld > 0");
        if (color == null) throw new IllegalArgumentException("color != null");
        if (align == null) throw new IllegalArgumentException("align != null");
        if (blend == null) throw new IllegalArgumentException("blend != null");
        if (opacity < 0f || opacity > 1f) throw new IllegalArgumentException("opacity in [0, 1]");
    }

    /** Convenience: default font group, CENTER, non-billboard, ALPHA, full opacity. */
    public TextLayer(String text, Vec3 anchor, float heightWorld, Color color) {
        this(text, FontGroups.DEFAULT, anchor, heightWorld, color,
             HAlign.CENTER, false, BlendMode.ALPHA, 1f);
    }

    public TextLayer withAlign(HAlign a)       { return new TextLayer(text, fontGroup, anchor, heightWorld, color, a, billboard, blend, opacity); }
    public TextLayer withBillboard(boolean b)  { return new TextLayer(text, fontGroup, anchor, heightWorld, color, align, b, blend, opacity); }
    public TextLayer withFontGroup(String g)   { return new TextLayer(text, g, anchor, heightWorld, color, align, billboard, blend, opacity); }
    public TextLayer withBlend(BlendMode b)    { return new TextLayer(text, fontGroup, anchor, heightWorld, color, align, billboard, b, opacity); }
    public TextLayer withOpacity(float o)      { return new TextLayer(text, fontGroup, anchor, heightWorld, color, align, billboard, blend, o); }
}
