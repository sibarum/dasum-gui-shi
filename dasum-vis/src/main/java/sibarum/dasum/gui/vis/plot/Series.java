package sibarum.dasum.gui.vis.plot;

import sibarum.dasum.gui.core.render.Color;

/**
 * One plotted data series in <b>data units</b> — paired {@code xs}/{@code ys}
 * samples drawn as a straight polyline ({@link Style#LINE}) or a smooth
 * Catmull-Rom curve through the points ({@link Style#CURVE}).
 *
 * <p>{@code thicknessWorld} is the stroke width in world units: {@code 0}
 * (the default) draws a crisp 1px {@code LineLayer}; any positive value is
 * tessellated into a {@code TriangleLayer} ribbon by {@link LinePlot}.
 * Arrays are taken by reference and treated as immutable — do not mutate
 * them after handing the series to a builder.
 *
 * @param xs            x samples, length == ys.length and >= 2
 * @param ys            y samples
 * @param color         stroke colour (linear RGB; alpha respected)
 * @param style         straight segments or smoothed curve
 * @param thicknessWorld stroke width in world units; 0 = thin 1px line
 */
public record Series(double[] xs, double[] ys, Color color, Style style, float thicknessWorld) {

    public enum Style { LINE, CURVE }

    public Series {
        if (xs == null || ys == null) throw new IllegalArgumentException("xs/ys != null");
        if (xs.length != ys.length) throw new IllegalArgumentException("xs and ys must have equal length");
        if (xs.length < 2) throw new IllegalArgumentException("a series needs >= 2 points");
        if (color == null) throw new IllegalArgumentException("color != null");
        if (style == null) throw new IllegalArgumentException("style != null");
        if (thicknessWorld < 0f) throw new IllegalArgumentException("thicknessWorld >= 0");
    }

    public static Series line(double[] xs, double[] ys, Color color) {
        return new Series(xs, ys, color, Style.LINE, 0f);
    }

    public static Series curve(double[] xs, double[] ys, Color color) {
        return new Series(xs, ys, color, Style.CURVE, 0f);
    }

    public Series withThickness(float worldUnits) {
        return new Series(xs, ys, color, style, worldUnits);
    }

    public int pointCount() { return xs.length; }
}
