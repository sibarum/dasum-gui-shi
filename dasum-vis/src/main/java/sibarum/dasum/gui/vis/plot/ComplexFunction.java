package sibarum.dasum.gui.vis.plot;

/**
 * A continuous complex function {@code f: ℂ → ℂ}, sampled over the data
 * domain (not a fixed pixel grid). This is the source a display-resolution
 * field map needs: the renderer can evaluate it at whatever pixel density
 * the on-screen viewport currently has, and re-evaluate the visible region
 * at full density as the user zooms in — a {@link ComplexField2D} bakes its
 * resolution in and so can only be upscaled.
 *
 * <p>Implementations must be pure and thread-safe; a worker may rasterize
 * off the GLFW thread.
 */
@FunctionalInterface
public interface ComplexFunction {

    /**
     * Evaluate {@code f(zr + i·zi)} and write {@code [re, im]} of the result
     * into {@code out2}.
     */
    void eval(double zr, double zi, float[] out2);
}
