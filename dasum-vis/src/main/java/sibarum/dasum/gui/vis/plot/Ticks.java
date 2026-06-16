package sibarum.dasum.gui.vis.plot;

import java.util.ArrayList;
import java.util.List;

/**
 * Nice-number tick generation for an {@link Axis} — the classic
 * Heckbert 1/2/5×10ⁿ "loose labelling" algorithm, plus per-decade ticks
 * for log axes. Pure functions; no GL, no state, fully unit-testable.
 *
 * <p>{@link #forAxis} returns ticks that fall within the axis range
 * (inclusive of the endpoints to within a rounding epsilon) along with
 * pre-formatted labels whose decimal precision is derived from the chosen
 * step, so adjacent labels never render identically.
 */
public final class Ticks {

    private Ticks() {}

    /** A set of tick positions (data units) and their formatted labels. */
    public record TickSet(double[] values, String[] labels) {
        public int count() { return values.length; }
    }

    /**
     * Generate up to roughly {@code targetCount} nicely-rounded ticks
     * covering {@code axis}.
     *
     * @param targetCount desired tick count (a hint; the result is rounded
     *                    to nice numbers and may differ by one or two)
     */
    public static TickSet forAxis(Axis axis, int targetCount) {
        if (targetCount < 2) targetCount = 2;
        return axis.scale() == Axis.Scale.LOG
            ? logTicks(axis)
            : linearTicks(axis, targetCount);
    }

    private static TickSet linearTicks(Axis axis, int targetCount) {
        double range = niceNum(axis.max() - axis.min(), false);
        double step = niceNum(range / (targetCount - 1), true);
        double graphMin = Math.floor(axis.min() / step) * step;
        double graphMax = Math.ceil(axis.max() / step) * step;

        List<Double> vals = new ArrayList<>();
        double eps = step * 1e-6;
        for (double v = graphMin; v <= graphMax + eps; v += step) {
            // Snap tiny floating drift to 0 and clip to the real axis window.
            double snapped = Math.abs(v) < eps ? 0d : v;
            if (snapped >= axis.min() - eps && snapped <= axis.max() + eps) {
                vals.add(snapped);
            }
        }
        double[] values = new double[vals.size()];
        String[] labels = new String[vals.size()];
        int decimals = decimalsFor(step);
        for (int i = 0; i < values.length; i++) {
            values[i] = vals.get(i);
            labels[i] = format(values[i], decimals);
        }
        return new TickSet(values, labels);
    }

    private static TickSet logTicks(Axis axis) {
        int lo = (int) Math.floor(Math.log10(axis.min()));
        int hi = (int) Math.ceil(Math.log10(axis.max()));
        List<Double> vals = new ArrayList<>();
        for (int e = lo; e <= hi; e++) {
            double v = Math.pow(10, e);
            if (v >= axis.min() * (1 - 1e-9) && v <= axis.max() * (1 + 1e-9)) vals.add(v);
        }
        double[] values = new double[vals.size()];
        String[] labels = new String[vals.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = vals.get(i);
            labels[i] = format(values[i], decimalsFor(values[i]));
        }
        return new TickSet(values, labels);
    }

    /**
     * Round {@code range} to a "nice" number. When {@code round} is true the
     * nearest nice number is returned (for step sizing); otherwise the
     * smallest nice number {@code >= range} (for whole-range rounding).
     */
    static double niceNum(double range, boolean round) {
        if (!(range > 0d)) return 1d;
        double exp = Math.floor(Math.log10(range));
        double frac = range / Math.pow(10, exp);
        double nice;
        if (round) {
            if (frac < 1.5) nice = 1; else if (frac < 3) nice = 2; else if (frac < 7) nice = 5; else nice = 10;
        } else {
            if (frac <= 1) nice = 1; else if (frac <= 2) nice = 2; else if (frac <= 5) nice = 5; else nice = 10;
        }
        return nice * Math.pow(10, exp);
    }

    /** Decimal places needed to distinguish ticks spaced by {@code step}. */
    static int decimalsFor(double step) {
        if (!(step > 0d)) return 0;
        return Math.max(0, (int) Math.ceil(-Math.log10(step) - 1e-9));
    }

    static String format(double v, int decimals) {
        if (v == 0d) return "0";
        double a = Math.abs(v);
        if (a >= 1e5 || a < 1e-4) {
            return stripTrailingZeros(String.format("%.2e", v));
        }
        return stripTrailingZeros(String.format("%." + decimals + "f", v));
    }

    private static String stripTrailingZeros(String s) {
        if (s.indexOf('e') >= 0 || s.indexOf('E') >= 0 || s.indexOf('.') < 0) return s;
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') end--;
        if (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }
}
