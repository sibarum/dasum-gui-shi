package sibarum.dasum.gui.vis.scene;

/**
 * Filled triangles with per-vertex colour interpolation — the universal
 * 2D/3D fill primitive. Bars, heatmap cells, pie wedges, thick polylines,
 * area fills, arrowheads and flow shapes are all CPU tessellations into
 * this layer.
 *
 * @param vertices xyz triples, three vertices per triangle — triangle
 *                 {@code i} occupies floats {@code [i*9, i*9+9)};
 *                 length % 9 == 0
 * @param colors   optional per-vertex RGB (linear, 0..1), length
 *                 {@code == vertices.length}; {@code null} = default colour
 * @param blend    fixed-function blend mode for this layer
 * @param opacity  uniform layer opacity in [0, 1]
 */
public record TriangleLayer(
    float[] vertices,
    float[] colors,
    BlendMode blend,
    float opacity
) implements Layer {

    public TriangleLayer {
        if (vertices == null) throw new IllegalArgumentException("vertices != null");
        if (vertices.length % 9 != 0) {
            throw new IllegalArgumentException("vertices length must be a multiple of 9 (three xyz per triangle)");
        }
        if (colors != null && colors.length != vertices.length) {
            throw new IllegalArgumentException("colors length must equal vertices length (RGB per vertex) or null");
        }
        if (blend == null) throw new IllegalArgumentException("blend != null");
        if (opacity < 0f || opacity > 1f) throw new IllegalArgumentException("opacity in [0, 1]");
    }

    /** Convenience: ALPHA blend, full opacity. */
    public TriangleLayer(float[] vertices, float[] colors) {
        this(vertices, colors, BlendMode.ALPHA, 1f);
    }

    public int triangleCount() { return vertices.length / 9; }

    public TriangleLayer withBlend(BlendMode b) { return new TriangleLayer(vertices, colors, b, opacity); }
    public TriangleLayer withOpacity(float o)   { return new TriangleLayer(vertices, colors, blend, o); }
}
