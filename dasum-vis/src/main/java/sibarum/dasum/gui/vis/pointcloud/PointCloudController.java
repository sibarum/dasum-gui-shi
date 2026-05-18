package sibarum.dasum.gui.vis.pointcloud;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.vis.math.CameraMode;
import sibarum.dasum.gui.vis.math.CameraSpec;
import sibarum.dasum.gui.vis.math.Vec3;

/**
 * Mouse-driven camera updates for {@code Component.PointCloud}
 * viewports. Mirrors the {@code SliderController} static pattern: app
 * input plumbing calls in via {@link #onMouseDown}, {@link #onCursorMove},
 * {@link #onMouseUp}, {@link #onScroll}; the controller mutates camera
 * state through {@link PointCloudStates#setCamera}.
 *
 * <p>Behavior:
 * <ul>
 *   <li>Perspective: drag rotates the camera around the orbit target;
 *       horizontal drag = yaw, vertical drag = pitch (pitch clamped
 *       just shy of ±90° to avoid the lookAt up-vector degeneracy).
 *       Scroll changes orbit {@code distance}.</li>
 *   <li>Orthographic: drag pans the target in the viewport plane.
 *       Scroll changes {@code orthoScale}.</li>
 * </ul>
 */
public final class PointCloudController {

    private static final float YAW_RAD_PER_PIXEL    = 0.008f;
    private static final float PITCH_RAD_PER_PIXEL  = 0.008f;
    private static final float MAX_PITCH            = (float) (Math.PI * 0.5 - 0.01);
    private static final float ZOOM_FACTOR_PER_NOTCH = 1.15f;

    private static Component.PointCloud dragging = null;
    private static double lastX = 0d;
    private static double lastY = 0d;

    private PointCloudController() {}

    public static boolean onMouseDown(Component hit, double cursorX, double cursorY) {
        if (!(hit instanceof Component.PointCloud pc) || !pc.interactive()) return false;
        dragging = pc;
        lastX = cursorX;
        lastY = cursorY;
        return true;
    }

    public static void onCursorMove(double cursorX, double cursorY) {
        if (dragging == null) return;
        float dx = (float) (cursorX - lastX);
        float dy = (float) (cursorY - lastY);
        lastX = cursorX;
        lastY = cursorY;

        CameraSpec cam = PointCloudStates.cameraOf(dragging);
        if (cam.mode() == CameraMode.PERSPECTIVE) {
            float yaw   = cam.yawRad() - dx * YAW_RAD_PER_PIXEL;
            float pitch = cam.pitchRad() + dy * PITCH_RAD_PER_PIXEL;
            pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch));
            PointCloudStates.setCamera(dragging, cam.withYaw(yaw).withPitch(pitch));
        } else {
            // Pan the orbit target in viewport space. orthoScale is the
            // half-height of the visible world in y; per-pixel pan distance
            // is (2 * orthoScale) / viewport-height. We don't have the
            // viewport height here without a layout lookup, so approximate
            // via the dragging component's most recent layout rect.
            float worldPerPixel = panWorldPerPixel(dragging, cam);
            Vec3 t = cam.target();
            Vec3 t2 = new Vec3(
                t.x() - dx * worldPerPixel,
                t.y() + dy * worldPerPixel,  // screen-Y-down, world-Y-up
                t.z()
            );
            PointCloudStates.setCamera(dragging, cam.withTarget(t2));
        }
    }

    public static void onMouseUp() {
        dragging = null;
    }

    public static void cancelDrag() {
        dragging = null;
    }

    public static boolean isDragging() {
        return dragging != null;
    }

    /**
     * Scroll-wheel handler. Returns true if the scroll was consumed by a
     * point-cloud viewport under the cursor (so the app's main scroll
     * handler doesn't also forward it to a scroll container).
     */
    public static boolean onScroll(Component hit, double yOff) {
        if (!(hit instanceof Component.PointCloud pc) || !pc.interactive()) return false;
        CameraSpec cam = PointCloudStates.cameraOf(pc);
        float factor = (float) Math.pow(ZOOM_FACTOR_PER_NOTCH, -yOff);
        if (cam.mode() == CameraMode.PERSPECTIVE) {
            float d = Math.max(0.01f, cam.distance() * factor);
            PointCloudStates.setCamera(pc, cam.withDistance(d));
        } else {
            float s = Math.max(1e-4f, cam.orthoScale() * factor);
            PointCloudStates.setCamera(pc, cam.withOrthoScale(s));
        }
        return true;
    }

    private static float panWorldPerPixel(Component.PointCloud pc, CameraSpec cam) {
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return cam.orthoScale() / 200f;
        PixelRect r = lr.rectOf(pc);
        if (r == null || r.height() <= 0f) return cam.orthoScale() / 200f;
        return (2f * cam.orthoScale()) / r.height();
    }
}
