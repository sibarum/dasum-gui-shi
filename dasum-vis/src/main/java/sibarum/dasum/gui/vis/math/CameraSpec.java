package sibarum.dasum.gui.vis.math;

/**
 * Immutable camera state for a point-cloud viewport. Updates are made by
 * constructing a new instance and storing it via
 * {@code PointCloudStates.setCamera}. The renderer reads the current
 * spec each frame and composes a JOML {@code Matrix4f} from it internally.
 * <p>
 * Orthographic-mode fields ({@code orthoScale}) and perspective-mode
 * fields ({@code distance}, {@code yawRad}, {@code pitchRad},
 * {@code fovYRad}) are both always present; the renderer reads whichever
 * set matches the {@code mode} field. {@code target} is the orbit center
 * in world space; in ortho mode it's the camera-look position.
 *
 * @param mode        which projection to use this frame
 * @param target      world-space orbit center (perspective) or look target (ortho)
 * @param distance    perspective: distance from target along view direction
 * @param yawRad      perspective: orbit yaw around target (around world Y)
 * @param pitchRad    perspective: orbit pitch
 * @param orthoScale  ortho: world-space half-height visible (zoom)
 * @param fovYRad     perspective: vertical field of view in radians
 * @param nearPlane   perspective near clip distance
 * @param farPlane    perspective far clip distance
 */
public record CameraSpec(
    CameraMode mode,
    Vec3 target,
    float distance,
    float yawRad,
    float pitchRad,
    float orthoScale,
    float fovYRad,
    float nearPlane, float farPlane
) {

    public static final float DEFAULT_FOV     = (float) Math.toRadians(50.0);
    public static final float DEFAULT_NEAR    = 0.01f;
    public static final float DEFAULT_FAR     = 1000.0f;

    public static CameraSpec defaultOrtho() {
        return new CameraSpec(
            CameraMode.ORTHOGRAPHIC, Vec3.ZERO,
            5f, 0f, 0f,
            2f,
            DEFAULT_FOV, DEFAULT_NEAR, DEFAULT_FAR
        );
    }

    public static CameraSpec defaultPerspective() {
        return new CameraSpec(
            CameraMode.PERSPECTIVE, Vec3.ZERO,
            5f, (float) Math.toRadians(35.0), (float) Math.toRadians(25.0),
            2f,
            DEFAULT_FOV, DEFAULT_NEAR, DEFAULT_FAR
        );
    }

    public CameraSpec withMode(CameraMode m)          { return new CameraSpec(m, target, distance, yawRad, pitchRad, orthoScale, fovYRad, nearPlane, farPlane); }
    public CameraSpec withTarget(Vec3 t)              { return new CameraSpec(mode, t, distance, yawRad, pitchRad, orthoScale, fovYRad, nearPlane, farPlane); }
    public CameraSpec withDistance(float d)           { return new CameraSpec(mode, target, d, yawRad, pitchRad, orthoScale, fovYRad, nearPlane, farPlane); }
    public CameraSpec withYaw(float y)                { return new CameraSpec(mode, target, distance, y, pitchRad, orthoScale, fovYRad, nearPlane, farPlane); }
    public CameraSpec withPitch(float p)              { return new CameraSpec(mode, target, distance, yawRad, p, orthoScale, fovYRad, nearPlane, farPlane); }
    public CameraSpec withOrthoScale(float s)         { return new CameraSpec(mode, target, distance, yawRad, pitchRad, s, fovYRad, nearPlane, farPlane); }
}
