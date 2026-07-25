package sibarum.dasum.gui.mathtext;

import sibarum.dasum.gui.core.render.Rect;
import sibarum.dasum.gui.core.text.AtlasData;
import sibarum.dasum.gui.core.text.GlyphData;
import sibarum.dasum.gui.mathtext.LaidOut.Draw;
import sibarum.dasum.gui.mathtext.LaidOut.GlyphRun;
import sibarum.dasum.gui.mathtext.LaidOut.Rule;
import sibarum.dasum.gui.mathtext.MathBox.Role;

import java.util.ArrayList;
import java.util.List;

/**
 * The math typesetter's brain: walks a {@link MathBox} and produces a {@link LaidOut} draw-list in
 * root-em coordinates, using glyph advances/bounds from an {@link AtlasData} and the offsets in a
 * {@link MathConstants}. This is the ONLY place formatting lives — {@link MathOgl} and {@link MathSvg}
 * are dumb tree-walks over the result — so both backends render identically.
 *
 * <p>Coordinate convention matches {@link LaidOut}: x right, baseline at y=0, y growing DOWN (so a
 * superscript sits at negative y). Scripts/roots/fences carry their own sub-em {@code scale}.
 */
public final class MathLayout {

    private final AtlasData atlas;
    private final MathConstants c;

    public MathLayout(AtlasData atlas, MathConstants constants) {
        this.atlas = atlas;
        this.c = constants;
    }

    /** Lay out {@code box} at the root size (1 em). */
    public LaidOut layout(MathBox box) {
        return lay(box, 1.0);
    }

    private LaidOut lay(MathBox box, double scale) {
        return switch (box) {
            case MathBox.Run r -> run(r, scale);
            case MathBox.Row r -> row(r, scale);
            case MathBox.Fraction f -> fraction(f, scale);
            case MathBox.Script s -> script(s, scale);
            case MathBox.Radical r -> radical(r, scale);
            case MathBox.Fenced f -> fenced(f, scale);
            case MathBox.Matrix m -> matrix(m, scale);
            case MathBox.UnderOver u -> underOver(u, scale);
            case MathBox.Cases c -> cases(c, scale);
            case MathBox.Prescript p -> prescript(p, scale);
        };
    }

    // --- Run --------------------------------------------------------------------------------------

    private LaidOut run(MathBox.Run r, double scale) {
        String glyphs = MathGlyphs.resolve(r.text(), r.role());
        double width = 0, top = 0, bottom = 0;
        boolean any = false;
        for (int i = 0; i < glyphs.length(); ) {
            int cp = glyphs.codePointAt(i);
            i += Character.charCount(cp);
            GlyphData g = atlas.glyph(cp);
            if (g == null) { width += 0.5; top = Math.max(top, 0.7); any = true; continue; }
            width += g.advance();
            Rect pb = g.planeBounds();
            if (pb != null) { top = Math.max(top, pb.top()); bottom = Math.min(bottom, pb.bottom()); }
            any = true;
        }
        if (!any) top = 0.7;
        List<Draw> draws = List.of(new GlyphRun(glyphs, 0, 0, scale));
        return new LaidOut(width * scale, top * scale, -bottom * scale, draws);
    }

    // --- Row --------------------------------------------------------------------------------------

    private LaidOut row(MathBox.Row r, double scale) {
        List<Draw> out = new ArrayList<>();
        double x = 0, ascent = 0, descent = 0;
        MathBox prev = null;
        for (MathBox item : r.items()) {
            x += Math.max(edge(prev, false), edge(item, true)) * scale;   // inter-atom gap
            LaidOut b = lay(item, scale);
            out.addAll(shift(b.draws(), x, 0));
            x += b.width();
            ascent = Math.max(ascent, b.ascent());
            descent = Math.max(descent, b.descent());
            prev = item;
        }
        return new LaidOut(x, ascent, descent, out);
    }

    /** Space (em, unscaled) contributed by a box's edge — leading (before) or trailing (after) — from
     *  the role of a {@link MathBox.Run}; non-runs contribute none. */
    private double edge(MathBox box, boolean leading) {
        if (!(box instanceof MathBox.Run run)) return 0;
        return switch (run.role()) {
            case OPERATOR -> c.spaceBinaryOp();
            case RELATION -> c.spaceRelation();
            case FUNCTION -> leading ? 0 : c.functionGap();
            case PUNCT    -> leading ? 0 : c.spacePunct();
            default -> 0;
        };
    }

    // --- Fraction ---------------------------------------------------------------------------------

