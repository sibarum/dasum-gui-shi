package sibarum.dasum.gui.vis.scene;

import java.util.List;

/**
 * Immutable frame of scene content — a retained display list drawn through
 * one camera. What the renderer draws when a {@code Component.SceneView}
 * is on screen. Constructed by the consumer (app code or a worker thread)
 * and atomically published via {@link SceneStates#publish}.
 *
 * <p><b>Painter's model:</b> layers draw in list order, each blended over
 * the result of the previous ones according to its {@link BlendMode}.
 * Because ALPHA and MULTIPLY are non-commutative, the order of
 * {@code layers} is semantic, not incidental — reordering a scene with
 * mixed blend modes changes the picture.
 *
 * <p><b>Thread-safety contract</b> (same as the original point-cloud
 * snapshot): after a snapshot is passed to {@code publish}, the calling
 * thread MUST NOT mutate any backing array of any layer. The renderer
 * reads them on the GLFW main thread without locking. GPU re-upload is
 * skipped per layer when the layer <em>reference</em> is unchanged between
 * two published scenes — republish cheaply by reusing the untouched layer
 * instances and replacing only what changed.
 */
public record SceneSnapshot(List<Layer> layers) {

    public SceneSnapshot {
        layers = List.copyOf(layers); // rejects null list and null elements
    }

    public static SceneSnapshot of(Layer... layers) {
        return new SceneSnapshot(List.of(layers));
    }
}
