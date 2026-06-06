package sibarum.dasum.gui.vis.math;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The single source of truth for composing a {@link CameraSpec} into a
 * model-view-projection matrix. Previously this logic was duplicated
 * (renderer + picker); any new renderer (scene layers, VexelRay field
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
