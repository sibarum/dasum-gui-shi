package sibarum.dasum.gui.vis.pointcloud;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.vis.math.CameraMode;
import sibarum.dasum.gui.vis.math.CameraSpec;
import sibarum.dasum.gui.vis.math.Vec3;

/**
 * Mouse-pick utility for point clouds. CPU-side ray-cast equivalent:
 * project each point's world position to screen space using the same
 * MVP the renderer would use, find the one closest to the cursor within
 * a pixel tolerance.
 *
 * <p>Cost: O(N) per pick where N is the snapshot's point count.
 * Allocations: a handful of JOML matrices per call — negligible relative
 * to per-frame work since picking only happens on mouse-up.
 *
 * <p>Main-thread only.
 */
final class PointPicker {

    private PointPicker() {}

    /**
     * Pick the screen-space nearest point to {@code (mouseX, mouseY)}
     * within {@code tolerancePx}. Returns {@code -1} if no point lies
     * within tolerance (including the empty-snapshot case).
     *
     * <p>When multiple points overlap, the one closest in screen space
     * wins; ties break to earlier indices. Behind-camera points are
     * filtered via the post-projection {@code w} check.
     */
    static int pickNearest(Component.PointCloud viewport, PixelRect rect,
                           double mouseX, double mouseY, float tolerancePx) {
        if (rect == null || rect.width() <= 0f || rect.height() <= 0f) return -1;
        PointCloudSnapshot snap = PointCloudStates.snapshotOf(viewport);
        if (snap == null || snap.pointCount() == 0) return -1;
        CameraSpec cam = PointCloudStates.cameraOf(viewport);

        Matrix4f mvp = composeMvp(cam, rect.width() / rect.height());
        float[] m = new float[16];
        mvp.get(m);

        int dim = snap.dimensionality();
        int[] proj = snap.projection();
        int dx = proj != null ? proj[0] : 0;
        int dy = proj != null ? proj[1] : (dim >= 2 ? 1 : 0);
        int dz;
        if (proj != null && proj.length == 3)      dz = proj[2];
        else if (proj == null && dim >= 3)         dz = 2;
        else                                       dz = -1;

        float[] pos = snap.positions();
        int n = snap.pointCount();
        float tolSq = tolerancePx * tolerancePx;
        float bestDistSq = tolSq;
        int bestIdx = -1;

        for (int i = 0; i < n; i++) {
            int base = i * dim;
            float x = pos[base + dx];
            float y = pos[base + dy];
            float z = dz >= 0 ? pos[base + dz] : 0f;

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
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /** Read the projected (x, y, z) of a point — what the renderer drew. */
    static Vec3 projectedPositionOf(PointCloudSnapshot snap, int idx) {
        int dim = snap.dimensionality();
        int[] proj = snap.projection();
        int dx = proj != null ? proj[0] : 0;
        int dy = proj != null ? proj[1] : (dim >= 2 ? 1 : 0);
        int dz;
        if (proj != null && proj.length == 3)      dz = proj[2];
        else if (proj == null && dim >= 3)         dz = 2;
        else                                       dz = -1;
        int base = idx * dim;
        float x = snap.positions()[base + dx];
        float y = snap.positions()[base + dy];
        float z = dz >= 0 ? snap.positions()[base + dz] : 0f;
        return new Vec3(x, y, z);
    }

    private static Matrix4f composeMvp(CameraSpec cam, float aspect) {
        Matrix4f projM = new Matrix4f();
        Matrix4f view  = new Matrix4f();
        float a = aspect > 0f ? aspect : 1f;
        if (cam.mode() == CameraMode.ORTHOGRAPHIC) {
            float halfH = Math.max(1e-4f, cam.orthoScale());
            float halfW = halfH * a;
            projM.ortho(-halfW, halfW, -halfH, halfH, -cam.farPlane(), cam.farPlane());
            view.translate(-cam.target().x(), -cam.target().y(), -cam.target().z());
        } else {
            projM.perspective(cam.fovYRad(), a, cam.nearPlane(), cam.farPlane());
            Vector3f eye = new Vector3f(0f, 0f, cam.distance());
            eye.rotateX(-cam.pitchRad());
            eye.rotateY(cam.yawRad());
            eye.add(cam.target().x(), cam.target().y(), cam.target().z());
            Vector3f tgt = new Vector3f(cam.target().x(), cam.target().y(), cam.target().z());
            view.lookAt(eye, tgt, new Vector3f(0f, 1f, 0f));
        }
        return projM.mul(view);
    }
}