    private LaidOut fraction(MathBox.Fraction f, double scale) {
        // TeX rule: a fraction's numerator/denominator sit one style smaller than the fraction. We
        // apply it only when the fraction is ALREADY in script style (scale ≤ scriptScale) — so a
        // superscript fraction like x^(a/b) shrinks its parts to ~half (scriptScale²), matching the
        // eye's expectation, while a full-size top-level fraction keeps its parts full-size.
        double childScale = scale <= c.scriptScale() + 1e-6 ? scale * c.scriptScale() : scale;
        LaidOut n = lay(f.numerator(), childScale), d = lay(f.denominator(), childScale);
        double rt = c.fractionRuleThickness() * scale;
        double axis = c.axisHeight() * scale;
        double barTop = -axis - rt / 2, barBot = -axis + rt / 2;
        double gapN = c.fractionGapNum() * scale, gapD = c.fractionGapDen() * scale;
        double pad = 0.1 * scale;
        double w = Math.max(n.width(), d.width());
        double total = w + 2 * pad;

        double nBaseY = barTop - gapN - n.descent();          // num above the bar
        double dBaseY = barBot + gapD + d.ascent();           // den below the bar
        double nx = pad + (w - n.width()) / 2, dx = pad + (w - d.width()) / 2;

        List<Draw> out = new ArrayList<>();
        out.addAll(shift(n.draws(), nx, nBaseY));
        out.addAll(shift(d.draws(), dx, dBaseY));
        out.add(new Rule(0, barTop, total, rt));
        double ascent = n.ascent() - nBaseY;                  // nBaseY is negative (above)
        double descent = dBaseY + d.descent();
        return new LaidOut(total, ascent, descent, out);
    }

    // --- Script (sup / sub) -----------------------------------------------------------------------

    private LaidOut script(MathBox.Script s, double scale) {
        LaidOut base = lay(s.base(), scale);
        List<Draw> out = new ArrayList<>(base.draws());
        double ascent = base.ascent(), descent = base.descent();
        double sideW = 0;
        double sScale = scale * c.scriptScale();
        if (s.superscript() != null) {
            LaidOut sup = lay(s.superscript(), sScale);
            double y = -c.superscriptShiftUp() * scale;
            out.addAll(shift(sup.draws(), base.width(), y));
            ascent = Math.max(ascent, sup.ascent() - y);
            descent = Math.max(descent, y + sup.descent());
            sideW = Math.max(sideW, sup.width());
        }
        if (s.subscript() != null) {
            LaidOut sub = lay(s.subscript(), sScale);
            double y = c.subscriptShiftDown() * scale;
            out.addAll(shift(sub.draws(), base.width(), y));
            ascent = Math.max(ascent, sub.ascent() - y);
            descent = Math.max(descent, y + sub.descent());
            sideW = Math.max(sideW, sub.width());
        }
        return new LaidOut(base.width() + sideW + c.scriptGapAfter() * scale, ascent, descent, out);
    }

    // --- Radical ----------------------------------------------------------------------------------

    private LaidOut radical(MathBox.Radical r, double scale) {
        LaidOut rad = lay(r.radicand(), scale);
        double rt = c.radicalRuleThickness() * scale;
        double gapA = c.radicalGapAbove() * scale;
        double kernB = c.radicalKernBefore() * scale, kernA = c.radicalKernAfter() * scale;

        GlyphData surd = atlas.glyph(0x221A);                 // √
        double surdNat = surd != null && surd.planeBounds() != null
                ? surd.planeBounds().top() - surd.planeBounds().bottom() : 1.0;
        double targetH = rad.ascent() + rad.descent() + gapA + rt;
        double surdScale = surdNat > 0 ? targetH / surdNat : scale;
        double surdW = (surd != null ? surd.advance() : 0.6) * surdScale;

        double radX = kernB + surdW + kernA;
        double vinY = -(rad.ascent() + gapA) - rt;

        List<Draw> out = new ArrayList<>();
        double ascent = rad.ascent() + gapA + rt;
        double leadX = 0;

        // The degree index (e.g. the 3 of a cube root) sits small at the upper-left, tucked into the
        // surd's kick. It shifts the whole radical right by its width where it overhangs the surd stem.
        if (r.index() != null) {
            LaidOut idx = lay(r.index(), scale * c.scriptScale() * c.scriptScale());
            double idxY = -(ascent - idx.descent()) + idx.ascent() * 0.5;   // high on the surd's left arm
            double overhang = Math.max(0, idx.width() - surdW * 0.5);
            leadX = overhang;
            out.addAll(shift(idx.draws(), 0, idxY));
        }

        // Surd baseline placed so the glyph descends to the radicand's bottom.
        out.add(new GlyphRun("√", leadX + kernB, rad.descent(), surdScale));
        out.addAll(shift(rad.draws(), leadX + radX, 0));
        out.add(new Rule(leadX + radX, vinY, rad.width(), rt));
        return new LaidOut(leadX + radX + rad.width(), ascent, rad.descent(), out);
    }

