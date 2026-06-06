package sibarum.dasum.gui.vis.pointcloud;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.input.InputState;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.vis.math.CameraMode;
import sibarum.dasum.gui.vis.math.CameraSpec;
import sibarum.dasum.gui.vis.math.Vec3;
import sibarum.dasum.gui.vis.scene.InteractionSpec;
import sibarum.dasum.gui.vis.scene.SceneStates;

import java.util.function.Consumer;

/**
 * Mouse-driven camera updates for {@code Component.SceneView}
 * viewports. Mirrors the {@code SliderController} static pattern: app
 * input plumbing calls in via {@link #onMouseDown}, {@link #onCursorMove},
 * {@link #onMouseUp}, {@link #onScroll}; the controller mutates camera
 * state through {@link SceneStates#setCamera} (which also notifies
 * {@code onCameraChange} listeners).
 *
 * <p>Behavior is governed by the component's
 * {@link SceneStates#interactionOf InteractionSpec}:
 * <ul>
 *   <li>{@code ORBIT_3D} (default, the historical behavior):
 *       perspective drag orbits the camera around the target (pitch
 *       clamped per spec); ortho drag pans the target. Scroll zooms
 *       (distance / orthoScale), centered on the view.</li>
 *   <li>{@code PAN_ZOOM_2D}: drag pans the target in the view plane;
 *       scroll zooms <b>anchored at the cursor</b> — the world point
 *       under the cursor stays fixed while the scale changes. Designed
 *       for ortho 2D scenes (charts, fractals, images); with a
 *       perspective camera it falls back to ORBIT_3D semantics.</li>
 *   <li>{@code LOCKED}: drags and scrolls don't touch the camera, and
 *       scroll is NOT consumed (so scroll containers behind the viewport
 *       still work). Click-picking still resolves.</li>
 * </ul>
 */
public final class SceneViewController {

    private static final float YAW_RAD_PER_PIXEL    = 0.008f;
    private static final float PITCH_RAD_PER_PIXEL  = 0.008f;
    private static final float ZOOM_FACTOR_PER_NOTCH = 1.15f;

    /**
     * Squared cursor-displacement threshold (px²) that distinguishes a
     * click from a drag. Movement strictly below this stays a "press"
     * and resolves on mouse-up as a click; movement past it commits to
     * a drag and the eventual mouse-up does not fire a click.
     */
    private static final float DRAG_THRESHOLD_PX_SQ = 4f * 4f;

    /** Screen-pixel tolerance for picking a point under the cursor. */
    private static final float PICK_TOLERANCE_PX = 8f;

    private static Component.SceneView pressed = null;
    private static boolean dragStarted = false;
    private static double pressX = 0d, pressY = 0d;
    private static double lastX  = 0d, lastY  = 0d;

    private SceneViewController() {}

    public static boolean onMouseDown(Component hit, double cursorX, double cursorY) {
        if (!(hit instanceof Component.SceneView pc) || !pc.interactive()) return false;
        pressed = pc;
        dragStarted = false;
        pressX = cursorX; pressY = cursorY;
        lastX = cursorX;  lastY = cursorY;
        return true;
    }

    public static void onCursorMove(double cursorX, double cursorY) {
        if (pressed == null) return;

        if (!dragStarted) {
            double dxPress = cursorX - pressX;
            double dyPress = cursorY - pressY;
            if (dxPress * dxPress + dyPress * dyPress < DRAG_THRESHOLD_PX_SQ) {
                // Still potentially a click — don't move the camera yet.
                return;
            }
            dragStarted = true;
            // Reset the per-step deltas so the first drag frame doesn't
            // snap by the entire press-to-now displacement.
            lastX = cursorX; lastY = cursorY;
            return;
        }

        float dx = (float) (cursorX - lastX);
        float dy = (float) (cursorY - lastY);
        lastX = cursorX;
        lastY = cursorY;

        InteractionSpec spec = SceneStates.interactionOf(pressed);
        if (spec.mode() == InteractionSpec.Mode.LOCKED) return;

        CameraSpec cam = SceneStates.cameraOf(pressed);
        boolean orbit = cam.mode() == CameraMode.PERSPECTIVE;
        // PAN_ZOOM_2D pans regardless of camera mode where it can — but
        // perspective view-plane panning is ambiguous, so it degrades to
        // orbit there (documented).
        if (orbit) {
            float yaw   = cam.yawRad() - dx * YAW_RAD_PER_PIXEL;
            float pitch = cam.pitchRad() + dy * PITCH_RAD_PER_PIXEL;
            float clamp = spec.pitchClampRad();
            pitch = Math.max(-clamp, Math.min(clamp, pitch));
            SceneStates.setCamera(pressed, cam.withYaw(yaw).withPitch(pitch));
        } else {
            // Pan the target in viewport space. orthoScale is the
            // half-height of the visible world in y; per-pixel pan
            // distance is (2 * orthoScale) / viewport-height.
            float worldPerPixel = panWorldPerPixel(pressed, cam);
            Vec3 t = cam.target();
            Vec3 t2 = new Vec3(
                t.x() - dx * worldPerPixel,
                t.y() + dy * worldPerPixel,  // screen-Y-down, world-Y-up
                t.z()
            );
            SceneStates.setCamera(pressed, cam.withTarget(t2));
        }
    }

