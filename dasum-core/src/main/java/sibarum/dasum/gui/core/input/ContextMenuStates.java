package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Identity-keyed sidecar of {@link ContextMenuProvider} per component.
 * Parallel to {@link Handlers}, {@link TextStates}, {@link FocusState}, etc.
 * <p>
 * Most components have no entry — right-click on them does nothing. Calling
 * {@link Handlers#onContextMenu} is the opt-in. The dispatcher in
 * {@link ContextMenuController} reads from here on right-click and falls
 * back to {@link TextContextMenu#defaultProvider()} for selectable text
 * when no explicit registration is found.
 */
public final class ContextMenuStates {

    private static final Map<Component, ContextMenuProvider> PROVIDERS = new IdentityHashMap<>();

    private ContextMenuStates() {}

    public static void put(Component c, ContextMenuProvider p) {
        PROVIDERS.put(c, p);
    }

    public static ContextMenuProvider get(Component c) {
        return PROVIDERS.get(c);
    }

    public static void remove(Component c) {
        PROVIDERS.remove(c);
    }

    /** Alias of {@link #remove(Component)} used by {@link sibarum.dasum.gui.core.component.Components#detach}. */
    public static void clear(Component c) { remove(c); }
}
