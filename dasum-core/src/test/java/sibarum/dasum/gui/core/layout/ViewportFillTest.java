package sibarum.dasum.gui.core.layout;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A null-sized viewport ({@link Component.SceneView}) fills its parent on BOTH
 * axes even inside a CENTER-aligned flex — the layout rule that lets a plot
 * dropped into {@code window(...)}'s auto-wrapper span the whole window instead
 * of collapsing to its zero intrinsic size. Default EmContext is 16px/em, so no
 * GL/window setup is needed.
 */
final class ViewportFillTest {

    private static final Color CLEAR = new Color(0f, 0f, 0f, 0f);

    /** Mirrors DasumBridge's window auto-wrapper: a CENTER/CENTER column holding one fill viewport. */
    private static Component.Flex centeredColumnWith(Component child) {
        return new Component.Flex(
            null, null, Em.ZERO, CLEAR,
            Direction.COLUMN, JustifyContent.CENTER, AlignItems.CENTER, Em.ZERO,
            List.of(child), false, 1, false);
    }

    @Test
    void nullSizedSceneViewFillsCenteredColumn() {
        // width=height=null → fill; flexGrow=1 → take the column's main axis.
        Component.SceneView plot =
            new Component.SceneView(null, null, Em.ZERO, CLEAR, true, 1);
        Component.Flex root = centeredColumnWith(plot);

        LayoutResult lr = Layout.compute(root, new PixelRect(0f, 0f, 800f, 600f));
        PixelRect r = lr.rectOf(plot);

        assertEquals(0f, r.x(), 0.5f, "spans from the left edge");
        assertEquals(800f, r.width(), 0.5f, "fills the full width (no CENTER collapse)");
        assertEquals(0f, r.y(), 0.5f, "spans from the top edge");
        assertEquals(600f, r.height(), 0.5f, "flexGrow fills the full height");
    }

    @Test
    void fixedSizeSceneViewStillCentersAndDoesNotFill() {
        // A viewport with an explicit size keeps its size and is centred — the
        // fill rule is scoped to null (unset) cross sizes only.
        Component.SceneView sized =
            new Component.SceneView(Em.of(10f), Em.of(6f), Em.ZERO, CLEAR, true, 0);
        Component.Flex root = centeredColumnWith(sized);

        LayoutResult lr = Layout.compute(root, new PixelRect(0f, 0f, 800f, 600f));
        PixelRect r = lr.rectOf(sized);

        assertEquals(160f, r.width(), 0.5f, "10em stays 160px, not stretched");
        assertTrue(r.x() > 1f, "centred horizontally, not pinned to the edge");
    }
}
