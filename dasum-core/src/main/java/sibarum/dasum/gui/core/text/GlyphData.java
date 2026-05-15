package sibarum.dasum.gui.core.text;

import sibarum.dasum.gui.core.render.Rect;

/**
 * Per-glyph metrics from msdf-atlas-gen output. For whitespace glyphs
 * (space, tab) only {@code unicode} and {@code advance} are meaningful;
 * {@code planeBounds} and {@code atlasBounds} are {@code null}.
 *
 * @param unicode     codepoint
 * @param advance     glyph advance in em units (multiply by font size for pixels)
 * @param planeBounds glyph quad relative to the baseline in em units, Y-up,
 *                    or {@code null} for whitespace
 * @param atlasBounds rectangle in the atlas image in pixel coords with the
 *                    atlas's yOrigin convention, or {@code null} for whitespace
 */
public record GlyphData(int unicode, float advance, Rect planeBounds, Rect atlasBounds) {}
