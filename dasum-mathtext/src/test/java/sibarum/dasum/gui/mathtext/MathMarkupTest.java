package sibarum.dasum.gui.mathtext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The ASCII markup → MathBox parser: grouping, scripts, fractions, inline symbols, words, errors. */
class MathMarkupTest {

    /** A canonical, fully-explicit rendering of a MathBox tree — makes structure assertions exact.
     *  Runs show their role initial so symbol/role choices are visible (V/N/O/R/F/P/S). */
    private static String s(MathBox b) {
        return switch (b) {
            case MathBox.Run r -> role(r.role()) + "'" + r.text() + "'";
            case MathBox.Row row -> {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < row.items().size(); i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(s(row.items().get(i)));
                }
                yield sb.append(']').toString();
            }
            case MathBox.Fraction f -> "frac(" + s(f.numerator()) + ", " + s(f.denominator()) + ")";
            case MathBox.Script sc -> "script(" + s(sc.base()) + ", sup="
                    + (sc.superscript() == null ? "_" : s(sc.superscript())) + ", sub="
                    + (sc.subscript() == null ? "_" : s(sc.subscript())) + ")";
            case MathBox.Radical rad -> "sqrt(" + s(rad.radicand())
                    + (rad.index() == null ? "" : ", idx=" + s(rad.index())) + ")";
            case MathBox.Fenced fe -> "fence" + fe.open() + fe.close() + "(" + s(fe.content()) + ")";
            case MathBox.Matrix mx -> {
                StringBuilder sb = new StringBuilder("matrix[");
                for (int r = 0; r < mx.rows().size(); r++) {
                    if (r > 0) sb.append("; ");
                    for (int cix = 0; cix < mx.rows().get(r).size(); cix++) {
                        if (cix > 0) sb.append(", ");
                        sb.append(s(mx.rows().get(r).get(cix)));
                    }
                }
                yield sb.append(']').toString();
            }
            case MathBox.UnderOver uo -> "underover(" + s(uo.base()) + ", over="
                    + (uo.over() == null ? "_" : s(uo.over())) + ", under="
                    + (uo.under() == null ? "_" : s(uo.under())) + ")";
            case MathBox.Cases cs -> {
                StringBuilder sb = new StringBuilder("cases[");
                for (int r = 0; r < cs.rows().size(); r++) {
                    if (r > 0) sb.append("; ");
                    sb.append(s(cs.rows().get(r)));
                }
                yield sb.append(']').toString();
            }
            case MathBox.Prescript pr -> "prescript(" + s(pr.base()) + ", sup="
                    + (pr.superscript() == null ? "_" : s(pr.superscript())) + ", sub="
                    + (pr.subscript() == null ? "_" : s(pr.subscript())) + ")";
        };
    }

    private static char role(MathBox.Role r) {
        return switch (r) {
            case VARIABLE -> 'V'; case NUMBER -> 'N'; case OPERATOR -> 'O';
            case RELATION -> 'R'; case FUNCTION -> 'F'; case PUNCT -> 'P'; case SYMBOL -> 'S';
        };
    }

    private static String p(String in) { return s(MathMarkup.parse(in)); }

    @Test
    void atoms_variablesNumbersImplicitMultiplication() {
        assertEquals("V'x'", p("x"));
        assertEquals("N'42'", p("42"));
        assertEquals("[N'2' V'x']", p("2x"), "juxtaposition is implicit multiplication");
        assertEquals("[V'x' V'y']", p("x y"), "each letter its own variable");
        assertEquals("[V'x' V'y']", p("xy"), "no space needed");
    }

    @Test
    void scripts_superSubAndBoth() {
        assertEquals("script(V'x', sup=N'2', sub=_)", p("x^2"));
        assertEquals("script(V'a', sup=_, sub=V'i')", p("a_i"));
        assertEquals("script(V'x', sup=V'a', sub=V'b')", p("x^a_b"), "aligned super+subscript");
        assertEquals("script(V'x', sup=V'b', sub=V'a')", p("x_a^b"), "either order");
    }

    @Test
    void fraction_andGrouping() {
        assertEquals("frac(V'a', V'b')", p("a/b"));
        // '/' binds the single intermediate on each side (ASCIIMath), so 2x/(x+1) is 2·(x/(x+1)).
        assertEquals("[N'2' frac(V'x', [V'x' O'+' N'1'])]", p("2x/(x+1)"));
        // The whole-numerator idiom: wrap it in parens, which the fraction unwraps (no drawn parens).
        assertEquals("frac([N'2' V'x'], [V'x' O'+' N'1'])", p("(2x)/(x+1)"));
    }

    @Test
    void invisibleBraces_groupWithoutDrawing() {
        assertEquals("script(V'x', sup=[N'2' V'y'], sub=_)", p("x^{2y}"),
                "braces group the exponent but are not drawn");
        assertEquals("fence()([V'a' O'+' V'b'])", p("(a+b)"), "parens ARE drawn");
    }

    @Test
    void inlineSymbols_operatorsRelationsArrows() {
        assertEquals("[V'a' O'+' V'b']", p("a+b"));
        assertEquals("[V'a' O'−' V'b']", p("a-b"), "minus sign, not hyphen");
        assertEquals("[V'a' O'⋅' V'b']", p("a*b"), "dot product");
        assertEquals("[V'a' O'×' V'b']", p("a><b"), "cross product");
        assertEquals("[V'a' O'⊕' V'b']", p("a(+)b"), "circled plus");
        assertEquals("[V'a' O'⊗' V'b']", p("a(*)b"), "circled times");
        assertEquals("[V'a' R'≤' V'b']", p("a<=b"));
        assertEquals("[V'a' R'≠' V'b']", p("a!=b"));
        assertEquals("[V'a' R'≈' V'b']", p("a~=b"));
        assertEquals("[V'x' R'→' V'y']", p("x-->y"));
        assertEquals("[V'x' R'⇒' V'y']", p("x==>y"));
        assertEquals("[V'a' O'/' V'b']", p("a//b"), "// is a literal slash, distinct from a/b fraction");
    }

    @Test
    void sign_leadingMinusAndPlusMinus() {
        assertEquals("[O'−' V'x']", p("-x"));
        assertEquals("[O'±' V'x']", p("+-x"));
    }

    @Test
    void words_greekFunctionsDegree() {
        assertEquals("S'π'", p("pi"));
        assertEquals("S'θ'", p("theta"));
        assertEquals("S'Ω'", p("Omega"), "capitalised name → uppercase Greek");
        assertEquals("S'∞'", p("infinity"));
        assertEquals("[S'π' V'r']", p("pir"), "longest-keyword then fall back to a variable");
        assertEquals("[F'sin' V'x']", p("sin x"), "function name is upright");
        assertEquals("[N'90' S'°' V'C']", p("90 degrees C"), "degree symbol");
    }

    @Test
    void radicalAndAbs_unaryConstructs() {
        assertEquals("sqrt(V'x')", p("sqrt x"));
        assertEquals("sqrt([V'x' O'+' N'1'])", p("sqrt(x+1)"), "parenthesised arg unwrapped");
        assertEquals("fence||(V'x')", p("|x|"));
        assertEquals("fence||(V'x')", p("abs(x)"), "abs(x) is |x|, not |(x)|");
    }

    @Test
    void verbatimText_upright() {
        assertEquals("F'hello world'", p("'hello world'"));
        assertEquals("F'x=y'", p("\"x=y\""), "double quotes, symbols verbatim");
    }

    @Test
    void matrix_andVector() {
        assertEquals("matrix[V'a', V'b'; V'c', V'd']", p("[[a,b],[c,d]]"));
        assertEquals("matrix[V'a'; V'b']", p("[[a],[b]]"), "one column is a column vector");
        // A flat bracket list is a bracketed vector, drawn with comma punctuation (no matrix grid).
        assertEquals("fence[]([V'a' P',' V'b'])", p("[a,b]"));
    }

    @Test
    void branches_andPrescript() {
        assertEquals("cases[[R'→' V'a']; [R'→' V'b']]", p("{->a,->b}"));
        assertEquals("prescript(V'C', sup=N'14', sub=N'6')", p("{^14_6}C"), "prescripts left of base");
        assertEquals("script(V'x', sup=[N'2' V'y'], sub=_)", p("x^{2y}"), "single-content braces still group");
    }

    @Test
    void callAliases_sumIntegralLimRoot() {
        assertEquals("[underover(O'∑', over=V'n', under=[V'k' R'=' N'1']) V'k']", p("sum(k=1,n,k)"));
        assertEquals("[script(O'∫', sup=V'b', sub=V'a') V'f']", p("integral(a,b,f)"), "integral side limits");
        assertEquals("[underover(F'lim', over=_, under=[V'x' R'→' N'0']) V'f']", p("lim(x->0,f)"));
        assertEquals("sqrt(V'x', idx=N'3')", p("root(3,x)"), "cube root");
        assertEquals("S'∑'", p("sum"), "bare sum is the operator glyph");
    }

    @Test
    void malformed_throws() {
        for (String bad : new String[]{"", "(x", "x)", "|x", "x^", "a/", "{x", "@"}) {
            assertThrows(MathMarkup.MarkupError.class, () -> MathMarkup.parse(bad),
                    "should reject: '" + bad + "'");
        }
    }

    @Test
    void parseOr_returnsFallbackOnError() {
        MathBox fb = MathBox.var("f");
        assertEquals(fb, MathMarkup.parseOr("(x", fb));
        assertEquals("V'x'", s(MathMarkup.parseOr("x", fb)));
    }
}
