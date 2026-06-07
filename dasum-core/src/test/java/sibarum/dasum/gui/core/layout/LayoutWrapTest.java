package sibarum.dasum.gui.core.layout;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.input.ScrollPosition;
import sibarum.dasum.gui.core.input.ScrollStates;
import sibarum.dasum.gui.core.render.Color;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flex wrap layout. Default EmContext is 16px/em, so em sizes convert
 * deterministically (10em = 160px) without any GL/window setup.
 */
final class LayoutWrapTest {

    private static final Color CLEAR = new Color(0f, 0f, 0f, 0f);

    private static Component.Box box(float wEm, float hEm) {
        return new Component.Box(Em.of(wEm), Em.of(hEm), Em.ZERO, CLEAR);
    }

    /** ROW flex, three 10em boxes, wrap on/off; gap 0. */
    private static Component.Flex row(boolean wrap, Em width) {
        return new Component.Flex(
            width, null, Em.ZERO, CLEAR,
            Direction.ROW, JustifyContent.START, AlignItems.START, Em.ZERO,
            List.of(box(10f, 12f), box(10f, 12f), box(10f, 12f)),
            false, 0, wrap);
    }

    @Test
    void wrappingRowBreaksOntoMultipleLines() {
        // 25em (400px) main axis fits two 160px boxes per row; third wraps.
        Component.Flex flex = row(true, Em.of(25f));
        LayoutResult lr = Layout.compute(flex, new PixelRect(0f, 0f, 400f, 400f));
        List<Component> kids = flex.children();
        PixelRect b0 = lr.rectOf(kids.get(0));
        PixelRect b1 = lr.rectOf(kids.get(1));
        PixelRect b2 = lr.rectOf(kids.get(2));

        assertEquals(b0.y(), b1.y(), 0.5f, "first two boxes share a row");
        assertTrue(b2.y() > b0.y() + 100f, "third box wraps to the next row");
        assertEquals(b0.x(), b2.x(), 0.5f, "wrapped row restarts at the main-axis origin");
    }

    @Test
    void nonWrapRowStaysOnOneLineAndOverflows() {
        Component.Flex flex = row(false, Em.of(25f));
        LayoutResult lr = Layout.compute(flex, new PixelRect(0f, 0f, 400f, 400f));
        List<Component> kids = flex.children();
        PixelRect b0 = lr.rectOf(kids.get(0));
        PixelRect b2 = lr.rectOf(kids.get(2));
        assertEquals(b0.y(), b2.y(), 0.5f, "no wrap: all boxes on one row");
        assertTrue(b2.x() > b0.x() + 300f, "third box overflows past the container edge");
    }

    @Test
    void scrollExposesWrappedHeight() {
        // Two rows of 12em (192px) boxes inside a 20em (320px) scroll →
        // content ~384px → ~64px of vertical overflow the scroll must expose.
        Component.Flex flex = row(true, null); // fill scroll width
        Component.Scroll scroll = new Component.Scroll(
            Em.of(25f), Em.of(20f), Em.ZERO, CLEAR, flex);
        Layout.compute(scroll, new PixelRect(0f, 0f, 400f, 320f));

        ScrollPosition pos = ScrollStates.of(scroll);
        assertTrue(pos.pxMaxY() > 50f,
            "wrapped two-row content must give the scroll vertical overflow, got " + pos.pxMaxY());
    }
}
