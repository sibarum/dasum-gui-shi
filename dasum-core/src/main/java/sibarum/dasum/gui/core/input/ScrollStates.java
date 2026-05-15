package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Identity-keyed registry of {@link ScrollPosition} state — one per
 * ScrollContainer instance. Lookups create-on-demand. Identity comparison
 * is essential: two structurally-identical Scroll records would otherwise
 * share state (see [[record-equality-trap]] in project memory).
 */
public final class ScrollStates {

    private static final Map<Component, ScrollPosition> STATES = new IdentityHashMap<>();

    private ScrollStates() {}

    public static ScrollPosition of(Component scroll) {
        ScrollPosition pos = STATES.get(scroll);
        if (pos == null) {
            pos = new ScrollPosition();
            STATES.put(scroll, pos);
        }
        return pos;
    }

    /**
     * Per-component cleanup hook called by
     * {@link sibarum.dasum.gui.core.component.Components#detach}. Drops any
     * stored scroll position for {@code c}.
     */
    public static void clear(Component c) {
        STATES.remove(c);
    }
}
