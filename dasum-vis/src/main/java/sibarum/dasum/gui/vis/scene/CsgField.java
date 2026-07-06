package sibarum.dasum.gui.vis.scene;

import sibarum.dasum.gui.vis.math.Vec3;

import java.util.List;

/**
 * CPU evaluation of a {@link CsgBox} op-list field — a faithful port of
 * the {@code sdCsgBoxes} GLSL in {@code sdf.frag}, so a shape sampled
 * on the CPU matches what the raymarcher draws. The seed of the eventual
 * CPU field evaluator for collision / surface probes; today it backs the
 * "bake an SDF into a splat point cloud" experiment.
 *
 * <p><b>Dual source of truth:</b> this must mirror the shader by hand.
 * Keep the two in lockstep until R2's field-tree codegen generates both
 * the GLSL and this evaluation from one description.
 */
public final class CsgField {

    private CsgField() {}

    /** Build a {@link ScalarField} for the op list + global rounding. */
    public static ScalarField of(List<CsgBox> ops, float rounding) {
        CsgBox[] arr = ops.toArray(new CsgBox[0]);
        return (x, y, z) -> {
            float d = 1e9f;
            for (CsgBox bx : arr) {
                float di = sdBox(x, y, z, bx.center(), bx.halfExtents());
                float k = bx.smoothK();
                switch (bx.op()) {
                    case UNION           -> d = Math.min(d, di);
                    case SUBTRACT        -> d = Math.max(d, -di);
                    case INTERSECT       -> d = Math.max(d, di);
                    case SMOOTH_UNION    -> d = smin(d, di, k);
                    case SMOOTH_SUBTRACT -> d = smax(d, -di, k);
                    case SMOOTH_INTERSECT-> d = smax(d, di, k);
                }
            }
            return d - rounding;
        };
    }

    /**
     * Unit surface normal at {@code p} via the same zero-sum tetrahedron
     * gradient the shader uses (offset {@code h} in world units).
     */
    public static Vec3 normal(ScalarField f, float x, float y, float z, float h) {
        // k = (1,-1): vertices (1,-1,-1),(-1,-1,1),(-1,1,-1),(1,1,1) sum to 0.
        float a = f.at(x + h, y - h, z - h);
        float b = f.at(x - h, y - h, z + h);
        float c = f.at(x - h, y + h, z - h);
        float e = f.at(x + h, y + h, z + h);
        float nx = a - b - c + e;
        float ny = -a - b + c + e;
        float nz = -a + b - c + e;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-12f) return new Vec3(0f, 1f, 0f);
        return new Vec3(nx / len, ny / len, nz / len);
    }

    private static float sdBox(float x, float y, float z, Vec3 c, Vec3 he) {
        float qx = Math.abs(x - c.x()) - he.x();
        float qy = Math.abs(y - c.y()) - he.y();
        float qz = Math.abs(z - c.z()) - he.z();
        float ox = Math.max(qx, 0f), oy = Math.max(qy, 0f), oz = Math.max(qz, 0f);
        float outside = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
        float inside = Math.min(Math.max(qx, Math.max(qy, qz)), 0f);
        return outside + inside;
    }

    private static float smin(float a, float b, float k) {
        float h = clamp(0.5f + 0.5f * (b - a) / k, 0f, 1f);
        return mix(b, a, h) - k * h * (1f - h);
    }

    private static float smax(float a, float b, float k) {
        return -smin(-a, -b, k);
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
