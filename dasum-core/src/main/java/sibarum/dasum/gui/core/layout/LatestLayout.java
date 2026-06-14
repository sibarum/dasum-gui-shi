package sibarum.dasum.gui.core.layout;

import sibarum.dasum.gui.core.component.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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

    /**
     * Callbacks fired right after every {@link #store}. Lets components-layer
     * features react to a freshly-computed layout without this low-level holder
     * depending on them (e.g. the navigator completes a deferred focus once its
     * target's rect exists). Copy-on-write so a callback registering during a
     * store doesn't disturb the in-progress iteration.
     */
    private static final List<Runnable> AFTER_STORE = new CopyOnWriteArrayList<>();

    private LatestLayout() {}

    /** Register a callback fired after each {@link #store}. Registered once, lives for the JVM. */
    public static void addAfterStore(Runnable r) {
        AFTER_STORE.add(r);
    }

    public static void store(Component r, LayoutResult lr) {
        root = r;
        result = lr;
        for (Runnable cb : AFTER_STORE) cb.run();
    }

    public static Component root() { return root; }
    public static LayoutResult result() { return result; }
}
