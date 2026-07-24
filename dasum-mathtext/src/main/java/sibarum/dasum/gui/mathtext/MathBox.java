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
 *   <li>{@link Fenced} — content wrapped in delimiters that grow to its height.</li>
 * </ul>
 */
public sealed interface MathBox
        permits MathBox.Run, MathBox.Row, MathBox.Fraction, MathBox.Script, MathBox.Radical,
                MathBox.Fenced {

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
}
