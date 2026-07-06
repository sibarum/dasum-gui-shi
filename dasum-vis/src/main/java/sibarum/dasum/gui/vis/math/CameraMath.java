package sibarum.dasum.gui.vis.math;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The single source of truth for composing a {@link CameraSpec} into a
 * model-view-projection matrix. Previously this logic was duplicated
 * (renderer + picker); any new renderer (scene layers, SDF field
 * layers) and any CPU-side projection (picking, tick derivation) must go
 * through here so screen mapping can never drift between consumers.
 *
 * <p>JOML stays contained per the module rule — inputs and outputs are
 * records and {@code float[16]} (column-major, ready for
 * {@code glUniformMatrix4fv}). Allocates small JOML scratch per call;
 * callers on hot paths can hold a reusable {@code float[16]} via
 * {@link #mvp(CameraSpec, float, float[])} but per-call allocation of the
 * internal matrices is accepted for clarity (a few tiny objects per
 * viewport per frame).
 */
public final class CameraMath {

    private CameraMath() {}

    /** The world-XY-plane basis: right = +X, up = +Y. */
    public static final float[] IDENTITY_BASIS = {1f, 0f, 0f, 0f, 1f, 0f};

    /**
     * World-space eye position — the same one the view matrix uses
     * (target-relative orbit rotate, then translate to target). For
     * orthographic cameras there is no meaningful eye point; returns the
     * target (callers in ortho mode use {@link #forward} for parallel
     * rays and ignore the eye).
     */
    public static float[] eye(CameraSpec cam) {
        if (cam.mode() == CameraMode.ORTHOGRAPHIC) {
            return new float[]{cam.target().x(), cam.target().y(), cam.target().z()};
        }
        Vector3f eye = new Vector3f(0f, 0f, cam.distance());
        eye.rotateX(-cam.pitchRad());
        eye.rotateY(cam.yawRad());
        eye.add(cam.target().x(), cam.target().y(), cam.target().z());
        return new float[]{eye.x, eye.y, eye.z};
    }

    /**
     * Unit view direction (eye toward target). Orthographic cameras look
     * down -Z by construction (see {@link #mvp}'s ortho branch).
     */
    public static float[] forward(CameraSpec cam) {
        if (cam.mode() == CameraMode.ORTHOGRAPHIC) {
            return new float[]{0f, 0f, -1f};
        }
        Vector3f eye = new Vector3f(0f, 0f, cam.distance());
        eye.rotateX(-cam.pitchRad());
        eye.rotateY(cam.yawRad());
        Vector3f f = eye.negate().normalize(); // target-relative: forward = -(eye - target)
        return new float[]{f.x, f.y, f.z};
    }

    /**
     * View-plane orientation basis for billboarding: 6 floats —
     * right xyz, then up xyz — orthonormal vectors spanning the plane
     * facing the camera. Orthographic cameras look down -Z with no
     * orbit, so the basis is the world XY identity; perspective derives
     * from the same eye/target math as {@link #mvp}, guaranteeing
     * billboards face exactly the rendered view.
     */
    public static float[] viewBasis(CameraSpec cam) {
        if (cam.mode() == CameraMode.ORTHOGRAPHIC) {
            return IDENTITY_BASIS.clone();
        }
        Vector3f eye = new Vector3f(0f, 0f, cam.distance());
        eye.rotateX(-cam.pitchRad());
        eye.rotateY(cam.yawRad());
        // forward = normalize(target - eye); eye is target-relative here,
        // so forward = normalize(-eye). Sanity anchor: eye (0,0,d) →
        // forward (0,0,-1), right = f×worldUp = (+1,0,0), up = r×f = (0,+1,0).
        Vector3f forward = new Vector3f(eye).negate().normalize();
        Vector3f right = new Vector3f(forward).cross(0f, 1f, 0f);
        // Degenerate straight-down view: fall back to world X.
        if (right.lengthSquared() < 1e-8f) {
            right.set(1f, 0f, 0f);
        } else {
            right.normalize();
        }
        Vector3f up = new Vector3f(right).cross(forward);
        return new float[]{right.x, right.y, right.z, up.x, up.y, up.z};
    }

    /** Compose the MVP for {@code cam} at the given viewport aspect ratio. */
    public static float[] mvp(CameraSpec cam, float aspect) {
        return mvp(cam, aspect, new float[16]);
    }

    /** As {@link #mvp(CameraSpec, float)}, writing into {@code dest16}. */
    public static float[] mvp(CameraSpec cam, float aspect, float[] dest16) {
        if (dest16 == null || dest16.length != 16) {
            throw new IllegalArgumentException("dest16 must be a float[16]");
        }
        Matrix4f proj = new Matrix4f();
        Matrix4f view = new Matrix4f();
        Vector3f target = new Vector3f(cam.target().x(), cam.target().y(), cam.target().z());

        if (cam.mode() == CameraMode.ORTHOGRAPHIC) {
            float halfH = Math.max(1e-4f, cam.orthoScale());
            float halfW = halfH * (aspect > 0f ? aspect : 1f);
            proj.identity().ortho(
                -halfW, halfW, -halfH, halfH,
                -cam.farPlane(), cam.farPlane()
            );
            view.identity().translate(-target.x, -target.y, -target.z);
        } else {
            proj.identity().perspective(
                cam.fovYRad(),
                aspect > 0f ? aspect : 1f,
                cam.nearPlane(), cam.farPlane()
            );
            // Orbit camera: place eye at (0, 0, distance), rotate by yaw/pitch,
            // translate into orbit around target. Equivalent to a yaw-pitch
            // rotation about the target point.
            Vector3f eye = new Vector3f(0f, 0f, cam.distance());
            eye.rotateX(-cam.pitchRad());
            eye.rotateY(cam.yawRad());
            eye.add(target);
            view.identity().lookAt(eye, target, new Vector3f(0f, 1f, 0f));
        }

        proj.mul(view).get(dest16);
        return dest16;
    }
}
