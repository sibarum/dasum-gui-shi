package sibarum.dasum.gui.vis.scene;

/**
 * One drawable stratum of a {@link SceneSnapshot}. Layers hold plain 3D
 * vertex data ({@code float[]} xyz triples) plus optional per-vertex RGB;
 * {@code null} colors mean "use the framework default colour". Each layer
 * carries a {@link BlendMode} and a scalar {@code opacity} in [0, 1]
 * applied uniformly to the whole layer (per-vertex data stays RGB to keep
 * uploads compact).
 *
 * <p>Layers are immutable value carriers with the same ownership contract
 * as the snapshot that holds them: once published, backing arrays MUST NOT
 * be mutated — the renderer reads them lock-free on the GLFW main thread,
 * and the GPU upload cache is keyed on layer <em>reference identity</em>
 * (an unchanged layer reference between two scenes skips its re-upload).
 * Reuse layer instances across publishes whenever the data hasn't changed.
 *
 * <p>n-dimensional data does not live here — project to 3D before
 * building layers (the {@code PointCloudSnapshot} compat path does this
 * automatically for legacy publishers).
 */
public sealed interface Layer permits PointLayer, LineLayer, TriangleLayer {

    BlendMode blend();

    /** Uniform layer opacity in [0, 1]; multiplies per-vertex colour alpha. */
    float opacity();
}
