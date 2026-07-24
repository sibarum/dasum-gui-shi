package sibarum.dasum.gui.mathtext;

import java.util.List;

/**
 * The result of laying out a {@link MathBox}: a flat draw-list plus the box's metrics, all in
 * <b>root-em</b> units (the root expression is 1 em; scripts etc. carry their own smaller sizes). A
 * backend renders this by a trivial tree-walk — scale by a chosen pixels-per-em and place each draw.
 * Because the geometry is computed once here, the OpenGL and SVG outputs are identical up to scale.
 *
 * <p>Coordinates: {@code x} grows right; the reference baseline is {@code y = 0} with <b>y growing
 * down</b>. {@link #ascent} is the extent above the baseline (a positive number), {@link #descent}
 * the extent below; total height is {@code ascent + descent}, total advance is {@link #width}.
 *
 * @param width   horizontal advance (em)
 * @param ascent  extent above the baseline (em, positive)
 * @param descent extent below the baseline (em, positive)
 * @param draws   the primitives to paint, in this box's frame
 */
public record LaidOut(double width, double ascent, double descent, List<Draw> draws) {

    /** One paintable primitive. */
    public sealed interface Draw permits GlyphRun, Rule {}

    /** A run of glyphs whose baseline sits at {@code y}, drawn at {@code size} em (relative to the
     *  root em) — the backend multiplies by pixels-per-em and selects the math font. */
    public record GlyphRun(String glyphs, double x, double y, double size) implements Draw {}

    /** A filled rectangle (a fraction bar or a radical vinculum), top-left at {@code (x, y)}. */
    public record Rule(double x, double y, double width, double height) implements Draw {}
}
