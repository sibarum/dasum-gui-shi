package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Opt-in marker: a {@link Component.Scroll} tagged here draws the focus ring on
 * its own (fixed, unscrolled) viewport frame whenever its direct child holds
 * focus, instead of letting the child draw the ring on its scrolled content
 * rect — where it slides out of the viewport and disappears the moment the user
 * scrolls (a focused code editor is the motivating case).
 *
 * <p>Identity-keyed like the other per-component sidecars ({@link ScrollStates},
 * {@code TextStates}), so structurally-identical records never share the flag.
 * This is the "builtin scroll inside the text's focus boundary" affordance: the
 * app wraps its editor in a Scroll as usual and marks it here — no new
 * Component field, no call-site churn.
 */
public final class ScrollFocusFrame {

    private static final Set<Component> FRAMED =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private ScrollFocusFrame() {}

    /** Mark {@code scroll} so its focused direct child rings the viewport frame. */
    public static void enable(Component scroll) {
        if (scroll != null) FRAMED.add(scroll);
    }

    /** Whether {@code scroll} was {@link #enable(Component) enabled}. */
    public static boolean isEnabled(Component scroll) {
        return FRAMED.contains(scroll);
    }

    /** Per-component cleanup hook (mirrors {@link ScrollStates#clear}). */
    public static void clear(Component c) {
        FRAMED.remove(c);
    }

    /** Carry the flag from {@code from} to {@code to} across a rebuild. */
    public static void migrate(Component from, Component to) {
        if (FRAMED.contains(from)) FRAMED.add(to);
    }
}
