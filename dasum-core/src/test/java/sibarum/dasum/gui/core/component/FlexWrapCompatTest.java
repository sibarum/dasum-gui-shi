package sibarum.dasum.gui.core.component;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The wrap field is additive: old positional call sites default to false. */
final class FlexWrapCompatTest {

    private static final Color CLEAR = new Color(0f, 0f, 0f, 0f);

    @Test
    void elevenArgConstructorDefaultsWrapFalse() {
        Component.Flex f = new Component.Flex(
            null, null, Em.ZERO, CLEAR,
            Direction.ROW, JustifyContent.START, AlignItems.START, Em.ZERO,
            List.of(), false, 0);
        assertFalse(f.wrap(), "pre-wrap constructor must default to false");
    }

    @Test
    void withWrapRoundTripsAndPreservesFields() {
        Component.Flex f = new Component.Flex(
            Em.of(3f), Em.of(4f), Em.of(1f), CLEAR,
            Direction.ROW, JustifyContent.CENTER, AlignItems.END, Em.of(0.5f),
            List.of(), true, 2).withWrap(true);
        assertTrue(f.wrap());
        assertEquals(Direction.ROW, f.direction());
        assertEquals(JustifyContent.CENTER, f.justify());
        assertEquals(AlignItems.END, f.align());
        assertEquals(2, f.flexGrow());
        assertTrue(f.interactive());
        // A further wither keeps wrap set.
        assertTrue(f.withAlign(AlignItems.START).wrap());
    }
}
