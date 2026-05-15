package sibarum.dasum.gui.core.graph;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;

/**
 * Process-global selection state for a single connection on a single
 * {@link Component.GraphSurface}. Single-select for now; multi-select can
 * sit on top of this when needed.
 * <p>
 * Modified by {@code ConnectionSelectionController} on left-click,
 * cleared by Delete key (after removing the connection) or ESC. Queried
 * by {@link ConnectionRenderer} to draw the selected curve with a halo.
 */
public final class ConnectionSelection {

    private static Component.GraphSurface selectedSurface = null;
    private static Connection selectedConnection = null;

    private ConnectionSelection() {}

    public static Component.GraphSurface surface() { return selectedSurface; }
    public static Connection connection() { return selectedConnection; }
    public static boolean has() { return selectedConnection != null; }

    public static void set(Component.GraphSurface surface, Connection c) {
        if (selectedSurface == surface && selectedConnection == c) return;
        selectedSurface    = surface;
        selectedConnection = c;
        Invalidator.invalidate();
    }

    public static void clear() {
        if (selectedConnection == null) return;
        selectedSurface    = null;
        selectedConnection = null;
        Invalidator.invalidate();
    }

    /**
     * Per-component cleanup hook called by
     * {@link sibarum.dasum.gui.core.component.Components#detach}. Drops the
     * selection if {@code c} is the selected surface, or one of the selected
     * connection's endpoints; no-op otherwise.
     */
    public static void clear(Component c) {
        if (selectedConnection == null) return;
        if (selectedSurface == c
            || selectedConnection.from() == c
            || selectedConnection.to() == c) {
            clear();
        }
    }

    /**
     * If the selection's surface or either endpoint is {@code from},
     * rewrite the selection to point at {@code to}. Designed for use
     * after {@link Connections#migrate}.
     */
    public static void migrate(Component from, Component to) {
        if (selectedConnection == null) return;
        if (selectedSurface == from && to instanceof Component.GraphSurface gs) {
            selectedSurface = gs;
        }
        Component newFrom = (selectedConnection.from() == from) ? to : selectedConnection.from();
        Component newTo   = (selectedConnection.to()   == from) ? to : selectedConnection.to();
        if (newFrom != selectedConnection.from() || newTo != selectedConnection.to()) {
            selectedConnection = new Connection(newFrom, newTo);
        }
    }
}
