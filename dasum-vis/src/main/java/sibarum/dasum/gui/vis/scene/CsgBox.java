package sibarum.dasum.gui.vis.scene;

import sibarum.dasum.gui.vis.math.Vec3;

import java.util.List;

/**
 * One step of a {@link SdfLayer.Field#CSG_BOXES} shape program: an
 * axis-aligned box combined into the running field by a boolean op. A
 * shape is a sequential left-fold over its op list —
 * {@code d = op(d, box)} — which covers union/subtract/intersect chains
 * (the overwhelmingly common case) without any tree interpretation, so
 * the whole program travels as a uniform array: editing a shape never
 * recompiles a shader.
 *
 * <p>Smooth variants use the polynomial smooth-min with blend radius
 * {@code smoothK} (world units) — boxes melt together / carve with
 * fillets instead of hard creases. Layer-level rounding
 * ({@code SdfLayer.params()[0]} for CSG_BOXES) additionally rounds
 * every edge of the final result.
 *
 * <p>The first op in a list should be a UNION (it combines against
 * "empty space"); a leading SUBTRACT/INTERSECT folds against nothing and
 * produces nothing.
 *
 * @param op          how this box combines with the field built so far
 * @param center      box center, in the layer's field-local space
 *                    (bounding-cube center at origin)
 * @param halfExtents box half-sizes per axis (all > 0)
 * @param smoothK     blend radius for SMOOTH_* ops (ignored by hard ops;
 *                    must be > 0 for smooth ops)
 */
public record CsgBox(Op op, Vec3 center, Vec3 halfExtents, float smoothK) {

    public enum Op { UNION, SUBTRACT, INTERSECT, SMOOTH_UNION, SMOOTH_SUBTRACT, SMOOTH_INTERSECT }

    /** Floats per packed op: (cx, cy, cz, opcode) + (hx, hy, hz, smoothK). */
    public static final int PACKED_FLOATS = 8;

    /** Uniform-array budget: ops per layer (2 vec4 uniforms each). */
    public static final int MAX_OPS = 48;

    public CsgBox {
        if (op == null) throw new IllegalArgumentException("op != null");
        if (center == null) throw new IllegalArgumentException("center != null");
        if (halfExtents == null) throw new IllegalArgumentException("halfExtents != null");
        if (!(halfExtents.x() > 0f) || !(halfExtents.y() > 0f) || !(halfExtents.z() > 0f)) {
            throw new IllegalArgumentException("halfExtents must be > 0 per axis");
        }
        boolean smooth = op == Op.SMOOTH_UNION || op == Op.SMOOTH_SUBTRACT || op == Op.SMOOTH_INTERSECT;
        if (smooth && !(smoothK > 0f)) {
            throw new IllegalArgumentException("smoothK > 0 required for smooth ops");
        }
    }

    /** Hard-op convenience (smoothK unused). */
    public CsgBox(Op op, Vec3 center, Vec3 halfExtents) {
        this(op, center, halfExtents, 0f);
    }

    /**
     * Pack an op list into the uniform-array layout consumed by the
     * SDF shader: {@value #PACKED_FLOATS} floats per op, two vec4s —
     * {@code (center.xyz, opcode)} then {@code (halfExtents.xyz, smoothK)}.
     */
    public static float[] pack(List<CsgBox> ops) {
        if (ops == null || ops.isEmpty()) throw new IllegalArgumentException("ops must be non-empty");
        if (ops.size() > MAX_OPS) {
            throw new IllegalArgumentException("at most " + MAX_OPS + " ops per layer (got " + ops.size() + ")");
        }
        float[] out = new float[ops.size() * PACKED_FLOATS];
        int w = 0;
        for (CsgBox b : ops) {
            out[w++] = b.center.x();
            out[w++] = b.center.y();
            out[w++] = b.center.z();
            out[w++] = b.op.ordinal();
            out[w++] = b.halfExtents.x();
            out[w++] = b.halfExtents.y();
            out[w++] = b.halfExtents.z();
            out[w++] = b.smoothK;
        }
        return out;
    }
}
