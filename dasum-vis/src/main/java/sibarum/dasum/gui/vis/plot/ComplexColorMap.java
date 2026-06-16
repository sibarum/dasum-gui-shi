package sibarum.dasum.gui.vis.plot;

/**
 * Maps a single complex sample {@code (re, im)} to a straight-alpha RGBA
 * pixel. This is the pluggable colouring strategy for every complex/vector
 * field map — {@link ComplexColorMaps} ships domain-colouring and
 * direction-field built-ins, and applications supply their own by
 * implementing this interface (per the node-editor-style mandate that
 * appearance is defined by the calling application, not hardcoded here).
 *
 * <p>Implementations write four bytes (R, G, B, A) into {@code out} starting
 * at {@code off}; bytes are unsigned 0..255 stored in a {@code byte}. They
 * must be pure and thread-safe — {@link FieldRaster} may call them across
 * many pixels, potentially off the GLFW thread.
 */
@FunctionalInterface
public interface ComplexColorMap {

    void color(float re, float im, byte[] out, int off);

    /** Pack a {@link sibarum.dasum.gui.core.render.Color} (0..1) into RGBA bytes. */
    static void put(sibarum.dasum.gui.core.render.Color c, byte[] out, int off) {
        out[off]     = unit8(c.r());
        out[off + 1] = unit8(c.g());
        out[off + 2] = unit8(c.b());
        out[off + 3] = unit8(c.a());
    }

    private static byte unit8(float v) {
        int i = Math.round(ColorMaps.clamp01(v) * 255f);
        return (byte) i;
    }
}
