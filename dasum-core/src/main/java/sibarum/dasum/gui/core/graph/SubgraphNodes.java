package sibarum.dasum.gui.core.graph;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Identity-keyed sidecar holding the inner {@link Component.GraphSurface}
 * (and outer↔inner port-proxy mappings) for each "subgraph" node — a node
 * type that visually presents as a regular node with title + ports but
 * whose contents are a separate sub-graph mounted into an overlay on
 * expand.
 * <p>
 * The framework treats inner surfaces as ordinary {@code GraphSurface}s —
 * {@link GraphSurfacePositions}, {@link GraphSurfaceChildren},
 * {@link Connections}, and {@link Ports} all work on them unchanged
 * (their per-surface maps are already keyed by surface identity, so an
 * inner surface gets its own scope automatically). The only piece the
 * framework needs to coordinate is detach-cascade: deleting a subgraph
 * outer node must also detach its inner surface (and everything on it),
 * otherwise the inner state lingers in the sidecars as a zombie.
 * <p>
 * Port proxies are a structural mapping, not a data-flow forwarder.
 * Each outer port has a corresponding "stub" port on the inner surface
 * (rendered as a tiny one-port node) with the INVERSE direction — outer
 * input ↔ inner OUTPUT stub (the stub is a producer inside the
 * sub-graph), outer output ↔ inner INPUT stub. Apps walking the surface
 * for serialization can rely on this mapping to record that "this inner
 * connection terminates at outer-port X" without inventing their own
 * proxy concept.
 * <p>
 * Identity-keyed throughout. The map key is the outer node component.
 */
public final class SubgraphNodes {

    /**
     * Per-subgraph-node state.
     *
     * @param innerSurface        the inner GraphSurface — never null
     * @param outerToInnerPort    outer port component → inner stub port
     *                            component. Unmodifiable. Source of truth
     *                            for the proxy mapping; use
     *                            {@link #innerToOuterPort()} for the inverse
     *                            view.
     */
    public record Subgraph(
        Component.GraphSurface innerSurface,
        Map<Component, Component> outerToInnerPort
    ) {
        public Subgraph {
            if (innerSurface == null) throw new IllegalArgumentException("innerSurface must not be null");
            outerToInnerPort = (outerToInnerPort == null) ? Map.of()
                : Collections.unmodifiableMap(new IdentityHashMap<>(outerToInnerPort));
        }

        /**
         * Inverse view: inner stub port → outer port. Computed on demand
         * (the map is typically small — one entry per declared port on the
         * outer node — and this method is only invoked during serialization
         * or right-click "find proxied port" actions, never in hot paths).
         */
        public Map<Component, Component> innerToOuterPort() {
            Map<Component, Component> inv = new IdentityHashMap<>(outerToInnerPort.size());
            for (Map.Entry<Component, Component> e : outerToInnerPort.entrySet()) {
                inv.put(e.getValue(), e.getKey());
            }
            return Collections.unmodifiableMap(inv);
        }
    }

    private static final Map<Component, Subgraph> SUBGRAPHS = new IdentityHashMap<>();

    /**
     * Re-entrancy guard for cascading detach. {@link #clear(Component)}
     * adds the outer node to this set before recursing into its inner
     * surface (and removes it on the way out). If detach finds an outer
     * node already in the set, it bails — protects against pathological
     * cycles like an inner surface containing the outer node it belongs
     * to (shouldn't happen, but a hand-rolled
     * {@link sibarum.dasum.gui.core.component.DynamicChildren} insertion
     * could create one and we don't want to stack-overflow).
     */
    private static final java.util.Set<Component> CLEAR_IN_PROGRESS =
        Collections.newSetFromMap(new IdentityHashMap<>());

    private SubgraphNodes() {}

    /**
     * Register {@code outerNode} as a subgraph node. Typically called from
     * {@link SubgraphNodeBuilder#build()}; apps can also call directly
     * when building subgraph nodes by hand.
     */
    public static void attach(Component outerNode, Subgraph subgraph) {
        if (outerNode == null) throw new IllegalArgumentException("outerNode must not be null");
        if (subgraph == null) throw new IllegalArgumentException("subgraph must not be null");
        SUBGRAPHS.put(outerNode, subgraph);
        Invalidator.invalidate();
    }

