package sibarum.dasum.gui.vis.scene;

import sibarum.dasum.gui.core.event.Invalidator;

/**
 * Inspection view mode for {@link SdfLayer} rendering — a global
 * debug switch read by the renderer each frame, NOT layer geometry.
 * Lets a tool/inspector flip how the raymarched surface is displayed
 * (final shading vs. diagnostic channels) without touching the scene.
 *
 * <p>Process-global because it's an inspector affordance over a single
 * viewport; if multiple independent view modes are ever needed, promote
 * it to per-component state. The ordinal is passed straight to the
 * shader's {@code u_viewMode} uniform, so the enum order is the shader
 * contract — keep it in sync with {@code sdf.frag}.
 */
public enum SdfView {
    /** Final Blinn-Phong + AO + soft-shadow shading. */
    LIT,
    /** Surface normal as RGB ({@code n*0.5+0.5}) — orientation debug. */
    NORMALS,
    /** Ambient-occlusion term as greyscale. */
    AO,
    /** March step count as a blue→red heatmap — the near-miss / cost map. */
    STEPS,
    /** Fractal escape-iteration count as a heatmap — the structure map
     *  (Mandelbulb only; flat for non-fractal fields). */
    ESCAPE_ITERS,
    /** Signed cost − structure diverging map: red = cost beyond structural
     *  justification (grazing waste), blue = deep-but-cheap, dark = balanced
     *  (Mandelbulb only). */
    COST_MINUS_ESCAPE,
    /** Total bulb iterations executed during the primary march, normalized to
     *  the worst case — the honest per-eval cost map. Iteration-LOD collapses
     *  the far-field halo here while the march-count (STEPS) map barely moves
     *  (Mandelbulb only). */
    WORK;

    private static volatile SdfView current = LIT;

    public static SdfView current() { return current; }

    /** Set the global view mode and request a redraw. */
    public static void set(SdfView v) {
        if (v == null || v == current) return;
        current = v;
        Invalidator.invalidate();
    }
}
