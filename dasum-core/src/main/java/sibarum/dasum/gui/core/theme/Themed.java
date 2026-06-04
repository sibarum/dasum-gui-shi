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

    /**
     * Convenience overload that constructs the button and registers
     * {@code onClick} against its final identity in one call. Prefer this
     * over the build-then-onClick two-step when no intermediate operations
     * are needed: it eliminates a whole class of bugs where the caller
     * accidentally applies a {@code with*} method between {@link #button}
     * and {@link sibarum.dasum.gui.core.input.Handlers#onClick}, leaving the
     * click handler stranded on a defunct record identity.
     */
    public static Component button(String label, Em width, Variant variant, int flexGrow, Runnable onClick) {
        Component b = button(label, width, variant, flexGrow);
        sibarum.dasum.gui.core.input.Handlers.onClick(b, onClick);
        return b;
    }

    /**
     * Toolbar-style button with an icon glyph left of the label. The icon
     * is drawn from the default {@code "icons"} font group — see
     * {@link sibarum.dasum.gui.core.text.Icon}; apps pass a codepoint from
     * their generated {@code Icons} class. Same height, padding, and label
     * size as {@link #button}, so the two mix cleanly in one toolbar row.
     */
    public static Component iconButton(int iconCodepoint, String label, Em width, Variant variant, int flexGrow) {
        Palette p = Theme.of(variant);
        Component icon           = sibarum.dasum.gui.core.text.Icon.of(iconCodepoint, Em.of(1.05f), p.onBase());
        Component.Text labelText = new Component.Text(label, Em.of(0.85f), p.onBase());
        return new Component.Flex(
            width, Em.of(2f), Em.of(0.3f), p.base(),
            Direction.ROW, JustifyContent.CENTER, AlignItems.CENTER, Em.of(0.4f),
            List.of(icon, labelText),
            true, 0
        ).withFlexGrow(flexGrow);
    }

    /**
     * Convenience overload that constructs the icon button and registers
     * {@code onClick} against its final identity in one call — same
     * motivation as {@link #button(String, Em, Variant, int, Runnable)}.
     */
    public static Component iconButton(int iconCodepoint, String label, Em width, Variant variant, int flexGrow, Runnable onClick) {
        Component b = iconButton(iconCodepoint, label, width, variant, flexGrow);
        sibarum.dasum.gui.core.input.Handlers.onClick(b, onClick);
        return b;
    }

    /**
     * Icon-only square button — {@code side × side} with the glyph centered.
     * The glyph renders at just over half the side so the button keeps
     * comfortable hit-target padding around it.
     */
    public static Component iconButton(int iconCodepoint, Em side, Variant variant, int flexGrow) {
        Palette p = Theme.of(variant);
        Component icon = sibarum.dasum.gui.core.text.Icon.of(iconCodepoint, Em.of(side.value() * 0.55f), p.onBase());
        return new Component.Flex(
            side, side, Em.ZERO, p.base(),
            Direction.ROW, JustifyContent.CENTER, AlignItems.CENTER, Em.ZERO,
            List.of(icon),
            true, 0
        ).withFlexGrow(flexGrow);
    }

    /** Icon-only square button with the click handler wired in one call. */
    public static Component iconButton(int iconCodepoint, Em side, Variant variant, int flexGrow, Runnable onClick) {
        Component b = iconButton(iconCodepoint, side, variant, flexGrow);
        sibarum.dasum.gui.core.input.Handlers.onClick(b, onClick);
        return b;
    }

    public static Component.Checkbox checkbox(Em size, Property<Boolean> value, Variant variant) {
        Palette p = Theme.of(variant);
        return new Component.Checkbox(size, Theme.subtleBg(), p.base(), value);
    }

    /**
     * Convenience: build a checkbox and subscribe {@code onChange} to its
     * value Property in one call. Mirrors
     * {@link #button(String, Em, Variant, int, Runnable)} — same motivation,
     * different value type.
     */
    public static Component.Checkbox checkbox(Em size, Property<Boolean> value, Variant variant,
                                              java.util.function.Consumer<Boolean> onChange) {
        Component.Checkbox cb = checkbox(size, value, variant);
        value.subscribe(onChange);
        return cb;
    }

    public static <T> Component.Radio<T> radio(Em size, Property<T> group, T value, Variant variant) {
        Palette p = Theme.of(variant);
        return new Component.Radio<>(size, Theme.subtleBg(), p.base(), group, value);
    }

    /** Build a radio and subscribe {@code onChange} to its group Property in one call. */
    public static <T> Component.Radio<T> radio(Em size, Property<T> group, T value, Variant variant,
                                               java.util.function.Consumer<T> onChange) {
        Component.Radio<T> r = radio(size, group, value, variant);
        group.subscribe(onChange);
        return r;
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

    /** Build a slider and subscribe {@code onChange} to its value Property in one call. */
    public static Component.Slider slider(
        Direction orientation, Em length, Em thickness, Em thumbThickness,
        Property<Float> value, float min, float max, Variant variant,
        java.util.function.Consumer<Float> onChange
    ) {
        Component.Slider s = slider(orientation, length, thickness, thumbThickness, value, min, max, variant);
        value.subscribe(onChange);
        return s;
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
