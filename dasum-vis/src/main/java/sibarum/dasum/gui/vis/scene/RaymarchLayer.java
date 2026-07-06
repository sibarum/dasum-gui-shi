package sibarum.dasum.gui.vis.scene;

import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.vis.math.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A raymarched layer whose surface is defined by a <em>caller-supplied
 * GLSL fragment shader</em>. Where {@link SdfLayer} selects from a
 * fixed built-in field menu, {@code RaymarchLayer} hosts arbitrary shader
 * source — so a downstream program that generates GLSL (the codegen
 * project) can render any signed-distance field through dasum-vis without
 * modifying the framework: it hands over a fragment shader and a bag of
 * uniforms, and the renderer compiles, caches, and draws it.
 *
 * <p><b>Rendering contract.</b> Geometry is the layer's bounding box
 * ({@code center} ± {@code halfExtent}); the fixed vertex stage emits the
 * box and a world-space fragment position, and the fragment shader
 * sphere-traces inside it. The shader:
 * <ul>
 *   <li>declares {@code in vec3 v_world;}</li>
 *   <li>may read any of the system uniforms the renderer supplies each
 *       frame ({@link #SYSTEM_UNIFORMS}: {@code u_mvp}, {@code u_center},
 *       {@code u_halfExtent}, {@code u_eye}, {@code u_forward},
 *       {@code u_ortho}, {@code u_lightDir}, {@code u_viewMode}) — only
 *       the ones it actually declares are bound;</li>
 *   <li>should write {@code gl_FragDepth} from the hit point so vector
 *       layers depth-compose against the surface (as the built-in fields
 *       do), and {@code discard} on a miss.</li>
 * </ul>
 * Every other uniform the shader declares is a shape parameter supplied
 * through {@link #uniforms()}.
 *
 * <p><b>Program caching.</b> Compiled programs are cached by
 * {@code fragmentSource} identity across the whole scene, so parameterise
 * through {@link #uniforms()} — never by regenerating source per frame, or
 * the cache degenerates into a per-frame recompile. {@code fragmentSource}
 * is the structure hash.
 *
 * <p>Use {@link #standard(String, Vec3, Vec3, Color)} for the turnkey
 * path: supply just a {@code float map(vec3 p)} body and get a
 * Blinn-Phong-shaded shape with sensible default uniforms. Use the full
 * constructor to host a completely custom shader (subsurface scattering,
 * refraction, custom materials, …).
 *
 * @param fragmentSource complete GLSL fragment shader source (non-blank)
 * @param center         bounding-box center in world space
 * @param halfExtent     bounding-box half-extents per axis (all &gt; 0)
 * @param uniforms       shape-parameter uniforms by name; may be empty,
 *                       must not name a {@link #SYSTEM_UNIFORMS} entry
 * @param blend          blend mode (OPAQUE writes depth in perspective)
 * @param opacity        uniform layer opacity in [0, 1]
 */
public record RaymarchLayer(
    String fragmentSource,
    Vec3 center,
    Vec3 halfExtent,
    Map<String, Uniform> uniforms,
    BlendMode blend,
    float opacity
) implements Layer {

    /** Uniform names the renderer owns; a layer must not bind these. */
    public static final Set<String> SYSTEM_UNIFORMS = Set.of(
        "u_mvp", "u_center", "u_halfExtent", "u_eye", "u_forward",
        "u_ortho", "u_lightDir", "u_viewMode");

    public RaymarchLayer {
        if (fragmentSource == null || fragmentSource.isBlank()) {
            throw new IllegalArgumentException("fragmentSource must be non-blank");
        }
        if (center == null) throw new IllegalArgumentException("center != null");
        if (halfExtent == null) throw new IllegalArgumentException("halfExtent != null");
        if (!(halfExtent.x() > 0f) || !(halfExtent.y() > 0f) || !(halfExtent.z() > 0f)) {
            throw new IllegalArgumentException("halfExtent must be > 0 per axis");
        }
        if (uniforms == null) throw new IllegalArgumentException("uniforms != null (use an empty map)");
        // Defensive copy — the map is read lock-free on the render thread.
        Map<String, Uniform> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Uniform> e : uniforms.entrySet()) {
            String name = e.getKey();
            Uniform value = e.getValue();
            if (name == null || name.isBlank()) throw new IllegalArgumentException("uniform name must be non-blank");
            if (value == null) throw new IllegalArgumentException("uniform value != null for '" + name + "'");
            if (SYSTEM_UNIFORMS.contains(name)) {
                throw new IllegalArgumentException("'" + name + "' is a renderer-owned system uniform");
            }
            copy.put(name, value);
        }
        uniforms = Map.copyOf(copy);
        if (blend == null) throw new IllegalArgumentException("blend != null");
        if (opacity < 0f || opacity > 1f) throw new IllegalArgumentException("opacity in [0, 1]");
    }

    /** Full-detail default: OPAQUE (writes depth), full opacity. */
    public RaymarchLayer(String fragmentSource, Vec3 center, Vec3 halfExtent, Map<String, Uniform> uniforms) {
        this(fragmentSource, center, halfExtent, uniforms, BlendMode.OPAQUE, 1f);
    }

    /**
     * Turnkey standard-shaded shape from a signed-distance function. The
     * {@code sdf} argument is GLSL that defines {@code float map(vec3 p)}
     * (in local coordinates — the box center is the origin), plus any
     * helper functions it needs; it is spliced into the standard raymarch
     * harness (see {@link RaymarchShader#standard}). Default uniforms give
     * a soft blue-white Blinn-Phong surface at 96 march steps; override any
     * of them via {@link #withUniform}.
     */
    public static RaymarchLayer standard(String sdf, Vec3 center, Vec3 halfExtent, Color color) {
        if (color == null) throw new IllegalArgumentException("color != null");
        Map<String, Uniform> u = new LinkedHashMap<>();
        u.put("u_color", Uniform.vec4(color.r(), color.g(), color.b(), color.a()));
        u.put("u_maxSteps", Uniform.i(96));
        u.put("u_relax", Uniform.f(1f));       // full-length steps (exact SDFs); lower for loose bounds
        u.put("u_epsScale", Uniform.f(1f));    // hit tolerance multiplier
        u.put("u_normalEps", Uniform.f(2e-3f));// normal tap, fraction of the box half-extent
        return new RaymarchLayer(RaymarchShader.standard(sdf), center, halfExtent, u);
    }

    /** Copy with one uniform added or replaced. */
    public RaymarchLayer withUniform(String name, Uniform value) {
        Map<String, Uniform> next = new LinkedHashMap<>(uniforms);
        next.put(name, value);
        return new RaymarchLayer(fragmentSource, center, halfExtent, next, blend, opacity);
    }

    public RaymarchLayer withUniforms(Map<String, Uniform> u) { return new RaymarchLayer(fragmentSource, center, halfExtent, u, blend, opacity); }
    public RaymarchLayer withCenter(Vec3 c)                   { return new RaymarchLayer(fragmentSource, c, halfExtent, uniforms, blend, opacity); }
    public RaymarchLayer withHalfExtent(Vec3 h)              { return new RaymarchLayer(fragmentSource, center, h, uniforms, blend, opacity); }
    public RaymarchLayer withBlend(BlendMode b)              { return new RaymarchLayer(fragmentSource, center, halfExtent, uniforms, b, opacity); }
    public RaymarchLayer withOpacity(float o)                { return new RaymarchLayer(fragmentSource, center, halfExtent, uniforms, blend, o); }
}
