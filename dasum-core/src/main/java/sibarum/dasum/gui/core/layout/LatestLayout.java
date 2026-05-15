package sibarum.dasum.gui.core.layout;

import sibarum.dasum.gui.core.component.Component;

/**
 * Holds the most recent {@link LayoutResult} so input listeners can hit
 * test against it without recomputing layout per mouse event. The render
 * lambda updates this each dirty frame.
 * <p>
 * Single-window assumption — a multi-window framework would key this by
 * window handle.
 */
public final class LatestLayout {

    private static Component root;
    private static LayoutResult result;

    private LatestLayout() {}

    public static void store(Component r, LayoutResult lr) {
        root = r;
        result = lr;
    }

    public static Component root() { return root; }
    public static LayoutResult result() { return result; }
}
