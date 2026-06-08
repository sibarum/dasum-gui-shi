package sibarum.dasum.gui.vis.scene;

/**
 * A field that exposes the locked-in dual output: signed distance (via
 * {@link ScalarField}) AND an intrinsic, <b>view-independent</b>
 * complexity measure — fractal escape-iterations, IFS generation depth,
 * recursion count, etc., normalized to [0,1].
 *
 * <p>The complexity channel is the probe-density signal. Surface probes
 * seeded by it land where the geometry is intricate — a property that
 * doesn't change with the camera — so the placement stays optimal from
 * every angle (unlike view-dependent step-count, which thrashes on
 * motion). This is the requirement every future field node (and the
 * external shader-codegen layer) must satisfy.
 */
public interface ComplexityField extends ScalarField {
    /** Intrinsic complexity at a point, normalized to [0,1]. */
    float complexityAt(float x, float y, float z);
}
