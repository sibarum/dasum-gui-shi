package sibarum.dasum.gui.core.graph;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Identity-keyed sidecar tagging spawned nodes with the {@link NodeTypes}
 * registry id that produced them, plus a snapshot of the params used at
 * spawn time.
 * <p>
 * Existence rationale: the framework's "node" is just a plain
 * {@link Component} (a Flex from {@link NodeBuilder}, or anything else
 * an app constructs). Without this sidecar, a serializer walking a
 * surface can't tell "this is a Constant node" from "this is a
 * Multiply" — every node is just a Flex tree. With it,
 * {@link #of(Component)} returns the type id + param map; apps walk the
 * surface, emit those, and on load call
 * {@link NodeTypes#instantiate(String, Map)} to recover the node.
 * <p>
 * Returning {@code null} for built-in / untagged nodes is fine — the
 * serializer's contract is "skip nodes I can't classify." This sidecar
 * is opt-in via {@link #attach}, which is called automatically by
 * {@link NodeTypes#instantiate}; apps that build nodes outside the
 * registry (e.g. the static {@code CONSTANT_FACTORY} pattern in the
 * demo today) can opt-in by calling {@code attach} themselves.
 * <p>
 * Identity-keyed: an {@code IdentityHashMap} like every other built-in
 * sidecar. Value-equal but distinct Components stay distinct here.
 */
public final class NodeInstances {

    /**
     * The stored instance metadata. Params are an unmodifiable view of the
     * map that was passed to {@link #attach} (or that
     * {@link NodeTypes#instantiate} merged from defaults + caller params)
     * so listeners cannot mutate stored state from underneath the sidecar.
     */
    public record NodeInstance(String typeId, Map<String, Object> params) {
        public NodeInstance {
            if (typeId == null) throw new IllegalArgumentException("typeId must not be null");
            // Defensive copy + unmodifiable wrap so external mutation can't
            // leak through the record (records can't enforce deep immutability
            // themselves). Cheap: param maps are tiny in practice (≤ tens of
            // keys per node) and attach happens once per spawn.
            params = (params == null)
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(params));
        }
    }

    private static final Map<Component, NodeInstance> INSTANCES = new IdentityHashMap<>();

    private NodeInstances() {}

    /**
     * Tag {@code node} with the given type id and params. Overwrites any
     * prior entry for the same node (so apps can re-tag a Component after
     * a {@code with*} rebuild — but prefer
     * {@link sibarum.dasum.gui.core.component.Components#migrateState} for
     * that case so handlers, ports, etc. come along too).
     */
    public static void attach(Component node, String typeId, Map<String, Object> params) {
        if (node == null) throw new IllegalArgumentException("node must not be null");
        INSTANCES.put(node, new NodeInstance(typeId, params));
        Invalidator.invalidate();
    }

    /** Returns the metadata for {@code node}, or {@code null} if untagged. */
    public static NodeInstance of(Component node) {
        if (node == null) return null;
        return INSTANCES.get(node);
    }

    /** Whether {@code node} carries a type tag. */
    public static boolean has(Component node) {
        return node != null && INSTANCES.containsKey(node);
    }

    /**
     * Replace the params on an existing entry. Keeps the same type id.
     * No-op if {@code node} isn't tagged.
     */
    public static void updateParams(Component node, Map<String, Object> newParams) {
        NodeInstance existing = INSTANCES.get(node);
        if (existing == null) return;
        INSTANCES.put(node, new NodeInstance(existing.typeId(), newParams));
        Invalidator.invalidate();
    }

    /**
     * Per-component cleanup hook called by
     * {@link sibarum.dasum.gui.core.component.Components#detach}. Drops the
     * entry keyed by {@code c}. No-op if {@code c} isn't tagged.
     */
    public static void clear(Component c) {
        if (INSTANCES.remove(c) != null) {
            Invalidator.invalidate();
        }
    }

    /** Migrate the tag from {@code from} to {@code to}. No-op if untagged. */
    public static void migrate(Component from, Component to) {
        NodeInstance ni = INSTANCES.get(from);
        if (ni != null) {
            INSTANCES.put(to, ni);
        }
    }
}
