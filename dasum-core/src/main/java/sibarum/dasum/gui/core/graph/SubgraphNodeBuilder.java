package sibarum.dasum.gui.core.graph;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sugar for building subgraph (group) nodes — runtime-creatable node
 * types whose contents are a sub-graph rather than a fixed visual.
 * Produces:
 * <ul>
 *   <li>An <b>outer node</b> Component, built via {@link NodeBuilder} for
 *       visual parity with normal nodes (title + inputs on the left +
 *       outputs on the right).</li>
 *   <li>An <b>inner {@link Component.GraphSurface}</b> registered in
 *       {@link SubgraphNodes} (hidden by default; apps mount it into an
 *       overlay on expand).</li>
 *   <li>An <b>inner stub port</b> for each declared outer port — a tiny
 *       single-port node sitting on the inner surface, with the inverse
 *       direction so wires inside the sub-graph land on it naturally
 *       (outer INPUT ↔ inner OUTPUT stub, outer OUTPUT ↔ inner INPUT stub).
 *       The mapping outer-port ↔ inner-stub-port is stored in
 *       {@link SubgraphNodes} so serializers / connection-renderer / apps
 *       can resolve the proxy.</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * Component group = SubgraphNodeBuilder.titled("Group")
 *     .input(NUMBER, "x")
 *     .output(NUMBER, "y")
 *     .build();
 * GraphSurfacePositions.set(surface, group, 4f, 4f);
 * GraphSurfaceChildren.add(surface, group);
 * }</pre>
 *
 * <p>The returned outer node carries:
 * <ul>
 *   <li>{@link Ports} declarations for its visible (outer) ports.</li>
 *   <li>A {@link NodeInstances} tag with {@code typeId="subgraph"} and
 *       {@code params={ "title": ... }} so apps walking the surface for
 *       serialization see it as a typed node. Apps that want a stronger
 *       type id can call {@link NodeInstances#attach} again after build.</li>
 *   <li>A {@link SubgraphNodes} entry holding the inner surface + proxy
 *       mappings. This is what makes the node "subgraph-shaped."</li>
 * </ul>
 *
 * <p>The outer node is a plain {@link Component.Flex}; the framework does
 * not need a new sealed-variant for subgraph nodes — the sidecar is enough
 * to distinguish them.
 */
public final class SubgraphNodeBuilder {

    /** Type id attached to {@link NodeInstances} for subgraph nodes. */
    public static final String SUBGRAPH_TYPE_ID = "subgraph";

    private final String title;
    private final List<NodeBuilder.PortSpec> inputs  = new ArrayList<>();
    private final List<NodeBuilder.PortSpec> outputs = new ArrayList<>();

    private Color outerBackground = new Color(0.24f, 0.28f, 0.38f, 1f);
    private Color innerSurfaceBg  = new Color(0.06f, 0.08f, 0.12f, 1f);
    private Color stubBackground  = new Color(0.18f, 0.22f, 0.30f, 1f);

    private SubgraphNodeBuilder(String title) { this.title = title; }

    public static SubgraphNodeBuilder titled(String title) { return new SubgraphNodeBuilder(title); }

    public SubgraphNodeBuilder input(PortType type, String name) {
        inputs.add(new NodeBuilder.PortSpec(type, name));
        return this;
    }
    public SubgraphNodeBuilder output(PortType type, String name) {
        outputs.add(new NodeBuilder.PortSpec(type, name));
        return this;
    }
    public SubgraphNodeBuilder outerBackground(Color c) { this.outerBackground = c; return this; }
    public SubgraphNodeBuilder innerSurfaceBg(Color c)  { this.innerSurfaceBg  = c; return this; }
    public SubgraphNodeBuilder stubBackground(Color c)  { this.stubBackground  = c; return this; }

    /**
     * Build the outer node + inner surface + stubs, and register everything
     * with the framework sidecars. Returns the outer node.
     */
    public Component build() {
        // ---- 1. Outer node via NodeBuilder. ----
        NodeBuilder outer = NodeBuilder.titled(title).background(outerBackground);
        for (NodeBuilder.PortSpec in  : inputs)  outer.input(in.type(),  in.label());
        for (NodeBuilder.PortSpec out : outputs) outer.output(out.type(), out.label());
        Component outerNode = outer.build();

        // After NodeBuilder.build(), every declared port is in Ports.onNode(outerNode)
        // in declaration order — inputs first, then outputs. Lookups by name
        // give us identity-stable references to the outer port Components.
        Map<Component, Component> outerToInner = new IdentityHashMap<>();
        List<Component> inputStubNodes  = new ArrayList<>(inputs.size());
        List<Component> outputStubNodes = new ArrayList<>(outputs.size());

        // ---- 2. Inner stub nodes (one per declared outer port). ----
        // Stubs are tiny single-port nodes; the stub's port direction is
        // the INVERSE of the outer's so the inner sub-graph wires
        // terminate at it naturally (an outer-INPUT stub is a SOURCE on
        // the inner surface, etc).
        for (NodeBuilder.PortSpec spec : inputs) {
            Component stubNode = NodeBuilder.titled(spec.label() + " ←")
                .output(spec.type(), spec.label())
                .background(stubBackground)
                .build();
            inputStubNodes.add(stubNode);
            // Find the stub's single port and pair it with the outer
            // port of the same name on the outer node.
            Ports.Port stubPort  = Ports.byName(stubNode, spec.label());
            Ports.Port outerPort = Ports.byName(outerNode, spec.label());
            if (stubPort != null && outerPort != null) {
                outerToInner.put(outerPort.component(), stubPort.component());
            }
        }
        for (NodeBuilder.PortSpec spec : outputs) {
            Component stubNode = NodeBuilder.titled("→ " + spec.label())
                .input(spec.type(), spec.label())
                .background(stubBackground)
                .build();
            outputStubNodes.add(stubNode);
            Ports.Port stubPort  = Ports.byName(stubNode, spec.label());
            Ports.Port outerPort = Ports.byName(outerNode, spec.label());
            if (stubPort != null && outerPort != null) {
                outerToInner.put(outerPort.component(), stubPort.component());
            }
        }

        // ---- 3. Inner GraphSurface holding the stubs as declared children. ----
        // null width/height → fills whatever overlay container the app mounts
        // it into (matches the demo's full-screen modal pattern).
        // interactive=true so right-clicks land on the surface for app-side
        // node-spawning context menus.
        List<Component> innerChildren = new ArrayList<>(inputStubNodes.size() + outputStubNodes.size());
        innerChildren.addAll(inputStubNodes);
        innerChildren.addAll(outputStubNodes);
        Component.GraphSurface innerSurface = new Component.GraphSurface(
            null, null, innerSurfaceBg, innerChildren, true, 0
        );

        // ---- 4. Pre-position the stubs down the left/right edges. ----
        // Em positions are arbitrary defaults; apps can rewrite via the
        // standard GraphSurfacePositions.set after build.
        float leftEmX = 2f, rightEmX = 28f, stepY = 4f;
        float yIn = 2f;
        for (Component stub : inputStubNodes) {
            GraphSurfacePositions.set(innerSurface, stub, leftEmX, yIn);
            yIn += stepY;
        }
        float yOut = 2f;
        for (Component stub : outputStubNodes) {
            GraphSurfacePositions.set(innerSurface, stub, rightEmX, yOut);
            yOut += stepY;
        }

        // ---- 5. Sidecar attaches. ----
        SubgraphNodes.attach(outerNode, new SubgraphNodes.Subgraph(innerSurface, outerToInner));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("title", title);
        NodeInstances.attach(outerNode, SUBGRAPH_TYPE_ID, params);

        return outerNode;
    }
}
