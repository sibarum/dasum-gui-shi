package sibarum.dasum.gui.vis.plot;

import sibarum.dasum.gui.core.render.Color;

import java.util.List;

/**
 * A <b>semantic</b> description of a 2D plot — the meaning of each element, not its pixels. Where
 * {@link Series} / {@link PlotFrame} / {@link Layer}-based rendering carries generic geometry (a
 * polyline is just points), this model records what each piece <em>is</em>: a curve, a vertical
 * asymptote, a feature marker (zero / optimum / intersection), and the reliable enclosure band. It
 * is the input a semantic exporter ({@link SvgPlotWriter}) needs to class every element — so a host
 * page can style curves, asymptotes and markers independently, and attach behaviour by feature kind.
 *
 * <p>All coordinates are in <b>data space</b>; the {@link #frame} carries the data ranges (via its
 * {@link Axis} pair) that map them onto the canvas. Nothing here is pixel- or colour-specific — the
 * look is the exporter's business, and for SVG it is the host stylesheet's.
 *
 * <p>Curves reuse the existing {@link Series} (a discontinuity — a pole or a domain edge — is a
 * break BETWEEN series, so an asymptote is never crossed); the OGL path draws them through the very
 * same {@link LinePlot#build} it always has, and the SVG writer reads their point arrays. Only the
 * genuinely-new semantic overlays get IR-native types. A {@code Series}' colour is a rendering
 * default the SVG writer ignores (it styles by CSS class).
 *
 * @param frame       the plot frame (its axes carry the x/y data ranges)
 * @param curves      the drawn curves, each one CONTINUOUS series
 * @param asymptotes  vertical asymptotes (a data-x and its display label)
 * @param features    marked features (zeros, local optima, intersections)
 * @param enclosures  the reliable interval-enclosure bands — one per reliably-plotted expression, in
 *                    curve order (empty when none supplied). Each is the provably-contains-the-curve
 *                    region a reliable plot can honestly shade; multiple auto-plots contribute one each.
 */
public record PlotScene2D(
    PlotFrame frame,
    List<Series> curves,
    List<Asymptote> asymptotes,
    List<Feature> features,
    List<EnclosureBand> enclosures
) {
    public PlotScene2D {
        enclosures = enclosures == null ? List.of() : enclosures;
    }

    /** A vertical asymptote at data-x {@code x}, labelled {@code label} (e.g. {@code "x=0.385"}). The
     *  optional {@code color} colour-codes the line/label to its owning plot; {@code null} means the
     *  renderer's default asymptote colour (and, for SVG, the {@code --pontif-asymptote} CSS var). */
    public record Asymptote(double x, String label, Color color) {
        public Asymptote(double x, String label) { this(x, label, null); }
    }

    /** The kind of a marked feature — selects its glyph and how a host might treat it. */
    public enum FeatureKind { ZERO, OPTIMUM, INTERSECTION }

    /** A marked feature at data point {@code (x, y)} with a display {@code label}. The optional
     *  {@code color} colour-codes the marker/label to its owning plot; {@code null} means the
     *  renderer's default feature colour (and, for SVG, the {@code --pontif-feature} CSS var). */
    public record Feature(FeatureKind kind, double x, double y, String label, Color color) {
        public Feature(FeatureKind kind, double x, double y, String label) { this(kind, x, y, label, null); }
    }

    /**
     * The reliable enclosure band: for each sampled column {@code xs[i]}, the guaranteed value range
     * {@code [loYs[i], hiYs[i]]} that provably contains the true curve there. Rendered as a filled
     * region a host can reveal to show "the curve is somewhere in here" — the reliability made visible.
     */
    public record EnclosureBand(double[] xs, double[] loYs, double[] hiYs) {
        public EnclosureBand {
            if (xs.length != loYs.length || xs.length != hiYs.length) {
                throw new IllegalArgumentException("enclosure xs/loYs/hiYs length mismatch");
            }
        }
        public int columnCount() { return xs.length; }
    }
}
