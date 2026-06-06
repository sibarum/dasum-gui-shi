package sibarum.dasum.gui.vis.scene;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Validation + compat-default behavior of the layer records and SceneSnapshot. */
final class SceneModelTest {

    private static final float[] XYZ3 = {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f}; // 3 vertices

    @Test
    void convenienceCtorsDefaultAlphaFullOpacity() {
        PointLayer p = new PointLayer(XYZ3, null);
        assertEquals(BlendMode.ALPHA, p.blend());
        assertEquals(1f, p.opacity());
        assertNull(p.sizes());
        assertEquals(PointLayer.DEFAULT_SIZE_PX, p.defaultSizePx());

        LineLayer l = new LineLayer(new float[]{0,0,0, 1,1,1}, null);
        assertEquals(BlendMode.ALPHA, l.blend());
        assertEquals(1f, l.opacity());

        TriangleLayer t = new TriangleLayer(XYZ3, null);
        assertEquals(BlendMode.ALPHA, t.blend());
        assertEquals(1f, t.opacity());
    }

    @Test
    void withersChangeOnlyTheirField() {
        PointLayer p = new PointLayer(XYZ3, null)
            .withBlend(BlendMode.ADDITIVE).withOpacity(0.5f).withDefaultSize(9f);
        assertEquals(BlendMode.ADDITIVE, p.blend());
        assertEquals(0.5f, p.opacity());
        assertEquals(9f, p.defaultSizePx());
        assertSame(XYZ3, p.positions(), "withers must not copy the backing arrays");

        TriangleLayer t = new TriangleLayer(XYZ3, null).withBlend(BlendMode.MAX);
        assertEquals(BlendMode.MAX, t.blend());
    }

    @Test
    void lengthValidation() {
        assertThrows(IllegalArgumentException.class, () -> new PointLayer(new float[]{1f, 2f}, null));
        assertThrows(IllegalArgumentException.class, () -> new PointLayer(XYZ3, new float[]{1f}));
        assertThrows(IllegalArgumentException.class,
            () -> new PointLayer(XYZ3, null, new float[]{1f, 2f}, 5f, BlendMode.ALPHA, 1f)); // sizes != pointCount
        assertThrows(IllegalArgumentException.class, () -> new LineLayer(new float[]{0,0,0}, null)); // not %6
        assertThrows(IllegalArgumentException.class, () -> new TriangleLayer(new float[]{0,0,0, 1,1,1}, null)); // not %9
        assertThrows(IllegalArgumentException.class, () -> new TriangleLayer(XYZ3, new float[]{1f, 2f, 3f}));
    }

    @Test
    void opacityAndBlendValidation() {
        assertThrows(IllegalArgumentException.class,
            () -> new TriangleLayer(XYZ3, null, BlendMode.ALPHA, 1.5f));
        assertThrows(IllegalArgumentException.class,
            () -> new TriangleLayer(XYZ3, null, BlendMode.ALPHA, -0.1f));
        assertThrows(IllegalArgumentException.class,
            () -> new TriangleLayer(XYZ3, null, null, 1f));
        assertThrows(IllegalArgumentException.class,
            () -> new PointLayer(XYZ3, null, null, 0f, BlendMode.ALPHA, 1f)); // defaultSize must be > 0
    }

    @Test
    void sceneSnapshotRejectsNulls() {
        assertThrows(NullPointerException.class, () -> new SceneSnapshot(null));
        assertThrows(NullPointerException.class,
            () -> new SceneSnapshot(java.util.Arrays.asList(new TriangleLayer(XYZ3, null), null)));
    }

    @Test
    void sceneSnapshotPreservesLayerOrderAndIdentity() {
        Layer a = new TriangleLayer(XYZ3, null);
        Layer b = new PointLayer(XYZ3, null);
        SceneSnapshot s = SceneSnapshot.of(a, b);
        assertEquals(List.of(a, b), s.layers());
        assertSame(a, s.layers().get(0), "painter's order must be preserved exactly");
        assertSame(b, s.layers().get(1));
    }

    @Test
    void interactionSpecValidationAndDefaults() {
        InteractionSpec d = InteractionSpec.defaults();
        assertEquals(InteractionSpec.Mode.ORBIT_3D, d.mode());
        assertEquals(InteractionSpec.Mode.PAN_ZOOM_2D, InteractionSpec.panZoom2d().mode());
        assertEquals(InteractionSpec.Mode.LOCKED, InteractionSpec.locked().mode());

        InteractionSpec ranged = d.withZoomRange(0.5f, 10f);
        assertEquals(0.5f, ranged.zoomMin());
        assertEquals(10f, ranged.zoomMax());

        assertThrows(IllegalArgumentException.class, () -> d.withZoomRange(0f, 1f));
        assertThrows(IllegalArgumentException.class, () -> d.withZoomRange(2f, 1f));
        assertThrows(IllegalArgumentException.class, () -> d.withPitchClamp(0f));
        assertThrows(IllegalArgumentException.class,
            () -> new InteractionSpec(null, 1f, 2f, 1f));
    }
}
