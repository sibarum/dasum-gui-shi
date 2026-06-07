package sibarum.dasum.gui.vis.scene;

/**
 * A colormapped pixel buffer drawn as a textured world-space quad —
 * fractal frames, dense heatmaps, volume slices, any scalar field a
 * worker can rasterize. The buffer uploads to a GPU texture cached by
 * layer reference identity; republishing a NEW layer with the SAME
 * dimensions reuses the texture allocation via sub-image update, so
 * streaming frames (progressive fractal refinement, live training
 * heatmaps) costs one upload and zero GL object churn per frame.
 *
 * <p>Pixels are row-major, <b>top row first</b>, straight-alpha RGBA
 * (4 bytes per pixel). Same ownership contract as every layer: do not
 * mutate {@code rgba} after publishing.
 *
 * @param width   pixels per row
 * @param height  rows
 * @param rgba    {@code width * height * 4} bytes, top row first
 * @param corners world-space quad as 12 floats — top-left, top-right,
 *                bottom-right, bottom-left (xyz each). "Top" = the first
 *                pixel row. Non-planar corners are allowed (the GPU
 *                interpolates), arbitrary orientation in 3D is the point.
 * @param smooth  LINEAR filtering when true; NEAREST (crisp texel edges,
 *                heatmap cells) when false
 * @param blend   fixed-function blend mode for this layer
 * @param opacity uniform layer opacity in [0, 1]
 */
public record ImageLayer(
    int width,
    int height,
    byte[] rgba,
    float[] corners,
    boolean smooth,
    BlendMode blend,
    float opacity
) implements Layer {

    public ImageLayer {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("width and height must be > 0");
        if (rgba == null) throw new IllegalArgumentException("rgba != null");
        if (rgba.length != width * height * 4) {
            throw new IllegalArgumentException("rgba length " + rgba.length
                + " != width * height * 4 (" + width + " * " + height + " * 4)");
        }
        if (corners == null || corners.length != 12) {
            throw new IllegalArgumentException("corners must be 12 floats (TL, TR, BR, BL xyz)");
        }
        if (blend == null) throw new IllegalArgumentException("blend != null");
        if (opacity < 0f || opacity > 1f) throw new IllegalArgumentException("opacity in [0, 1]");
    }

    /**
     * Convenience: an axis-aligned rect in the XY plane at depth {@code z},
     * spanning {@code [x0, x1] × [y0, y1]} with the image's top row at
     * {@code y1} (world Y-up). Smooth filtering, ALPHA blend, full opacity.
     */
    public static ImageLayer rect(float x0, float y0, float x1, float y1, float z,
                                  int width, int height, byte[] rgba) {
        float[] corners = {
            x0, y1, z,   // top-left
            x1, y1, z,   // top-right
            x1, y0, z,   // bottom-right
            x0, y0, z,   // bottom-left
        };
        return new ImageLayer(width, height, rgba, corners, true, BlendMode.ALPHA, 1f);
    }

    public ImageLayer withSmooth(boolean s)    { return new ImageLayer(width, height, rgba, corners, s, blend, opacity); }
    public ImageLayer withBlend(BlendMode b)   { return new ImageLayer(width, height, rgba, corners, smooth, b, opacity); }
    public ImageLayer withOpacity(float o)     { return new ImageLayer(width, height, rgba, corners, smooth, blend, o); }
}
