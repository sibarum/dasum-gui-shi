package sibarum.dasum.gui.core.style;

import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

/**
 * An inset outline painted on top of a component's fill, {@code width} em
 * thick in {@code color}. The border is drawn <em>inside</em> the component's
 * rect (it does not grow the layout box), following the same rounded-corner
 * geometry as the fill.
 * <p>
 * {@link #NONE} is a zero-width transparent border — the "no outline" default.
 */
public record Border(Em width, Color color) {

    public static final Border NONE = new Border(Em.ZERO, Color.TRANSPARENT);

    public Border {
        if (width == null) {
            throw new IllegalArgumentException("Border width is null; use Em.ZERO for no border");
        }
        if (width.isAuto()) {
            throw new IllegalArgumentException("Border width cannot be Em.AUTO");
        }
        if (color == null) {
            throw new IllegalArgumentException("Border color is null; use Color.TRANSPARENT for no border");
        }
    }

    public static Border of(Em width, Color color) {
        return new Border(width, color);
    }

    /** True when the border would actually paint (non-zero width and visible color). */
    public boolean visible() {
        return width.value() > 0f && color.a() > 0f;
    }
}
