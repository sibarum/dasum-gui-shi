package sibarum.dasum.gui.core.input;

import java.util.List;

/**
 * Computes the items shown in a context menu for a given right-click. Invoked
 * exactly once per right-click; the returned list reflects state at that
 * instant (selection, clipboard, focus, etc.). Returning {@link List#of()}
 * suppresses the menu entirely.
 * <p>
 * Register via {@link Handlers#onContextMenu(sibarum.dasum.gui.core.component.Component, ContextMenuProvider)}.
 * Registration is per-component opt-in; the dispatcher walks innermost-out
 * along the hit path and uses the first provider it finds.
 */
@FunctionalInterface
public interface ContextMenuProvider {
    List<ContextMenuItem> itemsFor(ContextEvent event);
}
