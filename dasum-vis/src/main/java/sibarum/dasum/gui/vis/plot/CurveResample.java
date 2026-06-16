package sibarum.dasum.gui.vis.plot;

/**
 * Catmull-Rom resampling of a control polyline into a dense smooth curve.
 * The spline passes <em>through</em> every input point (interpolating, not
 * approximating), with the endpoints duplicated so the first and last spans
 * have well-defined tangents. Pure: input arrays are never mutated.
 *
 * <p>Used by {@link LinePlot} for {@link Series.Style#CURVE}; exposed
 * separately because resampling in data space (before the data→world map)
 * keeps the curve shape independent of the plot's world placement.
 */
public final class CurveResample {

    private CurveResample() {}

    /** Resampled polyline: {@code xs}/{@code ys} of equal length. */
    public record Polyline(double[] xs, double[] ys) {}

    /**
     * @param xs               control x samples (length >= 2)
     * @param ys               control y samples (same length)
     * @param segmentsPerSpan  curve segments emitted between each adjacent
     *                         pair of control points (>= 1); higher = smoother
     */
    public static Polyline catmullRom(double[] xs, double[] ys, int segmentsPerSpan) {
        if (xs.length != ys.length) throw new IllegalArgumentException("xs and ys must have equal length");
        if (xs.length < 2) throw new IllegalArgumentException("need >= 2 points");
        if (segmentsPerSpan < 1) segmentsPerSpan = 1;

        int n = xs.length;
        int spans = n - 1;
        int outLen = spans * segmentsPerSpan + 1;
        double[] ox = new double[outLen];
        double[] oy = new double[outLen];

        int w = 0;
        for (int i = 0; i < spans; i++) {
            double p0x = xs[Math.max(0, i - 1)],     p0y = ys[Math.max(0, i - 1)];
            double p1x = xs[i],                       p1y = ys[i];
            double p2x = xs[i + 1],                   p2y = ys[i + 1];
            double p3x = xs[Math.min(n - 1, i + 2)],  p3y = ys[Math.min(n - 1, i + 2)];

            // Emit the span's start once; subsequent segments append their end
            // (so spans share their boundary sample without duplication).
            for (int s = 0; s < segmentsPerSpan; s++) {
                double t = (double) s / segmentsPerSpan;
                ox[w] = catmull(p0x, p1x, p2x, p3x, t);
                oy[w] = catmull(p0y, p1y, p2y, p3y, t);
                w++;
            }
        }
        ox[w] = xs[n - 1];
        oy[w] = ys[n - 1];
        return new Polyline(ox, oy);
    }

    /** Standard uniform Catmull-Rom basis evaluated at {@code t} in [0,1]. */
    private static double catmull(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2 * p1)
            + (-p0 + p2) * t
            + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
            + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }
}
