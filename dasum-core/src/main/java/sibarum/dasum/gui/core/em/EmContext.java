package sibarum.dasum.gui.core.em;

import sibarum.dasum.gui.core.event.Invalidator;

/**
 * Global em-to-pixel conversion state. {@code pixelsPerEm = rootEmPx ×
 * zoom × dpiScale}. Components consume this at layout time only.
 * <p>
 * Reactive: changing zoom or dpi invalidates the renderer so the next
 * frame picks up the new pixel measurements.
 */
public final class EmContext {

    private static float rootEmPx = 16f;
    private static float zoom = 1f;
    private static float dpiScale = 1f;

    private EmContext() {}

    /** Pixels per em — multiply this by any em value to get pixels. */
    public static float pixelsPerEm() {
        return rootEmPx * zoom * dpiScale;
    }

    public static float rootEmPx() { return rootEmPx; }
    public static float zoom() { return zoom; }
    public static float dpiScale() { return dpiScale; }

    public static void setRootEmPx(float px) {
        if (rootEmPx == px) return;
        rootEmPx = px;
        Invalidator.invalidate();
    }

    public static void setZoom(float z) {
        z = Math.max(0.25f, Math.min(8f, z));
        if (zoom == z) return;
        zoom = z;
        Invalidator.invalidate();
    }

    public static void setDpiScale(float s) {
        if (dpiScale == s) return;
        dpiScale = s;
        Invalidator.invalidate();
    }

    public static void multiplyZoom(float factor) {
        setZoom(zoom * factor);
    }
}
