package sibarum.dasum.gui.vis.plot;

import java.util.List;
import java.util.Locale;

/**
 * Serialises a {@link PlotScene2D} to a self-contained, <b>semantically classed</b> SVG string —
 * the web-embeddable sibling of the GL renderer. Every element carries a role class
 * ({@code curve}, {@code asymptote}, {@code feature zero}, {@code tick-label}, …) and its data
 * values as {@code data-*} attributes, so a host page can restyle it with CSS and drive it with JS
 * (tooltips, highlighting, reading off coordinates) without parsing geometry.
 *
 * <p>Design choices:
 * <ul>
 *   <li><b>Pixel geometry, semantic data.</b> Path/line coordinates are in the pixel canvas (so CSS
 *       {@code stroke-width} behaves predictably); the true data values ride along in
 *       {@code data-x} / {@code data-y} / {@code data-value} attributes for scripting.</li>
 *   <li><b>Self-contained but overridable.</b> An inlined {@code <style>} block sets class-based
 *       defaults via {@code currentColor} and {@code --pontif-*} custom properties, so the file
 *       looks right standalone yet a host restyles it by overriding a class or a variable.</li>
 *   <li><b>Enclosure band hidden by default.</b> The reliable {@code [lo,hi]} region is emitted as
 *       {@code <path class="enclosure-band">} with {@code display:none} in the default style — the
 *       host opts in by unsetting it, no regeneration needed.</li>
 * </ul>
 *
 * <p>Pure text: no GL, no window, no I/O — fully unit-testable, and the caller decides where the
 * string goes (a file, a page, a pipeline).
 */
public final class SvgPlotWriter {

    /** Canvas margins (px) reserving room for the axis tick labels. */
    private static final double MARGIN_LEFT = 64, MARGIN_RIGHT = 24, MARGIN_TOP = 24, MARGIN_BOTTOM = 44;
    private static final int TARGET_TICKS = 6;

    private final PlotScene2D scene;
    private final int width, height;
    private final double left, top, plotW, plotH;
    private final StringBuilder sb = new StringBuilder(4096);

    private SvgPlotWriter(PlotScene2D scene, int width, int height) {
        this.scene = scene;
        this.width = width;
        this.height = height;
        this.left = MARGIN_LEFT;
        this.top = MARGIN_TOP;
        this.plotW = Math.max(1, width - MARGIN_LEFT - MARGIN_RIGHT);
        this.plotH = Math.max(1, height - MARGIN_TOP - MARGIN_BOTTOM);
    }

    /** Serialise {@code scene} to an SVG document string sized to a {@code width × height} canvas. */
    public static String write(PlotScene2D scene, int width, int height) {
        return new SvgPlotWriter(scene, width, height).build();
    }

