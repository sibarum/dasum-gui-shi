package sibarum.dasum.gui.vis.scene;

import sibarum.dasum.gui.core.render.ShaderUtil;

/**
 * Assembles complete raymarch fragment shaders from a signed-distance
 * function, for the {@link RaymarchLayer#standard} turnkey path. The
 * standard harness (camera-ray setup, sphere trace, tetrahedron-gradient
 * normal, AO, soft shadow, Blinn-Phong shading, {@code gl_FragDepth}
 * compose, and the LIT/NORMALS/AO/STEPS view modes) lives in a resource
 * template; the caller supplies only the field.
 *
 * <p>A downstream codegen project that wants full control over shading
 * (subsurface scattering, refraction, custom materials) bypasses this and
 * constructs {@link RaymarchLayer} with its own complete
 * {@code fragmentSource}; the harness here is the batteries-included
 * default, not the only path.
 */
public final class RaymarchShader {

    /** Splice marker in {@code raymarch_std.frag}. */
    private static final String MARKER = "//@SDF@";

    private static volatile String template;

    private RaymarchShader() {}

    /**
     * Build a standard-shaded fragment shader. {@code sdf} is GLSL that
     * defines {@code float map(vec3 p)} in local (box-centered)
     * coordinates, plus any helper functions it uses. Example:
     * <pre>{@code
     * RaymarchShader.standard("float map(vec3 p){ return length(p) - 0.8; }");
     * }</pre>
     */
    public static String standard(String sdf) {
        if (sdf == null || sdf.isBlank()) {
            throw new IllegalArgumentException("sdf must define float map(vec3 p)");
        }
        String tpl = template;
        if (tpl == null) {
            tpl = ShaderUtil.readResource("/shaders/raymarch_std.frag");
            if (!tpl.contains(MARKER)) {
                throw new IllegalStateException("raymarch_std.frag is missing the " + MARKER + " splice marker");
            }
            template = tpl;
        }
        return tpl.replace(MARKER, sdf);
    }
}
