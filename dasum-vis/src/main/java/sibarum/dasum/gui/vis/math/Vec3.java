package sibarum.dasum.gui.vis.math;

/**
 * Immutable 3D float vector — public API type for code crossing the
 * worker / render-thread boundary. The renderer internally uses JOML's
 * mutable {@code Vector3f}, but those instances must never escape to
 * consumer threads (see the {@code dasum-vis} package documentation on
 * threading).
 */
public record Vec3(float x, float y, float z) {
    public static final Vec3 ZERO = new Vec3(0f, 0f, 0f);
    public static final Vec3 UP   = new Vec3(0f, 1f, 0f);

    public Vec3 add(Vec3 o)        { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 sub(Vec3 o)        { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public Vec3 scale(float s)     { return new Vec3(x * s, y * s, z * s); }
    public float length()          { return (float) Math.sqrt(x * x + y * y + z * z); }
}
