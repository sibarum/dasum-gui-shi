package sibarum.dasum.gui.core.overlay;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.input.InputState;
import sibarum.dasum.gui.core.layout.HitTest;
import sibarum.dasum.gui.core.layout.LayoutResult;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Global, passive tooltip state machine. Owns the currently-displayed
 * tooltip (anchor component + cursor-anchored position) and decides each
 * frame whether to show, hide, or relocate it.
 * <p>
 * <b>Passive</b> — the controller never consumes input events, never affects
 * hit-testing for clicks/drag, and never participates in focus or
 * keyboard navigation. It is read-only with respect to mouse/key dispatch.
 * Apps wire it by:
 * <ol>
 *   <li>calling {@link #resolveBeforeRender(LayoutResult, Component, double, double)}
 *       at the top of their render lambda (after layout, before
 *       {@code Render.render}). This re-hit-tests at the current mouse
 *       position so the tooltip stays consistent even when the UI
 *       rebuilds under a stationary cursor;</li>
 *   <li>calling {@link #onCursorMove(double, double)} from the cursor-pos
 *       callback (cheap; just updates the cached cursor coordinates);</li>
 *   <li>calling {@link #hideAll()} from cursor-exit and window-blur
 *       callbacks (so the tooltip doesn't linger when focus moves away);
 *       </li>
 *   <li>letting {@link Tooltips#clear(Component)} run during
 *       {@code Components.detach} — that hook lives in
 *       {@link Tooltips#clear(Component)} and calls
 *       {@link #onComponentDetached(Component)}.</li>
 * </ol>
 * <p>
 * Aggressive-hide invariants: the tooltip is hidden the instant any of
 * these happen:
 * <ul>
 *   <li>cursor leaves the window or the window loses focus
 *       (apps wire {@link #hideAll});</li>
 *   <li>the component under the cursor changes (re-hit-tested every
 *       frame — covers both real mouse movement and silent UI rebuilds
 *       that reposition components relative to a stationary cursor);</li>
 *   <li>the registered tooltip text for the current target changes;</li>
 *   <li>the trigger's modifier-key requirement is no longer met;</li>
 *   <li>the anchored component is detached.</li>
 * </ul>
 * <p>
 * Single-threaded: all state mutation runs on the GLFW main thread. The
 * only cross-thread surface is {@link #onComponentDetached} which uses
 * a reference-equal compare; no lock needed because publishing a fresh
 * tooltip target is a single reference store and main-thread reads see
 * a consistent snapshot.
 */
public final class TooltipController {

    private static final long DEFAULT_DWELL_MILLIS = 500L;

    private static volatile TooltipTrigger trigger = TooltipTrigger.ALWAYS;
    private static volatile long dwellMillis = DEFAULT_DWELL_MILLIS;

    /** Cursor offset in pixels — placement origin relative to the mouse. */
    private static final float CURSOR_OFFSET_X_PX = 14f;
    private static final float CURSOR_OFFSET_Y_PX = 18f;

    /** Live state — all mutated only on the main thread. */
    private static Component anchor = null;
    private static String     anchorText = null;
    private static long       dwellStartNanos = 0L;
    private static double     cursorX = -1d;
    private static double     cursorY = -1d;
    private static boolean    visible = false;
    private static int        lastSeenModBits = 0;

    /**
     * Single-thread daemon scheduler used to wake the event loop when the
     * dwell deadline elapses. Without this, the loop blocks in
     * {@code glfwWaitEvents} after the dwell timer was started — no event
     * fires to trigger a render at the deadline, so the tooltip would
     * never appear on a stationary cursor.
     * <p>
     * The scheduled task only calls {@link Invalidator#invalidate()} —
     * it never touches the controller's mutable state. The actual
     * decision to show happens in {@link #resolveBeforeRender} on the
     * main thread.
     */
    private static final ScheduledExecutorService DWELL_SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dasum-tooltip-dwell");
            t.setDaemon(true);
            return t;
        });
    private static ScheduledFuture<?> pendingWake;

    private TooltipController() {}

    // ---------- configuration ----------

    public static void setTrigger(TooltipTrigger t) {
        if (t == null) throw new IllegalArgumentException("trigger == null");
        if (trigger == t) return;
        trigger = t;
        // Hide immediately if a switch to a stricter trigger would make
        // the current display invalid — re-evaluation in
        // resolveBeforeRender would catch it on the next frame anyway,
        // but we want zero latency.
        if (!t.satisfiedBy(InputState.modBits())) hideAll();
        Invalidator.invalidate();
    }

    public static TooltipTrigger getTrigger() { return trigger; }

    /** Dwell delay in millis before the tooltip becomes visible. Default 500 ms. */
    public static void setDwellMillis(long ms) {
        if (ms < 0) ms = 0;
        dwellMillis = ms;
    }

    public static long getDwellMillis() { return dwellMillis; }

    // ---------- input wiring ----------

    /**
     * Update cached cursor position. App wires this into its
     * {@code cursor-pos} callback after
     * {@link InputState#updateMousePos(double, double)}.
     * <p>
     * Re-hit-testing happens in {@link #resolveBeforeRender} (which has
     * the live {@link LayoutResult}), not here — keep this hot path cheap.
     */
    public static void onCursorMove(double x, double y) {
        cursorX = x;
        cursorY = y;
    }

    /**
     * Re-evaluate the trigger against a fresh modifier bitmask. Apps wire
     * this into their GLFW key callback right after
     * {@link InputState#setMods(int)} so a mod-key press/release that
     * crosses the trigger threshold causes the next frame to re-resolve
     * (without it, the event loop won't redraw because nothing else is
     * dirty — GLFW key events alone don't mark the renderer dirty).
     * <p>
     * No-op when the trigger is {@link TooltipTrigger#ALWAYS} — modifier
     * state is irrelevant in that mode.
     */
    public static void onModsChanged(int modBits) {
        if (modBits == lastSeenModBits) return;
        boolean oldSatisfied = trigger.satisfiedBy(lastSeenModBits);
        boolean newSatisfied = trigger.satisfiedBy(modBits);
        lastSeenModBits = modBits;
        if (oldSatisfied != newSatisfied) {
            // If the trigger just transitioned to unsatisfied, drop any
            // visible tooltip immediately. The next resolveBeforeRender
            // would catch this, but explicit hide gives zero-frame
            // latency on the release.
            if (!newSatisfied && (visible || anchor != null)) {
                hideAll();
            } else {
                // Transition into satisfied: arm dwell from now so the
                // user gets the same delay as if they'd just hovered onto
                // the component.
                if (anchor != null) {
                    dwellStartNanos = System.nanoTime();
                    if (dwellMillis > 0L) scheduleDwellWake(dwellMillis);
                }
                Invalidator.invalidate();
            }
        }
    }

    /**
     * Hide the tooltip immediately and clear any pending dwell. Apps call
     * this from {@code cursor-enter}-with-{@code entered=false} and
     * {@code window-focus}-with-{@code focused=false} callbacks. Also
     * safe to call when an overlay opens/closes or any other ambient
     * state change should re-arm dwell from scratch.
     */
    public static void hideAll() {
        boolean wasVisible = visible || anchor != null;
        anchor = null;
        anchorText = null;
        visible = false;
        dwellStartNanos = 0L;
        cancelPendingWake();
        if (wasVisible) Invalidator.invalidate();
    }

    private static void cancelPendingWake() {
        ScheduledFuture<?> p = pendingWake;
        if (p != null) {
            p.cancel(false);
            pendingWake = null;
        }
    }

    /**
     * Arm a one-shot wakeup that invalidates the dirty flag after {@code ms}
     * milliseconds. The event loop blocks in {@code glfwWaitEvents} when
     * idle, so on a stationary cursor no event would fire to re-trigger
     * a render at the dwell deadline; without this scheduled poke the
     * tooltip would never appear. The task only sets the dirty flag —
     * never touches controller state — so it's race-free w.r.t. the main
     * thread's resolveBeforeRender (which re-evaluates the dwell against
     * the current target on the next render).
     */
    private static void scheduleDwellWake(long ms) {
        cancelPendingWake();
        if (ms <= 0L) return;
        pendingWake = DWELL_SCHEDULER.schedule(Invalidator::invalidate, ms, TimeUnit.MILLISECONDS);
    }

    /**
     * Called by {@link Tooltips#clear(Component)} during
     * {@code Components.detach}. Reference-equal compare against the
     * current anchor; clears if matched. Safe to call from any thread —
     * the underlying assignment is a single reference store. The worst
     * case under contention is one stale frame, which the next
     * {@link #resolveBeforeRender} corrects (the detached anchor's hit
     * test fails because the component no longer appears in the layout).
     */
    public static void onComponentDetached(Component c) {
        if (anchor == c) hideAll();
    }

    // ---------- per-frame resolution ----------

    /**
     * Re-evaluate the tooltip's visibility against the latest layout.
     * Invoked once per frame from the app's render lambda, after
     * {@code Layout.compute} (plus overlay layout merge) and before
     * {@code Render.render}.
     * <p>
     * Hit-tests at the cached cursor position using {@link HitTest#testAny}
     * (so non-interactive components participate), walks up the ancestor
     * chain to find the innermost component with a registered tooltip,
     * then runs the trigger / dwell logic.
     *
     * @param layout merged layout result for the frame
     * @param root   layout root used for hit-testing — caller may pass
     *               {@code OverlayStack.activeInputRoot(mainRoot)} to
     *               scope tooltips to the active modal layer
     */
    public static void resolveBeforeRender(LayoutResult layout, Component root, double mouseX, double mouseY) {
        if (layout == null || root == null || !InputState.mouseInWindow()) {
            if (visible || anchor != null) hideAll();
            return;
        }
        // Don't show tooltips during a mouse-drag (left button held);
        // tooltips during drag fight the cursor and obscure drop targets.
        if (InputState.leftButtonHeld()) {
            if (visible || anchor != null) hideAll();
            return;
        }
        if (!trigger.satisfiedBy(InputState.modBits())) {
            if (visible || anchor != null) hideAll();
            return;
        }
        cursorX = mouseX;
        cursorY = mouseY;

        // Deepest containing component, regardless of interactive().
        Component hit = HitTest.testAny(root, layout, (float) mouseX, (float) mouseY);
        Component target = nearestAncestorWithTooltip(root, hit);

        if (target == null) {
            if (visible || anchor != null) hideAll();
            return;
        }

        String text = Tooltips.get(target);
        if (text == null || text.isEmpty()) {
            if (visible || anchor != null) hideAll();
            return;
        }

        // Same target AND text? Just keep ticking the dwell.
        if (target == anchor && text.equals(anchorText)) {
            if (!visible) {
                long now = System.nanoTime();
                if (now - dwellStartNanos >= dwellMillis * 1_000_000L) {
                    visible = true;
                    cancelPendingWake();
                    Invalidator.invalidate();
                }
            }
            return;
        }

        // Target or text changed — restart dwell.
        anchor = target;
        anchorText = text;
        visible = false;
        dwellStartNanos = System.nanoTime();
        if (dwellMillis == 0L) {
            visible = true;
            cancelPendingWake();
        } else {
            // Schedule a wakeup at the dwell deadline so the event loop
            // returns from glfwWaitEvents and re-evaluates even if the
            // cursor stays still.
            scheduleDwellWake(dwellMillis);
        }
        Invalidator.invalidate();
    }

    private static Component nearestAncestorWithTooltip(Component root, Component leaf) {
        if (leaf == null) return null;
        // Walk root → leaf path and pick the deepest registered ancestor.
        // pathTo is identity-based, which is what we need (sidecar lookup
        // is identity-keyed).
        java.util.List<Component> path = HitTest.pathTo(root, leaf);
        for (int i = path.size() - 1; i >= 0; i--) {
            if (Tooltips.has(path.get(i))) return path.get(i);
        }
        return null;
    }

    // ---------- query for the renderer ----------

    public static boolean isVisible() { return visible && anchorText != null; }
    public static String currentText() { return visible ? anchorText : null; }
    public static Component currentAnchor() { return visible ? anchor : null; }
    public static double cursorX() { return cursorX; }
    public static double cursorY() { return cursorY; }
    public static float cursorOffsetXPx() { return CURSOR_OFFSET_X_PX; }
    public static float cursorOffsetYPx() { return CURSOR_OFFSET_Y_PX; }

    // ---------- test hooks ----------

    /** Test-only — reset all state so unit tests don't see cross-test bleed. */
    static void resetForTest() {
        trigger = TooltipTrigger.ALWAYS;
        dwellMillis = DEFAULT_DWELL_MILLIS;
        anchor = null;
        anchorText = null;
        dwellStartNanos = 0L;
        cursorX = -1d;
        cursorY = -1d;
        visible = false;
        lastSeenModBits = 0;
        cancelPendingWake();
    }
}