    // --- Fenced (growable delimiters) -------------------------------------------------------------

    private LaidOut fenced(MathBox.Fenced f, double scale) {
        LaidOut inner = lay(f.content(), scale);
        double pad = c.delimiterPad() * scale;
        double h = inner.ascent() + inner.descent();

        double dScale = delimScale(f.open(), h, scale);
        double openW = advance(f.open()) * dScale;
        double closeW = advance(f.close()) * dScale;
        // Center the delimiters on the math axis so they straddle the content symmetrically.
        double baseY = (inner.descent() - inner.ascent()) / 2 + c.axisHeight() * scale;

        List<Draw> out = new ArrayList<>();
        out.add(new GlyphRun(f.open(), 0, baseY, dScale));
        out.addAll(shift(inner.draws(), openW + pad, 0));
        out.add(new GlyphRun(f.close(), openW + pad + inner.width() + pad, baseY, dScale));
        double ascent = Math.max(inner.ascent(), h / 2 + c.axisHeight() * scale);
        double descent = Math.max(inner.descent(), h / 2 - c.axisHeight() * scale);
        double width = openW + pad + inner.width() + pad + closeW;
        return new LaidOut(width, ascent, descent, out);
    }

    // --- Matrix (grid of cells in growable delimiters) -------------------------------------------

    private LaidOut matrix(MathBox.Matrix m, double scale) {
        List<List<MathBox>> rows = m.rows();
        int nRows = rows.size();
        int nCols = 0;
        for (List<MathBox> r : rows) nCols = Math.max(nCols, r.size());

        LaidOut[][] cell = new LaidOut[nRows][nCols];
        double[] colW = new double[nCols];
        double[] rowAsc = new double[nRows], rowDesc = new double[nRows];
        for (int r = 0; r < nRows; r++) {
            for (int cix = 0; cix < rows.get(r).size(); cix++) {
                LaidOut b = lay(rows.get(r).get(cix), scale);
                cell[r][cix] = b;
                colW[cix] = Math.max(colW[cix], b.width());
                rowAsc[r] = Math.max(rowAsc[r], b.ascent());
                rowDesc[r] = Math.max(rowDesc[r], b.descent());
            }
        }
        double colGap = 0.6 * scale, rowGap = 0.35 * scale, pad = 0.15 * scale;

        double innerW = colGap * Math.max(0, nCols - 1);
        for (double w : colW) innerW += w;
        double gridH = rowGap * Math.max(0, nRows - 1);
        for (int r = 0; r < nRows; r++) gridH += rowAsc[r] + rowDesc[r];

        double axis = c.axisHeight() * scale;
        double topY = -gridH / 2 - axis;                 // grid's vertical middle on the math axis

        List<Draw> body = new ArrayList<>();
        double y = topY;
        for (int r = 0; r < nRows; r++) {
            double baseY = y + rowAsc[r], x = 0;
            for (int cix = 0; cix < nCols; cix++) {
                LaidOut b = cell[r][cix];
                if (b != null) body.addAll(shift(b.draws(), x + (colW[cix] - b.width()) / 2, baseY));
                x += colW[cix] + colGap;
            }
            y += rowAsc[r] + rowDesc[r] + rowGap;
        }
        double gridAscent = -topY, gridDescent = topY + gridH;
        return wrapInDelimiters(m.open(), m.close(), body, innerW, gridAscent, gridDescent, pad, scale);
    }

    /** Place {@code body} (already positioned around baseline 0) inside a growable delimiter pair,
     *  mirroring {@link #fenced}. Shared by {@link #matrix} and {@link #cases} (close = "" → no right
     *  delimiter). */
    private LaidOut wrapInDelimiters(String open, String close, List<Draw> body, double innerW,
                                     double ascent, double descent, double pad, double scale) {
        double h = ascent + descent;
        double dScale = delimScale(open, h, scale);
        double openW = advance(open) * dScale;
        double baseY = (descent - ascent) / 2 + c.axisHeight() * scale;
        List<Draw> out = new ArrayList<>();
        out.add(new GlyphRun(open, 0, baseY, dScale));
        out.addAll(shift(body, openW + pad, 0));
        double width = openW + pad + innerW;
        if (!close.isEmpty()) {
            out.add(new GlyphRun(close, openW + pad + innerW + pad, baseY, delimScale(close, h, scale)));
            width += pad + advance(close) * dScale;
        }
        return new LaidOut(width, ascent, descent, out);
    }

    // --- UnderOver (a base carrying limits above / below) ----------------------------------------

