package sibarum.dasum.gui.vis.pointcloud;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.vis.math.CameraSpec;
import sibarum.dasum.gui.vis.scene.SceneCompat;
import sibarum.dasum.gui.vis.scene.SceneSnapshot;
import sibarum.dasum.gui.vis.scene.SceneStates;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Legacy point-cloud publish API — now a thin compatibility delegate
 * over {@link SceneStates}. {@link #publish} converts the
 * {@link PointCloudSnapshot} into a {@link SceneSnapshot} (one point
 * layer + one line layer, n-D projection applied — see
 * {@link SceneCompat}) and forwards it; camera calls delegate directly.
 *
 * <p>The conversion is cached per component by snapshot <em>reference
 * identity</em>, so a worker republishing the same snapshot instance at
 * high frequency forwards the same scene instance — the renderer's
 * per-scene and per-layer identity skips keep working exactly as they
 * did pre-scene-model. {@link #snapshotOf} keeps returning the original
 * {@code PointCloudSnapshot} (the picker and legacy readers depend on
 * it), not the converted scene.
 *
 * <p>Two-level locking mirrors the framework's other sidecars: the
 * global lock only guards the cache map; conversion runs under the
 * per-component entry monitor so publishers to different components
 * never serialize against each other.
 *
 * <p>New code should build {@link SceneSnapshot}s and use
 * {@link SceneStates} directly.
 */
public final class PointCloudStates {

    private static final class Entry {
        PointCloudSnapshot src;
        SceneSnapshot scene;
    }

    private static final Object LOCK = new Object();
    private static final Map<Component, Entry> CACHE = new IdentityHashMap<>();

    private PointCloudStates() {}

    /**
     * Atomically replace the snapshot for {@code c} and request a redraw.
     * Safe to call from any thread. Does not block on other components'
     * publishers.
     */
    public static void publish(Component c, PointCloudSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Entry e;
        synchronized (LOCK) {
            e = CACHE.computeIfAbsent(c, k -> new Entry());
        }
        SceneSnapshot scene;
        synchronized (e) {
            if (e.src != snapshot) {
                e.scene = SceneCompat.convert(snapshot);
                e.src = snapshot;
            }
            scene = e.scene;
        }
        SceneStates.publish(c, scene);
    }

    /** @see SceneStates#setCamera */
    public static void setCamera(Component c, CameraSpec spec) {
        SceneStates.setCamera(c, spec);
    }

    /**
     * The most recently published {@link PointCloudSnapshot} (the
     * original, NOT the converted scene), or {@code null} if nothing was
     * published through this legacy API.
     */
    public static PointCloudSnapshot snapshotOf(Component c) {
        Entry e;
        synchronized (LOCK) {
            e = CACHE.get(c);
        }
        if (e == null) return null;
        synchronized (e) {
            return e.src;
        }
    }

    /** @see SceneStates#cameraOf */
    public static CameraSpec cameraOf(Component c) {
        return SceneStates.cameraOf(c);
    }

    /**
     * Per-component cleanup. Drops the conversion cache and the
     * underlying scene state (idempotent with the cleaner also calling
     * {@link SceneStates#clear} directly).
     */
    public static void clear(Component c) {
        synchronized (LOCK) {
            CACHE.remove(c);
        }
        SceneStates.clear(c);
    }
}
