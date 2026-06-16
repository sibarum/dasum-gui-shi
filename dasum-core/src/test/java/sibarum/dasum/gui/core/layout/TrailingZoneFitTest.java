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
 * Regression for the Status ribbon's docked zone: a trailing {@code flexGrow=0}
 * zone pushed to the right edge by a leading {@code flexGrow=1} zone must use
 * {@code Em.AUTO} width to hug its content. A {@code null} width resolves to
 * the 0 fallback in intrinsic context, collapsing the zone so its content
 * overflows past the right edge. Default EmContext is 16px/em, so em→px is
 * deterministic without any GL/font setup.
 */
final class TrailingZoneFitTest {

    private static final Color CLEAR = new Color(0f, 0f, 0f, 0f);

    /** ribbon = ROW[ leading(grow1, null), trailing(grow0, trailingWidth)[box(5em)] ]. */
    private static Component.Flex ribbon(Em trailingWidth) {
        Component box = new Component.Box(Em.of(5f), Em.of(1f), Em.ZERO, CLEAR);
        Component leading = new Component.Flex(
            null, null, Em.ZERO, CLEAR,
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.ZERO,
            List.of(), false, 1);
        Component trailing = new Component.Flex(
            trailingWidth, null, Em.ZERO, CLEAR,
            Direction.ROW, JustifyContent.END, AlignItems.CENTER, Em.ZERO,
            List.of(box), false, 0);
        return new Component.Flex(
            null, Em.of(1f), Em.ZERO, CLEAR,
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.ZERO,
            List.of(leading, trailing), false, 0);
    }

    private static Component box(Component.Flex ribbon) {
        Component trailing = ribbon.children().get(1);
        return ((Component.Flex) trailing).children().get(0);
    }

    @Test
    void autoWidthHugsContentAndRightAligns() {
        Component.Flex ribbon = ribbon(Em.AUTO);
        LayoutResult lr = Layout.compute(ribbon, new PixelRect(0f, 0f, 400f, 16f));
        PixelRect b = lr.rectOf(box(ribbon));
        assertEquals(80f, b.width(), 0.5f, "box keeps its 5em (80px) width");
        assertEquals(400f, b.x() + b.width(), 0.5f, "right edge sits at the ribbon's right edge");
        assertTrue(b.x() >= 0f && b.x() + b.width() <= 400.5f, "content stays inside the ribbon");
    }

    @Test
    void overflowShrinksFlexibleChildSoRigidSiblingHoldsItsSize() {
        // ROW 100px: leading grow=1 (intrinsic 160px) + trailing rigid 80px.
        // Sum (240) overflows, so the flexible leader must shrink to 20px and
        // the rigid trailer keeps 80px flush at the right edge — instead of the
        // old behavior where the leader kept 160px and shoved the trailer off.
        Component leader = new Component.Flex(
            Em.of(10f), null, Em.ZERO, CLEAR,
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.ZERO,
            List.of(), false, 1);
        Component trailer = new Component.Box(Em.of(5f), Em.of(1f), Em.ZERO, CLEAR);
        Component.Flex row = new Component.Flex(
            null, Em.of(1f), Em.ZERO, CLEAR,
            Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.ZERO,
            List.of(leader, trailer), false, 0);

        LayoutResult lr = Layout.compute(row, new PixelRect(0f, 0f, 100f, 16f));
        PixelRect l = lr.rectOf(leader);
        PixelRect t = lr.rectOf(trailer);
        assertEquals(20f, l.width(), 0.5f, "flexible leader absorbs the overflow");
        assertEquals(80f, t.width(), 0.5f, "rigid trailer keeps its intrinsic width");
        assertEquals(100f, t.x() + t.width(), 0.5f, "rigid trailer stays flush at the right edge");
    }

    @Test
    void nullWidthCollapsesAndContentOverflows() {
        // Documents the bug the AUTO fix avoids: the zone collapses to 0 width
        // and the child spills past the right edge.
        Component.Flex ribbon = ribbon(null);
        LayoutResult lr = Layout.compute(ribbon, new PixelRect(0f, 0f, 400f, 16f));
        PixelRect b = lr.rectOf(box(ribbon));
        assertEquals(400f, b.x(), 0.5f, "collapsed zone parks the child at the right edge");
        assertTrue(b.x() + b.width() > 400.5f, "child overflows past the right edge");
    }
}
