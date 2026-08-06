package sibarum.dasum.gui.vis.scene;

import java.util.Collections;
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
 * <p><b>z-index ({@code zIndices}):</b> a per-layer integer parallel to
 * {@code layers}, 0 by default. The renderer draws in ascending z-index
 * (stable — layers of equal z-index keep their list order), and layers
 * with a NON-zero z-index render <em>depth-independently</em>: a positive
 * z-index is forced ON TOP (drawn last, depth test off) and a negative one
 * BEHIND (drawn first, so depth-tested geometry paints over it). z-index 0
 * layers keep true 3D depth culling among themselves. An all-zero list (the
 * default) reproduces plain painter's order exactly.
 *
 * <p><b>Thread-safety contract</b> (same as the original point-cloud
 * snapshot): after a snapshot is passed to {@code publish}, the calling
 * thread MUST NOT mutate any backing array of any layer. The renderer
 * reads them on the GLFW main thread without locking. GPU re-upload is
 * skipped per layer when the layer <em>reference</em> is unchanged between
 * two published scenes — republish cheaply by reusing the untouched layer
 * instances and replacing only what changed.
 */
public record SceneSnapshot(List<Layer> layers, List<Integer> zIndices) {

    public SceneSnapshot {
        layers = List.copyOf(layers); // rejects null list and null elements
        zIndices = zIndices == null
                ? Collections.nCopies(layers.size(), 0)
                : List.copyOf(zIndices);
        if (zIndices.size() != layers.size()) {
            throw new IllegalArgumentException("zIndices size must equal layers size");
        }
    }

    /** All-zero z-index: plain painter's order with default depth culling. */
    public SceneSnapshot(List<Layer> layers) {
        this(layers, null);
    }

    public static SceneSnapshot of(Layer... layers) {
        return new SceneSnapshot(List.of(layers));
    }

    /** The z-index of layer {@code i} (0 = default depth-tested; &gt;0 on top; &lt;0 behind). */
    public int zIndexOf(int i) {
        return zIndices.get(i);
    }
}
