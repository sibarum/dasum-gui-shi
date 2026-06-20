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

    /**
     * Rasterize a continuous {@link ComplexFunction} over an arbitrary
     * world-space sub-rectangle of {@code frame}, at an arbitrary pixel
     * resolution — the building block for display-resolution field maps that
     * re-sample the visible region as the user zooms. Each output pixel maps
     * to a world point inside {@code [tx0, tx1] × [ty0, ty1]} (top row at
     * {@code ty1}, world Y-up), which is converted to a data coordinate
     * through the frame's axes (so LOG axes sample correctly) before
     * evaluating {@code fn}. The returned {@link ImageLayer} covers exactly
     * that world sub-rect.
     *
     * @param w pixels per row (≥ 1)
     * @param h rows (≥ 1)
     */
    public static ImageLayer functionTile(ComplexFunction fn, ComplexColorMap map, PlotFrame frame,
                                          float tx0, float ty0, float tx1, float ty1,
                                          int w, int h, boolean smooth) {
        byte[] rgba = new byte[w * h * 4];
        float[] tmp = new float[2];
        double fwx0 = frame.wx0(), fwx1 = frame.wx1();
        double fwy0 = frame.wy0(), fwy1 = frame.wy1();
        for (int j = 0; j < h; j++) {
            // Top row first: world y runs from ty1 (top) down to ty0.
            double wy = ty1 - (j + 0.5) / h * (ty1 - ty0);
            double zi = frame.y().unitToData((wy - fwy0) / (fwy1 - fwy0));
            for (int i = 0; i < w; i++) {
                double wx = tx0 + (i + 0.5) / w * (tx1 - tx0);
                double zr = frame.x().unitToData((wx - fwx0) / (fwx1 - fwx0));
                fn.eval(zr, zi, tmp);
                map.color(tmp[0], tmp[1], rgba, (j * w + i) * 4);
            }
        }
        float[] corners = {
            tx0, ty1, 0f,  // top-left
            tx1, ty1, 0f,  // top-right
            tx1, ty0, 0f,  // bottom-right
            tx0, ty0, 0f,  // bottom-left
        };
        return new ImageLayer(w, h, rgba, corners, smooth, BlendMode.ALPHA, 1f);
    }
}
