package sibarum.dasum.gui.vis.scene;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The enum ordinals are the shader contract — they're passed straight to
 * {@code u_viewMode}. If this order changes, sdf.frag's view-mode
 * branch must change with it.
 */
final class SdfViewTest {

    @Test
    void defaultIsLit() {
        assertEquals(SdfView.LIT, SdfView.current());
    }

    @Test
    void ordinalsMatchShaderContract() {
        assertEquals(0, SdfView.LIT.ordinal());
        assertEquals(1, SdfView.NORMALS.ordinal());
        assertEquals(2, SdfView.AO.ordinal());
        assertEquals(3, SdfView.STEPS.ordinal());
        assertEquals(4, SdfView.ESCAPE_ITERS.ordinal());
        assertEquals(5, SdfView.COST_MINUS_ESCAPE.ordinal());
    }
}
