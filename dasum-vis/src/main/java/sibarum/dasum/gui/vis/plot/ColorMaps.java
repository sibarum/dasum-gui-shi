package sibarum.dasum.gui.vis.plot;

import sibarum.dasum.gui.core.render.Color;

/**
 * Scalar colormaps ({@code t} in {@code [0, 1]} → {@link Color}) and the
 * HSV→RGB helper the complex maps build on. The framework ships no built-in
 * colormaps, so this is the shared palette for heatmaps and as the lightness
 * track of {@link ComplexColorMaps}. Callers wanting a bespoke palette
 * implement {@link ScalarColorMap} directly.
 *
 * <p>Colours are returned in straight RGB suitable for both {@code Color}
 * (linear) consumers and {@link FieldRaster}'s byte packing.
 */
public final class ColorMaps {

    private ColorMaps() {}

    /** {@code t} in [0,1] → colour. */
    @FunctionalInterface
    public interface ScalarColorMap {
        Color at(float t);
    }

    /** Perceptually-uniform-ish blue→green→yellow ramp (viridis approximation). */
    public static final ScalarColorMap VIRIDIS = t -> {
        float x = clamp01(t);
        // Cubic-ish polynomial fit to the viridis control points; cheap & close.
        float r = clamp01(0.267f + x * (0.005f + x * (2.27f + x * (-3.68f + x * 2.13f))));
        float g = clamp01(0.005f + x * (1.41f + x * (-0.80f + x * 0.34f)));
        float b = clamp01(0.329f + x * (1.39f + x * (-3.95f + x * 2.95f)) - 0.18f * x * x * x * x);
        return new Color(r, g, b, 1f);
    };

    /** Black → white. */
    public static final ScalarColorMap GRAYSCALE = t -> {
        float x = clamp01(t);
        return new Color(x, x, x, 1f);
    };

    /** Diverging blue → light → red, centred at {@code t = 0.5}. */
    public static final ScalarColorMap COOL_WARM = t -> {
        float x = clamp01(t);
        float r = clamp01(0.23f + 1.4f * x);
        float g = clamp01(0.30f + 1.4f * x - 1.4f * Math.abs(x - 0.5f) * 2f * 0.5f);
        float b = clamp01(0.75f - 1.0f * x + 0.55f);
        return new Color(r, g, b, 1f);
    };

    /**
     * HSV→RGB. {@code h} in [0,1) (wraps), {@code s} and {@code v} in [0,1].
     * Returns an opaque {@link Color}.
     */
    public static Color hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);            // wrap into [0,1)
        s = clamp01(s); v = clamp01(v);
        float i = (float) Math.floor(h * 6f);
        float f = h * 6f - i;
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float u = v * (1f - (1f - f) * s);
        int seg = ((int) i) % 6;
        return switch (seg) {
            case 0 -> new Color(v, u, p, 1f);
            case 1 -> new Color(q, v, p, 1f);
            case 2 -> new Color(p, v, u, 1f);
            case 3 -> new Color(p, q, v, 1f);
            case 4 -> new Color(u, p, v, 1f);
            default -> new Color(v, p, q, 1f);
        };
    }

    static float clamp01(float x) { return x < 0f ? 0f : (x > 1f ? 1f : x); }
}
