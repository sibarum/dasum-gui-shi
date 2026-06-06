package sibarum.dasum.gui.vis.math;

/**
 * Camera convenience constructors: frame a bounding box, jump to standard
 * views. Pure record math — pairs naturally with the declarative
 * transition system if the caller wants the resulting spec animated
 * rather than snapped.
 */
public final class CameraRig {

    /** Headroom so framed content doesn't touch the viewport edges. */
    private static final float FIT_MARGIN = 1.2f;

    /** Just shy of straight-down to avoid the lookAt up-vector degeneracy. */
    private static final float TOP_PITCH = (float) (Math.PI / 2.0 - 0.02);

    /** Classic isometric elevation: atan(1/sqrt(2)) ≈ 35.264°. */
    private static final float ISO_PITCH = (float) Math.atan(1.0 / Math.sqrt(2.0));

    private CameraRig() {}

    /**
     * A camera spec that frames the axis-aligned bounding box
     * {@code [min, max]} with a little margin, preserving {@code base}'s
     * mode, orientation (yaw/pitch) and lens (fov/near/far) — only target
     * and distance/orthoScale change. Degenerate (zero-size) bounds frame
     * a unit-ish region instead of collapsing.
     */
    public static CameraSpec fitToBounds(CameraSpec base, Vec3 min, Vec3 max) {
        Vec3 center = new Vec3(
            (min.x() + max.x()) * 0.5f,
            (min.y() + max.y()) * 0.5f,
            (min.z() + max.z()) * 0.5f
        );
        float radius = max.sub(min).length() * 0.5f;
        if (!(radius > 1e-6f)) radius = 1f;

        CameraSpec framed = base.withTarget(center);
        if (base.mode() == CameraMode.ORTHOGRAPHIC) {
            return framed.withOrthoScale(radius * FIT_MARGIN);
        }
        float distance = (float) (radius * FIT_MARGIN / Math.tan(base.fovYRad() * 0.5));
        return framed.withDistance(distance);
    }

    /** Looking down -Z at the target. */
    public static CameraSpec front(CameraSpec base) { return base.withYaw(0f).withPitch(0f); }

    /** Looking down -X at the target. */
    public static CameraSpec side(CameraSpec base) { return base.withYaw((float) (Math.PI / 2.0)).withPitch(0f); }

    /** Looking (almost) straight down +Y; pitch clamped shy of 90°. */
    public static CameraSpec top(CameraSpec base) { return base.withYaw(0f).withPitch(TOP_PITCH); }

    /** Classic isometric three-quarter view. */
    public static CameraSpec iso(CameraSpec base) { return base.withYaw((float) (Math.PI / 4.0)).withPitch(ISO_PITCH); }
}
