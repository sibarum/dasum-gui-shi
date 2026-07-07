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
    void imageLayerValidationAndRectConvenience() {
        byte[] px = new byte[2 * 3 * 4];
        ImageLayer img = ImageLayer.rect(-1f, -2f, 3f, 4f, 0.5f, 2, 3, px);
        assertEquals(BlendMode.ALPHA, img.blend());
        assertEquals(1f, img.opacity());
        assertEquals(true, img.smooth());
        // Corner order: TL, TR, BR, BL; top = y1 (world Y-up).
        float[] c = img.corners();
        assertEquals(-1f, c[0]);  assertEquals(4f, c[1]);   // TL
        assertEquals(3f, c[3]);   assertEquals(4f, c[4]);   // TR
        assertEquals(3f, c[6]);   assertEquals(-2f, c[7]);  // BR
        assertEquals(-1f, c[9]);  assertEquals(-2f, c[10]); // BL
        assertEquals(0.5f, c[2]); assertEquals(0.5f, c[11]);

        assertEquals(false, img.withSmooth(false).smooth());
        assertThrows(IllegalArgumentException.class,
            () -> new ImageLayer(2, 3, new byte[5], c, true, BlendMode.ALPHA, 1f));
        assertThrows(IllegalArgumentException.class,
            () -> new ImageLayer(2, 3, px, new float[11], true, BlendMode.ALPHA, 1f));
        assertThrows(IllegalArgumentException.class,
            () -> new ImageLayer(0, 3, new byte[0], c, true, BlendMode.ALPHA, 1f));
    }

    @Test
    void textLayerValidationAndDefaults() {
        sibarum.dasum.gui.vis.math.Vec3 a = new sibarum.dasum.gui.vis.math.Vec3(1f, 2f, 3f);
        sibarum.dasum.gui.core.render.Color white = new sibarum.dasum.gui.core.render.Color(1f, 1f, 1f, 1f);

        TextLayer t = new TextLayer("hello", a, 0.5f, white);
        assertEquals(TextLayer.HAlign.CENTER, t.align());
        assertEquals(false, t.billboard());
        assertEquals(BlendMode.ALPHA, t.blend());
        assertEquals(1f, t.opacity());

        assertEquals(true, t.withBillboard(true).billboard());
        assertEquals(TextLayer.HAlign.RIGHT, t.withAlign(TextLayer.HAlign.RIGHT).align());

        assertThrows(IllegalArgumentException.class, () -> new TextLayer(null, a, 0.5f, white));
        assertThrows(IllegalArgumentException.class, () -> new TextLayer("x", a, 0f, white));
        assertThrows(IllegalArgumentException.class, () -> new TextLayer("x", null, 0.5f, white));
        assertThrows(IllegalArgumentException.class, () -> new TextLayer("x", a, 0.5f, null));
    }

    @Test
    void sdfLayerValidationAndDefaults() {
        SdfLayer bulb = SdfLayer.of(SdfLayer.Field.MANDELBULB);
        assertEquals(SdfLayer.Field.MANDELBULB, bulb.field());
        assertEquals(BlendMode.OPAQUE, bulb.blend(), "fields are surfaces — OPAQUE writes depth");
        assertEquals(1f, bulb.opacity());
        assertEquals(8f, bulb.params()[0], "canonical bulb power");

        SdfLayer tuned = bulb.withMaxSteps(64).withScale(2f);
        assertEquals(64, tuned.maxSteps());
        assertEquals(2f, tuned.scale());

        sibarum.dasum.gui.vis.math.Vec3 o = sibarum.dasum.gui.vis.math.Vec3.ZERO;
        sibarum.dasum.gui.core.render.Color w = new sibarum.dasum.gui.core.render.Color(1f, 1f, 1f, 1f);
        assertThrows(IllegalArgumentException.class,
            () -> new SdfLayer(null, new float[4], o, 1f, w, 96, BlendMode.OPAQUE, 1f));
        assertThrows(IllegalArgumentException.class,
            () -> new SdfLayer(SdfLayer.Field.SPHERE, new float[3], o, 1f, w, 96, BlendMode.OPAQUE, 1f));
        assertThrows(IllegalArgumentException.class,
            () -> bulb.withScale(0f));
        assertThrows(IllegalArgumentException.class,
            () -> bulb.withMaxSteps(4));
        assertThrows(IllegalArgumentException.class,
            () -> bulb.withMaxSteps(1000));
    }

    @Test
    void csgBoxPackingAndValidation() {
        sibarum.dasum.gui.vis.math.Vec3 c = new sibarum.dasum.gui.vis.math.Vec3(1f, 2f, 3f);
        sibarum.dasum.gui.vis.math.Vec3 he = new sibarum.dasum.gui.vis.math.Vec3(0.5f, 0.6f, 0.7f);

        // Packed layout golden: (center.xyz, opcode), (halfExtents.xyz, smoothK).
        float[] packed = CsgBox.pack(java.util.List.of(
            new CsgBox(CsgBox.Op.UNION, c, he),
            new CsgBox(CsgBox.Op.SMOOTH_SUBTRACT, c, he, 0.25f)
        ));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new float[]{
            1f, 2f, 3f, 0f,   0.5f, 0.6f, 0.7f, 0f,
            1f, 2f, 3f, 4f,   0.5f, 0.6f, 0.7f, 0.25f,
        }, packed);

        assertThrows(IllegalArgumentException.class,
            () -> new CsgBox(CsgBox.Op.SMOOTH_UNION, c, he, 0f), "smooth ops need k > 0");
        assertThrows(IllegalArgumentException.class,
            () -> new CsgBox(CsgBox.Op.UNION, c, new sibarum.dasum.gui.vis.math.Vec3(0f, 1f, 1f)));
        assertThrows(IllegalArgumentException.class, () -> CsgBox.pack(java.util.List.of()));

        // Layer-level: csg required iff CSG_BOXES.
        SdfLayer shape = SdfLayer.csgBoxes(
            java.util.List.of(new CsgBox(CsgBox.Op.UNION, c, he)), 0.05f);
        assertEquals(SdfLayer.Field.CSG_BOXES, shape.field());
        assertEquals(1, shape.csgOpCount());
        assertEquals(0.05f, shape.params()[0], "params[0] = global rounding");

        sibarum.dasum.gui.core.render.Color w = new sibarum.dasum.gui.core.render.Color(1f, 1f, 1f, 1f);
        assertThrows(IllegalArgumentException.class,
            () -> new SdfLayer(SdfLayer.Field.CSG_BOXES, new float[4], null,
                sibarum.dasum.gui.vis.math.Vec3.ZERO, 1f, w, 96, BlendMode.OPAQUE, 1f),
            "CSG_BOXES without an op list must be rejected");
        assertThrows(IllegalArgumentException.class,
            () -> new SdfLayer(SdfLayer.Field.SPHERE, new float[4], packed,
                sibarum.dasum.gui.vis.math.Vec3.ZERO, 1f, w, 96, BlendMode.OPAQUE, 1f),
            "csg on an analytic field must be rejected");
        assertThrows(IllegalArgumentException.class,
            () -> SdfLayer.of(SdfLayer.Field.CSG_BOXES));
    }

    @Test
    void alienPlantDefaults() {
        SdfLayer plant = SdfLayer.of(SdfLayer.Field.ALIEN_PLANT);
        assertEquals(SdfLayer.Field.ALIEN_PLANT, plant.field());
        assertEquals(9f, plant.params()[2], "default generations");
        assertEquals(BlendMode.OPAQUE, plant.blend());
        assertNull(plant.csg(), "plant carries no csg program");
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
            () -> new InteractionSpec(null, 1f, 2f, 1f, null));
    }
}
