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
}
