package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.graph.Connection;
import sibarum.dasum.gui.core.graph.ConnectionSelection;
import sibarum.dasum.gui.core.graph.Connections;
import sibarum.dasum.gui.core.graph.Ports;

import java.util.List;

/**
 * Default context menu for declared {@link Ports.Port ports} on a
 * {@link Component.GraphSurface}. Mirror of {@link TextContextMenu} for the
 * port domain: framework supplies the helper, apps opt in per-port (or
 * blanket-wire via {@link #registerDefaults}). NodeBuilder doesn't auto-wire
 * — that would violate the "context menus are opt-in by default" rule for
 * non-text components.
 * <p>
 * Single default item: <b>Disconnect</b> — removes every connection
 * incident on the port. Label shows the count when {@code > 1}
 * (e.g. "Disconnect (3)"). The item renders disabled when the port has no
 * connections so the menu still indicates "this port supports disconnect,
 * just nothing to disconnect right now."
 * <p>
 * The owning surface is resolved from the {@link ContextEvent#hitPath},
 * so providers don't need to be parameterized at registration time.
 */
public final class PortContextMenu {

    private PortContextMenu() {}

    /** Provider that returns {@link #defaultItems} for the right-clicked port. */
    public static ContextMenuProvider defaultProvider() {
        return event -> {
            Ports.Port port = Ports.of(event.target());
            if (port == null) return List.of();
            Component.GraphSurface surface = surfaceFor(event);
            if (surface == null) return List.of();
            return defaultItems(surface, port);
        };
    }

    /**
     * Build the default item list for a port. Exposed so apps can compose
     * (their custom provider returns {@code defaultItems(...) ++ myItems}).
     */
    public static List<ContextMenuItem> defaultItems(Component.GraphSurface surface, Ports.Port port) {
        int count = countConnections(surface, port.component());
        String label = (count > 1) ? "Disconnect (" + count + ")" : "Disconnect";
        return List.of(new ContextMenuItem(label, count > 0, () -> disconnect(surface, port.component())));
    }

    /**
     * Register {@link #defaultProvider} on every port declared on {@code node}.
     * Call after the node and its ports are built (typically right after
     * {@code NodeBuilder.build()}).
     */
    public static void registerDefaults(Component node) {
        for (Ports.Port p : Ports.onNode(node)) {
            Handlers.onContextMenu(p.component(), defaultProvider());
        }
    }

    // ---------- internals ----------

    private static int countConnections(Component.GraphSurface surface, Component port) {
        int n = 0;
        for (Connection c : Connections.on(surface)) {
            if (c.from() == port || c.to() == port) n++;
        }
        return n;
    }

    private static void disconnect(Component.GraphSurface surface, Component port) {
        Connection selected = ConnectionSelection.connection();
        boolean clearedSelected = false;
        // Connections.on returns a defensive copy, so removing during iteration
        // is safe.
        for (Connection c : Connections.on(surface)) {
            if (c.from() == port || c.to() == port) {
                Connections.remove(surface, c);
                if (c == selected) clearedSelected = true;
            }
        }
        if (clearedSelected) ConnectionSelection.clear();
    }

    private static Component.GraphSurface surfaceFor(ContextEvent event) {
        for (Component c : event.hitPath()) {
            if (c instanceof Component.GraphSurface s) return s;
        }
        return null;
    }
}
