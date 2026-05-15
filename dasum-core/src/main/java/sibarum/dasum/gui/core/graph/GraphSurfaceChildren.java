package sibarum.dasum.gui.core.graph;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-{@link Component.GraphSurface GraphSurface} sidecar of children added
 * at runtime — alongside the surface's record-final declared
 * {@code children()} list.
 * <p>
 * Existence of this sidecar is what lets the Everything Menu (and the
 * right-click "Add Node Here" context item) spawn fresh nodes onto a live
 * surface without rebuilding the component tree. Layout / Render /
 * HitTest / Ports / connection-drag all walk the combined view via
 * {@link #all}; existing pure-declared callers keep working unchanged
 * because the sidecar starts empty.
 */
public final class GraphSurfaceChildren {

    private static final Map<Component, List<Component>> ADDED = new IdentityHashMap<>();

    private GraphSurfaceChildren() {}

    /** Append {@code node} to {@code surface}'s dynamic-children list. */
    public static void add(Component.GraphSurface surface, Component node) {
        ADDED.computeIfAbsent(surface, k -> new ArrayList<>()).add(node);
        Invalidator.invalidate();
    }

    /** Remove {@code node} from {@code surface}'s dynamic-children list (no-op if not present). */
    public static void remove(Component.GraphSurface surface, Component node) {
        List<Component> list = ADDED.get(surface);
        if (list != null && list.removeIf(c -> c == node)) {
            Invalidator.invalidate();
        }
    }

    /** Just the dynamically-added children, in insertion order. */
    public static List<Component> added(Component.GraphSurface surface) {
        List<Component> list = ADDED.get(surface);
        return (list == null || list.isEmpty()) ? List.of() : List.copyOf(list);
    }

    /**
     * Declared children + dynamically-added children, in that order. The
     * single source of truth for "what's on this surface" — Layout, Render,
     * HitTest, Ports, connection-drag all walk this.
     */
    public static List<Component> all(Component.GraphSurface surface) {
        List<Component> declared = surface.children();
        List<Component> dynamic  = ADDED.get(surface);
        if (dynamic == null || dynamic.isEmpty()) return declared;
        List<Component> out = new ArrayList<>(declared.size() + dynamic.size());
        out.addAll(declared);
        out.addAll(dynamic);
        return out;
    }

    /**
     * Per-component cleanup hook called by
     * {@link sibarum.dasum.gui.core.component.Components#detach}. Handles
     * both surface-keyed entries (drop the entire dynamic-children list)
     * and child-keyed entries (remove {@code c} from every surface's list).
     */
    public static void clear(Component c) {
        if (ADDED.remove(c) != null) {
            Invalidator.invalidate();
            return;
        }
        boolean changed = false;
        for (List<Component> list : ADDED.values()) {
            if (list.removeIf(x -> x == c)) changed = true;
        }
        if (changed) Invalidator.invalidate();
    }
}
