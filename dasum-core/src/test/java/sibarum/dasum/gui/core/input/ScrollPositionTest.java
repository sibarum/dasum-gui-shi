package sibarum.dasum.gui.core.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ScrollPosition#scrollByPx} must report whether it actually moved
 * — that boolean is what the wheel handler uses to bubble an unconsumed
 * event from a bottomed-out inner scroll to its parent.
 */
final class ScrollPositionTest {

    @Test
    void reportsMovementAndClampsAtLimits() {
        ScrollPosition pos = new ScrollPosition();
        pos.updateBoundsPx(0f, 100f);

        assertTrue(pos.scrollByPx(0f, 50f), "real movement returns true");
        assertEquals(50f, pos.pxY(), 1e-4f);

        assertTrue(pos.scrollByPx(0f, 100f), "movement that clamps to the limit still moved");
        assertEquals(100f, pos.pxY(), 1e-4f);

        assertFalse(pos.scrollByPx(0f, 40f), "already at the limit → no movement → false (bubbles)");
        assertEquals(100f, pos.pxY(), 1e-4f);

        assertTrue(pos.scrollByPx(0f, -30f), "scrolling back off the limit moves again");
    }

    @Test
    void noMovementAtTopReturnsFalse() {
        ScrollPosition pos = new ScrollPosition();
        pos.updateBoundsPx(0f, 100f);
        assertFalse(pos.scrollByPx(0f, -10f), "already at top, scrolling further up does nothing");
    }

    @Test
    void axisWithNoOverflowDoesNotConsume() {
        ScrollPosition pos = new ScrollPosition();
        pos.updateBoundsPx(0f, 100f); // vertical overflow only
        assertFalse(pos.scrollByPx(20f, 0f), "no horizontal overflow → horizontal wheel bubbles");
    }
}
