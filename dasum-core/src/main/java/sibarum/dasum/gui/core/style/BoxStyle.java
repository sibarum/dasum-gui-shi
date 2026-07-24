package sibarum.dasum.gui.core.style;

import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

/**
 * Optional geometry styling carried by the container component records
 * ({@link sibarum.dasum.gui.core.component.Component.Box Box} and
 * {@link sibarum.dasum.gui.core.component.Component.Flex Flex}): per-corner
 * {@link CornerRadii} plus an inset {@link Border}.
 * <p>
 * A {@code null} {@code BoxStyle} on a record means "no rounding, no border"
 * and takes the renderer's flat {@link sibarum.dasum.gui.core.render.DrawCommand.ColoredQuad}
 * fast path — so existing UI is byte-for-byte unchanged and pays no cost. A
 * non-null but {@link #isPlain() plain} style also falls back to the flat path.
 */
public record BoxStyle(CornerRadii radii, Border border) {

    public static final BoxStyle NONE = new BoxStyle(CornerRadii.NONE, Border.NONE);

    public BoxStyle {
        if (radii == null)  radii = CornerRadii.NONE;
        if (border == null) border = Border.NONE;
    }

    /** Rounded-only style with no border. */
    public static BoxStyle rounded(Em radius) {
        return new BoxStyle(CornerRadii.all(radius), Border.NONE);
    }

    /** Rounded-only style with per-corner radii. */
    public static BoxStyle rounded(CornerRadii radii) {
        return new BoxStyle(radii, Border.NONE);
    }

    /** Border-only style with square corners. */
    public static BoxStyle bordered(Em width, Color color) {
        return new BoxStyle(CornerRadii.NONE, Border.of(width, color));
    }

    public BoxStyle withRadii(CornerRadii r) { return new BoxStyle(r, border); }
    public BoxStyle withBorder(Border b)     { return new BoxStyle(radii, b); }

    /**
     * True when this style would render identically to a flat colored quad —
     * no rounding and no visible border. The renderer uses this to keep the
     * fast path even when a (redundant) style object is attached.
     */
    public boolean isPlain() {
        return !radii.rounded() && !border.visible();
    }
}
