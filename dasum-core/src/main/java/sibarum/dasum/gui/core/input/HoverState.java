package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;

/**
 * Global pointer to the currently-hovered component (or null). Invalidates
 * the renderer when the hovered component CHANGES — moving the cursor
 * within the same component is free (zero idle frames).
 */
public final class HoverState {

    private static Component hovered = null;

    private HoverState() {}

    public static Component hovered() { return hovered; }

    public static void update(Component newHover) {
        if (hovered == newHover) return;
        hovered = newHover;
        Invalidator.invalidate();
    }

    public static void clear() {
        if (hovered == null) return;
        hovered = null;
        Invalidator.invalidate();
    }

    /**
     * Per-component cleanup hook called by
     * {@link sibarum.dasum.gui.core.component.Components#detach}. Drops the
     * hover pointer if it's {@code c}; no-op otherwise.
     */
    public static void clear(Component c) {
        if (hovered == c) clear();
    }
}
