package sibarum.dasum.gui.core.graph;

import sibarum.dasum.gui.core.component.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Process-global registry of "node types" — string-id-keyed factories for
 * building nodes at runtime. Mirrors {@code CommandRegistry}'s shape (open
 * registry, insertion-ordered iteration, case-sensitive id lookup).
 * <p>
 * A {@link NodeType} bundles three things every "make me a node of this
 * shape" call needs:
 * <ul>
 *   <li>A {@link Supplier#get factory} that builds the bare Component (the
 *       same kind of {@code () -> NodeBuilder.titled(...).build()} pattern
 *       apps use today, just named and discoverable by id).</li>
 *   <li>A {@code Function<Map<String,Object>, Component>} parameterised
 *       factory — the v2 surface for nodes whose appearance depends on
 *       runtime params (e.g. "Constant" with a different default value, or
 *       a "CSV-source" node whose inner graph mirrors the imported file).
 *       Apps that don't need params register a no-arg supplier and the
 *       registry adapts.</li>
 *   <li>Display metadata: human-readable label + optional icon name. The
 *       label is what the command palette / right-click menu shows; the
 *       icon name is an opaque string for the app to interpret (e.g. a
 *       Lucide glyph constant).</li>
 * </ul>
 * <p>
 * The registry does NOT do serialization itself. App-side serialization
 * walks a surface, reads {@link NodeInstances#of(Component)} for each
 * node's type id + params, then on load calls
 * {@link #instantiate(String, Map)} with the recovered id+params to
 * reproduce the node. See {@link NodeInstances} for the per-instance
 * sidecar that pairs with this registry.
 * <p>
 * Lifetime: a registered {@link NodeType} lives for the JVM lifetime, like
 * {@code CommandRegistry}'s entries. {@link #unregister(String)} exists for
 * tests; production code generally registers once at startup.
 */
public final class NodeTypes {

    /**
     * A registered node type. Either the {@link #parameterizedFactory} or
     * the simple {@link #factory} produces the Component; the registry
     * picks {@code parameterizedFactory} when present and falls back to
     * {@code factory.get()} when null.
     *
     * @param id                    unique stable id (used in serialization)
     * @param label                 human-readable label for menus
     * @param iconName              opaque icon hint for the app (may be null)
     * @param factory               no-arg factory (may be null if parameterized supplied)
     * @param parameterizedFactory  param-aware factory (may be null if simple supplied)
     * @param defaults              default param map passed to {@code instantiate}
     *                              when the caller's params don't supply a key;
     *                              never null (use {@link Map#of()} if empty)
     */
    public record NodeType(
        String id,
        String label,
        String iconName,
        Supplier<Component> factory,
        Function<Map<String, Object>, Component> parameterizedFactory,
        Map<String, Object> defaults
    ) {
        public NodeType {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("NodeType.id must be non-blank");
            }
            if (factory == null && parameterizedFactory == null) {
                throw new IllegalArgumentException("NodeType " + id + " must supply at least one factory");
            }
            if (defaults == null) defaults = Map.of();
            if (label == null) label = id;
        }

        /** Convenience for the no-param case. */
        public static NodeType simple(String id, String label, Supplier<Component> factory) {
            return new NodeType(id, label, null, factory, null, Map.of());
        }

        /** Convenience for params-with-defaults. */
        public static NodeType parameterized(String id, String label,
                                             Function<Map<String, Object>, Component> factory,
                                             Map<String, Object> defaults) {
            return new NodeType(id, label, null, null, factory, defaults);
        }
    }

    // LinkedHashMap preserves registration order (matches CommandRegistry).
    private static final Map<String, NodeType> TYPES = new LinkedHashMap<>();

    private NodeTypes() {}

    /**
     * Register a node type. Overwrites any prior registration under the
     * same id (last-write-wins, matching {@code CommandRegistry.register}).
     */
    public static void register(NodeType type) {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        TYPES.put(type.id(), type);
    }

    /** Remove the registration for {@code id}. No-op if absent. */
    public static void unregister(String id) {
        if (id == null) return;
        TYPES.remove(id);
    }

    /** Returns the registered NodeType or {@code null} if none. */
    public static NodeType byId(String id) {
        if (id == null) return null;
        return TYPES.get(id);
    }

    /** All registered types in registration order. */
    public static List<NodeType> all() {
        return new ArrayList<>(TYPES.values());
    }

    /**
     * Instantiate a node of the given type. Calls the parameterized factory
     * when present with {@code defaults} merged with {@code params} (params
     * win on key collision); otherwise calls the no-arg factory. Returns
     * the produced Component AND attaches a {@link NodeInstances} record
     * tagging the node with its type id and the resolved param map, so the
     * node is queryable for serialization without the caller having to
     * remember to register it.
     *
     * <p>Apps that want to override the attached params (e.g. record an
     * extra non-default param post-build) can call
     * {@link NodeInstances#updateParams(Component, Map)} after this returns.
     *
     * @throws IllegalArgumentException if {@code id} isn't registered
     */
    public static Component instantiate(String id, Map<String, Object> params) {
        NodeType type = byId(id);
        if (type == null) {
            throw new IllegalArgumentException("NodeType not registered: " + id);
        }
        Map<String, Object> merged = new LinkedHashMap<>(type.defaults());
        if (params != null) merged.putAll(params);
        Component node = (type.parameterizedFactory() != null)
            ? type.parameterizedFactory().apply(Collections.unmodifiableMap(merged))
            : type.factory().get();
        if (node == null) {
            throw new IllegalStateException("Factory for NodeType " + id + " returned null");
        }
        NodeInstances.attach(node, id, merged);
        return node;
    }

    /** Convenience: instantiate with no params (uses defaults only). */
    public static Component instantiate(String id) {
        return instantiate(id, Map.of());
    }
}