    private LaidOut underOver(MathBox.UnderOver u, double scale) {
        LaidOut base = lay(u.base(), scale);
        double sScale = scale * c.scriptScale();
        LaidOut over = u.over() == null ? null : lay(u.over(), sScale);
        LaidOut under = u.under() == null ? null : lay(u.under(), sScale);
        double gap = 0.15 * scale;
        double w = base.width();
        if (over != null) w = Math.max(w, over.width());
        if (under != null) w = Math.max(w, under.width());

        List<Draw> out = new ArrayList<>(shift(base.draws(), (w - base.width()) / 2, 0));
        double ascent = base.ascent(), descent = base.descent();
        if (over != null) {
            double oy = -base.ascent() - gap - over.descent();
            out.addAll(shift(over.draws(), (w - over.width()) / 2, oy));
            ascent = Math.max(ascent, over.ascent() - oy);
        }
        if (under != null) {
            double uy = base.descent() + gap + under.ascent();
            out.addAll(shift(under.draws(), (w - under.width()) / 2, uy));
            descent = Math.max(descent, uy + under.descent());
        }
        return new LaidOut(w, ascent, descent, out);
    }

    // --- Cases (rows under a tall left brace) ----------------------------------------------------

    private LaidOut cases(MathBox.Cases cs, double scale) {
        double rowGap = 0.35 * scale, pad = 0.15 * scale;
        List<LaidOut> laid = new ArrayList<>();
        double innerW = 0, gridH = rowGap * Math.max(0, cs.rows().size() - 1);
        for (MathBox row : cs.rows()) {
            LaidOut b = lay(row, scale);
            laid.add(b);
            innerW = Math.max(innerW, b.width());
            gridH += b.ascent() + b.descent();
        }
        double axis = c.axisHeight() * scale;
        double topY = -gridH / 2 - axis;

        List<Draw> body = new ArrayList<>();
        double y = topY;
        for (LaidOut b : laid) {                          // rows left-aligned (unlike matrix's centering)
            body.addAll(shift(b.draws(), 0, y + b.ascent()));
            y += b.ascent() + b.descent() + rowGap;
        }
        return wrapInDelimiters("{", "", body, innerW, -topY, topY + gridH, pad, scale);
    }

    // --- Prescript (scripts to the LEFT of the base) ---------------------------------------------

    private LaidOut prescript(MathBox.Prescript p, double scale) {
        LaidOut base = lay(p.base(), scale);
        double sScale = scale * c.scriptScale();
        LaidOut sup = p.superscript() == null ? null : lay(p.superscript(), sScale);
        LaidOut sub = p.subscript() == null ? null : lay(p.subscript(), sScale);
        double preW = 0;
        if (sup != null) preW = Math.max(preW, sup.width());
        if (sub != null) preW = Math.max(preW, sub.width());

        List<Draw> out = new ArrayList<>();
        double ascent = base.ascent(), descent = base.descent();
        if (sup != null) {                               // right-align the prescripts against the base
            double y = -c.superscriptShiftUp() * scale;
            out.addAll(shift(sup.draws(), preW - sup.width(), y));
            ascent = Math.max(ascent, sup.ascent() - y);
            descent = Math.max(descent, y + sup.descent());
        }
        if (sub != null) {
            double y = c.subscriptShiftDown() * scale;
            out.addAll(shift(sub.draws(), preW - sub.width(), y));
            ascent = Math.max(ascent, sub.ascent() - y);
            descent = Math.max(descent, y + sub.descent());
        }
        out.addAll(shift(base.draws(), preW, 0));
        return new LaidOut(preW + base.width(), ascent, descent, out);
    }

    private double delimScale(String glyph, double contentHeight, double scale) {
        GlyphData g = atlas.glyph(glyph.codePointAt(0));
        double nat = g != null && g.planeBounds() != null
                ? g.planeBounds().top() - g.planeBounds().bottom() : 1.0;
        return Math.max(scale, nat > 0 ? contentHeight / nat : scale);
    }

    private double advance(String glyph) {
        GlyphData g = atlas.glyph(glyph.codePointAt(0));
        return g != null ? g.advance() : 0.5;
    }

    // --- shared -----------------------------------------------------------------------------------

    /** Translate a draw list by {@code (dx, dy)} — how a parent places a laid-out child. */
    private static List<Draw> shift(List<Draw> draws, double dx, double dy) {
        List<Draw> out = new ArrayList<>(draws.size());
        for (Draw d : draws) {
            switch (d) {
                case GlyphRun g -> out.add(new GlyphRun(g.glyphs(), g.x() + dx, g.y() + dy, g.size()));
                case Rule r -> out.add(new Rule(r.x() + dx, r.y() + dy, r.width(), r.height()));
            }
        }
        return out;
    }
}
