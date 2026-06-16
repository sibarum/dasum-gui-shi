package sibarum.dasum.gui.vis.plot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TicksTest {

    @Test
    void linearTicksAreNiceAndInRange() {
        Ticks.TickSet t = Ticks.forAxis(Axis.linear(0, 10), 6);
        // step 2 → 0,2,4,6,8,10
        assertEquals(6, t.count());
        assertEquals(0d, t.values()[0], 1e-9);
        assertEquals(2d, t.values()[1], 1e-9);
        assertEquals(10d, t.values()[5], 1e-9);
        for (double v : t.values()) {
            assertTrue(v >= 0 - 1e-9 && v <= 10 + 1e-9, "tick within axis range");
        }
        assertEquals("0", t.labels()[0]);
        assertEquals("10", t.labels()[5]);
    }

    @Test
    void fractionalStepGetsEnoughDecimals() {
        Ticks.TickSet t = Ticks.forAxis(Axis.linear(0, 1), 11);
        // step 0.1 → labels need one decimal and must be distinct
        boolean sawTenth = false;
        for (String label : t.labels()) if (label.equals("0.1")) sawTenth = true;
        assertTrue(sawTenth, "expected a 0.1 tick label, got distinct labels");
    }

    @Test
    void logTicksAreDecadePowers() {
        Ticks.TickSet t = Ticks.forAxis(Axis.log(1, 1000), 6);
        assertEquals(4, t.count());
        assertEquals(1d, t.values()[0], 1e-9);
        assertEquals(10d, t.values()[1], 1e-9);
        assertEquals(100d, t.values()[2], 1e-9);
        assertEquals(1000d, t.values()[3], 1e-9);
    }

    @Test
    void niceNumRounds() {
        assertEquals(1d, Ticks.niceNum(1.2, true), 1e-9);
        assertEquals(2d, Ticks.niceNum(1.8, true), 1e-9);
        assertEquals(5d, Ticks.niceNum(4.0, true), 1e-9);
        assertEquals(10d, Ticks.niceNum(9.0, true), 1e-9);
    }
}
