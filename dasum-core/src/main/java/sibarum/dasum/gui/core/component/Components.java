package sibarum.dasum.gui.core.component;

import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.graph.ConnectionSelection;
import sibarum.dasum.gui.core.graph.Connections;
import sibarum.dasum.gui.core.graph.GraphSurfaceChildren;
import sibarum.dasum.gui.core.graph.GraphSurfacePositions;
import sibarum.dasum.gui.core.graph.GraphSurfaceZOrder;
import sibarum.dasum.gui.core.graph.Ports;
import sibarum.dasum.gui.core.input.ContextMenuStates;
import sibarum.dasum.gui.core.input.FocusState;
import sibarum.dasum.gui.core.input.Handlers;
import sibarum.dasum.gui.core.input.HoverState;
import sibarum.dasum.gui.core.input.ScrollStates;
import sibarum.dasum.gui.core.input.TextStates;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Component lifecycle utilities. Pair with the sealed {@link Component} type
 * to manage the transient state a component accumulates in the framework's
 * sidecars over its lifetime — {@code Handlers}, {@code Ports},
 * {@code Connections}, {@code TextStates}, {@code ScrollStates},
 * {@code GraphSurface*}, {@code FocusState}, {@code HoverState},
 * {@code ConnectionSelection}, {@code ContextMenuStates}.
 *
 * <p>{@link #detach(Component)} performs a depth-first walk over a component
 * subtree and clears every built-in sidecar's entries for each visited node.
 * Apps that introduce their own identity-keyed sidecars register a cleaner
 * via {@link #registerCleaner(Consumer)} so {@code detach} picks them up
 * uniformly.
 *
 * <p>Idempotent: calling {@code detach} on an already-detached subtree is a
 * no-op (each sidecar's {@code clear(Component)} is a no-op when no entries
 * exist for that key). Invalidates the renderer once at the end of the walk
 * iff any sidecar mutation actually happened.
 *
 * <p>This API was added to close the long-standing eviction gap on dasum's
 * sidecars: until {@code detach} existed, components removed from the visual
 * tree left dead entries in every sidecar that had touched them, accumulating
 * silently over the lifetime of dynamic-UI apps (the canonical case being a
 * node editor that spawns and discards nodes).
 */
public final class Components {

    /**
     * App-registered cleaners. Each is called for every component visited by
     * {@link #detach}. Built-in dasum sidecars are NOT in this list — they
     * are called directly by {@link #clearAllBuiltIn} so class-load ordering
     * doesn't matter (the sidecars don't need to be touched first to
     * participate).
     *
     * <p>Copy-on-write so reads during a detach walk see a stable list even
     * if a cleaner happens to register another during cleanup. In practice
     * registrations happen at startup and clearings happen later, so contention
     * is negligible.
     */
    private static final List<Consumer<Component>> EXTERNAL_CLEANERS = new CopyOnWriteArrayList<>();

    private Components() {}

    /**
     * Register a per-component cleaner for an app-defined sidecar. The
     * cleaner is invoked once for every component in the subtree passed to
     * {@link #detach}. The component identity is the only argument — the
     * cleaner is responsible for knowing whether {@code c} is one of its
     * keys.
     *
     * <p>Registration is process-global and lives for the lifetime of the
     * JVM. There is no unregister API; cleaners are expected to be added
     * once at startup.
     */
    public static void registerCleaner(Consumer<Component> cleaner) {
        EXTERNAL_CLEANERS.add(cleaner);
    }

    /**
     * Depth-first walk over {@code root}'s subtree, calling
     * {@link #clearAllBuiltIn(Component)} plus every registered external
     * cleaner on each visited component (including {@code root} itself).
     * {@code null} is tolerated and treated as an empty subtree.
     *
     * <p>For a {@link Component.GraphSurface} the walk includes both the
     * record-declared children and the dynamically-added children from
     * {@link GraphSurfaceChildren}. Children are snapshotted before clearing
     * so the walk doesn't interfere with the cleanup it's performing.
     */
    public static void detach(Component root) {
        if (root == null) return;
        List<Component> visited = new ArrayList<>();
        collect(root, visited);
        for (Component c : visited) {
            clearAllBuiltIn(c);
            for (Consumer<Component> ext : EXTERNAL_CLEANERS) {
                ext.accept(c);
            }
        }
        Invalidator.invalidate();
    }

    /** Pre-order traversal — root before children. */
    private static void collect(Component c, List<Component> into) {
        if (c == null) return;
        into.add(c);
        for (Component child : childrenOf(c)) {
            collect(child, into);
        }
    }

    /**
     * Children visible in the component subtree for traversal purposes. For
     * {@link Component.GraphSurface} the declared children list is unioned
     * with dynamically-added children from {@link GraphSurfaceChildren}.
     */
    private static List<Component> childrenOf(Component c) {
        return switch (c) {
            case Component.Box b      -> b.children();
            case Component.Flex f     -> f.children();
            case Component.Scroll s   -> s.child() == null ? List.of() : List.of(s.child());
            case Component.Tabs t     -> tabContents(t);
            case Component.GraphSurface g -> GraphSurfaceChildren.all(g);
            case Component.Text t     -> List.of();
            case Component.Checkbox cb -> List.of();
            case Component.Radio<?> r -> List.of();
            case Component.Slider sl  -> List.of();
        };
    }

    private static List<Component> tabContents(Component.Tabs t) {
        List<Component> out = new ArrayList<>(t.tabs().size());
        for (Component.Tabs.TabPanel panel : t.tabs()) {
            if (panel.content() != null) out.add(panel.content());
        }
        return out;
    }

    /**
     * Call every built-in sidecar's {@code clear(Component)} for {@code c}.
     * Each sidecar's {@code clear} is documented to be a no-op when no
     * entries exist for the given key, so this method is safe to invoke on
     * any component regardless of whether it ever participated in that
     * sidecar's state.
     */
    private static void clearAllBuiltIn(Component c) {
        Handlers.clear(c);
        ContextMenuStates.clear(c);
        TextStates.clear(c);
        ScrollStates.clear(c);
        FocusState.clear(c);
        HoverState.clear(c);
        // Graph-package sidecars: a port-component clear cascades into
        // Connections / ConnectionSelection / Ports correctly because each
        // sidecar checks both surface-keyed and endpoint-keyed roles.
        Connections.clear(c);
        ConnectionSelection.clear(c);
        Ports.clear(c);
        GraphSurfacePositions.clear(c);
        GraphSurfaceZOrder.clear(c);
        GraphSurfaceChildren.clear(c);
    }
}
