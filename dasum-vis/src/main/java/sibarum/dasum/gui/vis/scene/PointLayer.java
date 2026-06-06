package sibarum.dasum.gui.vis.scene;

/**
 * Scatter layer: round screen-space dots at 3D positions.
 *
 * @param positions     xyz triples, row-major; {@code positions.length % 3 == 0}
 * @param colors        optional per-point RGB (linear, 0..1), length
 *                      {@code == positions.length}; {@code null} = default colour
 * @param sizes         optional per-point diameter in pixels, length
 *                      {@code == pointCount}; {@code null} = {@code defaultSizePx}
 *                      for every point
 * @param defaultSizePx point diameter used where {@code sizes} is null
 * @param blend         fixed-function blend mode for this layer
 * @param opacity       uniform layer opacity in [0, 1]
 */
public record PointLayer(
    float[] positions,
    float[] colors,
    float[] sizes,
    float defaultSizePx,
    BlendMode blend,
    float opacity
) implements Layer {

    /** Default point diameter — matches the pre-scene renderer's constant. */
    public static final float DEFAULT_SIZE_PX = 5f;

    public PointLayer {
        if (positions == null) throw new IllegalArgumentException("positions != null");
        if (positions.length % 3 != 0) throw new IllegalArgumentException("positions length must be a multiple of 3");
        if (colors != null && colors.length != positions.length) {
            throw new IllegalArgumentException("colors length must equal positions length (RGB per point) or null");
        }
        if (sizes != null && sizes.length != positions.length / 3) {
            throw new IllegalArgumentException("sizes length must equal pointCount or null");
        }
        if (!(defaultSizePx > 0f)) throw new IllegalArgumentException("defaultSizePx > 0");
        if (blend == null) throw new IllegalArgumentException("blend != null");
        if (opacity < 0f || opacity > 1f) throw new IllegalArgumentException("opacity in [0, 1]");
    }

    /** Convenience: default size, ALPHA blend, full opacity. */
    public PointLayer(float[] positions, float[] colors) {
        this(positions, colors, null, DEFAULT_SIZE_PX, BlendMode.ALPHA, 1f);
    }

    public int pointCount() { return positions.length / 3; }

    public PointLayer withBlend(BlendMode b)   { return new PointLayer(positions, colors, sizes, defaultSizePx, b, opacity); }
    public PointLayer withOpacity(float o)     { return new PointLayer(positions, colors, sizes, defaultSizePx, blend, o); }
    public PointLayer withDefaultSize(float s) { return new PointLayer(positions, colors, sizes, s, blend, opacity); }
}
