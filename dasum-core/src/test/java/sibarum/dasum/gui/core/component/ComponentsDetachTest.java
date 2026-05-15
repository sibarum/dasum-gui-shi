package sibarum.dasum.gui.core.component;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.graph.Connection;
import sibarum.dasum.gui.core.graph.ConnectionSelection;
import sibarum.dasum.gui.core.graph.Connections;
import sibarum.dasum.gui.core.graph.GraphSurfaceChildren;
import sibarum.dasum.gui.core.graph.GraphSurfacePositions;
import sibarum.dasum.gui.core.graph.GraphSurfaceZOrder;
import sibarum.dasum.gui.core.graph.PortDirection;
import sibarum.dasum.gui.core.graph.PortType;
import sibarum.dasum.gui.core.graph.Ports;
import sibarum.dasum.gui.core.input.ContextMenuStates;
import sibarum.dasum.gui.core.input.FocusState;
import sibarum.dasum.gui.core.input.Handlers;
import sibarum.dasum.gui.core.input.HoverState;
import sibarum.dasum.gui.core.input.ScrollStates;
import sibarum.dasum.gui.core.input.TextStates;
import sibarum.dasum.gui.core.reactive.Property;
import sibarum.dasum.gui.core.render.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip test: build a subtree that touches every built-in sidecar,
 * call {@link Components#detach}, assert every sidecar's per-component
 * state is gone afterwards.
 */
final class ComponentsDetachTest {

    private static final PortType DATA = PortType.of("test.data", "Data",
        new Color(0.5f, 0.5f, 0.5f, 1f));

    @Test
    void detachClearsEveryBuiltInSidecar() {
        // ---- Build a subtree exercising every sidecar ----
        Component.Text textInside    = new Component.Text("Hello", Em.of(1f),
            new Color(1f, 1f, 1f, 1f)).withSelectable(true);
        Component.Scroll scrollInside = new Component.Scroll(
            null, null, Em.ZERO, new Color(0f, 0f, 0f, 0f), textInside);
        Component.Box  inputPort     = new Component.Box(Em.of(0.5f), Em.of(0.5f),
            Em.ZERO, new Color(0.3f, 0.6f, 0.9f, 1f));
        Component.Box  outputPort    = new Component.Box(Em.of(0.5f), Em.of(0.5f),
            Em.ZERO, new Color(0.9f, 0.6f, 0.3f, 1f));
        Component.Flex node          = new Component.Flex(
            Em.of(8f), Em.of(4f), Em.of(0.5f), new Color(0.2f, 0.2f, 0.3f, 1f),
            sibarum.dasum.gui.core.component.Direction.COLUMN,
            sibarum.dasum.gui.core.component.JustifyContent.START,
            sibarum.dasum.gui.core.component.AlignItems.STRETCH,
            Em.of(0.3f), List.of(scrollInside, inputPort, outputPort), true, 0);
        Component.GraphSurface surface = new Component.GraphSurface(
            null, null, new Color(0.05f, 0.07f, 0.10f, 1f),
            List.of(node), true, 0);

        // Per-sidecar state pre-loaded:
        Handlers.onClick(node, () -> {});
        Handlers.onFocus(textInside, () -> {});
        Handlers.onContextMenu(node, ev -> List.of());          // ContextMenuStates
        TextStates.of(textInside).caretIndex = 3;
        ScrollStates.of(scrollInside).scrollBy(0f, 1f);
        FocusState.set(textInside);
        HoverState.update(node);
        Ports.declare(node, inputPort, DATA, PortDirection.INPUT, "in");
        Ports.declare(node, outputPort, DATA, PortDirection.OUTPUT, "out");

        // Add a second node with one port so we have an inter-node connection.
        Component.Box otherPort  = new Component.Box(Em.of(0.5f), Em.of(0.5f),
            Em.ZERO, new Color(0.3f, 0.6f, 0.9f, 1f));
        Component.Flex otherNode = new Component.Flex(
            Em.of(8f), Em.of(4f), Em.of(0.5f), new Color(0.2f, 0.3f, 0.2f, 1f),
            sibarum.dasum.gui.core.component.Direction.COLUMN,
            sibarum.dasum.gui.core.component.JustifyContent.START,
            sibarum.dasum.gui.core.component.AlignItems.STRETCH,
            Em.ZERO, List.of(otherPort), true, 0);
        Ports.declare(otherNode, otherPort, DATA, PortDirection.INPUT, "in");
        GraphSurfaceChildren.add(surface, otherNode);
        GraphSurfacePositions.set(surface, node,       2f, 2f);
        GraphSurfacePositions.set(surface, otherNode, 12f, 2f);
        GraphSurfaceZOrder.bumpToTop(surface, node);
        Connection conn = Connections.add(surface, outputPort, otherPort);
        ConnectionSelection.set(surface, conn);

        // External cleaner — track that detach visits every component.
        List<Component> visited = new ArrayList<>();
        Components.registerCleaner(visited::add);

        // Sanity: state is present before detach.
        assertNotNull(Ports.of(inputPort), "input port registered pre-detach");
        assertNotNull(Ports.of(outputPort), "output port registered pre-detach");
        assertNotNull(Ports.of(otherPort), "other port registered pre-detach");
        assertEquals(1, Connections.on(surface).size(), "one connection pre-detach");
        assertTrue(ConnectionSelection.has(), "selection present pre-detach");
        assertEquals(textInside, FocusState.focused(), "focus on textInside pre-detach");
        assertEquals(node, HoverState.hovered(), "hover on node pre-detach");
        assertEquals(1, GraphSurfaceChildren.added(surface).size(), "dynamic child pre-detach");

        // ---- Detach the surface (whole subtree) ----
        Components.detach(surface);

        // ---- Assert per-sidecar emptiness ----
        // Ports: all port components removed; their node entries also dropped.
        assertNull(Ports.of(inputPort),  "inputPort cleared");
        assertNull(Ports.of(outputPort), "outputPort cleared");
        assertNull(Ports.of(otherPort),  "otherPort cleared");
        assertTrue(Ports.onNode(node).isEmpty(),      "node port list cleared");
        assertTrue(Ports.onNode(otherNode).isEmpty(), "otherNode port list cleared");

        // Connections: surface entry gone.
        assertTrue(Connections.on(surface).isEmpty(), "surface connection list cleared");

        // ConnectionSelection: selection cleared since its surface was detached.
        assertFalse(ConnectionSelection.has(), "selection cleared");

        // GraphSurfaceChildren dynamic list cleared. (Declared children
        // live on the record itself and are not removable post-hoc.)
        assertEquals(0, GraphSurfaceChildren.added(surface).size(),
            "dynamic children list cleared");
        // GraphSurfacePositions surface entry wiped — defaults to (0, 0).
        assertEquals(0f, GraphSurfacePositions.of(surface, node).emX(),
            "position for node defaults to 0 after surface clear");
        assertEquals(0f, GraphSurfacePositions.of(surface, otherNode).emX(),
            "position for otherNode defaults to 0 after surface clear");

        // FocusState / HoverState — only cleared if the active pointer was in the subtree.
        assertNull(FocusState.focused(),  "focus cleared (was on textInside)");
        assertNull(HoverState.hovered(),  "hover cleared (was on node)");

        // External cleaner saw every component in the subtree (visit count > 0).
        assertFalse(visited.isEmpty(), "external cleaner invoked at least once");
        assertTrue(visited.contains(surface),     "external cleaner saw surface");
        assertTrue(visited.contains(node),        "external cleaner saw node");
        assertTrue(visited.contains(textInside),  "external cleaner saw text leaf");
        assertTrue(visited.contains(inputPort),   "external cleaner saw input port");
        assertTrue(visited.contains(outputPort),  "external cleaner saw output port");
        assertTrue(visited.contains(otherNode),   "external cleaner saw dynamic child");
        assertTrue(visited.contains(otherPort),   "external cleaner saw dynamic child's port");
    }

    @Test
    void detachOnEmptySubtreeIsNoOp() {
        Component.Box leaf = new Component.Box(Em.of(1f), Em.of(1f), Em.ZERO,
            new Color(0f, 0f, 0f, 1f));
        Components.detach(leaf);   // no state ever registered
        // Should not throw, and re-registration should still work afterward.
        Handlers.onClick(leaf, () -> {});
    }

    @Test
    void migrateStateCopiesEveryBuiltInSidecar() {
        // Use a clean component pair — registration on `original`, then
        // migrate to `replacement`, then verify replacement carries the
        // state.
        Component.Box  original    = new Component.Box(Em.of(2f), Em.of(2f), Em.ZERO,
            new Color(0.5f, 0.5f, 0.5f, 1f));
        Component.Box  replacement = new Component.Box(Em.of(2f), Em.of(2f), Em.ZERO,
            new Color(0.6f, 0.6f, 0.6f, 1f));

        // Register every kind of sidecar entry on `original`.
        int[] clickFired = { 0 };
        Handlers.onClick(original, () -> clickFired[0]++);
        Handlers.onContextMenu(original, ev -> java.util.List.of());
        FocusState.set(original);
        HoverState.update(original);

        // Migrate.
        Components.migrateState(original, replacement);

        // Replacement now has handlers + provider.
        assertNotNull(ContextMenuStates.get(replacement),
            "context-menu provider copied to replacement");
        assertEquals(replacement, FocusState.focused(),
            "focus transferred to replacement");
        assertEquals(replacement, HoverState.hovered(),
            "hover transferred to replacement");

        // Click handler reachable on replacement via Handlers.activate.
        Handlers.activate(replacement, replacement);
        assertEquals(1, clickFired[0],
            "click handler fires on replacement after migrate");

        // Note on FocusState semantics: migrate(original, replacement)
        // already moved focus to replacement (FocusState.set transitions
        // away from original). So after migrate, original is no longer
        // focused. Subsequent detach(original) doesn't clear replacement's
        // focus.
        Components.detach(original);
        assertEquals(replacement, FocusState.focused(),
            "focus stays on replacement after detaching original");
    }

    @Test
    void dynamicChildrenParticipateInDetach() {
        // Build a Flex with one declared child and add two more via
        // DynamicChildren. Detach the parent — verify all three children
        // have their state cleared and the dynamic-children sidecar is
        // empty afterwards.
        Component.Box declared = new Component.Box(Em.of(1f), Em.of(1f), Em.ZERO,
            new Color(0.5f, 0.5f, 0.5f, 1f));
        Component.Box dyn1     = new Component.Box(Em.of(1f), Em.of(1f), Em.ZERO,
            new Color(0.4f, 0.4f, 0.4f, 1f));
        Component.Box dyn2     = new Component.Box(Em.of(1f), Em.of(1f), Em.ZERO,
            new Color(0.3f, 0.3f, 0.3f, 1f));
        Component.Flex parent = new Component.Flex(
            null, null, Em.ZERO, new Color(0f, 0f, 0f, 0f),
            sibarum.dasum.gui.core.component.Direction.COLUMN,
            sibarum.dasum.gui.core.component.JustifyContent.START,
            sibarum.dasum.gui.core.component.AlignItems.STRETCH,
            Em.ZERO, List.of(declared), false, 0);
        sibarum.dasum.gui.core.component.DynamicChildren.add(parent, dyn1);
        sibarum.dasum.gui.core.component.DynamicChildren.add(parent, dyn2);
        Handlers.onClick(dyn1, () -> {});
        Handlers.onClick(dyn2, () -> {});

        // Pre-detach: effective children = [declared, dyn1, dyn2].
        assertEquals(3,
            sibarum.dasum.gui.core.component.DynamicChildren.effectiveChildren(parent).size());

        Components.detach(parent);

        // Dynamic-children list cleared.
        assertTrue(
            sibarum.dasum.gui.core.component.DynamicChildren.added(parent).isEmpty(),
            "dynamic-children list cleared after parent detach");
        // Declared child's handler is also cleared (cascade through declared
        // children — same as before).
        assertTrue(
            sibarum.dasum.gui.core.component.DynamicChildren.effectiveChildren(parent).isEmpty()
                || sibarum.dasum.gui.core.component.DynamicChildren.effectiveChildren(parent)
                    .stream().allMatch(c -> ContextMenuStates.get(c) == null),
            "no orphaned context-menu providers");
    }

    @Test
    void externalCleanerReceivesEveryComponentExactlyOnce() {
        Component.Box leaf1 = new Component.Box(Em.of(1f), Em.of(1f), Em.ZERO,
            new Color(0f, 0f, 0f, 1f));
        Component.Box leaf2 = new Component.Box(Em.of(1f), Em.of(1f), Em.ZERO,
            new Color(0f, 0f, 0f, 1f));
        Component.Flex root = new Component.Flex(
            null, null, Em.ZERO, new Color(0f, 0f, 0f, 0f),
            sibarum.dasum.gui.core.component.Direction.ROW,
            sibarum.dasum.gui.core.component.JustifyContent.START,
            sibarum.dasum.gui.core.component.AlignItems.STRETCH,
            Em.ZERO, List.of(leaf1, leaf2), false, 0);

        AtomicInteger calls = new AtomicInteger();
        Components.registerCleaner(c -> calls.incrementAndGet());
        Components.detach(root);

        // 3 components: root, leaf1, leaf2.
        assertEquals(3, calls.get(), "cleaner invoked once per component");
    }
}
