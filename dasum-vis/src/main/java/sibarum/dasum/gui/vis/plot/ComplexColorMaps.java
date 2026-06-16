package sibarum.dasum.gui.vis.plot;

import sibarum.dasum.gui.core.render.Color;

/**
 * Built-in {@link ComplexColorMap} strategies. Two cover the user's stated
 * needs and most others fall out as variations:
 *
 * <ul>
 *   <li>{@link #domainColoring()} — classic complex-function visualisation:
 *       hue = arg(z), brightness rises with |z|, with subtle magnitude
 *       contour banding. Best for a general complex field (a wavefunction,
 *       a transfer function, f: ℂ→ℂ).</li>
 *   <li>{@link #directionField(float)} — treats the sample as a 2D
 *       normal/tangent vector: hue = direction, saturation/brightness track
 *       the vector's magnitude against a reference scale. Best for normal
 *       and tangent maps where direction is the signal.</li>
 * </ul>
 *
 * All are pure and thread-safe.
 */
public final class ComplexColorMaps {

    private ComplexColorMaps() {}

    private static final float INV_TWO_PI = (float) (1.0 / (2.0 * Math.PI));

    /** Domain colouring with default magnitude scaling (|z| mapped through a soft knee). */
    public static ComplexColorMap domainColoring() {
        return domainColoring(1f, true);
    }

    /**
     * @param magnitudeScale |z| value that maps to mid brightness (the knee)
     * @param contours       overlay faint log-magnitude bands when true
     */
    public static ComplexColorMap domainColoring(float magnitudeScale, boolean contours) {
        float scale = magnitudeScale > 0f ? magnitudeScale : 1f;
        return (re, im, out, off) -> {
            float mag = (float) Math.hypot(re, im);
            float hue = (float) (Math.atan2(im, re)) * INV_TWO_PI; // [-0.5, 0.5], wraps in hsv()

            // Soft saturating brightness: 0 at the origin, → ~1 for large |z|.
            float t = mag / (mag + scale);
            float value = 0.15f + 0.85f * t;
            float sat = 1f;
            if (contours) {
                // Faint banding on each magnitude octave to read level sets.
                float band = (float) (Math.log(mag + 1e-12) / Math.log(2));
                float frac = band - (float) Math.floor(band);
                value *= 0.9f + 0.1f * frac;
            }
            ComplexColorMap.put(ColorMaps.hsv(hue, sat, value), out, off);
        };
    }

    /** Direction-field colouring with a reference magnitude of 1 (unit vectors). */
    public static ComplexColorMap directionField() {
        return directionField(1f);
    }

    /**
     * Direction-field / "normal map" colouring: hue encodes the 2D direction
     * {@code atan2(im, re)}, while the vector's length relative to
     * {@code referenceMagnitude} drives saturation and brightness (a
     * zero-length sample reads as dim grey, a full-length one as a vivid hue).
     *
     * @param referenceMagnitude the length that maps to full saturation/brightness
     */
    public static ComplexColorMap directionField(float referenceMagnitude) {
        float ref = referenceMagnitude > 0f ? referenceMagnitude : 1f;
        return (re, im, out, off) -> {
            float mag = (float) Math.hypot(re, im);
            float hue = (float) (Math.atan2(im, re)) * INV_TWO_PI;
            float t = ColorMaps.clamp01(mag / ref);
            // Dim grey at zero length → saturated hue at full length.
            float sat = t;
            float value = 0.25f + 0.75f * t;
            ComplexColorMap.put(ColorMaps.hsv(hue, sat, value), out, off);
        };
    }

    /**
     * Adapt a scalar {@link ColorMaps.ScalarColorMap} to colour by magnitude
     * only (ignoring phase) — handy for plain |z| heatmaps.
     *
     * @param map     scalar palette
     * @param maxMag  magnitude mapped to {@code t = 1}
     */
    public static ComplexColorMap magnitude(ColorMaps.ScalarColorMap map, float maxMag) {
        float m = maxMag > 0f ? maxMag : 1f;
        return (re, im, out, off) -> {
            float t = ColorMaps.clamp01((float) Math.hypot(re, im) / m);
            Color c = map.at(t);
            ComplexColorMap.put(c, out, off);
        };
    }
}
