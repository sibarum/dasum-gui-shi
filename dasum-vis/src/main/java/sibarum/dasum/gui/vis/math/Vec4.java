package sibarum.dasum.gui.vis.math;

/**
 * Immutable 4D float vector — see {@link Vec3} for thread-affinity notes.
 */
public record Vec4(float x, float y, float z, float w) {
    public static final Vec4 ZERO = new Vec4(0f, 0f, 0f, 0f);
}
