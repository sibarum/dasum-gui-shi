package sibarum.dasum.gui.vis.scene;

import sibarum.dasum.gui.vis.math.Vec3;

/**
 * A typed shader-uniform value carried by a {@link RaymarchLayer}. This is
 * the generic name→value channel that lets a downstream shader (hand-
 * written or codegen-emitted) expose arbitrary parameters without a
 * bespoke Java binding path: the renderer walks the layer's uniform map
 * and pushes each value with the matching {@code glUniform*} call.
 *
 * <p>Only the small set of GLSL scalar/vector/matrix types the raymarch
 * harness needs is modelled. Construct via the static factories
 * ({@link #i}, {@link #f}, {@link #vec2}, {@link #vec3}, {@link #vec4},
 * {@link #vec4Array}, {@link #mat4}); the records are the tagged carriers
 * the renderer switches on.
 *
 * <p><b>System uniforms are off-limits.</b> Camera/light/viewport values
 * ({@link RaymarchLayer#SYSTEM_UNIFORMS}) are supplied by the renderer;
 * a layer that tries to bind one of those names is rejected at
 * construction.
 */
public sealed interface Uniform {

    /** {@code int} / {@code bool}-as-int uniform. */
    static Uniform i(int value) { return new Int1(value); }

    /** {@code float} uniform. */
    static Uniform f(float value) { return new Float1(value); }

    /** {@code vec2} uniform. */
    static Uniform vec2(float x, float y) { return new Vec2(x, y); }

    /** {@code vec3} uniform. */
    static Uniform vec3(float x, float y, float z) { return new Vec3f(x, y, z); }

    /** {@code vec3} uniform from a {@link Vec3}. */
    static Uniform vec3(Vec3 v) { return new Vec3f(v.x(), v.y(), v.z()); }

    /** {@code vec4} uniform. */
    static Uniform vec4(float x, float y, float z, float w) { return new Vec4f(x, y, z, w); }

    /** {@code vec4[]} uniform; {@code values.length} must be a multiple of 4. */
    static Uniform vec4Array(float[] values) { return new Vec4Array(values); }

    /** {@code mat4} uniform in column-major order; {@code m.length} must be 16. */
    static Uniform mat4(float[] m) { return new Mat4(m); }

    record Int1(int value) implements Uniform {}

    record Float1(float value) implements Uniform {}

    record Vec2(float x, float y) implements Uniform {}

    record Vec3f(float x, float y, float z) implements Uniform {}

    record Vec4f(float x, float y, float z, float w) implements Uniform {}

    record Vec4Array(float[] values) implements Uniform {
        public Vec4Array {
            if (values == null || values.length == 0 || values.length % 4 != 0) {
                throw new IllegalArgumentException("vec4Array length must be a positive multiple of 4");
            }
        }
    }

    record Mat4(float[] m) implements Uniform {
        public Mat4 {
            if (m == null || m.length != 16) {
                throw new IllegalArgumentException("mat4 must have length 16");
            }
        }
    }
}
