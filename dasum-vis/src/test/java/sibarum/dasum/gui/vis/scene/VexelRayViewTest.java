package sibarum.dasum.gui.vis.scene;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The enum ordinals are the shader contract — they're passed straight to
 * {@code u_viewMode}. If this order changes, vexelray.frag's view-mode
 * branch must change with it.
 */
final class VexelRayViewTest {

    @Test
    void defaultIsLit() {
        assertEquals(VexelRayView.LIT, VexelRayView.current());
    }

    @Test
    void ordinalsMatchShaderContract() {
        assertEquals(0, VexelRayView.LIT.ordinal());
        assertEquals(1, VexelRayView.NORMALS.ordinal());
        assertEquals(2, VexelRayView.AO.ordinal());
        assertEquals(3, VexelRayView.STEPS.ordinal());
        assertEquals(4, VexelRayView.ESCAPE_ITERS.ordinal());
        assertEquals(5, VexelRayView.COST_MINUS_ESCAPE.ordinal());
    }
}
