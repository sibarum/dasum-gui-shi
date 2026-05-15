package sibarum.dasum.gui.core.graph;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-{@link Component.GraphSurface GraphSurface} z-order overlay. Stores
 * a list of children-that-have-been-explicitly-bumped, in bump order
 * (oldest bump first, most recent last → topmost). Children never bumped
 * keep their declared order in {@code surface.children()} and sit
 * underneath any bumped sibling.
 * <p>
 * Identity-based throughout (records compare by value but we want a
 * stable per-instance bump record).
 */
public final class GraphSurfaceZOrder {

    private static final Map<Component, List<Component>> ORDERS = new IdentityHashMap<>();

    private GraphSurfaceZOrder() {}

    /** Children in render order: bottom (paint first) → top (paint last). */
    public static List<Component> orderedChildren(Component.GraphSurface surface) {
        // Source of truth is GraphSurfaceChildren.all — declared + dynamically
        // added. Z-bump only reorders what's in that list.
        List<Component> base = GraphSurfaceChildren.all(surface);
        List<Component> bumped = ORDERS.get(surface);
        if (bumped == null || bumped.isEmpty()) return base;

        List<Component> out = new ArrayList<>(base.size());
        // 1. Members of base that haven't been bumped — in their original order.
        for (Component c : base) {
            if (!containsIdentity(bumped, c)) out.add(c);
        }
        // 2. Bumped children — in bump order (so most recently bumped is last).
        //    Filter to ones that are actually on the surface right now.
        for (Component b : bumped) {
            if (containsIdentity(base, b)) out.add(b);
        }
        return out;
    }

    /** Move {@code child} to the top of its surface's z-order. */
    public static void bumpToTop(Component.GraphSurface surface, Component child) {
        List<Component> order = ORDERS.computeIfAbsent(surface, k -> new ArrayList<>());
        order.removeIf(c -> c == child);
        order.add(child);
        Invalidator.invalidate();
    }

    private static boolean containsIdentity(List<Component> list, Component target) {
        for (Component c : list) if (c == target) return true;
        return false;
    }

    /**
     * Per-component cleanup hook called by
     * {@link sibarum.dasum.gui.core.component.Components#detach}. Handles
     * both surface-keyed entries (drop the whole bumped list) and
     * child-keyed entries (remove {@code c} from every surface's list).
     */
    public static void clear(Component c) {
        if (ORDERS.remove(c) != null) {
            Invalidator.invalidate();
            return;
        }
        boolean changed = false;
        for (List<Component> list : ORDERS.values()) {
            if (list.removeIf(x -> x == c)) changed = true;
        }
        if (changed) Invalidator.invalidate();
    }
}
