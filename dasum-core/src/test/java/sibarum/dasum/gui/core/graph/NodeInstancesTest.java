package sibarum.dasum.gui.core.graph;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NodeInstancesTest {

    private static Component leaf() {
        return new Component.Box(Em.of(1f), Em.of(1f), Em.ZERO, new Color(0.5f, 0.5f, 0.5f, 1f));
    }

    @Test
    void ofReturnsNullForUntaggedNode() {
        Component c = leaf();
        assertNull(NodeInstances.of(c));
        assertNull(NodeInstances.of(null));
        assertFalse(NodeInstances.has(c));
    }

    @Test
    void attachThenOfRoundtrip() {
        Component c = leaf();
        NodeInstances.attach(c, "type.alpha", Map.of("k", "v"));
        NodeInstances.NodeInstance ni = NodeInstances.of(c);
        assertNotNull(ni);
        assertEquals("type.alpha", ni.typeId());
        assertEquals("v", ni.params().get("k"));
        assertTrue(NodeInstances.has(c));
        NodeInstances.clear(c);
    }

    @Test
    void paramsMapIsImmutableSnapshot() {
        Component c = leaf();
        Map<String, Object> live = new LinkedHashMap<>();
        live.put("k", "v0");
        NodeInstances.attach(c, "type.alpha", live);
        // External mutation of the source map must NOT leak through to the
        // stored entry.
        live.put("k", "MUTATED");
        live.put("new", "key");
        Map<String, Object> stored = NodeInstances.of(c).params();
        assertEquals("v0", stored.get("k"), "stored params snapshotted at attach time");
        assertNull(stored.get("new"), "post-attach mutation does not leak");
        // The stored map must be unmodifiable.
        assertThrows(UnsupportedOperationException.class,
            () -> stored.put("x", "y"),
            "stored params map is unmodifiable");
        NodeInstances.clear(c);
    }

    @Test
    void updateParamsKeepsTypeIdReplacesParams() {
        Component c = leaf();
        NodeInstances.attach(c, "type.alpha", Map.of("k", "old"));
        NodeInstances.updateParams(c, Map.of("k", "new"));
        NodeInstances.NodeInstance ni = NodeInstances.of(c);
        assertEquals("type.alpha", ni.typeId(), "type id preserved");
        assertEquals("new", ni.params().get("k"), "params replaced");
        NodeInstances.clear(c);
    }

    @Test
    void updateParamsNoOpForUntagged() {
        Component c = leaf();
        NodeInstances.updateParams(c, Map.of("k", "v"));
        assertNull(NodeInstances.of(c), "no entry created for untagged node");
    }

    @Test
    void clearRemovesEntry() {
        Component c = leaf();
        NodeInstances.attach(c, "type.alpha", Map.of());
        NodeInstances.clear(c);
        assertNull(NodeInstances.of(c));
        assertFalse(NodeInstances.has(c));
    }

    @Test
    void migrateMovesEntry() {
        Component from = leaf();
        Component to   = leaf();
        NodeInstances.attach(from, "type.alpha", Map.of("k", "v"));
        NodeInstances.migrate(from, to);
        NodeInstances.NodeInstance fromNi = NodeInstances.of(from);
        NodeInstances.NodeInstance toNi   = NodeInstances.of(to);
        // Per other sidecars (Ports, Handlers, etc.), migrate copies state to
        // the new identity; the original entry stays in place. The caller is
        // responsible for follow-up detach if they want the original gone.
        assertSame(fromNi, toNi, "migrate copies the same NodeInstance record to the new identity");
        NodeInstances.clear(from);
        NodeInstances.clear(to);
    }

    @Test
    void attachRejectsNullNode() {
        assertThrows(IllegalArgumentException.class,
            () -> NodeInstances.attach(null, "type.alpha", Map.of()));
    }

    @Test
    void instanceRejectsNullTypeId() {
        assertThrows(IllegalArgumentException.class,
            () -> new NodeInstances.NodeInstance(null, Map.of()));
    }
}
