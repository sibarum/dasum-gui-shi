package sibarum.dasum.gui.vis.scene;

import sibarum.dasum.gui.vis.pointcloud.PointCloudSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts the legacy {@link PointCloudSnapshot} (n-D positions +
 * optional projection dims, fixed point/segment pair) into a
 * {@link SceneSnapshot} (pure-3D layers, painter's order). This is where
 * n-dimensionality lives now: scene layers are always 3D, and the
 * projection-dim selection that used to happen inside the GPU buffer
 * builders happens once here, at conversion time.
 *
 * <p>Zero-copy fast path: a 3D snapshot with no explicit projection
 * wraps its arrays directly (the publish ownership contract already
 * forbids mutation), so the common case allocates only the layer
 * records. Layer order matches the original renderer: points first,
 * lines on top ("scaffolding" — axes, edges — sits over the dots).
 */
public final class SceneCompat {

    private SceneCompat() {}

    public static SceneSnapshot convert(PointCloudSnapshot pc) {
        List<Layer> layers = new ArrayList<>(2);
        if (pc.pointCount() > 0) {
            layers.add(new PointLayer(projectPositions(pc), pc.colors()));
        }
        if (pc.segmentCount() > 0) {
            layers.add(new LineLayer(projectEndpoints(pc), pc.segmentColors()));
        }
        return new SceneSnapshot(layers);
    }

    // ---- projection (dim-selection rules preserved verbatim) ----

    private static int dxOf(int[] proj)          { return proj != null ? proj[0] : 0; }
    private static int dyOf(int[] proj, int dim) { return proj != null ? proj[1] : (dim >= 2 ? 1 : 0); }

    private static int dzOf(int[] proj, int dim) {
        if (proj != null && proj.length == 3) return proj[2];
        if (proj == null && dim >= 3)         return 2;
        return -1;
    }

    private static boolean identity3d(PointCloudSnapshot pc) {
        return pc.dimensionality() == 3 && pc.projection() == null;
    }

    private static float[] projectPositions(PointCloudSnapshot pc) {
        if (identity3d(pc)) return pc.positions();
        int n = pc.pointCount();
        int dim = pc.dimensionality();
        int[] proj = pc.projection();
        int dx = dxOf(proj), dy = dyOf(proj, dim), dz = dzOf(proj, dim);
        float[] src = pc.positions();
        float[] out = new float[n * 3];
        for (int i = 0; i < n; i++) {
            int base = i * dim;
            out[i * 3    ] = src[base + dx];
            out[i * 3 + 1] = src[base + dy];
            out[i * 3 + 2] = dz >= 0 ? src[base + dz] : 0f;
        }
        return out;
    }

    private static float[] projectEndpoints(PointCloudSnapshot pc) {
        if (identity3d(pc)) return pc.segmentEndpoints();
        int segCount = pc.segmentCount();
        int dim = pc.dimensionality();
        int[] proj = pc.projection();
        int dx = dxOf(proj), dy = dyOf(proj, dim), dz = dzOf(proj, dim);
        float[] src = pc.segmentEndpoints();
        float[] out = new float[segCount * 2 * 3];
        for (int v = 0; v < segCount * 2; v++) {
            int base = v * dim;
            out[v * 3    ] = src[base + dx];
            out[v * 3 + 1] = src[base + dy];
            out[v * 3 + 2] = dz >= 0 ? src[base + dz] : 0f;
        }
        return out;
    }
}
