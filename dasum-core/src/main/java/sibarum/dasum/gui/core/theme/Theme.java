package sibarum.dasum.gui.core.theme;

import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

import java.util.EnumMap;
import java.util.Map;

/**
 * Process-global palette registry. Maps {@link Variant} to {@link Palette}
 * and exposes a shared surface color used as the dim background of
 * variant-tinted input widgets (so the variant only shifts the accent,
 * not the surface).
 * <p>
 * Defaults are a dark-theme palette inspired by Tailwind 500/300 shades
 * — bright enough to read against the demo's dark canvas. Apps may swap
 * any palette or the surface color via {@link #override} / {@link #setSubtleBg}.
 */
public final class Theme {

    private static final Map<Variant, Palette> PALETTES = new EnumMap<>(Variant.class);
    private static Color subtleBg = new Color(0.18f, 0.20f, 0.25f, 1f);
    private static Em scrollbarThickness = Em.of(0.5f);
    private static Color overlayBackdrop = new Color(0f, 0f, 0f, 0.40f);

    private static final Color WHITE      = new Color(1f, 1f, 1f, 1f);
    private static final Color NEAR_BLACK = new Color(0.10f, 0.10f, 0.12f, 1f);

    static {
        PALETTES.put(Variant.DEFAULT, new Palette(
            new Color(0.424f, 0.459f, 0.490f, 1f),  // #6c757d
            new Color(0.678f, 0.710f, 0.741f, 1f),  // #adb5bd
            WHITE
        ));
        PALETTES.put(Variant.PRIMARY, new Palette(
            new Color(0.231f, 0.510f, 0.965f, 1f),  // #3b82f6
            new Color(0.576f, 0.773f, 0.992f, 1f),  // #93c5fd
            WHITE
        ));
        PALETTES.put(Variant.SUCCESS, new Palette(
            new Color(0.133f, 0.773f, 0.369f, 1f),  // #22c55e
            new Color(0.525f, 0.937f, 0.675f, 1f),  // #86efac
            WHITE
        ));
        PALETTES.put(Variant.WARNING, new Palette(
            new Color(0.961f, 0.620f, 0.043f, 1f),  // #f59e0b
            new Color(0.988f, 0.827f, 0.302f, 1f),  // #fcd34d
            NEAR_BLACK
        ));
        PALETTES.put(Variant.ERROR, new Palette(
            new Color(0.937f, 0.267f, 0.267f, 1f),  // #ef4444
            new Color(0.988f, 0.647f, 0.647f, 1f),  // #fca5a5
            WHITE
        ));
        PALETTES.put(Variant.INFO, new Palette(
            new Color(0.024f, 0.714f, 0.831f, 1f),  // #06b6d4
            new Color(0.404f, 0.910f, 0.976f, 1f),  // #67e8f9
            NEAR_BLACK
        ));
    }

    private Theme() {}

    public static Palette of(Variant v) {
        return PALETTES.get(v);
    }

    public static void override(Variant v, Palette p) {
        PALETTES.put(v, p);
    }

    public static Color subtleBg() { return subtleBg; }

    public static void setSubtleBg(Color c) { subtleBg = c; }

    /** Track / thumb thickness for scrollbars rendered by {@code Component.Scroll}, in em. */
    public static Em scrollbarThickness() { return scrollbarThickness; }

    /** Scrollbar thickness in pixels — convenience for framework rendering / hit-test code. */
    public static float scrollbarThicknessPx() { return scrollbarThickness.toPixels(); }

    public static void setScrollbarThickness(Em thickness) { scrollbarThickness = thickness; }

    /** Translucent quad painted over the viewport behind modal overlays. */
    public static Color overlayBackdrop() { return overlayBackdrop; }

    public static void setOverlayBackdrop(Color c) { overlayBackdrop = c; }
}
