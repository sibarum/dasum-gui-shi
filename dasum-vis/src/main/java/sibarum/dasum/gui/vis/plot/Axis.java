package sibarum.dasum.gui.vis.plot;

/**
 * A 1D mapping between <b>data units</b> and a normalized {@code [0, 1]}
 * unit interval (and, through {@link #dataToWorld}, world units). The
 * building block both {@link PlotFrame} and {@link Ticks} read so the
 * data→world transform is defined in exactly one place.
 *
 * <p>An axis is an immutable value: pick a fixed data range up front
 * (explicitly, or via {@link #autoRange} from a data sweep) and reuse it
 * across the frame, the series, and the tick labels. {@link Scale#LOG}
 * requires a strictly positive range — both {@code min} and {@code max}
 * must be {@code > 0}.
 *
 * @param min   data value at unit 0 (the low/left/bottom edge)
 * @param max   data value at unit 1 (the high/right/top edge)
 * @param scale linear or logarithmic mapping
 */
public record Axis(double min, double max, Scale scale) {

    public enum Scale { LINEAR, LOG }

    public Axis {
        if (!(max > min)) {
            throw new IllegalArgumentException("axis max (" + max + ") must be > min (" + min + ")");
        }
        if (scale == null) throw new IllegalArgumentException("scale != null");
        if (scale == Scale.LOG && min <= 0d) {
            throw new IllegalArgumentException("LOG axis requires min > 0 (was " + min + ")");
        }
    }

    public static Axis linear(double min, double max) { return new Axis(min, max, Scale.LINEAR); }
    public static Axis log(double min, double max)    { return new Axis(min, max, Scale.LOG); }

    /**
     * A linear axis fitted to {@code [dataMin, dataMax]} with a symmetric
     * fractional {@code pad} on each end (e.g. {@code 0.05} for 5% breathing
     * room). A degenerate range (all values equal) is widened to a unit span
     * so the axis never collapses.
     */
    public static Axis autoRange(double dataMin, double dataMax, double pad) {
        if (dataMax < dataMin) { double t = dataMin; dataMin = dataMax; dataMax = t; }
        double span = dataMax - dataMin;
        if (!(span > 0d)) { // degenerate: centre a unit window on the value
            double half = Math.max(0.5d, Math.abs(dataMin) * 0.5d);
            return new Axis(dataMin - half, dataMax + half, Scale.LINEAR);
        }
        double m = span * pad;
        return new Axis(dataMin - m, dataMax + m, Scale.LINEAR);
    }

    /** Map a data value to the {@code [0, 1]} unit interval (unclamped). */
    public double dataToUnit(double v) {
        return switch (scale) {
            case LINEAR -> (v - min) / (max - min);
            case LOG -> (Math.log(v) - Math.log(min)) / (Math.log(max) - Math.log(min));
        };
    }

    /** Inverse of {@link #dataToUnit}. */
    public double unitToData(double u) {
        return switch (scale) {
            case LINEAR -> min + u * (max - min);
            case LOG -> Math.exp(Math.log(min) + u * (Math.log(max) - Math.log(min)));
        };
    }

    /** Map a data value onto a world span {@code [world0, world1]}. */
    public double dataToWorld(double v, double world0, double world1) {
        return world0 + dataToUnit(v) * (world1 - world0);
    }
}
