package sibarum.dasum.gui.core.render;

/**
 * Linear-space RGBA color. SRGB conversion (if any) happens in the fragment
 * shader. Components are not clamped here — out-of-[0,1] values are allowed
 * for HDR / tone-mapping use cases that don't exist yet.
 */
public record Color(float r, float g, float b, float a) {
    public static final Color WHITE = new Color(1f, 1f, 1f, 1f);
    public static final Color BLACK = new Color(0f, 0f, 0f, 1f);
    public static final Color TRANSPARENT = new Color(0f, 0f, 0f, 0f);

    public static Color rgb(float r, float g, float b) { return new Color(r, g, b, 1f); }
}
