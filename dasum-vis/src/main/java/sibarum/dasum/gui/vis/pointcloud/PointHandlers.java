package sibarum.dasum.gui.vis.pointcloud;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.vis.math.Vec3;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Per-{@code Component.PointCloud} click handlers. Mirrors the
 * {@code Handlers} sidecar in dasum-core: identity-keyed registry of
 * callbacks, populated by application code, invoked by
 * {@link PointCloudController} when a click resolves to a specific
 * point.
 * <p>
 * Unlike the generic {@code Handlers.onClick} (which fires only on the
 * viewport as a whole), this delivers a {@link PointHit} with the index
 * and world position of the picked point, plus its dimensionality so
 * consumers can index the snapshot themselves for richer metadata.
 */
public final class PointHandlers {

    /**
     * Result of a successful point pick.
     *
     * @param pointIndex     index into the current snapshot's arrays
     * @param worldPosition  the projected (x, y, z) used for rendering —
     *                       i.e. after the snapshot's
     *                       {@link PointCloudSnapshot#projection()} was
     *                       applied. For raw N-dim values, read
     *                       {@code snapshot.positions()[pointIndex * dim + d]}
     *                       directly.
     */
    public record PointHit(int pointIndex, Vec3 worldPosition) {}

    private static final Object LOCK = new Object();
    private static final Map<Component, Consumer<PointHit>> HANDLERS = new IdentityHashMap<>();

    private PointHandlers() {}

    public static void onPointClick(Component pc, Consumer<PointHit> handler) {
        synchronized (LOCK) { HANDLERS.put(pc, handler); }
    }

    /** Returns the registered handler, or {@code null} if none. */
    public static Consumer<PointHit> handlerFor(Component pc) {
        synchronized (LOCK) { return HANDLERS.get(pc); }
    }

    /**
     * Per-component cleanup hook registered by
     * {@link sibarum.dasum.gui.vis.DasumVis#init()} via
     * {@code Components.registerCleaner}.
     */
    public static void clear(Component c) {
        synchronized (LOCK) { HANDLERS.remove(c); }
    }
}
