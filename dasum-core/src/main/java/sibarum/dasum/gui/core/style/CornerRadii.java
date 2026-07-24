package sibarum.dasum.gui.core.style;

import sibarum.dasum.gui.core.em.Em;

/**
 * Per-corner corner radii in em, ordered clockwise from the top-left:
 * {@code tl, tr, br, bl}. {@link #NONE} means square corners.
 * <p>
 * Radii are resolved to pixels at draw time (via {@link Em#toPixels()}) and
 * clamped in the rounded-rect shader to {@code min(width, height) / 2}, so an
 * over-specified radius degrades gracefully to a fully-rounded end cap rather
 * than an artifact.
 */
public record CornerRadii(Em tl, Em tr, Em br, Em bl) {

    public static final CornerRadii NONE = new CornerRadii(Em.ZERO, Em.ZERO, Em.ZERO, Em.ZERO);

    public CornerRadii {
        if (tl == null || tr == null || br == null || bl == null) {
            throw new IllegalArgumentException(
                "CornerRadii components are null; use Em.ZERO for a square corner");
        }
        if (tl.isAuto() || tr.isAuto() || br.isAuto() || bl.isAuto()) {
            throw new IllegalArgumentException("CornerRadii cannot be Em.AUTO");
        }
    }

    /** Same radius on all four corners. */
    public static CornerRadii all(Em r) {
        return new CornerRadii(r, r, r, r);
    }

    /** Round only the top-left and top-right corners — the classic tab shape. */
    public static CornerRadii top(Em r) {
        return new CornerRadii(r, r, Em.ZERO, Em.ZERO);
    }

    /** True when at least one corner has a non-zero radius. */
    public boolean rounded() {
        return tl.value() > 0f || tr.value() > 0f || br.value() > 0f || bl.value() > 0f;
    }
}
