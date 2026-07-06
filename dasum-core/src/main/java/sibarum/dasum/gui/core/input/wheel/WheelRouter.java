package sibarum.dasum.gui.core.input.wheel;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.input.DataTableSelectionController;
import sibarum.dasum.gui.core.input.InputState;
import sibarum.dasum.gui.core.input.ScrollStates;
import sibarum.dasum.gui.core.layout.HitTest;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.overlay.OverlayStack;
import sibarum.dasum.gui.core.theme.Theme;
import sibarum.dasum.gui.natives.glfw.Glfw;
import sibarum.dasum.gui.natives.glfw.GlfwCallbacks;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Framework-owned mouse-wheel router. There is exactly one, installed by
 * {@code Window.create}; it owns the single GLFW scroll callback. Apps no
 * longer wire scroll themselves — they get correct scrolling for free and
 * <em>add</em> {@link WheelHandler}s for custom integrations.
 *
 * <h2>Cascade</h2>
 * On each wheel event the router resolves a {@link WheelEvent} once, then
 * offers it to every registered handler in priority order (highest first,
 * ties broken by registration order). The first handler to return
 * {@code true} consumes the event. If none does, the router runs its
 * built-in, <b>non-removable</b> terminal step: route to the scroll
 * container under the cursor, walking the innermost-first scroll chain so a
 * bottomed-out inner scroll bubbles to its parent. Because the terminal
 * step is structural — not a registered handler — client code can never
 * displace dasum's own scrolling, only layer on top of it.
 *
 * <p>A DataTable handler is registered as a built-in at
 * {@link #PRIORITY_DATATABLE}. {@code dasum-vis} registers its focus-gated
 * SceneView zoom handler at a higher priority; that is the pattern clients
 * copy.
 *
 * <h2>Threading</h2>
 * {@code dispatch} runs only on the GUI thread (it is the GLFW callback
 * body), so all of {@code InputState}/{@code LatestLayout}/
 * {@code ScrollStates} access stays where it already lives. The handler
 * registry is the one cross-thread surface: it is an immutable list behind
 * an {@link AtomicReference}, so {@link #addHandler}/{@link #removeHandler}
 * are safe from any thread and {@code dispatch} always iterates a
 * consistent snapshot — no locks, no {@code IdentityHashMap} hazard.
 */
public final class WheelRouter {

    /** Priority of the built-in DataTable handler. Register above this to run first. */
    public static final int PRIORITY_DATATABLE = 0;

    private record Entry(int priority, long seq, WheelHandler handler) {}

    // Highest priority first; ties resolved by registration order (FIFO).
    private static final Comparator<Entry> ORDER =
            Comparator.comparingInt(Entry::priority).reversed()
                      .thenComparingLong(Entry::seq);

    private static final AtomicReference<List<Entry>> HANDLERS =
            new AtomicReference<>(List.of());
    private static final AtomicLong SEQ = new AtomicLong();

    private static volatile MemorySegment windowHandle;
    private static volatile boolean installed;

    private WheelRouter() {}

    /**
     * Install the router as the process-wide scroll listener and register
     * the built-in handlers. Idempotent. Called once by {@code Window.create}.
     *
     * @param handle the GLFW window, used to poll shift state per event
     */
    public static synchronized void install(MemorySegment handle) {
        windowHandle = handle;
        if (installed) return;
        installed = true;
        addHandler(PRIORITY_DATATABLE, WheelRouter::dataTableHandler);
        GlfwCallbacks.setScrollListener((win, xOff, yOff) -> dispatch(xOff, yOff));
    }

    /**
     * Register a wheel handler. Safe to call from any thread. The returned
     * reference is the same instance passed in — keep it if you intend to
     * {@link #removeHandler} later.
     *
     * @param priority higher runs first; see {@link #PRIORITY_DATATABLE}
     */
    public static WheelHandler addHandler(int priority, WheelHandler handler) {
        if (handler == null) throw new NullPointerException("handler");
        Entry e = new Entry(priority, SEQ.getAndIncrement(), handler);
        List<Entry> prev, next;
        do {
            prev = HANDLERS.get();
            List<Entry> tmp = new ArrayList<>(prev);
            tmp.add(e);
            tmp.sort(ORDER);
            next = List.copyOf(tmp);
        } while (!HANDLERS.compareAndSet(prev, next));
        return handler;
    }

    /** Unregister a previously added handler (by identity). Safe from any thread. */
    public static void removeHandler(WheelHandler handler) {
        List<Entry> prev, next;
        do {
            prev = HANDLERS.get();
            List<Entry> tmp = new ArrayList<>(prev.size());
            for (Entry e : prev) if (e.handler() != handler) tmp.add(e);
            if (tmp.size() == prev.size()) return; // not present
            next = List.copyOf(tmp);
        } while (!HANDLERS.compareAndSet(prev, next));
    }

    /**
     * Resolve a {@link WheelEvent} and run the cascade. The GLFW scroll
     * callback body — runs on the GUI thread.
     */
    public static void dispatch(double xOff, double yOff) {
        LayoutResult lr = LatestLayout.result();
        Component mainRoot = LatestLayout.root();
        Component root = OverlayStack.activeInputRoot(mainRoot);
        if (lr == null || root == null) return;

        double mx = InputState.mouseX();
        double my = InputState.mouseY();

        // A non-modal overlay (e.g. the Find bar) doesn't block the content
        // behind it. When the cursor is outside the topmost non-modal overlay,
        // route the wheel to the main UI so its scroll containers still
        // respond — otherwise the overlay would silently swallow every notch.
        // Modal overlays keep capturing (they draw a backdrop and block input).
        OverlayStack.Overlay top = OverlayStack.topmost();
        if (top != null && !top.modal()
                && OverlayStack.isOutsideTopmost(lr, (float) mx, (float) my)
                && mainRoot != null) {
            root = mainRoot;
        }

        boolean shift = shiftDown();
        Component hit = HitTest.test(root, lr, (float) mx, (float) my);

        // Notches → em, with the standard shift-to-scroll-horizontally swap
        // and GLFW's sign flipped to match scroll-offset direction.
        float emPerNotch = Theme.wheelEmPerNotch();
        float emDx, emDy;
        if (shift) { emDx = (float) (-yOff * emPerNotch); emDy = 0f; }
        else       { emDx = (float) (-xOff * emPerNotch); emDy = (float) (-yOff * emPerNotch); }

        WheelEvent e = new WheelEvent(xOff, yOff, emDx, emDy, mx, my, hit, root, lr, shift);

        if (!offerToHandlers(e)) terminalScroll(e);
    }

    /**
     * Offer the event to each registered handler in priority order; stop at
     * the first that consumes. Package-private seam for tests.
     *
     * @return true if a registered handler consumed the event
     */
    static boolean offerToHandlers(WheelEvent e) {
        for (Entry entry : HANDLERS.get()) {
            if (entry.handler().onWheel(e)) return true;
        }
        return false;
    }

    /**
     * Built-in, non-removable terminal step: route the wheel to the scroll
     * container under the cursor. Walks the innermost-first scroll chain so
     * a bottomed-out inner scroll bubbles to its parent.
     */
    private static void terminalScroll(WheelEvent e) {
        List<Component.Scroll> chain =
                HitTest.findScrollChain(e.root(), e.layout(), (float) e.mouseXPx(), (float) e.mouseYPx());
        for (Component.Scroll s : chain) {
            if (ScrollStates.of(s).scrollBy(e.emDx(), e.emDy())) break;
        }
    }

    private static boolean dataTableHandler(WheelEvent e) {
        return DataTableSelectionController.onScroll(
                e.mouseXPx(), e.mouseYPx(), e.rawXOff(), e.rawYOff(), e.shiftDown());
    }

    private static boolean shiftDown() {
        MemorySegment h = windowHandle;
        if (h == null) return InputState.shiftHeld();
        return Glfw.glfwGetKey(h, Glfw.GLFW_KEY_LEFT_SHIFT)  == Glfw.GLFW_PRESS
            || Glfw.glfwGetKey(h, Glfw.GLFW_KEY_RIGHT_SHIFT) == Glfw.GLFW_PRESS;
    }

    /** Test-only: clear all handlers and reset install state. */
    static void resetForTest() {
        HANDLERS.set(List.of());
        installed = false;
        windowHandle = null;
    }
}
