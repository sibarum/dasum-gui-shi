package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

/**
 * A character-index range with an associated color, used by
 * {@link TextStyleStates} to drive per-range foreground (glyph tint) or
 * background (fill behind glyphs) styling of a
 * {@link sibarum.dasum.gui.core.component.Component.Text Text}.
 * <p>
 * The range is half-open: characters in {@code [start, end)} are affected.
 * Indices are character offsets into the Text's effective content (the
 * same coordinate space as {@link TextState#caretIndex} and
 * {@link TextState#selectionAnchor}). Indices that fall outside the live
 * content are silently clipped at render time, so a stale range left over
 * from a previous content version is harmless once the worker republishes.
 * <p>
 * Foreground overlap is last-wins per codepoint; background overlap stacks
 * in submitted order under the selection fill.
 * <p>
 * {@code wrapLineEndings} affects background rendering only: when the range
 * includes a hard {@code '\n'}, that visual line's fill extends to the right
 * edge of the text area (diff-view style) instead of stopping at the last
 * glyph. Soft-wrap boundaries are unaffected (there is no newline character
 * to mark), and foreground ranges ignore the flag — it has no glyph to tint.
 * <p>
 * {@code outlineColor}/{@code outlineWidth} and {@code weight} are
 * foreground-axis glyph effects (background ranges ignore them), rendered
 * by shifting the MSDF distance threshold:
 * <ul>
 *   <li><b>Outline</b> — a dilated copy of each glyph in {@code outlineColor}
 *       drawn under the fill. {@code outlineColor} and {@code outlineWidth}
 *       must be set together. Maximum visible width is bounded by the
 *       atlas's SDF distance range (the shader clamps to stay inside the
 *       band), so outlines are thin by design.</li>
 *   <li><b>Weight</b> — stroke dilation (positive = bolder) or erosion
 *       (negative = lighter) of the fill itself. Advances are unchanged, so
 *       keep values subtle (≈±0.02 em) or long runs look cramped.</li>
 * </ul>
 * On the foreground axis a {@code null} {@code color} means "keep the
 * Text's default color" — useful for weight- or outline-only ranges.
 * Background ranges require a non-null {@code color}.
 */
public record TextStyle(int start, int end, Color color, boolean wrapLineEndings,
                        Color outlineColor, Em outlineWidth, Em weight) {

    public TextStyle {
        if ((outlineColor == null) != (outlineWidth == null)) {
            throw new IllegalArgumentException("outlineColor and outlineWidth must be set together");
        }
    }

    /** Compatibility constructor — no wrap, no outline, no weight. */
    public TextStyle(int start, int end, Color color) {
        this(start, end, color, false, null, null, null);
    }

    /** Compatibility constructor — no outline, no weight. */
    public TextStyle(int start, int end, Color color, boolean wrapLineEndings) {
        this(start, end, color, wrapLineEndings, null, null, null);
    }

    /** Copy with a glyph outline (dilated underlay) in {@code color}, {@code width} thick. */
    public TextStyle withOutline(Color color, Em width) {
        return new TextStyle(start, end, this.color, wrapLineEndings, color, width, weight);
    }

    /** Copy with a stroke-weight delta: positive dilates (bolder), negative erodes (lighter). */
    public TextStyle withWeight(Em weight) {
        return new TextStyle(start, end, color, wrapLineEndings, outlineColor, outlineWidth, weight);
    }

    /** Copy with background extend-to-edge at hard line endings toggled. */
    public TextStyle withWrapLineEndings(boolean wrap) {
        return new TextStyle(start, end, color, wrap, outlineColor, outlineWidth, weight);
    }
}
