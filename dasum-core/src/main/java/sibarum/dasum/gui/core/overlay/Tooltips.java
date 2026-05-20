package sibarum.dasum.gui.core.overlay;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Identity-keyed sidecar of tooltip strings per component. Parallel to
 * {@link sibarum.dasum.gui.core.input.HoverState},
 * {@link sibarum.dasum.gui.core.input.Handlers}, etc.: components stay
 * pure-data records; tooltip text lives here.
 * <p>
 * Any component — interactive or not — can carry a tooltip. The hit-tester
 * used by {@link TooltipController} walks from the deepest containing
 * component up to the root and surfaces the innermost ancestor that has
 * a registered string.
 * <p>
 * Thread-safe: registration may happen on any thread (workers building
 * dynamic UI). All accesses synchronize on a private lock; critical
 * sections stay tiny. After mutation the global {@link Invalidator} is
 * pinged so the controller re-evaluates on the next frame (the same
 * coalescing pattern other sidecars use — never bypass with
 * {@code glfwPostEmptyEvent}).
 */
public final class Tooltips {

    private static final Object LOCK = new Object();
    private static final Map<Component, String> TEXTS = new IdentityHashMap<>();

    private Tooltips() {}

    /**
     * Register or replace the tooltip string for {@code c}. {@code null}
     * or empty text removes any existing entry — same as calling
     * {@link #remove}.
     */
    public static void set(Component c, String text) {
        if (c == null) return;
        if (text == null || text.isEmpty()) {
            remove(c);
            return;
        }
        boolean changed;
        synchronized (LOCK) {
            String prev = TEXTS.put(c, text);
            changed = !text.equals(prev);
        }
        if (changed) Invalidator.invalidate();
    }

    public static String get(Component c) {
        if (c == null) return null;
        synchronized (LOCK) {
            return TEXTS.get(c);
        }
    }

    public static boolean has(Component c) {
        if (c == null) return false;
        synchronized (LOCK) {
            return TEXTS.containsKey(c);
        }
    }

    public static void remove(Component c) {
        if (c == null) return;
        boolean removed;
        synchronized (LOCK) {
            removed = TEXTS.remove(c) != null;
        }
        if (removed) Invalidator.invalidate();
    }

    /**
     * Per-component cleanup hook called by
     * {@link sibarum.dasum.gui.core.component.Components#detach}. Drops any
     * tooltip string for {@code c} and hides the live tooltip if it was
     * anchored on {@code c}.
     */
    public static void clear(Component c) {
        if (c == null) return;
        synchronized (LOCK) {
            TEXTS.remove(c);
        }
        TooltipController.onComponentDetached(c);
    }

    /** Copy {@code from}'s tooltip text (if any) to {@code to}. */
    public static void migrate(Component from, Component to) {
        if (from == null || to == null || from == to) return;
        synchronized (LOCK) {
            String s = TEXTS.get(from);
            if (s != null) TEXTS.put(to, s);
        }
    }
}
