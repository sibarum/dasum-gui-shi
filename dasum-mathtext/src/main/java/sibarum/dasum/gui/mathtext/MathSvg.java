package sibarum.dasum.gui.mathtext;

import sibarum.dasum.gui.mathtext.LaidOut.GlyphRun;
import sibarum.dasum.gui.mathtext.LaidOut.Rule;

import java.util.Locale;

/**
 * SVG backend: a tree-walk of a {@link LaidOut} into a self-contained SVG string. Glyph runs become
 * {@code <text>}, rules (fraction bars, radical vinculums) become {@code <rect>}, both positioned by
 * the layout's em coordinates scaled to pixels. All the formatting already happened in
 * {@link MathLayout}; this just paints. Colors use {@code currentColor} so a host can theme it.
 *
 * <p>POC note: glyphs are placed by OUR advances but rendered with a {@code font-family} reference —
 * self-contained font embedding (base64 subset) is a later upgrade; the layout/geometry is the point.
 */
public final class MathSvg {

    private MathSvg() {}

    /** Render {@code m} to an SVG document string at {@code pxPerEm} pixels per em. */
    public static String write(LaidOut m, double pxPerEm) {
        double pad = 0.2 * pxPerEm;
        double w = m.width() * pxPerEm + 2 * pad;
        double h = (m.ascent() + m.descent()) * pxPerEm + 2 * pad;
        double baseline = m.ascent() * pxPerEm + pad;

        StringBuilder sb = new StringBuilder(1024);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"math\" viewBox=\"0 0 ")
          .append(num(w)).append(' ').append(num(h)).append("\">\n");
        sb.append("<style>.math{font-family:\"STIX Two Math\",\"math\",serif}")
          .append(".math-glyph{fill:currentColor}.math-rule{fill:currentColor}</style>\n");
        for (LaidOut.Draw d : m.draws()) {
            switch (d) {
                case GlyphRun g -> sb.append("<text class=\"math-glyph\" x=\"")
                        .append(num(pad + g.x() * pxPerEm)).append("\" y=\"")
                        .append(num(baseline + g.y() * pxPerEm)).append("\" font-size=\"")
                        .append(num(g.size() * pxPerEm)).append("\">")
                        .append(esc(g.glyphs())).append("</text>\n");
                case Rule r -> sb.append("<rect class=\"math-rule\" x=\"")
                        .append(num(pad + r.x() * pxPerEm)).append("\" y=\"")
                        .append(num(baseline + r.y() * pxPerEm)).append("\" width=\"")
                        .append(num(r.width() * pxPerEm)).append("\" height=\"")
                        .append(num(Math.max(1.0, r.height() * pxPerEm))).append("\"/>\n");
            }
        }
        sb.append("</svg>\n");
        return sb.toString();
    }

    private static String num(double v) {
        if (!Double.isFinite(v)) return "0";
        String s = String.format(Locale.ROOT, "%.3f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.isEmpty() || s.equals("-0") ? "0" : s;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
