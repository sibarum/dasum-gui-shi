package sibarum.dasum.gui.vis.scene;

/**
 * Straight 3D line segments (1px, per-endpoint colour gradient). Useful
 * for axes, wireframes, graph edges, vector fields. For thick lines,
 * expand to quads CPU-side and use a {@link TriangleLayer}.
 *
 * @param endpoints xyz triples, two vertices per segment — segment {@code i}
 *                  occupies floats {@code [i*6, i*6+6)}; length % 6 == 0
 * @param colors    optional per-endpoint RGB (linear, 0..1), length
 *                  {@code == endpoints.length}; {@code null} = default colour
 * @param blend     fixed-function blend mode for this layer
 * @param opacity   uniform layer opacity in [0, 1]
 */
public record LineLayer(
    float[] endpoints,
    float[] colors,
    BlendMode blend,
    float opacity
) implements Layer {

    public LineLayer {
        if (endpoints == null) throw new IllegalArgumentException("endpoints != null");
        if (endpoints.length % 6 != 0) {
            throw new IllegalArgumentException("endpoints length must be a multiple of 6 (two xyz per segment)");
        }
        if (colors != null && colors.length != endpoints.length) {
            throw new IllegalArgumentException("colors length must equal endpoints length (RGB per endpoint) or null");
        }
        if (blend == null) throw new IllegalArgumentException("blend != null");
        if (opacity < 0f || opacity > 1f) throw new IllegalArgumentException("opacity in [0, 1]");
    }

    /** Convenience: ALPHA blend, full opacity. */
    public LineLayer(float[] endpoints, float[] colors) {
        this(endpoints, colors, BlendMode.ALPHA, 1f);
    }

    public int segmentCount() { return endpoints.length / 6; }

    public LineLayer withBlend(BlendMode b) { return new LineLayer(endpoints, colors, b, opacity); }
    public LineLayer withOpacity(float o)   { return new LineLayer(endpoints, colors, blend, o); }
}
