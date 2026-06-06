package sibarum.dasum.gui.vis.pointcloud;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.vis.math.Vec3;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Per-{@code Component.SceneView} click handlers. Mirrors the
 * {@code Handlers} sidecar in dasum-core: identity-keyed registry of
 * callbacks, populated by application code, invoked by
 * {@link SceneViewController} when a click resolves to a specific
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
     * @param layerIndex     index of the {@code PointLayer} within the
     *                       scene's layer list. Scenes published through
     *                       the legacy {@code PointCloudStates} path put
     *                       points in layer 0.
     * @param pointIndex     index into that layer's arrays
     * @param worldPosition  the 3D (x, y, z) the renderer drew. For
     *                       legacy n-D snapshots this is the projected
     *                       position; raw N-dim values remain readable
     *                       via {@code snapshot.positions()[pointIndex *
     *                       dim + d]} on the original snapshot.
     */
    public record PointHit(int layerIndex, int pointIndex, Vec3 worldPosition) {

        /** Compatibility constructor — layer 0. */
        public PointHit(int pointIndex, Vec3 worldPosition) {
            this(0, pointIndex, worldPosition);
        }
    }

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
