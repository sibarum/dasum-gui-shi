package sibarum.dasum.gui.vis.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property tests for the single-source-of-truth MVP composition and the
 * CameraRig helpers. These encode the screen-mapping invariants the
 * renderer and picker both rely on, so a drift in either direction
 * fails here rather than as a subtle pick-offset bug.
 */
final class CameraMathTest {

    /** Project a world point through an MVP (column-major), returning NDC xyz. */
    private static float[] ndc(float[] m, float x, float y, float z) {
        float w = m[3]*x + m[7]*y + m[11]*z + m[15];
        return new float[]{
            (m[0]*x + m[4]*y + m[8]*z  + m[12]) / w,
            (m[1]*x + m[5]*y + m[9]*z  + m[13]) / w,
            (m[2]*x + m[6]*y + m[10]*z + m[14]) / w,
        };
    }

    @Test
    void targetProjectsToViewCenter_perspective() {
        CameraSpec cam = CameraSpec.defaultPerspective()
            .withTarget(new Vec3(3f, -2f, 7f))
            .withYaw(0.7f).withPitch(0.3f);
        float[] m = CameraMath.mvp(cam, 1.5f);
        float[] p = ndc(m, 3f, -2f, 7f);
        assertEquals(0f, p[0], 1e-4f, "orbit target must land at NDC x=0");
        assertEquals(0f, p[1], 1e-4f, "orbit target must land at NDC y=0");
    }

    @Test
    void targetProjectsToViewCenter_ortho() {
        CameraSpec cam = CameraSpec.defaultOrtho().withTarget(new Vec3(5f, 4f, 0f));
        float[] m = CameraMath.mvp(cam, 2f);
        float[] p = ndc(m, 5f, 4f, 0f);
        assertEquals(0f, p[0], 1e-5f);
        assertEquals(0f, p[1], 1e-5f);
    }

    @Test
    void orthoScaleMapsToFullHeight() {
        // A point orthoScale above the target must land exactly at the top
        // edge (NDC y = +1); aspect scales the width axis only.
        float scale = 2f;
        CameraSpec cam = CameraSpec.defaultOrtho().withOrthoScale(scale);
        float aspect = 1.6f;
        float[] m = CameraMath.mvp(cam, aspect);
        assertEquals(1f, ndc(m, 0f, scale, 0f)[1], 1e-5f);
        assertEquals(1f, ndc(m, scale * aspect, 0f, 0f)[0], 1e-5f, "x edge = scale * aspect");
    }

    @Test
    void destVariantWritesInPlaceAndValidates() {
        float[] dest = new float[16];
        CameraSpec cam = CameraSpec.defaultOrtho();
        assertSame(dest, CameraMath.mvp(cam, 1f, dest));
        assertThrows(IllegalArgumentException.class, () -> CameraMath.mvp(cam, 1f, new float[15]));
    }

    @Test
    void viewBasisIsOrthonormalAndFacesTheCamera() {
        // Ortho: world XY identity.
        float[] ortho = CameraMath.viewBasis(CameraSpec.defaultOrtho());
        org.junit.jupiter.api.Assertions.assertArrayEquals(
            new float[]{1f, 0f, 0f, 0f, 1f, 0f}, ortho);

        // Canonical perspective: eye on +Z (yaw=0, pitch=0) → identity basis.
        float[] canon = CameraMath.viewBasis(
            CameraSpec.defaultPerspective().withYaw(0f).withPitch(0f));
        assertEquals(1f, canon[0], 1e-5f);
        assertEquals(0f, canon[1], 1e-5f);
        assertEquals(1f, canon[4], 1e-5f);

        // Arbitrary orbit: right ⊥ up, both unit length, up has no
        // world-flip (positive Y component for moderate pitch).
        float[] b = CameraMath.viewBasis(
            CameraSpec.defaultPerspective().withYaw(0.8f).withPitch(0.4f));
        float dot = b[0]*b[3] + b[1]*b[4] + b[2]*b[5];
        assertEquals(0f, dot, 1e-5f, "right and up must be orthogonal");
        assertEquals(1f, (float) Math.sqrt(b[0]*b[0] + b[1]*b[1] + b[2]*b[2]), 1e-5f);
        assertEquals(1f, (float) Math.sqrt(b[3]*b[3] + b[4]*b[4] + b[5]*b[5]), 1e-5f);
        assertTrue(b[4] > 0f, "up must not be world-flipped at moderate pitch");
        // Yawed orbit must tilt right out of the X axis.
        assertTrue(Math.abs(b[2]) > 1e-3f, "yawed view's right vector gains a Z component");
    }

    @Test
    void fitToBoundsFramesTheBox() {
        Vec3 min = new Vec3(-1f, -2f, -3f);
        Vec3 max = new Vec3(3f, 2f, 1f);
        Vec3 center = new Vec3(1f, 0f, -1f);
        float radius = max.sub(min).length() * 0.5f;

        CameraSpec ortho = CameraRig.fitToBounds(CameraSpec.defaultOrtho(), min, max);
        assertEquals(center, ortho.target());
        assertEquals(radius * 1.2f, ortho.orthoScale(), 1e-5f);

        CameraSpec persp = CameraRig.fitToBounds(CameraSpec.defaultPerspective(), min, max);
        assertEquals(center, persp.target());
        float expected = (float) (radius * 1.2f / Math.tan(persp.fovYRad() * 0.5));
        assertEquals(expected, persp.distance(), 1e-4f);

        // Degenerate bounds don't collapse the camera.
        CameraSpec degenerate = CameraRig.fitToBounds(CameraSpec.defaultOrtho(), center, center);
        assertTrue(degenerate.orthoScale() > 0.5f);
    }

    @Test
    void presetsSetOrientationOnly() {
        CameraSpec base = CameraSpec.defaultPerspective().withDistance(42f);
        assertEquals(0f, CameraRig.front(base).yawRad());
        assertEquals(0f, CameraRig.front(base).pitchRad());
        assertEquals((float) (Math.PI / 2.0), CameraRig.side(base).yawRad(), 1e-6f);
        assertTrue(CameraRig.top(base).pitchRad() < (float) (Math.PI / 2.0), "top stays shy of 90°");
        assertEquals(42f, CameraRig.iso(base).distance(), "presets must not touch distance");
    }

}
