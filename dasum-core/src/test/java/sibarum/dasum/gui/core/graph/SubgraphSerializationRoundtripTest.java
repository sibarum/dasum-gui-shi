package sibarum.dasum.gui.core.graph;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Components;
import sibarum.dasum.gui.core.render.Color;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end serialization-style walk: build a surface containing a
 * subgraph node + a connected sibling. Walk the surface, extract the
 * info a real serializer would persist. Recreate the structure on a
 * second surface via {@link NodeTypes#instantiate}. Assert the recovered
 * structure matches.
 *
 * <p>This is the load-bearing test for "queryable for serialization"
 * — the user's stated requirement for the subgraph feature.
 */
final class SubgraphSerializationRoundtripTest {

    private static final String CONST_ID = "test.const";
    private static final String GROUP_ID = "test.group";

    private static final PortType NUMBER = PortType.of(
        "subgraph.round.number", "Number", new Color(0.85f, 0.55f, 0.25f, 1f));

    @AfterEach
    void tearDown() {
        NodeTypes.unregister(CONST_ID);
        NodeTypes.unregister(GROUP_ID);
    }

    @Test
    void surfaceWalkRecoversTypedNodesAndConnections() {
        // Register two node types: a Constant (plain NodeBuilder) and a
        // Group (SubgraphNodeBuilder).
        NodeTypes.register(NodeTypes.NodeType.simple(CONST_ID, "Constant",
            () -> NodeBuilder.titled("Constant").output(NUMBER, "value").build()));
        NodeTypes.register(NodeTypes.NodeType.simple(GROUP_ID, "Group",
            () -> SubgraphNodeBuilder.titled("Group").input(NUMBER, "x").build()));

        // Build the original surface.
        Component constant = NodeTypes.instantiate(CONST_ID);
        Component group    = NodeTypes.instantiate(GROUP_ID);
        Component.GraphSurface surface = new Component.GraphSurface(
            null, null, new Color(0f, 0f, 0f, 1f),
            List.of(constant, group), true, 0);
        GraphSurfacePositions.set(surface, constant, 1f, 1f);
        GraphSurfacePositions.set(surface, group,    8f, 1f);
        Connections.add(surface,
            Ports.byName(constant, "value").component(),
            Ports.byName(group,    "x").component());

        // ---- WALK (serializer-side) ----
        List<NodeSnapshot> snapshots = new ArrayList<>();
        Map<Component, Integer> nodeIndex = new java.util.IdentityHashMap<>();
        for (Component child : GraphSurfaceChildren.all(surface)) {
            NodeInstances.NodeInstance ni = NodeInstances.of(child);
            if (ni == null) continue;
            GraphSurfacePositions.Pos pos = GraphSurfacePositions.of(surface, child);
            // Capture each port name on this node — what the file format
            // would store so deserialization can resolve connection
            // endpoints by name.
            List<String> portNames = new ArrayList<>();
            for (Ports.Port p : Ports.onNode(child)) portNames.add(p.name());
            snapshots.add(new NodeSnapshot(ni.typeId(), pos.emX(), pos.emY(), portNames));
            nodeIndex.put(child, snapshots.size() - 1);
        }
        List<ConnectionSnapshot> connSnaps = new ArrayList<>();
        for (Connection c : Connections.on(surface)) {
            Ports.Port from = Ports.of(c.from());
            Ports.Port to   = Ports.of(c.to());
            connSnaps.add(new ConnectionSnapshot(
                nodeIndex.get(from.node()), from.name(),
                nodeIndex.get(to.node()),   to.name()));
        }

        // Snapshots captured what we'd write to disk.
        assertEquals(2, snapshots.size(), "two typed nodes");
        assertEquals(1, connSnaps.size(), "one connection");
        assertEquals(CONST_ID, snapshots.get(0).typeId());
        assertEquals(GROUP_ID, snapshots.get(1).typeId());

        // ---- REBUILD (deserializer-side) ----
        List<Component> recreatedNodes = new ArrayList<>();
        Component.GraphSurface recreatedSurface = new Component.GraphSurface(
            null, null, new Color(0f, 0f, 0f, 1f),
            List.of(), true, 0);
        for (NodeSnapshot snap : snapshots) {
            Component node = NodeTypes.instantiate(snap.typeId());
            recreatedNodes.add(node);
            GraphSurfaceChildren.add(recreatedSurface, node);
            GraphSurfacePositions.set(recreatedSurface, node, snap.emX(), snap.emY());
        }
        for (ConnectionSnapshot cs : connSnaps) {
            Component from = Ports.byName(recreatedNodes.get(cs.fromNode()), cs.fromPort()).component();
            Component to   = Ports.byName(recreatedNodes.get(cs.toNode()),   cs.toPort()).component();
            Connections.add(recreatedSurface, from, to);
        }

        // ---- ASSERT EQUIVALENCE ----
        assertEquals(2, GraphSurfaceChildren.all(recreatedSurface).size());
        assertEquals(1, Connections.on(recreatedSurface).size(),
            "recreated surface has one connection");
        // Subgraph node retained its sidecar through instantiation.
        Component recreatedGroup = recreatedNodes.get(1);
        assertNotNull(SubgraphNodes.of(recreatedGroup),
            "second instantiation produces a fresh subgraph entry");
        assertEquals(CONST_ID, NodeInstances.of(recreatedNodes.get(0)).typeId());
        assertEquals(GROUP_ID, NodeInstances.of(recreatedGroup).typeId());

        // Position recovery.
        assertEquals(1f, GraphSurfacePositions.of(recreatedSurface, recreatedNodes.get(0)).emX(),
            0.001f);
        assertEquals(8f, GraphSurfacePositions.of(recreatedSurface, recreatedGroup).emX(),
            0.001f);

        // ---- CLEANUP — both surfaces ----
        Components.detach(surface);
        Components.detach(recreatedSurface);
        // After detach the surfaces themselves are gone from every sidecar.
        assertTrue(GraphSurfaceChildren.all(surface).size() == 0
            || GraphSurfaceChildren.all(surface).stream().allMatch(n -> NodeInstances.of(n) == null),
            "post-detach: typed-node references no longer resolve");
    }

    private record NodeSnapshot(String typeId, float emX, float emY, List<String> portNames) {
        NodeSnapshot { portNames = List.copyOf(portNames); }
    }
    private record ConnectionSnapshot(int fromNode, String fromPort, int toNode, String toPort) {}
}
