package sibarum.dasum.gui.vis.scene;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.vis.math.CameraSpec;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Process-global per-component registry of scene-view state: the
 * published {@link SceneSnapshot}, the {@link CameraSpec}, and the
 * {@link InteractionSpec}. Identity-keyed; entries are removed by
 * {@link #clear} (wired into {@code Components.detach} via the cleaner
 * registered in {@code DasumVis.init()}).
 *
 * <h2>Threading model</h2>
 *
 * Same contract as the original point-cloud registry: {@link #publish}
 * and {@link #setCamera} swap an immutable value into an
 * {@code AtomicReference} and call {@link Invalidator#invalidate()} —
 * lock-free, never blocking, safe from any thread (the canonical caller
 * is a training worker republishing at high frequency). Reads happen on
 * the GLFW main thread from the renderer; the renderer compares the
 * scene reference (and each layer reference) against what it last
 * uploaded and skips matching uploads.
 *
 * <h2>Camera-change listeners</h2>
 *
 * {@link #onCameraChange} listeners fire synchronously inside
 * {@link #setCamera}, on whatever thread called it (controller drags
 * fire on the GLFW thread). Listeners must be fast and must not call
 * back into {@code setCamera} for the same component. Wrappers use this
 * to re-derive view-dependent content (fractal recompute, chart ticks).
 *
 * <h2>Viewport-size listeners</h2>
 *
 * The renderer calls {@link #reportViewportPx} once per frame with each
 * scene view's on-screen pixel rect; when that integer size changes,
 * {@link #onViewportResize} listeners fire synchronously on the GLFW
 * thread. Wrappers use this (with {@link #viewportPxOf}) to rasterize
 * content at the display's pixel density — a complex field map matching
 * its render resolution to the viewport.
 */
public final class SceneStates {

    /** Viewport size in framebuffer pixels. */
    public record ViewportPx(int width, int height) {}

    /** Per-component reactive cell. */
    private static final class State {
        final AtomicReference<SceneSnapshot> scene       = new AtomicReference<>();
        final AtomicReference<CameraSpec> camera         = new AtomicReference<>(CameraSpec.defaultPerspective());
        final AtomicReference<InteractionSpec> interaction = new AtomicReference<>(InteractionSpec.defaults());
        final AtomicReference<ViewportPx> viewportPx      = new AtomicReference<>();
        final CopyOnWriteArrayList<Consumer<CameraSpec>> cameraListeners = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Consumer<ViewportPx>> viewportListeners = new CopyOnWriteArrayList<>();
    }

    private static final Object LOCK = new Object();
    private static final Map<Component, State> STATES = new IdentityHashMap<>();

    private SceneStates() {}

    private static State stateOf(Component c) {
        synchronized (LOCK) {
            return STATES.computeIfAbsent(c, k -> new State());
        }
    }

    private static State peek(Component c) {
        synchronized (LOCK) {
            return STATES.get(c);
        }
    }

    /**
     * Atomically replace the scene for {@code c} and request a redraw.
     * Safe from any thread; never blocks. Reuse unchanged {@link Layer}
     * instances across publishes — re-upload is skipped per layer by
     * reference identity.
     */
    public static void publish(Component c, SceneSnapshot scene) {
        Objects.requireNonNull(scene, "scene");
        stateOf(c).scene.set(scene);
        Invalidator.invalidate();
    }

    /** Current scene, or {@code null} if nothing has been published. */
    public static SceneSnapshot sceneOf(Component c) {
        State s = peek(c);
        return s == null ? null : s.scene.get();
    }

    /**
     * Atomically replace the camera and request a redraw; fires any
     * {@link #onCameraChange} listeners synchronously on this thread.
     */
    public static void setCamera(Component c, CameraSpec spec) {
        Objects.requireNonNull(spec, "spec");
        State s = stateOf(c);
        s.camera.set(spec);
        Invalidator.invalidate();
        for (Consumer<CameraSpec> l : s.cameraListeners) l.accept(spec);
    }

    /** Current camera spec; defaults rather than null. */
    public static CameraSpec cameraOf(Component c) {
        State s = peek(c);
        if (s == null) return CameraSpec.defaultPerspective();
        CameraSpec spec = s.camera.get();
        return spec != null ? spec : CameraSpec.defaultPerspective();
    }

    /** Replace the interaction policy for {@code c}. */
    public static void setInteraction(Component c, InteractionSpec spec) {
        Objects.requireNonNull(spec, "spec");
        stateOf(c).interaction.set(spec);
    }

    /** Current interaction policy; {@link InteractionSpec#defaults()} when unset. */
    public static InteractionSpec interactionOf(Component c) {
        State s = peek(c);
        if (s == null) return InteractionSpec.defaults();
        InteractionSpec spec = s.interaction.get();
        return spec != null ? spec : InteractionSpec.defaults();
    }

    /**
     * Subscribe to camera changes on {@code c} (user navigation and
     * programmatic {@link #setCamera} alike). No unsubscribe API yet —
     * listeners live until {@link #clear}, matching the component's
     * lifetime.
     */
    public static void onCameraChange(Component c, Consumer<CameraSpec> listener) {
        Objects.requireNonNull(listener, "listener");
        stateOf(c).cameraListeners.add(listener);
    }

    /**
     * Report {@code c}'s current on-screen size in framebuffer pixels.
     * Called by the renderer each frame; fires {@link #onViewportResize}
     * listeners synchronously (on the GLFW thread) only when the integer
     * size actually changes, so it is cheap to call every frame and never
     * loops (a listener that republishes does not change the size).
     */
    public static void reportViewportPx(Component c, int width, int height) {
        if (width <= 0 || height <= 0) return;
        State s = stateOf(c);
        ViewportPx next = new ViewportPx(width, height);
        ViewportPx prev = s.viewportPx.getAndSet(next);
        if (next.equals(prev)) return;
        for (Consumer<ViewportPx> l : s.viewportListeners) l.accept(next);
    }

    /** Last reported viewport size, or {@code null} if it hasn't rendered yet. */
    public static ViewportPx viewportPxOf(Component c) {
        State s = peek(c);
        return s == null ? null : s.viewportPx.get();
    }

    /**
     * Subscribe to viewport-size changes on {@code c}. Same lifetime and
     * threading contract as {@link #onCameraChange}: fires on the GLFW
     * thread, lives until {@link #clear}, must be fast.
     */
    public static void onViewportResize(Component c, Consumer<ViewportPx> listener) {
        Objects.requireNonNull(listener, "listener");
        stateOf(c).viewportListeners.add(listener);
    }

    /**
     * Per-component cleanup hook (registered with
     * {@code Components.registerCleaner} by {@code DasumVis.init()}).
     */
    public static void clear(Component c) {
        boolean removed;
        synchronized (LOCK) {
            removed = STATES.remove(c) != null;
        }
        if (removed) Invalidator.invalidate();
    }
}