    private String build() {
        Axis ax = scene.frame().x(), ay = scene.frame().y();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"pontif-plot\" viewBox=\"0 0 ")
          .append(width).append(' ').append(height).append("\"")
          .append(" data-domain=\"").append(num(ax.min())).append(',').append(num(ax.max())).append("\"")
          .append(" data-range=\"").append(num(ay.min())).append(',').append(num(ay.max())).append("\">\n");
        style();
        defs();
        axes();
        enclosure();
        curves();
        asymptotes();
        features();
        sb.append("</svg>\n");
        return sb.toString();
    }

    /** Class-based defaults; every colour is a CSS custom property so a host overrides one line. */
    private void style() {
        sb.append("<style>\n")
          .append(".pontif-plot{--pontif-fg:#e8e9ee;--pontif-grid:#4d5560;--pontif-axis:#c0c4cc;")
          .append("--pontif-curve:#66ccff;--pontif-asymptote:#f27373;--pontif-feature:#fad84d;")
          .append("--pontif-enclosure:#66ccff;color:var(--pontif-fg);font-family:sans-serif}\n")
          .append(".pontif-plot .gridline{stroke:var(--pontif-grid);stroke-width:1}\n")
          .append(".pontif-plot .frame{fill:none;stroke:var(--pontif-axis);stroke-width:1.5}\n")
          .append(".pontif-plot .tick-label{fill:var(--pontif-fg);font-size:13px;")
          .append("paint-order:stroke;stroke:#0a0c10;stroke-width:2px;stroke-linejoin:round}\n")
          .append(".pontif-plot .tick-x{text-anchor:middle}\n")
          .append(".pontif-plot .tick-y{text-anchor:end;dominant-baseline:middle}\n")
          .append(".pontif-plot .curve{fill:none;stroke:var(--pontif-curve);stroke-width:2;")
          .append("stroke-linejoin:round;stroke-linecap:round}\n")
          .append(".pontif-plot .asymptote{stroke:var(--pontif-asymptote);stroke-width:1.5;")
          .append("stroke-dasharray:4 3;opacity:0.7}\n")
          .append(".pontif-plot .asymptote-label{fill:var(--pontif-asymptote);font-size:13px;")
          .append("text-anchor:middle}\n")
          .append(".pontif-plot .feature-mark{fill:none;stroke:var(--pontif-feature);stroke-width:2}\n")
          .append(".pontif-plot .feature-label{fill:var(--pontif-fg);font-size:12px}\n")
          .append(".pontif-plot .label{paint-order:stroke;stroke:#0a0c10;stroke-width:2.5px;")
          .append("stroke-linejoin:round}\n")
          // The reliable enclosure band ships hidden — a host reveals it with `.enclosure-band{display:revert}`.
          .append(".pontif-plot .enclosure-band{fill:var(--pontif-enclosure);opacity:0.16;")
          .append("stroke:none;display:none}\n")
          .append("</style>\n");
    }

    /** A clip path so curves/enclosure never spill past the plot rect. */
    private void defs() {
        sb.append("<defs><clipPath id=\"pontif-plot-area\"><rect x=\"").append(num(left))
          .append("\" y=\"").append(num(top)).append("\" width=\"").append(num(plotW))
          .append("\" height=\"").append(num(plotH)).append("\"/></clipPath></defs>\n");
    }

    private void axes() {
        Ticks.TickSet xt = Ticks.forAxis(scene.frame().x(), TARGET_TICKS);
        Ticks.TickSet yt = Ticks.forAxis(scene.frame().y(), TARGET_TICKS);
        sb.append("<g class=\"axes\">\n");
        for (double v : xt.values()) {
            double x = px(v);
            line("gridline", x, top, x, top + plotH, null);
        }
        for (double v : yt.values()) {
            double y = py(v);
            line("gridline", left, y, left + plotW, y, null);
        }
        sb.append("<rect class=\"frame\" x=\"").append(num(left)).append("\" y=\"").append(num(top))
          .append("\" width=\"").append(num(plotW)).append("\" height=\"").append(num(plotH)).append("\"/>\n");
        for (int i = 0; i < xt.count(); i++) {
            text("tick-label tick-x", px(xt.values()[i]), top + plotH + 18, xt.labels()[i], null);
        }
        for (int i = 0; i < yt.count(); i++) {
            text("tick-label tick-y", left - 8, py(yt.values()[i]), yt.labels()[i], null);
        }
        sb.append("</g>\n");
    }

    private void enclosure() {
        List<PlotScene2D.EnclosureBand> bands = scene.enclosures();
        if (bands.isEmpty()) return;
        boolean any = false;
        StringBuilder g = new StringBuilder("<g class=\"enclosure\" clip-path=\"url(#pontif-plot-area)\">");
        for (int bi = 0; bi < bands.size(); bi++) {
            PlotScene2D.EnclosureBand b = bands.get(bi);
            if (b == null || b.columnCount() < 2) continue;
            any = true;
            StringBuilder d = new StringBuilder("M");
            for (int i = 0; i < b.columnCount(); i++) {          // forward along the upper bound
                d.append(i == 0 ? "" : "L").append(num(px(b.xs()[i]))).append(',').append(num(py(b.hiYs()[i]))).append(' ');
            }
            for (int i = b.columnCount() - 1; i >= 0; i--) {     // back along the lower bound → closed area
                d.append('L').append(num(px(b.xs()[i]))).append(',').append(num(py(b.loYs()[i]))).append(' ');
            }
            d.append('Z');
            // One path per reliably-plotted expression; data-band indexes it so a host can target each.
            g.append("<path class=\"enclosure-band\" data-band=\"").append(bi).append("\" d=\"")
             .append(d).append("\"/>");
        }
        if (any) sb.append(g).append("</g>\n");
    }

    private void curves() {
        sb.append("<g class=\"curves\" clip-path=\"url(#pontif-plot-area)\">\n");
        for (Series c : scene.curves()) {
            if (c.pointCount() < 2) continue;
            double[] xs = c.xs(), ys = c.ys();
            StringBuilder d = new StringBuilder("M");
            for (int i = 0; i < xs.length; i++) {
                d.append(i == 0 ? "" : "L").append(num(px(xs[i]))).append(',').append(num(py(ys[i]))).append(' ');
            }
            sb.append("<path class=\"curve\" d=\"").append(d.toString().trim()).append("\"/>\n");
        }
        sb.append("</g>\n");
    }

    private void asymptotes() {
        if (scene.asymptotes().isEmpty()) return;
        sb.append("<g class=\"asymptotes\">\n");
        for (PlotScene2D.Asymptote a : scene.asymptotes()) {
            double x = px(a.x());
            String data = " data-x=\"" + num(a.x()) + "\"";
            line("asymptote", x, top, x, top + plotH, data);
            text("label asymptote-label", x, top + 12, a.label(), data);
        }
        sb.append("</g>\n");
    }

    private void features() {
        if (scene.features().isEmpty()) return;
        sb.append("<g class=\"features\">\n");
        for (PlotScene2D.Feature f : scene.features()) {
            double x = px(f.x()), y = py(f.y());
            String cls = "feature " + f.kind().name().toLowerCase(Locale.ROOT);
            String data = " data-x=\"" + num(f.x()) + "\" data-y=\"" + num(f.y())
                        + "\" data-kind=\"" + f.kind().name().toLowerCase(Locale.ROOT) + "\"";
            sb.append("<g class=\"").append(cls).append("\"").append(data).append(">")
              .append("<circle class=\"feature-mark\" cx=\"").append(num(x)).append("\" cy=\"")
              .append(num(y)).append("\" r=\"4\"/>");
            text("label feature-label", x + 7, y - 7, f.label(), null);
            sb.append("</g>\n");
        }
        sb.append("</g>\n");
    }

    // --- primitives ------------------------------------------------------------------------------

    private void line(String cls, double x1, double y1, double x2, double y2, String extra) {
        sb.append("<line class=\"").append(cls).append("\"")
          .append(extra == null ? "" : extra)
          .append(" x1=\"").append(num(x1)).append("\" y1=\"").append(num(y1))
          .append("\" x2=\"").append(num(x2)).append("\" y2=\"").append(num(y2)).append("\"/>\n");
    }

    private void text(String cls, double x, double y, String content, String extra) {
        sb.append("<text class=\"").append(cls).append("\"")
          .append(extra == null ? "" : extra)
          .append(" x=\"").append(num(x)).append("\" y=\"").append(num(y)).append("\">")
          .append(esc(content)).append("</text>\n");
    }

    // --- coordinate mapping + formatting ---------------------------------------------------------

    /** Data-x → pixel-x (via the axis unit mapping, so LOG axes map correctly). */
    private double px(double dataX) { return left + scene.frame().x().dataToUnit(dataX) * plotW; }

    /** Data-y → pixel-y, flipped (SVG y grows downward). */
    private double py(double dataY) { return top + (1.0 - scene.frame().y().dataToUnit(dataY)) * plotH; }

    /** A compact, locale-independent number: up to 2 decimals for pixels / 4 for data, zeros trimmed. */
    private static String num(double v) {
        if (!Double.isFinite(v)) return "0";
        String s = String.format(Locale.ROOT, "%.4f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s.isEmpty() || s.equals("-0") ? "0" : s;
    }

    /** XML-escape label text (labels are numeric but may carry '-', '(', ')', ','; be safe). */
    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
