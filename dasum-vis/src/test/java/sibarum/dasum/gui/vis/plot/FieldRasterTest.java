package sibarum.dasum.gui.vis.plot;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.vis.scene.ImageLayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FieldRasterTest {

    private static int u8(byte b) { return b & 0xFF; }

    @Test
    void hsvPrimaries() {
        Color red = ColorMaps.hsv(0f, 1f, 1f);
        assertEquals(1f, red.r(), 1e-6);
        assertEquals(0f, red.g(), 1e-6);
        assertEquals(0f, red.b(), 1e-6);
    }

    @Test
    void directionFieldUnitVectorAlongRealAxisIsRed() {
        // single pixel, sample (re=1, im=0) → direction 0 → pure red, full value
        ComplexField2D f = new ComplexField2D.Array(1, 1, new float[]{1f, 0f});
        byte[] rgba = FieldRaster.rasterize(f, ComplexColorMaps.directionField(1f));
        assertEquals(4, rgba.length);
        assertEquals(255, u8(rgba[0]), "R");
        assertEquals(0, u8(rgba[1]), "G");
        assertEquals(0, u8(rgba[2]), "B");
        assertEquals(255, u8(rgba[3]), "A opaque");
    }

    @Test
    void domainColoringOriginIsDimAndOpaque() {
        ComplexField2D f = new ComplexField2D.Array(1, 1, new float[]{0f, 0f});
        byte[] rgba = FieldRaster.rasterize(f, ComplexColorMaps.domainColoring());
        assertTrue(u8(rgba[0]) < 60, "origin is dim");
        assertEquals(0, u8(rgba[1]));
        assertEquals(0, u8(rgba[2]));
        assertEquals(255, u8(rgba[3]));
    }

    @Test
    void imageLayerMatchesFieldDimsAndOrientation() {
        ComplexField2D f = ComplexField2D.Array.from(3, 2, (x, y, out) -> { out[0] = x; out[1] = y; });
        PlotFrame frame = new PlotFrame(0f, 0f, 6f, 4f, Axis.linear(0, 3), Axis.linear(0, 2));
        ImageLayer img = FieldRaster.imageLayer(f, ComplexColorMaps.domainColoring(), frame);
        assertEquals(3, img.width());
        assertEquals(2, img.height());
        assertEquals(3 * 2 * 4, img.rgba().length);
        // top-left corner = (wx0, wy1) so the top row (y=0) sits at the frame top
        assertEquals(0f, img.corners()[0], 1e-6);
        assertEquals(4f, img.corners()[1], 1e-6);
    }

    @Test
    void sliceFromVolumePicksTheRightPlane() {
        // z encodes the slice: sample re = z, so each Z-slice is constant = its index
        ComplexField3D vol = ComplexField3D.Array.from(2, 2, 4, (x, y, z, out) -> { out[0] = z; out[1] = 0f; });
        assertEquals(4, vol.sliceCount(ComplexField3D.Axis3.Z));
        ComplexField2D s = vol.slice(ComplexField3D.Axis3.Z, 2);
        float[] tmp = new float[2];
        s.sample(1, 1, tmp);
        assertEquals(2f, tmp[0], 1e-6, "Z-slice index 2 samples re = 2");
    }
}
