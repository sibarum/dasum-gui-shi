package sibarum.dasum.gui.vis.pointcloud;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.vis.math.Vec3;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure-math pieces of the viewport controller. */
final class SceneViewControllerTest {

    @Test
    void anchoredZoomKeepsCursorWorldPointFixed() {
        // Anchor at (2, 3); zoom in by ratio 0.5 from target (0, 0).
        Vec3 t2 = SceneViewController.anchoredZoomTarget(new Vec3(0f, 0f, 5f), 2f, 3f, 0.5f);
        assertEquals(1f, t2.x(), 1e-6f);   // 2 + (0-2)*0.5
        assertEquals(1.5f, t2.y(), 1e-6f); // 3 + (0-3)*0.5
        assertEquals(5f, t2.z(), "z untouched");
    }

    @Test
    void anchoredZoomRatioOneIsNoOp() {
        Vec3 same = SceneViewController.anchoredZoomTarget(new Vec3(7f, -2f, 0f), 100f, -50f, 1f);
        assertEquals(7f, same.x(), 1e-6f);
        assertEquals(-2f, same.y(), 1e-6f);
    }

    @Test
    void anchoredZoomComposesLikeRepeatedZooms() {
        // Zooming 0.8 twice anchored at the same point must equal zooming
        // 0.64 once — the anchor invariant is multiplicative.
        Vec3 t = new Vec3(1f, 2f, 0f);
        Vec3 twice = SceneViewController.anchoredZoomTarget(
            SceneViewController.anchoredZoomTarget(t, 4f, 5f, 0.8f), 4f, 5f, 0.8f);
        Vec3 once = SceneViewController.anchoredZoomTarget(t, 4f, 5f, 0.64f);
        assertEquals(once.x(), twice.x(), 1e-5f);
        assertEquals(once.y(), twice.y(), 1e-5f);
    }
}
