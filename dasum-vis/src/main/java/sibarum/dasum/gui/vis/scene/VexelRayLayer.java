package sibarum.dasum.gui.vis.scene;

import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.vis.math.Vec3;

/**
 * VexelRay R1 — a raymarched signed-distance field. Unlike every other
 * layer, the content is <em>computed per fragment</em>: the GPU sphere-
 * traces the selected field inside an axis-aligned bounding cube
 * ({@code center} ± {@code scale}) and shades the hit surface. The hit
 * writes real depth, so in perspective scenes the other layers (points,
 * lines, text) correctly occlude and pierce the field surface.
 *
 * <p>R1 is the fixed-function tier: a built-in {@link Field} menu with
 * a {@code float[4]} parameter block, all delivered as uniforms — no
 * shader recompilation ever. The composable {@code Field}-tree codegen
 * tier (CSG, domain ops, Mix/Iterate) is R2 and will arrive as a
 * separate authoring API on top of the same rendering seam.
 *
 * <p>Param block semantics by field (unused slots ignored):
 * <ul>
 *   <li>{@code SPHERE}     — [radius]</li>
 *   <li>{@code BOX}        — [halfX, halfY, halfZ]</li>
 *   <li>{@code TORUS}      — [majorRadius, minorRadius]</li>
 *   <li>{@code BLOBS}      — [spread, smoothK] — three spheres smooth-
 *       unioned; k is the blend radius that melts them together</li>
 *   <li>{@code MANDELBULB} — [power, iterations] — the classic power-N
 *       triplex bulb; power 8 is canonical</li>
 * </ul>
 *
 * <p>Default blend is {@link BlendMode#OPAQUE}: the field is a surface,
 * and OPAQUE layers write depth in perspective scenes (the renderer's
 * standing rule), which is what makes depth-composition work.
 * {@code maxSteps} is the quality/cost knob — march iterations per
 * fragment.
 *
 * @param field    which built-in SDF to trace
 * @param params   float[4] parameter block, semantics per field
 * @param center   bounding-cube center in world space
 * @param scale    bounding-cube half-extent (> 0)
 * @param color    surface base colour (lit per fragment; alpha × opacity)
 * @param maxSteps march iteration cap, in [8, 512]
 * @param blend    fixed-function blend mode for this layer
 * @param opacity  uniform layer opacity in [0, 1]
 */
public record VexelRayLayer(
    Field field,
    float[] params,
    Vec3 center,
    float scale,
    Color color,
    int maxSteps,
    BlendMode blend,
    float opacity
) implements Layer {

    public enum Field { SPHERE, BOX, TORUS, BLOBS, MANDELBULB }

    public VexelRayLayer {
        if (field == null) throw new IllegalArgumentException("field != null");
        if (params == null || params.length != 4) {
            throw new IllegalArgumentException("params must be float[4]");
        }
        if (center == null) throw new IllegalArgumentException("center != null");
        if (!(scale > 0f)) throw new IllegalArgumentException("scale > 0");
        if (color == null) throw new IllegalArgumentException("color != null");
        if (maxSteps < 8 || maxSteps > 512) {
            throw new IllegalArgumentException("maxSteps in [8, 512]");
        }
        if (blend == null) throw new IllegalArgumentException("blend != null");
        if (opacity < 0f || opacity > 1f) throw new IllegalArgumentException("opacity in [0, 1]");
    }

    /** Sensible defaults per field: unit-ish size, origin, soft blue-white, 96 steps. */
    public static VexelRayLayer of(Field field) {
        float[] params = switch (field) {
            case SPHERE     -> new float[]{0.8f, 0f, 0f, 0f};
            case BOX        -> new float[]{0.6f, 0.45f, 0.6f, 0f};
            case TORUS      -> new float[]{0.65f, 0.25f, 0f, 0f};
            case BLOBS      -> new float[]{0.55f, 0.35f, 0f, 0f};
            case MANDELBULB -> new float[]{8f, 10f, 0f, 0f};
        };
        return new VexelRayLayer(field, params, Vec3.ZERO, 1.2f,
            new Color(0.75f, 0.82f, 0.95f, 1f), 96, BlendMode.OPAQUE, 1f);
    }

    public VexelRayLayer withParams(float[] p)   { return new VexelRayLayer(field, p, center, scale, color, maxSteps, blend, opacity); }
    public VexelRayLayer withCenter(Vec3 c)      { return new VexelRayLayer(field, params, c, scale, color, maxSteps, blend, opacity); }
    public VexelRayLayer withScale(float s)      { return new VexelRayLayer(field, params, center, s, color, maxSteps, blend, opacity); }
    public VexelRayLayer withColor(Color c)      { return new VexelRayLayer(field, params, center, scale, c, maxSteps, blend, opacity); }
    public VexelRayLayer withMaxSteps(int n)     { return new VexelRayLayer(field, params, center, scale, color, n, blend, opacity); }
    public VexelRayLayer withBlend(BlendMode b)  { return new VexelRayLayer(field, params, center, scale, color, maxSteps, b, opacity); }
    public VexelRayLayer withOpacity(float o)    { return new VexelRayLayer(field, params, center, scale, color, maxSteps, blend, o); }
}
