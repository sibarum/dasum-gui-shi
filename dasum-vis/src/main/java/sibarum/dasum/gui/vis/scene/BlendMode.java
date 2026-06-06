package sibarum.dasum.gui.vis.scene;

/**
 * Per-{@link Layer} fixed-function blend mode. The scene renderer maps
 * each mode to a {@code glBlendFunc} / {@code glBlendEquation} pair before
 * drawing the layer, and restores the framework default (ALPHA) when the
 * viewport scope closes.
 *
 * <p><b>Order-dependence:</b> ALPHA and MULTIPLY are non-commutative —
 * with these modes the painter's order of {@link SceneSnapshot#layers()}
 * is semantic. ADDITIVE, SCREEN, MAX and MIN are commutative: draw order
 * (and therefore depth-sort order within the layer) does not affect the
 * result, which makes them the right choice for translucent content in
 * perspective scenes — unsorted ALPHA-blended 3D geometry pops as the
 * camera orbits; commutative modes never do.
 *
 * <p>MAX with a colormapped point/voxel layer is maximum-intensity
 * projection; MIN is its dual. MULTIPLY darkens (tint/shadow/masking);
 * SCREEN lightens. OPAQUE disables blending for the layer entirely.
 *
 * <p>These are the fixed-function family. Photoshop-style OVERLAY /
 * SOFT_LIGHT need framebuffer reads and are deliberately not promised
 * here.
 */
public enum BlendMode {
    /** Standard translucency: src_alpha / one_minus_src_alpha. Default. */
    ALPHA,
    /** Accumulate brightness: src_alpha / one. Glow, density, particles. */
    ADDITIVE,
    /** Soft lighten: one / one_minus_src_color. */
    SCREEN,
    /** Darken/tint: dst_color / zero. */
    MULTIPLY,
    /** Per-channel maximum (blend equation MAX). Max-intensity projection. */
    MAX,
    /** Per-channel minimum (blend equation MIN). */
    MIN,
    /** Blending disabled — the layer overwrites. */
    OPAQUE
}
