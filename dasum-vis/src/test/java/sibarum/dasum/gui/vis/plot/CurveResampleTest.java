package sibarum.dasum.gui.vis.plot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CurveResampleTest {

    @Test
    void passesThroughEveryControlPoint() {
        double[] xs = {0, 1, 2, 3};
        double[] ys = {0, 4, -1, 2};
        int seg = 4;
        CurveResample.Polyline p = CurveResample.catmullRom(xs, ys, seg);

        // outLen = spans * seg + 1
        assertEquals((xs.length - 1) * seg + 1, p.xs().length);
        assertEquals(p.xs().length, p.ys().length);

        // control point i lands at output index i*seg
        for (int i = 0; i < xs.length; i++) {
            int idx = Math.min(i * seg, p.xs().length - 1);
            assertEquals(xs[i], p.xs()[idx], 1e-9, "x through control " + i);
            assertEquals(ys[i], p.ys()[idx], 1e-9, "y through control " + i);
        }
    }

    @Test
    void segmentsPerSpanFlooredAtOne() {
        double[] xs = {0, 1};
        double[] ys = {0, 1};
        CurveResample.Polyline p = CurveResample.catmullRom(xs, ys, 0);
        assertEquals(2, p.xs().length); // 1 span * 1 + 1
    }
}
