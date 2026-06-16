package sibarum.dasum.gui.vis.plot;

import sibarum.dasum.gui.core.render.Color;

/**
 * Appearance + density knobs for a plot's chrome (border, gridlines, tick
 * labels). Kept separate from {@link PlotFrame} (geometry) and {@link Series}
 * (data) so the same data can be re-styled without rebuilding either.
 *
 * @param axisColor        border / axis line colour
 * @param gridColor        interior gridline colour (usually a dim axisColor)
 * @param labelColor       tick-label glyph colour
 * @param labelHeightWorld tick-label em height in world units
 * @param targetXTicks     desired x-axis tick count (a hint for {@link Ticks})
 * @param targetYTicks     desired y-axis tick count
 * @param grid             draw interior gridlines when true
 */
public record PlotStyle(
    Color axisColor,
    Color gridColor,
    Color labelColor,
    float labelHeightWorld,
    int targetXTicks,
    int targetYTicks,
    boolean grid
) {

    public PlotStyle {
        if (axisColor == null || gridColor == null || labelColor == null) {
            throw new IllegalArgumentException("colours != null");
        }
        if (!(labelHeightWorld > 0f)) throw new IllegalArgumentException("labelHeightWorld > 0");
        if (targetXTicks < 2 || targetYTicks < 2) throw new IllegalArgumentException("tick targets >= 2");
    }

    /** Sensible light-on-dark defaults sized for a ~[0,10] world frame. */
    public static PlotStyle defaults() {
        return new PlotStyle(
            new Color(0.75f, 0.78f, 0.82f, 1f),
            new Color(0.30f, 0.32f, 0.36f, 1f),
            new Color(0.85f, 0.87f, 0.90f, 1f),
            0.32f,
            6, 6,
            true
        );
    }

    public PlotStyle withTargetTicks(int x, int y) {
        return new PlotStyle(axisColor, gridColor, labelColor, labelHeightWorld, x, y, grid);
    }

    public PlotStyle withGrid(boolean g) {
        return new PlotStyle(axisColor, gridColor, labelColor, labelHeightWorld, targetXTicks, targetYTicks, g);
    }
}
