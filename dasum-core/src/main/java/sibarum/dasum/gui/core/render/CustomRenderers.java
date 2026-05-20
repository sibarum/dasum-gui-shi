package sibarum.dasum.gui.core.render;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.layout.PixelRect;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Process-global registry of extension renderers for {@link Component}
 * variants whose drawing isn't owned by {@code dasum-core} itself.
 * <p>
 * dasum-core stays 2D-only: it knows how to draw {@link Component.Box},
 * {@link Component.Flex}, {@link Component.Text}, etc., but the
 * {@link Component.PointCloud} variant requires OpenGL 3D state
 * (depth test, MVP matrix, GPU-resident vertex buffers) that lives in
 * {@code dasum-vis}. Rather than have dasum-core depend on dasum-vis,
 * dasum-vis registers a {@link Renderer} for the PointCloud class at
 * its module's init time and dasum-core's {@code Render} dispatch looks
 * it up by component class.
 * <p>
 * Components with no registered renderer fall through to a no-op — they
 * still get a background quad from the generic render pass, they just
 * render no content. A {@code Component.PointCloud} placed in a UI before
 * {@code DasumVis.init()} runs is therefore harmless.
 * <p>
 * Renderers are invoked AFTER {@code Render} has emitted the component's
 * background fill, and BEFORE descending into children (for leaf
 * variants like PointCloud there are none). The renderer is expected to
 * flush the batcher with {@code batcher.flush(projection)} before
 * touching any global GL state it doesn't own, so previously-buffered
 * geometry isn't drawn under the new state.
 */
public final class CustomRenderers {

    @FunctionalInterface
    public interface Renderer {
        /**
         * Draw {@code c} into {@code rect}. The batcher's solid-fill /
         * text accumulators may hold buffered geometry — call
         * {@code batcher.flush(projection)} before changing global GL
         * state (depth test, scissor, blend, etc.) and again after
         * restoring it.
         *
         * <p>Renderers that change {@code glViewport} (essential for 3D
         * content so NDC maps to the rect rather than the framebuffer
         * centre) must save the previous viewport via
         * {@code Gl.glGetIntegerv4(GL_VIEWPORT)} on entry and restore
         * the saved values before returning. The framework does NOT
         * pass the framebuffer dimensions through this interface — they
         * came in once via {@code Gl.glViewport(0, 0, fbW, fbH)} at
         * the start of the frame and the GL state holds the source of
         * truth. This avoids a footgun where a caller of
         * {@code Render.render} that didn't pass valid dimensions would
         * silently corrupt the viewport for every component rendered
         * after the 3D one.
         */
        void render(Component c, PixelRect rect, Batcher batcher, float[] projection);
    }

    private static final Map<Class<? extends Component>, Renderer> RENDERERS = new IdentityHashMap<>();

    private CustomRenderers() {}

    public static void register(Class<? extends Component> klass, Renderer renderer) {
        RENDERERS.put(klass, renderer);
    }

    public static Renderer find(Component c) {
        if (c == null) return null;
        return RENDERERS.get(c.getClass());
    }
}
