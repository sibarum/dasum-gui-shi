package sibarum.dasum.gui.vis.plot;

import sibarum.dasum.gui.vis.scene.BlendMode;
import sibarum.dasum.gui.vis.scene.ImageLayer;

/**
 * The single rasterization path for complex fields: walk a
 * {@link ComplexField2D}, colour each sample through a {@link ComplexColorMap},
 * and pack the result into a straight-alpha RGBA buffer / {@link ImageLayer}.
 * Both the 2D map flow and the 3D slice flow funnel through here.
 *
 * <p>Output rows are top-first to match {@code ImageLayer}; one byte buffer
 * is one full frame. Republishing a fresh {@code ImageLayer} of the same
 * dimensions reuses the GPU texture allocation (a single {@code glTexSubImage2D}),
 * so scrubbing slices or recolouring streams cheaply.
 */
public final class FieldRaster {

    private FieldRaster() {}

    /** Colour {@code field} into a new {@code width*height*4} RGBA byte buffer (top row first). */
    public static byte[] rasterize(ComplexField2D field, ComplexColorMap map) {
        int w = field.width(), h = field.height();
        byte[] rgba = new byte[w * h * 4];
        float[] tmp = new float[2];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                field.sample(x, y, tmp);
                map.color(tmp[0], tmp[1], rgba, (y * w + x) * 4);
            }
        }
        return rgba;
    }

    /**
     * Rasterize {@code field} and wrap it as an {@link ImageLayer} filling
     * {@code frame}'s world rectangle in the XY plane (NEAREST filtering so
     * individual field cells stay crisp). Use {@link #imageLayer(ComplexField2D,
     * ComplexColorMap, PlotFrame, boolean, BlendMode)} to override filtering/blend.
     */
    public static ImageLayer imageLayer(ComplexField2D field, ComplexColorMap map, PlotFrame frame) {
        return imageLayer(field, map, frame, false, BlendMode.ALPHA);
    }

    public static ImageLayer imageLayer(ComplexField2D field, ComplexColorMap map, PlotFrame frame,
                                        boolean smooth, BlendMode blend) {
        byte[] rgba = rasterize(field, map);
        // ImageLayer.rect places the image's top row at y1 (world Y-up) — matches
        // our top-first row order, so y = 0 samples land at the top of the frame.
        float[] corners = {
            frame.wx0(), frame.wy1(), 0f,  // top-left
            frame.wx1(), frame.wy1(), 0f,  // top-right
            frame.wx1(), frame.wy0(), 0f,  // bottom-right
            frame.wx0(), frame.wy0(), 0f,  // bottom-left
        };
        return new ImageLayer(field.width(), field.height(), rgba, corners, smooth, blend, 1f);
    }
}
