package sibarum.dasum.gui.core.component;

import sibarum.dasum.gui.core.event.Invalidator;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Identity-keyed sidecar of "extra children" added at runtime to a
 * container Component. Generalizes the
 * {@link sibarum.dasum.gui.core.graph.GraphSurfaceChildren} pattern to
 * any container that has a {@code children()} accessor — currently
 * {@link Component.Flex} and {@link Component.Box}. Use it when you
 * need add/remove semantics on a list of children without rebuilding
 * the parent record on every change.
 *
 * <p>The framework reads <em>effective</em> children — the record's
 * declared list plus any dynamic entries — via
 * {@link #effectiveChildren(Component)}. Layout, Render, and HitTest
 * all route through this helper, so dynamic children participate in
 * sizing, painting, and hit-testing automatically.
 *
 * <p>Designed for list-style UIs where the row set changes at runtime
 * — corpus editors, file lists, chat messages, settings rows. Apps
 * shouldn't generally need to add children to a Component.Box (single-
 * child semantics are usually clearer), but the mechanism is supported
 * symmetrically.
 *
 * <p>{@link Component.Scroll} and {@link Component.Tabs} are excluded:
 * Scroll's single-child slot doesn't generalize to a list, and Tabs's
 * children are typed by label, so dynamic Tabs needs a richer API
 * than raw "add a Component." {@link Component.GraphSurface} keeps
 * its own
 * {@link sibarum.dasum.gui.core.graph.GraphSurfaceChildren} because it
 * carries extra per-child state (absolute em positions, z-order).
 */
public final class DynamicChildren {

    private static final Map<Component, List<Component>> ADDED = new IdentityHashMap<>();

    private DynamicChildren() {}

    /** Append {@code child} to {@code container}'s dynamic-children list. */
    public static void add(Component container, Component child) {
        ADDED.computeIfAbsent(container, k -> new ArrayList<>()).add(child);
        Invalidator.invalidate();
    }

    /** Insert {@code child} at the given index in the dynamic-children list. */
    public static void insert(Component container, int index, Component child) {
        List<Component> list = ADDED.computeIfAbsent(container, k -> new ArrayList<>());
        int clamped = Math.max(0, Math.min(index, list.size()));
        list.add(clamped, child);
        Invalidator.invalidate();
    }

    /** Remove {@code child} from {@code container}'s dynamic-children list (no-op if not present). */
    public static void remove(Component container, Component child) {
        List<Component> list = ADDED.get(container);
        if (list != null && list.removeIf(c -> c == child)) {
            Invalidator.invalidate();
        }
    }

    /** Drop the entire dynamic-children list for {@code container}. */
    public static void clearChildren(Component container) {
        List<Component> existing = ADDED.remove(container);
        if (existing != null && !existing.isEmpty()) Invalidator.invalidate();
    }

    /** Just the dynamically-added children, in insertion order. */
    public static List<Component> added(Component container) {
        List<Component> list = ADDED.get(container);
        return (list == null || list.isEmpty()) ? List.of() : List.copyOf(list);
    }

    /**
     * Declared children + dynamically-added children, in that order.
     * Layout / Render / HitTest call this. Returns the declared list
     * directly when no dynamic entries exist, to avoid allocation in
     * the common case.
     */
    public static List<Component> effectiveChildren(Component c) {
        List<Component> declared = declaredChildren(c);
        List<Component> dynamic  = ADDED.get(c);
        if (dynamic == null || dynamic.isEmpty()) return declared;
        List<Component> out = new ArrayList<>(declared.size() + dynamic.size());
        out.addAll(declared);
        out.addAll(dynamic);
        return out;
    }

    private static List<Component> declaredChildren(Component c) {
        return switch (c) {
            case Component.Flex f -> f.children();
            case Component.Box  b -> b.children();
            default -> List.of();
        };
    }

    /**
     * Per-component cleanup hook called by
     * {@link Components#detach}. Handles both container-keyed entries
     * (drop the whole list) and child-keyed entries (remove from every
     * container's list).
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

    /** Migrate dynamic-children entries (container and child roles). */
    public static void migrate(Component from, Component to) {
        List<Component> containerList = ADDED.get(from);
        if (containerList != null) {
            ADDED.put(to, new ArrayList<>(containerList));
        }
        for (List<Component> list : ADDED.values()) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) == from) list.set(i, to);
            }
        }
    }
}
