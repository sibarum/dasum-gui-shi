package sibarum.dasum.gui.vis.scene;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.vis.pointcloud.PointCloudSnapshot;
import sibarum.dasum.gui.vis.pointcloud.PointCloudStates;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * PointCloudSnapshot → SceneSnapshot conversion: projection correctness,
 * the zero-copy 3D fast path, layer order, and the identity caching that
 * keeps the renderer's upload-skip working through the legacy publish
 * API.
 */
final class SceneCompatTest {

    private static Component.SceneView view() {
        return new Component.SceneView(Em.of(10f), Em.of(7f), Em.ZERO, new Color(0f, 0f, 0f, 1f));
    }

    @Test
    void identity3dWrapsArraysZeroCopy() {
        float[] pos = {1f, 2f, 3f, 4f, 5f, 6f};
        float[] col = {1f, 0f, 0f, 0f, 1f, 0f};
        float[] eps = {0f, 0f, 0f, 1f, 1f, 1f};
        PointCloudSnapshot pc = new PointCloudSnapshot(3, 2, pos, col, null, null)
            .withSegments(1, eps, null);

        SceneSnapshot scene = SceneCompat.convert(pc);
        assertEquals(2, scene.layers().size());
        PointLayer p = assertInstanceOf(PointLayer.class, scene.layers().get(0), "points first");
        LineLayer l  = assertInstanceOf(LineLayer.class,  scene.layers().get(1), "lines on top");
        assertSame(pos, p.positions(), "3D no-projection positions must be wrapped, not copied");
        assertSame(col, p.colors());
        assertSame(eps, l.endpoints());
    }

    @Test
    void projectionSelectsDims() {
        // 4D points, projection picks dims (3, 1, 0).
        float[] pos = {
            10f, 11f, 12f, 13f,   // point 0
            20f, 21f, 22f, 23f,   // point 1
        };
        PointCloudSnapshot pc = new PointCloudSnapshot(4, 2, pos, null, null, new int[]{3, 1, 0});
        PointLayer p = (PointLayer) SceneCompat.convert(pc).layers().get(0);
        assertArrayEquals(new float[]{13f, 11f, 10f, 23f, 21f, 20f}, p.positions());
        assertNotSame(pos, p.positions());
    }

    @Test
    void twoDimensionalProjectsToZ0() {
        float[] pos = {1f, 2f, 3f, 4f};
        PointCloudSnapshot pc = new PointCloudSnapshot(2, 2, pos, null, null, null);
        PointLayer p = (PointLayer) SceneCompat.convert(pc).layers().get(0);
        assertArrayEquals(new float[]{1f, 2f, 0f, 3f, 4f, 0f}, p.positions());
    }

    @Test
    void segmentProjectionMatchesPointRules() {
        // One 4D segment, default projection (first 3 dims when no array... dim>=3 so dz=2).
        float[] eps = {
            1f, 2f, 3f, 4f,    // endpoint A
            5f, 6f, 7f, 8f,    // endpoint B
        };
        PointCloudSnapshot pc = new PointCloudSnapshot(4, 0, new float[0], null, null, null)
            .withSegments(1, eps, null);
        LineLayer l = (LineLayer) SceneCompat.convert(pc).layers().get(0);
        assertArrayEquals(new float[]{1f, 2f, 3f, 5f, 6f, 7f}, l.endpoints());
    }

    @Test
    void emptySnapshotConvertsToEmptyScene() {
        PointCloudSnapshot pc = new PointCloudSnapshot(3, 0, new float[0], null, null, null);
        assertEquals(0, SceneCompat.convert(pc).layers().size());
    }

    @Test
    void legacyPublishCachesConversionByIdentity() {
        Component.SceneView v = view();
        try {
            PointCloudSnapshot snap = PointCloudSnapshot.of3D(new float[]{0f, 0f, 0f});

            PointCloudStates.publish(v, snap);
            SceneSnapshot first = SceneStates.sceneOf(v);
            PointCloudStates.publish(v, snap); // same reference republished
            SceneSnapshot second = SceneStates.sceneOf(v);
            assertSame(first, second,
                "republishing the same snapshot reference must forward the same scene "
                + "reference — the renderer's identity skip depends on it");

            // The original snapshot stays readable through the legacy API.
            assertSame(snap, PointCloudStates.snapshotOf(v));

            // A new snapshot instance converts fresh.
            PointCloudSnapshot snap2 = PointCloudSnapshot.of3D(new float[]{1f, 1f, 1f});
            PointCloudStates.publish(v, snap2);
            assertNotSame(first, SceneStates.sceneOf(v));
            assertSame(snap2, PointCloudStates.snapshotOf(v));
        } finally {
            PointCloudStates.clear(v);
        }
    }
}
