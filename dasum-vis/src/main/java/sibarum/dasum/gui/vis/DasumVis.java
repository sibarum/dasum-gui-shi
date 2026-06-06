package sibarum.dasum.gui.vis;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Components;
import sibarum.dasum.gui.core.render.CustomRenderers;
import sibarum.dasum.gui.vis.pointcloud.PointCloudStates;
import sibarum.dasum.gui.vis.pointcloud.PointHandlers;
import sibarum.dasum.gui.vis.render.SceneRenderer;
import sibarum.dasum.gui.vis.scene.SceneStates;

/**
 * Module bootstrap. Consumers call {@link #init()} once after a GL
 * context is current (and after {@code Gl.load()}) to wire the
 * point-cloud renderer into {@code dasum-core}'s render pipeline.
 * <p>
 * The renderer instance is owned here so it can be disposed via
 * {@link #close()} on app shutdown (also closeable via try-with-resources
 * if you keep the reference). Calling {@code init} twice is a no-op.
 */
public final class DasumVis implements AutoCloseable {

    private static DasumVis instance;

    private final SceneRenderer renderer = new SceneRenderer();

    private DasumVis() {}

    /**
     * Initialise the visualization module. Requires:
     * <ul>
     *   <li>An active GLFW context.</li>
     *   <li>{@code Gl.load()} already called.</li>
     * </ul>
     * Returns the (process-singleton) instance; closing it tears down GL
     * resources.
     */
    public static synchronized DasumVis init() {
        if (instance != null) return instance;
        DasumVis v = new DasumVis();
        v.renderer.init();
        CustomRenderers.register(Component.SceneView.class, v.renderer.asRenderer());
        Components.registerCleaner(c -> {
            SceneStates.clear(c);
            PointCloudStates.clear(c); // compat conversion cache (idempotent with the line above)
            PointHandlers.clear(c);
            v.renderer.onComponentDetached(c);
        });
        instance = v;
        return v;
    }

    public SceneRenderer renderer() { return renderer; }

    @Override
    public synchronized void close() {
        renderer.close();
        instance = null;
    }
}
