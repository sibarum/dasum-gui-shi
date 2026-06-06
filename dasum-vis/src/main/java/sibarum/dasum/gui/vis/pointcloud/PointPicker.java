package sibarum.dasum.gui.vis.pointcloud;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.vis.math.CameraMath;
import sibarum.dasum.gui.vis.math.CameraSpec;
import sibarum.dasum.gui.vis.math.Vec3;
import sibarum.dasum.gui.vis.scene.Layer;
import sibarum.dasum.gui.vis.scene.PointLayer;
import sibarum.dasum.gui.vis.scene.SceneSnapshot;
import sibarum.dasum.gui.vis.scene.SceneStates;

import java.util.List;

/**
 * Mouse-pick utility for scene viewports. CPU-side ray-cast equivalent:
 * project every {@link PointLayer} point to screen space using the same
 * MVP the renderer uses (via {@link CameraMath} — single source of
 * truth), find the one closest to the cursor within a pixel tolerance.
 *
 * <p>Cost: O(total points across point layers) per pick. Picking only
 * happens on mouse-up, so per-call allocation is fine.
 *
 * <p>Only point layers are pickable; line and triangle layers are
 * passed over (segment/triangle picking is future work).
 *
 * <p>Main-thread only.
 */
final class PointPicker {

    /** A successful pick: which layer, which point, and its 3D position. */
    record Pick(int layerIndex, int pointIndex, Vec3 world) {}

    private PointPicker() {}

    /**
     * Pick the screen-space nearest point to {@code (mouseX, mouseY)}
     * within {@code tolerancePx} across all point layers of the
     * component's current scene. Returns {@code null} if nothing lies
     * within tolerance.
     *
     * <p>When multiple points overlap, the screen-space closest wins;
     * ties break to the earlier layer, then the earlier index.
     * Behind-camera points are filtered via the post-projection
     * {@code w} check.
     */
    static Pick pickNearest(Component viewport, PixelRect rect,
                            double mouseX, double mouseY, float tolerancePx) {
        if (rect == null || rect.width() <= 0f || rect.height() <= 0f) return null;
        SceneSnapshot scene = SceneStates.sceneOf(viewport);
        if (scene == null || scene.layers().isEmpty()) return null;
        CameraSpec cam = SceneStates.cameraOf(viewport);

        float[] m = CameraMath.mvp(cam, rect.width() / rect.height());

        float tolSq = tolerancePx * tolerancePx;
        float bestDistSq = tolSq;
        Pick best = null;

        List<Layer> layers = scene.layers();
        for (int li = 0; li < layers.size(); li++) {
            if (!(layers.get(li) instanceof PointLayer pl)) continue;
            float[] pos = pl.positions();
            int n = pl.pointCount();
            for (int i = 0; i < n; i++) {
                float x = pos[i * 3    ];
                float y = pos[i * 3 + 1];
                float z = pos[i * 3 + 2];

                // Column-major matrix layout: m[col*4 + row].
                float cw = m[3]*x + m[7]*y + m[11]*z + m[15];
                if (cw <= 1e-6f) continue; // at or behind near plane

                float ndcx = (m[0]*x + m[4]*y + m[8] *z + m[12]) / cw;
                float ndcy = (m[1]*x + m[5]*y + m[9] *z + m[13]) / cw;
                if (ndcx < -1f || ndcx > 1f || ndcy < -1f || ndcy > 1f) continue;

                float sx = rect.x() + (ndcx + 1f) * 0.5f * rect.width();
                float sy = rect.y() + (1f - ndcy) * 0.5f * rect.height();
                float ddx = (float) (sx - mouseX);
                float ddy = (float) (sy - mouseY);
                float dSq = ddx*ddx + ddy*ddy;
                if (dSq < bestDistSq) {
                    bestDistSq = dSq;
                    best = new Pick(li, i, new Vec3(x, y, z));
                }
            }
        }
        return best;
    }
}