    public static void onMouseUp() {
        if (pressed != null && !dragStarted) {
            // Press resolved without crossing the drag threshold — treat
            // as a click. Pick the nearest point under the press position
            // and fire any registered handler. (LOCKED viewports pick too —
            // locking freezes the camera, not selection.)
            firePointClick(pressed, pressX, pressY);
        }
        pressed = null;
        dragStarted = false;
    }

    public static void cancelDrag() {
        pressed = null;
        dragStarted = false;
    }

    /**
     * True iff the mouse is currently pressed on a scene viewport,
     * regardless of whether the drag threshold has been crossed yet. The
     * App-level mouse-up dispatcher uses this to suppress the standard
     * "click activation" path so a press-and-release on a viewport is
     * routed through {@link PointHandlers} instead of
     * {@code Handlers.activate}.
     */
    public static boolean isDragging() {
        return pressed != null;
    }

    private static void firePointClick(Component.SceneView pc, double mouseX, double mouseY) {
        Consumer<PointHandlers.PointHit> handler = PointHandlers.handlerFor(pc);
        if (handler == null) return;
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return;
        PixelRect rect = lr.rectOf(pc);
        PointPicker.Pick pick = PointPicker.pickNearest(pc, rect, mouseX, mouseY, PICK_TOLERANCE_PX);
        if (pick == null) return;
        handler.accept(new PointHandlers.PointHit(pick.layerIndex(), pick.pointIndex(), pick.world()));
    }

    /**
     * Scroll-wheel handler. Returns true if the scroll was consumed by a
     * scene viewport under the cursor (so the app's main scroll handler
     * doesn't also forward it to a scroll container). LOCKED viewports
     * deliberately do NOT consume — a chart inside a scroll column
     * shouldn't swallow the page's wheel.
     */
    public static boolean onScroll(Component hit, double yOff) {
        if (!(hit instanceof Component.SceneView pc) || !pc.interactive()) return false;
        InteractionSpec spec = SceneStates.interactionOf(pc);
        if (spec.mode() == InteractionSpec.Mode.LOCKED) return false;

        CameraSpec cam = SceneStates.cameraOf(pc);
        float factor = (float) Math.pow(ZOOM_FACTOR_PER_NOTCH, -yOff);

        if (cam.mode() == CameraMode.PERSPECTIVE) {
            float d = clamp(cam.distance() * factor, spec.zoomMin(), spec.zoomMax());
            SceneStates.setCamera(pc, cam.withDistance(d));
            return true;
        }

        float s1 = cam.orthoScale();
        float s2 = clamp(s1 * factor, spec.zoomMin(), spec.zoomMax());
        if (s2 == s1) return true;

        if (spec.mode() == InteractionSpec.Mode.PAN_ZOOM_2D) {
            // Zoom anchored at the cursor: keep the world point under the
            // cursor fixed across the scale change —
            //   target2 = cursorWorld + (target1 - cursorWorld) * (s2/s1)
            PixelRect rect = rectOf(pc);
            if (rect != null && rect.height() > 0f) {
                float worldPerPixel = (2f * s1) / rect.height();
                float cx = (float) InputState.mouseX();
                float cy = (float) InputState.mouseY();
                Vec3 t = cam.target();
                float wx = t.x() + (cx - (rect.x() + rect.width()  * 0.5f)) * worldPerPixel;
                float wy = t.y() - (cy - (rect.y() + rect.height() * 0.5f)) * worldPerPixel;
                Vec3 t2 = anchoredZoomTarget(t, wx, wy, s2 / s1);
                SceneStates.setCamera(pc, cam.withOrthoScale(s2).withTarget(t2));
                return true;
            }
        }
        SceneStates.setCamera(pc, cam.withOrthoScale(s2));
        return true;
    }

    /**
     * New camera target that keeps the world point {@code (wx, wy)} fixed
     * on screen across an ortho zoom by {@code ratio} (= newScale /
     * oldScale): {@code target2 = anchor + (target - anchor) * ratio}.
     * Package-private for unit testing.
     */
    static Vec3 anchoredZoomTarget(Vec3 target, float wx, float wy, float ratio) {
        return new Vec3(
            wx + (target.x() - wx) * ratio,
            wy + (target.y() - wy) * ratio,
            target.z()
        );
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static PixelRect rectOf(Component.SceneView pc) {
        LayoutResult lr = LatestLayout.result();
        return lr == null ? null : lr.rectOf(pc);
    }

    private static float panWorldPerPixel(Component.SceneView pc, CameraSpec cam) {
        PixelRect r = rectOf(pc);
        if (r == null || r.height() <= 0f) return cam.orthoScale() / 200f;
        return (2f * cam.orthoScale()) / r.height();
    }
}
