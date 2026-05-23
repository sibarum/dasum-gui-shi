package sibarum.dasum.gui.core.input;

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
 */
public record TextStyle(int start, int end, Color color) {}
