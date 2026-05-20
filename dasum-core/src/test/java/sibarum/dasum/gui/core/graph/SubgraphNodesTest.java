package sibarum.dasum.gui.core.graph;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Components;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SubgraphNodesTest {

    private static final PortType NUMBER = PortType.of(
        "subgraph.test.number", "Number", new Color(0.85f, 0.55f, 0.25f, 1f));

    @Test
    void buildSubgraphRegistersSidecars() {
        Component outer = SubgraphNodeBuilder.titled("Group")
            .input(NUMBER, "x")
            .output(NUMBER, "y")
            .build();

        // Outer node is a real Component with declared outer ports.
        assertNotNull(Ports.byName(outer, "x"), "outer input port declared");
        assertNotNull(Ports.byName(outer, "y"), "outer output port declared");
        assertEquals(PortDirection.INPUT,  Ports.byName(outer, "x").direction());
        assertEquals(PortDirection.OUTPUT, Ports.byName(outer, "y").direction());

        // SubgraphNodes entry attached.
        SubgraphNodes.Subgraph sg = SubgraphNodes.of(outer);
        assertNotNull(sg, "Subgraph attached");
        assertTrue(SubgraphNodes.isSubgraph(outer));
        assertNotNull(sg.innerSurface());

        // NodeInstance tag attached with the subgraph type id.
        NodeInstances.NodeInstance ni = NodeInstances.of(outer);
        assertNotNull(ni, "NodeInstance attached");
        assertEquals(SubgraphNodeBuilder.SUBGRAPH_TYPE_ID, ni.typeId());
        assertEquals("Group", ni.params().get("title"));

        // Inner stubs exist as children of the inner surface.
        Component.GraphSurface inner = sg.innerSurface();
        List<Component> innerKids = inner.children();
        assertEquals(2, innerKids.size(), "one stub per declared port");

        // Cleanup so identity-keyed sidecar state doesn't leak into other tests.
        Components.detach(outer);
    }

    @Test
    void innerStubPortHasInverseDirection() {
        Component outer = SubgraphNodeBuilder.titled("Group")
            .input(NUMBER, "in")
            .output(NUMBER, "out")
            .build();

        Component outerIn  = Ports.byName(outer, "in").component();
        Component outerOut = Ports.byName(outer, "out").component();

        Component innerStubForIn  = SubgraphNodes.innerPortFor(outer, outerIn);
        Component innerStubForOut = SubgraphNodes.innerPortFor(outer, outerOut);

        assertNotNull(innerStubForIn,  "outer INPUT has paired inner stub");
        assertNotNull(innerStubForOut, "outer OUTPUT has paired inner stub");

        // Outer INPUT ↔ inner OUTPUT stub.
        Ports.Port inStubPort  = Ports.of(innerStubForIn);
        Ports.Port outStubPort = Ports.of(innerStubForOut);
        assertEquals(PortDirection.OUTPUT, inStubPort.direction(),
            "inner stub for outer INPUT is an OUTPUT (source inside subgraph)");
        assertEquals(PortDirection.INPUT, outStubPort.direction(),
            "inner stub for outer OUTPUT is an INPUT (sink inside subgraph)");

        // Reverse lookup.
        assertSame(outerIn,  SubgraphNodes.outerPortFor(outer, innerStubForIn));
        assertSame(outerOut, SubgraphNodes.outerPortFor(outer, innerStubForOut));

        Components.detach(outer);
    }

    @Test
    void detachOuterCascadesIntoInnerSurface() {
        Component outer = SubgraphNodeBuilder.titled("Group")
            .input(NUMBER, "in")
            .build();
        SubgraphNodes.Subgraph sg = SubgraphNodes.of(outer);
        Component.GraphSurface inner = sg.innerSurface();
        Component innerStub = SubgraphNodes.innerPortFor(outer,
            Ports.byName(outer, "in").component());

        // Sanity: inner stub IS a port on the inner surface (transitively).
        assertNotNull(Ports.of(innerStub),
            "inner stub port registered before detach");

        // Detach the outer node — cascade must clean up the inner surface.
        Components.detach(outer);

        assertNull(SubgraphNodes.of(outer), "SubgraphNodes entry cleared");
        assertNull(NodeInstances.of(outer),  "NodeInstance entry cleared");
        assertNull(Ports.of(innerStub),
            "inner stub's Ports entry cleared via cascade");
        assertTrue(Ports.onNode(inner).isEmpty(),
            "no port records remain pointing at the inner surface");
    }

    @Test
    void clearOnPlainNodeIsNoOp() {
        Component plain = new Component.Box(Em.of(1f), Em.of(1f), Em.ZERO,
            new Color(0.5f, 0.5f, 0.5f, 1f));
        // No exception, no state change.
        SubgraphNodes.clear(plain);
        assertNull(SubgraphNodes.of(plain));
    }

    @Test
    void clearOnInnerSurfaceAlsoRemovesParentEntry() {
        Component outer = SubgraphNodeBuilder.titled("Group")
            .input(NUMBER, "in")
            .build();
        Component.GraphSurface inner = SubgraphNodes.of(outer).innerSurface();

        // Call clear directly on the inner surface (as Components.detach
        // would when an app foolishly detaches just the inner surface). The
        // outer node's entry should be removed too so it doesn't become a
        // zombie pointer to a now-empty surface.
        SubgraphNodes.clear(inner);
        assertNull(SubgraphNodes.of(outer),
            "outer entry whose innerSurface == c is dropped when inner is cleared");

        // Cleanup any remaining state from the now-orphaned outer.
        Components.detach(outer);
    }

    @Test
    void migrateRepointsEntry() {
        Component outer = SubgraphNodeBuilder.titled("Group")
            .input(NUMBER, "in")
            .build();
        SubgraphNodes.Subgraph orig = SubgraphNodes.of(outer);
        Component newOuter = new Component.Box(Em.of(1f), Em.of(1f), Em.ZERO,
            new Color(0.1f, 0.2f, 0.3f, 1f));
        SubgraphNodes.migrate(outer, newOuter);
        assertSame(orig, SubgraphNodes.of(newOuter),
            "migrate copies the Subgraph entry to the new outer identity");
        // Cleanup.
        Components.detach(outer);
        Components.detach(newOuter);
    }

    @Test
    void subgraphRejectsNullInnerSurface() {
        try {
            new SubgraphNodes.Subgraph(null, Map.of());
            org.junit.jupiter.api.Assertions.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
