package sibarum.dasum.gui.core.graph;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NodeTypesRegistryTest {

    @AfterEach
    void tearDown() {
        for (String id : List.of("test.alpha", "test.beta", "test.param", "test.dup")) {
            NodeTypes.unregister(id);
        }
    }

    private static Component leaf(Color c) {
        return new Component.Box(Em.of(1f), Em.of(1f), Em.ZERO, c);
    }

    @Test
    void registerAndByIdRoundtrip() {
        NodeTypes.NodeType type = NodeTypes.NodeType.simple(
            "test.alpha", "Alpha", () -> leaf(new Color(1f, 0f, 0f, 1f)));
        NodeTypes.register(type);
        assertSame(type, NodeTypes.byId("test.alpha"));
    }

    @Test
    void byIdReturnsNullForUnknown() {
        assertNull(NodeTypes.byId("does.not.exist"));
        assertNull(NodeTypes.byId(null));
    }

    @Test
    void instantiateThrowsForUnknownId() {
        assertThrows(IllegalArgumentException.class,
            () -> NodeTypes.instantiate("does.not.exist"));
    }

    @Test
    void instantiateReturnsFreshComponentEachCall() {
        NodeTypes.register(NodeTypes.NodeType.simple(
            "test.alpha", "Alpha", () -> leaf(new Color(0f, 1f, 0f, 1f))));
        Component a = NodeTypes.instantiate("test.alpha");
        Component b = NodeTypes.instantiate("test.alpha");
        assertNotNull(a);
        assertNotNull(b);
        assertNotSame(a, b, "each instantiate produces a distinct Component identity");
    }

    @Test
    void instantiateAttachesNodeInstancesTag() {
        NodeTypes.register(NodeTypes.NodeType.simple(
            "test.alpha", "Alpha", () -> leaf(new Color(0f, 0f, 1f, 1f))));
        Component c = NodeTypes.instantiate("test.alpha");
        NodeInstances.NodeInstance ni = NodeInstances.of(c);
        assertNotNull(ni, "NodeInstance attached automatically by instantiate");
        assertEquals("test.alpha", ni.typeId());
        // Defaults map is empty for a simple type with no defaults.
        assertTrue(ni.params().isEmpty(), "no params -> empty map");
        NodeInstances.clear(c);
    }

    @Test
    void parameterizedFactoryMergesDefaultsAndParams() {
        NodeTypes.register(NodeTypes.NodeType.parameterized(
            "test.param", "Param",
            params -> leaf(new Color(0.5f, 0.5f, 0.5f, 1f)),
            Map.of("color", "red", "size", 12)));
        Component c = NodeTypes.instantiate("test.param", Map.of("color", "blue"));
        NodeInstances.NodeInstance ni = NodeInstances.of(c);
        assertNotNull(ni);
        // Caller's "color" overrides default; default "size" is retained.
        assertEquals("blue", ni.params().get("color"));
        assertEquals(12, ni.params().get("size"));
        NodeInstances.clear(c);
    }

    @Test
    void allReturnsInsertionOrder() {
        NodeTypes.register(NodeTypes.NodeType.simple("test.alpha", "Alpha",
            () -> leaf(new Color(0f, 0f, 0f, 1f))));
        NodeTypes.register(NodeTypes.NodeType.simple("test.beta", "Beta",
            () -> leaf(new Color(0f, 0f, 0f, 1f))));
        List<NodeTypes.NodeType> all = NodeTypes.all();
        // The list may include types registered by other tests' setUp,
        // but our two should appear in order.
        int alphaIdx = -1, betaIdx = -1;
        for (int i = 0; i < all.size(); i++) {
            if ("test.alpha".equals(all.get(i).id())) alphaIdx = i;
            if ("test.beta".equals(all.get(i).id()))  betaIdx  = i;
        }
        assertTrue(alphaIdx >= 0 && betaIdx >= 0, "both registered types present");
        assertTrue(alphaIdx < betaIdx, "registration order preserved");
    }

    @Test
    void unregisterRemovesType() {
        NodeTypes.register(NodeTypes.NodeType.simple("test.alpha", "Alpha",
            () -> leaf(new Color(0f, 0f, 0f, 1f))));
        assertNotNull(NodeTypes.byId("test.alpha"));
        NodeTypes.unregister("test.alpha");
        assertNull(NodeTypes.byId("test.alpha"));
    }

    @Test
    void registerOverwritesPreviousEntry() {
        NodeTypes.NodeType first  = NodeTypes.NodeType.simple("test.dup", "First",
            () -> leaf(new Color(0f, 0f, 0f, 1f)));
        NodeTypes.NodeType second = NodeTypes.NodeType.simple("test.dup", "Second",
            () -> leaf(new Color(1f, 1f, 1f, 1f)));
        NodeTypes.register(first);
        NodeTypes.register(second);
        assertSame(second, NodeTypes.byId("test.dup"),
            "second registration overwrites first (matches CommandRegistry behavior)");
    }

    @Test
    void instantiateThrowsIfFactoryReturnsNull() {
        NodeTypes.register(NodeTypes.NodeType.simple("test.alpha", "Alpha", () -> null));
        assertThrows(IllegalStateException.class, () -> NodeTypes.instantiate("test.alpha"));
    }
}
