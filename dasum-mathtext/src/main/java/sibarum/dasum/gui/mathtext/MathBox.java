package sibarum.dasum.gui.mathtext;

import java.util.List;

/**
 * The <b>semantic</b> math-notation IR — what an expression <em>is</em>, not how it's drawn. A tree
 * of these is laid out once ({@link MathLayout}) and then tree-walked to any backend ({@link MathOgl}
 * on-screen, {@link MathSvg} for export); the formatting lives in the layout, so the backends only
 * place glyphs and rules. Designed first, "reverse language design": a front-end (an {@code AlgExpr}
 * bridge, later an ASCII parser) targets THIS, and gets both renderers for free.
 *
 * <p>The vocabulary is deliberately small — the common-notation set:
 * <ul>
 *   <li>{@link Run} — an atom/run of text with a semantic {@link Role} (italic variable, upright
 *       number/operator/function, symbol) that drives style, spacing, and glyph selection;</li>
 *   <li>{@link Row} — a horizontal sequence (role-aware inter-atom spacing);</li>
 *   <li>{@link Fraction} — numerator over denominator, centered on the math axis;</li>
 *   <li>{@link Script} — a base with an optional superscript and/or subscript;</li>
 *   <li>{@link Radical} — a radicand under a surd, with an optional degree index;</li>
 *   <li>{@link Fenced} — content wrapped in delimiters that grow to its height;</li>
 *   <li>{@link Matrix} — a grid of cells in growable delimiters (also a vector, a single column);</li>
 *   <li>{@link UnderOver} — a base with material centered above and/or below (big operators with
 *       limits: {@code sum}/{@code product}/{@code lim});</li>
 *   <li>{@link Cases} — rows stacked under a single tall left brace (branches / piecewise);</li>
 *   <li>{@link Prescript} — a super/subscript placed to the LEFT of its base (pre-scripts).</li>
 * </ul>
 */
public sealed interface MathBox
        permits MathBox.Run, MathBox.Row, MathBox.Fraction, MathBox.Script, MathBox.Radical,
                MathBox.Fenced, MathBox.Matrix, MathBox.UnderOver, MathBox.Cases, MathBox.Prescript {

    /** The semantic class of a {@link Run} — drives font style, inter-atom spacing, and how the
     *  text maps to glyphs (a VARIABLE letter becomes an italic math codepoint; the rest stay upright). */
    enum Role { VARIABLE, NUMBER, OPERATOR, RELATION, FUNCTION, PUNCT, SYMBOL }

    /** A run of text of one semantic role (e.g. the variable {@code x}, the number {@code 42},
     *  the operator {@code +}, the function {@code sin}). */
    record Run(String text, Role role) implements MathBox {}

    /** A horizontal sequence of boxes. */
    record Row(List<MathBox> items) implements MathBox {}

    /** {@code numerator / denominator} as a stacked fraction. */
    record Fraction(MathBox numerator, MathBox denominator) implements MathBox {}

    /** A base with an optional superscript and/or subscript ({@code null} where absent). */
    record Script(MathBox base, MathBox superscript, MathBox subscript) implements MathBox {}

    /** A radicand under a surd; {@code index} is the degree (e.g. 3 for a cube root) or {@code null}
     *  for a square root. */
    record Radical(MathBox radicand, MathBox index) implements MathBox {}

    /** {@code content} wrapped in a growable delimiter pair (e.g. {@code (} … {@code )}). */
    record Fenced(String open, String close, MathBox content) implements MathBox {}

    /** A grid of cells (row-major) inside a growable delimiter pair — a matrix, or, with one column,
     *  a column vector. Columns are aligned to their widest cell; rows center on the whole grid's axis. */
    record Matrix(List<List<MathBox>> rows, String open, String close) implements MathBox {}

    /** A {@code base} with material stacked directly above ({@code over}) and/or below ({@code under}),
     *  centered — a big operator carrying its limits ({@code ∑} with bounds, {@code lim} with {@code x→a}).
     *  Either script may be {@code null}. Distinct from {@link Script}, whose scripts sit to the side. */
    record UnderOver(MathBox base, MathBox over, MathBox under) implements MathBox {}

    /** Rows stacked vertically under a single tall left brace — piecewise definitions / branches. */
    record Cases(List<MathBox> rows) implements MathBox {}

    /** A super/subscript placed to the LEFT of the {@code base} (a pre-script, e.g. an isotope's mass
     *  number). Either script may be {@code null}. The right-hand counterpart is {@link Script}. */
    record Prescript(MathBox base, MathBox superscript, MathBox subscript) implements MathBox {}

    // --- terse factories for hand-built trees (the POC front-end) ------------------------------

    static MathBox var(String s)  { return new Run(s, Role.VARIABLE); }
    static MathBox num(String s)  { return new Run(s, Role.NUMBER); }
    static MathBox op(String s)   { return new Run(s, Role.OPERATOR); }
    static MathBox rel(String s)  { return new Run(s, Role.RELATION); }
    static MathBox fn(String s)   { return new Run(s, Role.FUNCTION); }
    static MathBox sym(String s)  { return new Run(s, Role.SYMBOL); }
    static MathBox row(MathBox... items) { return new Row(List.of(items)); }
    static MathBox frac(MathBox n, MathBox d) { return new Fraction(n, d); }
    static MathBox pow(MathBox base, MathBox sup) { return new Script(base, sup, null); }
    static MathBox idx(MathBox base, MathBox sub) { return new Script(base, null, sub); }
    static MathBox sqrt(MathBox radicand) { return new Radical(radicand, null); }
    static MathBox fenced(String open, String close, MathBox content) {
        return new Fenced(open, close, content);
    }
    static MathBox paren(MathBox content) { return new Fenced("(", ")", content); }
    static MathBox matrix(List<List<MathBox>> rows) { return new Matrix(rows, "[", "]"); }
    static MathBox underover(MathBox base, MathBox over, MathBox under) {
        return new UnderOver(base, over, under);
    }
    static MathBox cases(List<MathBox> rows) { return new Cases(rows); }
    static MathBox root(MathBox radicand, MathBox index) { return new Radical(radicand, index); }
}
