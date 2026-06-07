package sibarum.dasum.gui.core.layout;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scroll-chain hit testing for wheel routing (innermost-first, clip-respected). */
final class HitTestScrollChainTest {

    private static final Color CLEAR = new Color(0f, 0f, 0f, 0f);

    @Test
    void chainIsInnermostFirst() {
        // Inner scroll (tall content) nested inside an outer scroll.
        Component.Box innerContent = new Component.Box(Em.of(10f), Em.of(40f), Em.ZERO, CLEAR);
        Component.Scroll inner = new Component.Scroll(Em.of(10f), Em.of(10f), Em.ZERO, CLEAR, innerContent);
        Component.Scroll outer = new Component.Scroll(Em.of(20f), Em.of(20f), Em.ZERO, CLEAR, inner);

        LayoutResult lr = Layout.compute(outer, new PixelRect(0f, 0f, 320f, 320f));

        // A point inside the inner scroll's viewport → [inner, outer].
        List<Component.Scroll> chain = HitTest.findScrollChain(outer, lr, 40f, 40f);
        assertEquals(2, chain.size());
        assertSame(inner, chain.get(0), "innermost first");
        assertSame(outer, chain.get(1));
    }

    @Test
    void pointOutsideInnerYieldsOnlyOuter() {
        // Inner scroll is 10em (160px) tall at the top; a point well below
        // it but still inside the 20em (320px) outer → outer only.
        Component.Box innerContent = new Component.Box(Em.of(10f), Em.of(40f), Em.ZERO, CLEAR);
        Component.Scroll inner = new Component.Scroll(Em.of(10f), Em.of(10f), Em.ZERO, CLEAR, innerContent);
        Component.Flex col = new Component.Flex(
            null, null, Em.ZERO, CLEAR,
            sibarum.dasum.gui.core.component.Direction.COLUMN,
            sibarum.dasum.gui.core.component.JustifyContent.START,
            sibarum.dasum.gui.core.component.AlignItems.START, Em.ZERO,
            List.of(inner), false, 0);
        Component.Scroll outer = new Component.Scroll(Em.of(20f), Em.of(20f), Em.ZERO, CLEAR, col);

        LayoutResult lr = Layout.compute(outer, new PixelRect(0f, 0f, 320f, 320f));

        List<Component.Scroll> chain = HitTest.findScrollChain(outer, lr, 40f, 300f);
        assertEquals(1, chain.size(), "point below the inner scroll hits only the outer");
        assertSame(outer, chain.get(0));
    }

    @Test
    void pointOutsideAllScrollsYieldsEmpty() {
        Component.Box content = new Component.Box(Em.of(10f), Em.of(10f), Em.ZERO, CLEAR);
        Component.Scroll s = new Component.Scroll(Em.of(10f), Em.of(10f), Em.ZERO, CLEAR, content);
        LayoutResult lr = Layout.compute(s, new PixelRect(0f, 0f, 160f, 160f));
        assertTrue(HitTest.findScrollChain(s, lr, 500f, 500f).isEmpty());
    }
}
