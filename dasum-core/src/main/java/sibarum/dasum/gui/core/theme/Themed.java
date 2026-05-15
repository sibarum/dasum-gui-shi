package sibarum.dasum.gui.core.theme;

import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.reactive.Property;

import java.util.List;

/**
 * Builder facade that constructs widgets with colors derived from the
 * active {@link Theme} for a given {@link Variant}. Does not change the
 * widget records' API — explicit-color constructors continue to work.
 * <p>
 * For text-input style components that don't have a strong variant tint
 * (the body, the box of an unchecked checkbox), the {@link Theme#subtleBg}
 * surface color is shared across all variants, so only the accent shifts.
 */
public final class Themed {

    private Themed() {}

    /** Solid-color rectangle whose interaction wires to {@code onClick} via
     *  {@link sibarum.dasum.gui.core.input.Handlers}; visually a button. */
    public static Component button(String label, Em width, Variant variant, int flexGrow) {
        Palette p = Theme.of(variant);
        Component.Text labelText = new Component.Text(label, Em.of(0.85f), p.onBase());
        return new Component.Flex(
            width, Em.of(2f), Em.of(0.3f), p.base(),
            Direction.ROW, JustifyContent.CENTER, AlignItems.CENTER, Em.ZERO,
            List.of(labelText),
            true, 0
        ).withFlexGrow(flexGrow);
    }

    public static Component.Checkbox checkbox(Em size, Property<Boolean> value, Variant variant) {
        Palette p = Theme.of(variant);
        return new Component.Checkbox(size, Theme.subtleBg(), p.base(), value);
    }

    public static <T> Component.Radio<T> radio(Em size, Property<T> group, T value, Variant variant) {
        Palette p = Theme.of(variant);
        return new Component.Radio<>(size, Theme.subtleBg(), p.base(), group, value);
    }

    public static Component.Slider slider(
        Direction orientation, Em length, Em thickness, Em thumbThickness,
        Property<Float> value, float min, float max, Variant variant
    ) {
        Palette p = Theme.of(variant);
        return new Component.Slider(
            orientation, length, thickness, thumbThickness,
            Theme.subtleBg(), p.base(), p.onBase(),
            value, min, max
        );
    }

    /** Variant-colored text label — uses the variant's {@code emphasis} shade. */
    public static Component.Text label(String text, Em fontSize, Variant variant) {
        return new Component.Text(text, fontSize, Theme.of(variant).emphasis());
    }

    /**
     * Tabbed container themed off the given variant. Header strip uses the
     * theme's subtle surface; the active tab cell is painted in the
     * variant's {@code base} color; labels in the variant's
     * {@code emphasis}; content area in {@link Theme#subtleBg()}.
     */
    public static Component.Tabs tabs(
        Em width, Em height, Em headerHeight, Em tabPadding, Em contentPadding,
        Em tabFontSize, java.util.List<Component.Tabs.TabPanel> panels,
        sibarum.dasum.gui.core.reactive.Property<Integer> activeIndex,
        Variant variant
    ) {
        Palette p = Theme.of(variant);
        return new Component.Tabs(
            width, height,
            headerHeight, tabPadding, contentPadding,
            Theme.subtleBg(), p.base(), p.emphasis(), Theme.subtleBg(),
            tabFontSize, sibarum.dasum.gui.core.text.FontGroups.DEFAULT,
            panels, activeIndex, true, 0
        );
    }
}
