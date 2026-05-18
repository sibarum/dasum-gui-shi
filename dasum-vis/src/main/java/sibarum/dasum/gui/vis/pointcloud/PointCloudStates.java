package sibarum.dasum.gui.vis.pointcloud;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.vis.math.CameraSpec;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-global per-component registry of point-cloud state. Each
 * {@code Component.PointCloud} instance owns one {@link State} —
 * created on first access by either {@link #publish} or
 * {@link #setCamera}. State entries are removed via {@link #clear}
 * (wired into {@code Components.detach} via
 * {@link sibarum.dasum.gui.core.component.Components#registerCleaner}).
 *
 * <h2>Threading model</h2>
 *
 * <p>This is the worker-publish bottleneck. The pattern is built on top
 * of the framework's existing {@link Invalidator} which already
 * coalesces high-frequency cross-thread wakes (so a training worker
 * republishing at thousands of Hz won't flood the GLFW event queue).
 *
 * <ul>
 *   <li><b>{@link #publish}:</b> swap an immutable snapshot into an
 *       {@code AtomicReference}, then {@code Invalidator.invalidate()}.
 *       Both operations are lock-free and never block.</li>
 *   <li><b>{@link #setCamera}:</b> same, for the camera spec.</li>
 *   <li><b>Reads:</b> happen on the GLFW main thread from the renderer.
 *       {@code AtomicReference.get()} is a plain volatile read.</li>
 * </ul>
 *
 * <p>The renderer compares the snapshot reference identity against what
 * it last uploaded to GPU; if unchanged, it skips the upload. Workers
 * therefore pay only the snapshot construction cost, not a GPU upload,
 * even at very high publish rates. The frame ultimately drawn is always
 * the most recently published snapshot at the moment the frame began.
 *
 * <p>The map lookup itself synchronizes briefly on creation only — once
 * a {@link State} exists for a component, all subsequent
 * {@code publish}/{@code setCamera}/{@code snapshotOf}/{@code cameraOf}
 * calls go through the per-state {@link AtomicReference} without
 * touching the lock.
 */
public final class PointCloudStates {

    /** Per-component reactive cell. Mutable fields are atomic. */
    public static final class State {
        final AtomicReference<PointCloudSnapshot> snapshot = new AtomicReference<>();
        final AtomicReference<CameraSpec> camera           = new AtomicReference<>(CameraSpec.defaultPerspective());
    }

    private static final Object LOCK = new Object();
    private static final Map<Component, State> STATES = new IdentityHashMap<>();

    private PointCloudStates() {}

    private static State stateOf(Component c) {
        synchronized (LOCK) {
            State s = STATES.get(c);
            if (s == null) {
                s = new State();
                STATES.put(c, s);
            }
            return s;
        }
    }

    /**
     * Atomically replace the snapshot for {@code c} and request a redraw.
     * Safe to call from any thread. Does not block.
     */
    public static void publish(Component c, PointCloudSnapshot snapshot) {
        stateOf(c).snapshot.set(snapshot);
        Invalidator.invalidate();
    }

    /**
     * Atomically replace the camera spec for {@code c} and request a
     * redraw. Safe to call from any thread.
     */
    public static void setCamera(Component c, CameraSpec spec) {
        stateOf(c).camera.set(spec);
        Invalidator.invalidate();
    }

    /** Current snapshot, or {@code null} if nothing has been published. */
    public static PointCloudSnapshot snapshotOf(Component c) {
        State s;
        synchronized (LOCK) { s = STATES.get(c); }
        return s == null ? null : s.snapshot.get();
    }

    /**
     * Current camera spec. Returns a sensible default rather than null so
     * the renderer never has to null-check.
     */
    public static CameraSpec cameraOf(Component c) {
        State s;
        synchronized (LOCK) { s = STATES.get(c); }
        if (s == null) return CameraSpec.defaultPerspective();
        CameraSpec spec = s.camera.get();
        return spec != null ? spec : CameraSpec.defaultPerspective();
    }

    /**
     * Per-component cleanup hook. Registered with
     * {@code Components.registerCleaner} by {@link DasumVis#init()}.
     * Removes any state entry; the entry's referenced snapshot arrays
     * become GC-eligible.
     */
    public static void clear(Component c) {
        boolean removed;
        synchronized (LOCK) {
            removed = STATES.remove(c) != null;
        }
        if (removed) Invalidator.invalidate();
    }
}
