package sibarum.dasum.gui.vis.plot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AxisTest {

    @Test
    void linearRoundTripAndEdges() {
        Axis a = Axis.linear(-2, 8);
        assertEquals(0d, a.dataToUnit(-2), 1e-9);
        assertEquals(1d, a.dataToUnit(8), 1e-9);
        assertEquals(0.5d, a.dataToUnit(3), 1e-9);
        // round-trip
        assertEquals(4.2d, a.unitToData(a.dataToUnit(4.2d)), 1e-9);
        // world mapping respects span direction
        assertEquals(100d, a.dataToWorld(-2, 100, 200), 1e-9);
        assertEquals(200d, a.dataToWorld(8, 100, 200), 1e-9);
    }

    @Test
    void logMapsGeometricMidpointToHalf() {
        Axis a = Axis.log(1, 100);
        assertEquals(0d, a.dataToUnit(1), 1e-9);
        assertEquals(1d, a.dataToUnit(100), 1e-9);
        assertEquals(0.5d, a.dataToUnit(10), 1e-9, "geometric mean sits at unit 0.5");
        assertEquals(10d, a.unitToData(0.5d), 1e-6);
    }

    @Test
    void autoRangePadsAndGuardsDegenerate() {
        Axis a = Axis.autoRange(0, 10, 0.05);
        assertEquals(-0.5d, a.min(), 1e-9);
        assertEquals(10.5d, a.max(), 1e-9);
        // all-equal data must not collapse
        Axis d = Axis.autoRange(5, 5, 0.05);
        assertTrue(d.max() > d.min());
    }

    @Test
    void invalidRangesRejected() {
        assertThrows(IllegalArgumentException.class, () -> Axis.linear(5, 5));
        assertThrows(IllegalArgumentException.class, () -> Axis.log(-1, 10));
    }
}
