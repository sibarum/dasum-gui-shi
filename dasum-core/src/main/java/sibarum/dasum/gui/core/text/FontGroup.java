package sibarum.dasum.gui.core.text;

import sibarum.dasum.gui.core.render.Texture;

/**
 * Bundle of (name, MSDF atlas, GL texture, distance range, glyph layout).
 * Text components reference a font group by name; the framework resolves
 * the name to a FontGroup via {@link FontGroups} at layout/render time.
 * <p>
 * The {@code "primary"} group is the framework default — apps register at
 * startup, before any text rendering happens.
 */
public record FontGroup(String name, AtlasData atlas, Texture texture, GlyphLayout layout, float distanceRange) {

    public static FontGroup of(String name, AtlasData atlas, Texture texture) {
        return new FontGroup(name, atlas, texture, new GlyphLayout(atlas), atlas.info().distanceRange());
    }
}