    /** Returns the {@link Subgraph} for {@code outerNode}, or {@code null} if not a subgraph. */
    public static Subgraph of(Component outerNode) {
        if (outerNode == null) return null;
        return SUBGRAPHS.get(outerNode);
    }

    /** Whether {@code outerNode} is registered as a subgraph node. */
    public static boolean isSubgraph(Component outerNode) {
        return outerNode != null && SUBGRAPHS.containsKey(outerNode);
    }

    /** Convenience: inner surface or null. */
    public static Component.GraphSurface innerSurfaceOf(Component outerNode) {
        Subgraph sg = of(outerNode);
        return sg == null ? null : sg.innerSurface();
    }

    /** Find the inner stub port for an outer port, or null. */
    public static Component innerPortFor(Component outerNode, Component outerPort) {
        Subgraph sg = of(outerNode);
        if (sg == null) return null;
        return sg.outerToInnerPort().get(outerPort);
    }

    /** Inverse lookup — find the outer port mapped to a given inner stub, or null. */
    public static Component outerPortFor(Component outerNode, Component innerPort) {
        Subgraph sg = of(outerNode);
        if (sg == null) return null;
        return sg.innerToOuterPort().get(innerPort);
    }

    /**
     * Per-component cleanup hook called by
     * {@link sibarum.dasum.gui.core.component.Components#detach}. When
     * {@code c} is a registered subgraph outer node, this drops its entry
     * AND recursively detaches the inner surface so every inner node,
     * inner connection, inner position, inner port, etc. gets cleaned up
     * too. When {@code c} isn't a subgraph, this is a fast no-op.
     * <p>
     * Uses {@link #CLEAR_IN_PROGRESS} to guard against re-entry in the
     * unlikely event that the inner surface somehow contains its own
     * outer node.
     */
    public static void clear(Component c) {
        if (c == null) return;
        Subgraph sg = SUBGRAPHS.remove(c);
        if (sg != null) {
            Invalidator.invalidate();
            if (!CLEAR_IN_PROGRESS.contains(c)) {
                CLEAR_IN_PROGRESS.add(c);
                try {
                    sibarum.dasum.gui.core.component.Components.detach(sg.innerSurface());
                } finally {
                    CLEAR_IN_PROGRESS.remove(c);
                }
            }
            return;
        }
        // c isn't a known outer node — check whether it's an inner surface
        // referenced from an outer node's entry. If so, drop that outer's
        // entry too (the inner surface is gone; the entry is now a zombie
        // pointer). Cost: O(n) over registered subgraphs; n is small in
        // practice (≤ tens) and this branch only fires for components that
        // happen to match an inner surface — most detach calls won't reach
        // here at all.
        boolean changed = false;
        for (java.util.Iterator<Map.Entry<Component, Subgraph>> it = SUBGRAPHS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Component, Subgraph> e = it.next();
            if (e.getValue().innerSurface() == c) {
                it.remove();
                changed = true;
            }
        }
        if (changed) Invalidator.invalidate();
    }

    /**
     * Migrate the subgraph entry from {@code from} to {@code to}. Used by
     * {@link sibarum.dasum.gui.core.component.Components#migrateState} when
     * an app rebuilds an outer node via {@code with*} after attaching the
     * subgraph. The inner surface and port-proxy mappings are reused — only
     * the outer node identity changes.
     */
    public static void migrate(Component from, Component to) {
        Subgraph sg = SUBGRAPHS.get(from);
        if (sg != null) {
            SUBGRAPHS.put(to, sg);
        }
    }

    /**
     * Helper used by {@link SubgraphNodeBuilder} to construct the inverse
     * map externally if needed; exposed so apps building subgraph state by
     * hand can use the same derivation logic.
     */
    public static Map<Component, Component> invertMap(Map<Component, Component> m) {
        Map<Component, Component> inv = new LinkedHashMap<>(m.size());
        for (Map.Entry<Component, Component> e : m.entrySet()) inv.put(e.getValue(), e.getKey());
        return inv;
    }
}
