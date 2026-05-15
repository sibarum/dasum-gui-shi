package sibarum.dasum.gui.core.em;

/**
 * A scalar length in em units. One em equals the framework's root font size,
 * scaled by the current zoom factor and the system DPI factor — see
 * {@link EmContext}.
 * <p>
 * Components are written in em throughout; conversion to pixels happens
 * exactly once per layout pass, at the boundary into the renderer.
 * Cascading em (parent's font-size affecting child) is deliberately NOT
 * used — that's CSS's most confusing bit, and we have a flat root anchor.
 */
public record Em(float value) {

    public static final Em ZERO = new Em(0f);

    /**
     * Fit-content sentinel — interpret as "size to children" wherever an
     * axis accepts an em length. Backed by {@code NaN} so it cannot be
     * confused with a real measurement. Distinct from {@code null}, which
     * means "fill the parent's available extent". Callers must check
     * {@link #isAuto} before {@link #toPixels}.
     */
    public static final Em AUTO = new Em(Float.NaN);

    public static Em of(float value) { return new Em(value); }

    public boolean isAuto() { return Float.isNaN(value); }

    public Em plus(Em other) { return new Em(value + other.value); }
    public Em minus(Em other) { return new Em(value - other.value); }
    public Em times(float scalar) { return new Em(value * scalar); }
    public Em half() { return new Em(value * 0.5f); }

    public float toPixels() { return value * EmContext.pixelsPerEm(); }
}
