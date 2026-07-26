package sibarum.dasum.gui.mathtext;

import sibarum.dasum.gui.mathtext.MathBox.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The ASCII math-markup front-end: a typed string → a {@link MathBox} tree, which then gets both
 * renderers ({@link MathOgl}, {@link MathSvg}) for free. This is the human-facing input language of
 * the "reverse language design" stack — the IR was designed first, and this targets it.
 *
 * <p>The grammar is deliberately ASCIIMath-shaped: <b>only</b> {@code /} (fraction), {@code _}
 * (subscript) and {@code ^} (superscript) carry grammar. Everything else — {@code + - // = < >}
 * {@code <= >= != ~= --> <-- ==> <== * >< (+) (*)} — is an ordinary <em>symbol run</em> laid out
 * inline in a row, so no operator precedence is needed and spacing falls out of each run's
 * {@link Role}. Juxtaposition is implicit multiplication ({@code 2x}, {@code a b}, {@code (a)(b)}).
 *
 * <p>Grammar (EBNF-ish):
 * <pre>
 *   expr   = (inter ('/' inter)?)*                 ; sequence; '/' between two intermediates = fraction
 *   inter  = simple (('_' simple)? ('^' simple)? | ('^' simple)? ('_' simple)?)   ; scripts bind to simple
 *   simple = number | word | symbol
 *          | '(' expr ')' | '[' expr ']'          ; visible fences
 *          | '{' expr '}'                          ; INVISIBLE grouping (braces not drawn)
 *          | '|' expr '|'                          ; absolute value
 *          | 'sqrt' arg | 'abs' arg                ; unary constructs
 *          | "'" text "'" | '"' text '"'           ; verbatim text
 *   arg    = simple, but a leading '(' … ')' / '{' … '}' group is UNWRAPPED (so sqrt(x+1) has no
 *            visible parens under the vinculum, and abs(x) is |x| not |(x)|)
 * </pre>
 *
 * <p>Recognised words: Greek letter names ({@code pi}, {@code theta}, … capitalised → uppercase
 * Greek), {@code infinity}; function names ({@code sin cos tan … lim det}) rendered upright; the
 * {@code degrees}/{@code degree} keyword → {@code °}. Any other run of letters is variables (italic),
 * one {@link Role#VARIABLE} run per letter so {@code xy} spaces as a product — which is why a Greek
 * name must be matched as a whole token before falling back to single letters.
 *
 * <p>Also handled, mapping onto the extended IR: matrices/vectors ({@code [[a,b],[c,d]]}, {@code
 * [a,b]}), branches ({@code &#123;->a,->b&#125;} → cases), prescripts ({@code &#123;^a&#125;b}), the
 * {@code root(index, radicand)} radical, and the call aliases {@code sum} / {@code product} /
 * {@code integral} / {@code lim} that carry limits (e.g. {@code sum(k=1, n, expr)}, {@code
 * lim(x->0, expr)}). See the call-alias builders for the exact argument conventions.
 */
public final class MathMarkup {

    private final String src;
    private int pos;

    private MathMarkup(String src) { this.src = src; }

    /** Parse {@code s} to a {@link MathBox}; throws {@link MarkupError} on malformed input. */
    public static MathBox parse(String s) {
        MathMarkup p = new MathMarkup(s == null ? "" : s);
        MathBox box = p.expr("");                         // stops only at end of input
        p.skipWs();
        if (p.pos != p.src.length()) throw new MarkupError("unexpected '" + p.src.charAt(p.pos) + "'", p.pos);
        return box;
    }

    /** Parse {@code s}, or return {@code fallback} on any error — for a live input field. */
    public static MathBox parseOr(String s, MathBox fallback) {
        try { return parse(s); } catch (MarkupError e) { return fallback; }
    }

    /** A malformed-markup error, carrying the source offset for diagnostics. */
    public static final class MarkupError extends RuntimeException {
        public final int at;
        MarkupError(String message, int at) { super(message + " at " + at); this.at = at; }
    }

    private static final char END_OF_INPUT = '\0';

    // --- grammar ---------------------------------------------------------------------------------

    /** A sequence of intermediates up to (but not consuming) any char in {@code stops}; {@code '/'}
     *  between two makes a {@link MathBox.Fraction}. Returns a single box when there's exactly one
     *  item. A {@code ','} in {@code stops} makes commas SEPARATORS (for cell/argument lists);
     *  otherwise a comma is an ordinary punctuation run. */
    private MathBox expr(String stops) {
        List<MathBox> items = new ArrayList<>();
        while (true) {
            skipWs();
            if (pos >= src.length() || stops.indexOf(peekRaw()) >= 0) break;
            MathBox i = inter();
            skipWs();
            // A single '/' is a fraction; '//' is the literal-slash operator (handled as a symbol in
            // the next loop iteration), so don't mistake its first char for a fraction bar.
            boolean isDoubleSlash = peekRaw() == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/';
            if (peekRaw() == '/' && !isDoubleSlash) {     // fraction: this intermediate over the next
                pos++;
                // Unwrap a parenthesised operand so (a+b)/c draws as a bare stacked fraction, not
                // with visible parens — the ASCIIMath "(numerator)/(denominator)" idiom.
                items.add(new MathBox.Fraction(unwrapParens(i), unwrapParens(inter())));
            } else {
                items.add(i);
            }
        }
        if (items.isEmpty()) throw new MarkupError("empty expression", pos);
        return items.size() == 1 ? items.get(0) : new MathBox.Row(items);
    }

    /** A simple with optional subscript and/or superscript in either order ({@code x^2}, {@code a_i},
     *  {@code x^a_b}, {@code x_a^b}). */
    private MathBox inter() {
        MathBox base = simple();
        MathBox sup = null, sub = null;
        for (int guard = 0; guard < 2; guard++) {         // at most one of each, either order
            skipWs();
            char c = peekRaw();
            if (c == '^' && sup == null) { pos++; sup = simple(); }
            else if (c == '_' && sub == null) { pos++; sub = simple(); }
            else break;
        }
        return (sup == null && sub == null) ? base : new MathBox.Script(base, sup, sub);
    }

    /** A top-level {@code (…)} group unwrapped to its content (for a fraction operand); else unchanged. */
    private static MathBox unwrapParens(MathBox b) {
        return b instanceof MathBox.Fenced f && "(".equals(f.open()) ? f.content() : b;
    }

    private MathBox simple() {
        skipWs();
        if (pos >= src.length()) throw new MarkupError("expected an operand", pos);
        char c = peekRaw();

        switch (c) {
            case '(': {
                // A circled operator '(+)' / '(*)' is a symbol, not a group — try it first.
                MathBox circled = circledOp();
                if (circled != null) return circled;
                pos++;
                MathBox inner = expr(")");
                expect(')');
                return new MathBox.Fenced("(", ")", inner);
            }
            case '[': return bracket();                   // vector [a,b] or matrix [[a,b],[c,d]]
            case '{': return braceGroup();                // invisible group, branches, or prescript
            case '|': {
                pos++;
                MathBox inner = expr("|");
                expect('|');
                return new MathBox.Fenced("|", "|", inner);
            }
            case '\'': return verbatim('\'');
            case '"':  return verbatim('"');
            default: break;
        }

        // A number, including a leading-decimal one (.5). The "digit right after the dot" test is what
        // keeps this space-free and unambiguous: a '.' NOT followed by a digit isn't part of a number,
        // so it stays available for the ./. obelus (2./.3, 1/.2 both parse with no spaces).
        if (isDigit(c) || (c == '.' && pos + 1 < src.length() && isDigit(src.charAt(pos + 1)))) return number();
        if (isLetter(c)) return word();

        MathBox symbol = symbol();                        // + - // = < > arrows dot cross etc.
        if (symbol != null) return symbol;
        throw new MarkupError("unexpected '" + c + "'", pos);
    }

    /** The operand of a unary construct (sqrt/abs): a plain simple, except a parenthesised or braced
     *  group is UNWRAPPED so {@code sqrt(x+1)} draws no inner parens and {@code abs(x)} is {@code |x|}. */
    private MathBox unaryArg() {
        skipWs();
        char c = peekRaw();
        if (c == '(') { pos++; MathBox inner = expr(")"); expect(')'); return inner; }
        if (c == '{') { pos++; MathBox inner = expr("}"); expect('}'); return inner; }
        return simple();
    }

    // --- brackets, braces, argument lists --------------------------------------------------------

    /** A {@code [...]} group: a matrix when its top-level items are themselves bracketed rows
     *  ({@code [[a,b],[c,d]]}), otherwise a bracketed list / vector ({@code [a,b]}, drawn {@code [a, b]}). */
    private MathBox bracket() {
        pos++;                                            // consume '['
        List<List<MathBox>> matrixRows = null;
        List<MathBox> flat = new ArrayList<>();
        while (true) {
            skipWs();
            if (peekRaw() == ']' || pos >= src.length()) break;
            if (peekRaw() == '[') {                       // a nested row → matrix mode
                if (matrixRows == null) matrixRows = new ArrayList<>();
                matrixRows.add(commaCells('['));
            } else {
                flat.add(expr(",]"));
            }
            skipWs();
            if (peekRaw() == ',') pos++; else break;
        }
        expect(']');
        if (matrixRows != null) return new MathBox.Matrix(matrixRows, "[", "]");
        return new MathBox.Fenced("[", "]", withCommas(flat));   // vector / list
    }

    /** A {@code {...}}: a prescript when it opens with {@code ^}/{@code _} ({@code &#123;^a&#125;b}), a
     *  {@link MathBox.Cases branches} block when it has top-level commas ({@code &#123;->a,->b&#125;}),
     *  otherwise an INVISIBLE group (the braces aren't drawn). */
    private MathBox braceGroup() {
        pos++;                                            // consume '{'
        skipWs();
        if (peekRaw() == '^' || peekRaw() == '_') {       // prescript group: {^a}, {_a}, {^a_b}
            MathBox sup = null, sub = null;
            while (peekRaw() == '^' || peekRaw() == '_') {
                boolean up = peekRaw() == '^';
                pos++;
                if (up) sup = simple(); else sub = simple();
                skipWs();
            }
            expect('}');
            return new MathBox.Prescript(simple(), sup, sub);
        }
        List<MathBox> cells = commaList("}");
        expect('}');
        if (cells.size() > 1) return new MathBox.Cases(cells);   // branches
        return cells.get(0);                              // invisible group (single content)
    }

    /** A bracketed row {@code [a,b,…]} → its cells, for matrix assembly. Consumes the {@code [ … ]}. */
    private List<MathBox> commaCells(char open) {
        expect(open);
        List<MathBox> cells = commaList(",]");
        expect(']');
        return cells;
    }

    /** One-or-more comma-separated expressions, each stopping at ',' or a char in {@code closers}. */
    private List<MathBox> commaList(String closers) {
        List<MathBox> out = new ArrayList<>();
        while (true) {
            skipWs();
            if (pos >= src.length() || closers.indexOf(peekRaw()) >= 0) break;
            out.add(expr("," + closers));
            skipWs();
            if (peekRaw() == ',') pos++; else break;
        }
        if (out.isEmpty()) throw new MarkupError("empty list", pos);
        return out;
    }

    /** A parenthesised, comma-separated argument list {@code (a, b, …)} for a call alias. */
    private List<MathBox> parenArgs() {
        expect('(');
        skipWs();
        if (peekRaw() == ')') { pos++; return new ArrayList<>(); }
        List<MathBox> args = commaList(")");
        expect(')');
        return args;
    }

    /** Join list items with comma punctuation runs (the drawn form of a bracketed list / vector). */
    private static MathBox withCommas(List<MathBox> items) {
        if (items.size() == 1) return items.get(0);
        List<MathBox> row = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) row.add(new MathBox.Run(",", Role.PUNCT));
            row.add(items.get(i));
        }
        return new MathBox.Row(row);
    }

    // --- atoms -----------------------------------------------------------------------------------

    /** A number: optional integer part, optional fractional part (leading decimals like {@code .5}
     *  are fine), but a decimal point is only consumed when a digit follows it — so a trailing-dot
     *  {@code 2.} is not a number and the dot stays free for the {@code ./.} obelus. */
    private MathBox number() {
        int start = pos;
        while (pos < src.length() && isDigit(peekRaw())) pos++;
        if (pos < src.length() && peekRaw() == '.'
                && pos + 1 < src.length() && isDigit(src.charAt(pos + 1))) {
            pos++;
            while (pos < src.length() && isDigit(peekRaw())) pos++;
        }
        return new MathBox.Run(src.substring(start, pos), Role.NUMBER);
    }

    /** A run of letters: matched greedily against the keyword table (Greek, functions, unary
     *  constructs, {@code degrees}); an unrecognised prefix falls back to a single italic variable. */
    private MathBox word() {
        int start = pos;
        while (pos < src.length() && isLetter(peekRaw())) pos++;
        String run = src.substring(start, pos);
        return resolveWordRun(run, start);
    }

    /** Resolve a maximal letter run left-to-right: longest keyword prefix wins, else one letter as a
     *  variable, then continue — so {@code pix} is {@code π·x} and {@code sinx} is {@code sin·x}. */
    private MathBox resolveWordRun(String run, int startOffset) {
        List<MathBox> parts = new ArrayList<>();
        int i = 0;
        while (i < run.length()) {
            Keyword kw = longestKeyword(run, i);
            if (kw != null) {
                boolean atEnd = i + kw.text.length() == run.length();
                if (kw.unary != null) {                   // sqrt/abs consume an argument from the stream
                    // Only valid when the keyword ends the letter run (nothing glued after it).
                    if (!atEnd) {
                        throw new MarkupError("'" + kw.text + "' must be followed by an argument", startOffset + i);
                    }
                    parts.add(kw.unary.apply(unaryArg()));
                } else if (kw.call != null && atEnd && nextIsLParen()) {   // sum/integral/lim/root(…)
                    parts.add(kw.call.apply(parenArgs()));
                } else if (kw.call != null && kw.glyph.isEmpty()) {        // call-only keyword, no '('
                    throw new MarkupError("'" + kw.text + "' expects a parenthesised argument list", startOffset + i);
                } else {
                    parts.add(new MathBox.Run(kw.glyph, kw.role));         // bare word / fallback glyph
                }
                i += kw.text.length();
            } else {
                parts.add(new MathBox.Run(String.valueOf(run.charAt(i)), Role.VARIABLE));
                i++;
            }
        }
        return parts.size() == 1 ? parts.get(0) : new MathBox.Row(parts);
    }

    private MathBox verbatim(char quote) {
        pos++;                                            // opening quote
        int start = pos;
        while (pos < src.length() && peekRaw() != quote) pos++;
        if (pos >= src.length()) throw new MarkupError("unterminated " + quote + "…" + quote + " text", start);
        String text = src.substring(start, pos);
        pos++;                                            // closing quote
        return new MathBox.Run(text, Role.FUNCTION);      // upright, verbatim passthrough
    }

    /** {@code (+)} → ⊕, {@code (*)} → ⊗ (a symbol, checked before '(' opens a group). */
    private MathBox circledOp() {
        if (matches("(+)")) return new MathBox.Run("⊕", Role.OPERATOR);
        if (matches("(*)")) return new MathBox.Run("⊗", Role.OPERATOR);
        return null;
    }

    /** A non-letter operator/relation/arrow/punct token, longest-match; {@code null} if none here. */
    private MathBox symbol() {
        for (Sym s : SYMBOLS) {
            if (matches(s.token)) return new MathBox.Run(s.glyph, s.role);
        }
        return null;
    }

    // --- keyword & symbol tables -----------------------------------------------------------------

    private interface UnaryBox { MathBox apply(MathBox arg); }
    private interface CallBox { MathBox apply(List<MathBox> args); }

    /** A recognised word. {@code unary} (sqrt/abs) takes one following simple; {@code call}
     *  (sum/integral/lim/root) takes a parenthesised argument list — and, when {@code glyph} is
     *  non-empty, falls back to that glyph as a bare word (e.g. {@code sum} alone → ∑). Otherwise it's
     *  a plain glyph run of {@code role}. */
    private record Keyword(String text, String glyph, Role role, UnaryBox unary, CallBox call) {
        Keyword(String text, String glyph, Role role) { this(text, glyph, role, null, null); }
        static Keyword unary(String text, UnaryBox u) { return new Keyword(text, "", Role.FUNCTION, u, null); }
        static Keyword call(String text, String bareGlyph, Role role, CallBox c) {
            return new Keyword(text, bareGlyph, role, null, c);
        }
    }

    /** Is the next non-whitespace source char a '(' (a call's argument list)? Doesn't consume. */
    private boolean nextIsLParen() {
        int i = pos;
        while (i < src.length() && Character.isWhitespace(src.charAt(i))) i++;
        return i < src.length() && src.charAt(i) == '(';
    }

    /** The longest keyword whose text is a prefix of {@code run} at {@code i}, or {@code null}. */
    private static Keyword longestKeyword(String run, int i) {
        Keyword best = null;
        for (Keyword k : KEYWORDS) {
            if (run.startsWith(k.text, i) && (best == null || k.text.length() > best.text.length())) {
                best = k;
            }
        }
        return best;
    }

    private record Sym(String token, String glyph, Role role) {}

    /** Non-letter tokens, ORDERED longest-first so the scanner is greedy (e.g. {@code <=} before
     *  {@code <}, {@code -->} before {@code -}). */
    private static final Sym[] SYMBOLS = {
        new Sym("-->", "→", Role.RELATION),   // →
        new Sym("<--", "←", Role.RELATION),   // ←
        new Sym("==>", "⇒", Role.RELATION),   // ⇒
        new Sym("<==", "⇐", Role.RELATION),   // ⇐
        new Sym("->",  "→", Role.RELATION),   // → (short arrow, e.g. inside lim(x->0, …))
        new Sym("<-",  "←", Role.RELATION),   // ←
        new Sym("<=",  "≤", Role.RELATION),   // ≤
        new Sym(">=",  "≥", Role.RELATION),   // ≥
        new Sym("!=",  "≠", Role.RELATION),   // ≠
        new Sym("~=",  "≈", Role.RELATION),   // ≈
        new Sym("+-",  "±", Role.OPERATOR),   // ±
        new Sym("><",  "×", Role.OPERATOR),   // × (cross product)
        new Sym("./.", "÷", Role.OPERATOR),   // ÷ (obelus — dot·slash·dot mirrors the glyph)
        new Sym("//",  "/",       Role.OPERATOR),  // literal slash (contrast a/b = fraction)
        new Sym("=",   "=",       Role.RELATION),
        new Sym("<",   "<",       Role.RELATION),
        new Sym(">",   ">",       Role.RELATION),
        new Sym("+",   "+",       Role.OPERATOR),
        new Sym("-",   "−", Role.OPERATOR),   // − (minus sign, not hyphen)
        new Sym("*",   "⋅", Role.OPERATOR),   // ⋅ (dot product)
        new Sym(",",   ",",       Role.PUNCT),
    };

    /** Lowercase Greek letter names → their U+03B1.. codepoints (uppercase is 0x20 lower). Declared
     *  before {@link #KEYWORDS}, which reads it during its own static initialisation. */
    private static final Map<String, Integer> GREEK = Map.ofEntries(
        Map.entry("alpha", 0x3B1), Map.entry("beta", 0x3B2), Map.entry("gamma", 0x3B3),
        Map.entry("delta", 0x3B4), Map.entry("epsilon", 0x3B5), Map.entry("zeta", 0x3B6),
        Map.entry("eta", 0x3B7), Map.entry("theta", 0x3B8), Map.entry("iota", 0x3B9),
        Map.entry("kappa", 0x3BA), Map.entry("lambda", 0x3BB), Map.entry("mu", 0x3BC),
        Map.entry("nu", 0x3BD), Map.entry("xi", 0x3BE), Map.entry("omicron", 0x3BF),
        Map.entry("pi", 0x3C0), Map.entry("rho", 0x3C1), Map.entry("sigma", 0x3C3),
        Map.entry("tau", 0x3C4), Map.entry("upsilon", 0x3C5), Map.entry("phi", 0x3C6),
        Map.entry("chi", 0x3C7), Map.entry("psi", 0x3C8), Map.entry("omega", 0x3C9));

    private static final Keyword[] KEYWORDS = buildKeywords();

    private static Keyword[] buildKeywords() {
        List<Keyword> k = new ArrayList<>();
        // Unary constructs: one following simple.
        k.add(Keyword.unary("sqrt", MathBox::sqrt));
        k.add(Keyword.unary("abs", arg -> new MathBox.Fenced("|", "|", arg)));
        // Call aliases: a parenthesised argument list (and a bare fallback glyph where sensible).
        //   sum(under, over, body) / sum(under, body) — ∑ carrying its limits above/below.
        //   product(…)             — same, ∏.
        //   integral(under, over, body) / (under, body) / (body) — ∫ with side limits (int_lo^hi).
        //   lim(under, body) / lim(body) — "lim" with x→a underneath.
        //   root(index, radicand) — a non-square root.
        k.add(Keyword.call("sum", "∑", Role.SYMBOL, args -> bigOp("∑", args)));
        k.add(Keyword.call("product", "∏", Role.SYMBOL, args -> bigOp("∏", args)));
        k.add(Keyword.call("integral", "∫", Role.SYMBOL, MathMarkup::integralOp));
        k.add(Keyword.call("lim", "lim", Role.FUNCTION, MathMarkup::limOp));
        k.add(Keyword.call("root", "", Role.FUNCTION, MathMarkup::rootOp));
        // The degree symbol and infinity.
        k.add(new Keyword("degrees", "°", Role.SYMBOL));
        k.add(new Keyword("degree", "°", Role.SYMBOL));
        k.add(new Keyword("infinity", "∞", Role.SYMBOL));
        // Upright function names.
        for (String fn : new String[]{
                "sin", "cos", "tan", "sec", "csc", "cot",
                "sinh", "cosh", "tanh", "arcsin", "arccos", "arctan",
                "exp", "log", "ln", "det", "dim", "gcd", "lcm", "max", "min", "mod"}) {
            k.add(new Keyword(fn, fn, Role.FUNCTION));
        }
        // Greek letters: lower and upper (capitalised name → uppercase Greek).
        GREEK.forEach((name, cp) -> {
            k.add(new Keyword(name, String.valueOf((char) (int) cp), Role.SYMBOL));
            String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            int upper = cp - 0x20;                        // Greek uppercase block sits 0x20 below lower
            k.add(new Keyword(cap, String.valueOf((char) upper), Role.SYMBOL));
        });
        return k.toArray(new Keyword[0]);
    }

    // --- call-alias builders ---------------------------------------------------------------------

    /** A big operator ({@code ∑}, {@code ∏}) carrying under/over limits, followed by the body:
     *  {@code (under, over, body)} or {@code (under, body)}. */
    private static MathBox bigOp(String glyph, List<MathBox> args) {
        MathBox op = new MathBox.Run(glyph, Role.OPERATOR);
        return switch (args.size()) {
            case 3 -> new MathBox.Row(List.of(new MathBox.UnderOver(op, args.get(1), args.get(0)), args.get(2)));
            case 2 -> new MathBox.Row(List.of(new MathBox.UnderOver(op, null, args.get(0)), args.get(1)));
            default -> throw new MarkupError("this operator takes (under, over, body) or (under, body)", 0);
        };
    }

    /** An integral with side-set limits {@code ∫_under^over body}: {@code (under, over, body)},
     *  {@code (under, body)}, or just {@code (body)}. */
    private static MathBox integralOp(List<MathBox> args) {
        MathBox sign = new MathBox.Run("∫", Role.OPERATOR);
        return switch (args.size()) {
            case 3 -> new MathBox.Row(List.of(new MathBox.Script(sign, args.get(1), args.get(0)), args.get(2)));
            case 2 -> new MathBox.Row(List.of(new MathBox.Script(sign, null, args.get(0)), args.get(1)));
            case 1 -> new MathBox.Row(List.of(sign, args.get(0)));
            default -> throw new MarkupError("integral takes (under, over, body), (under, body) or (body)", 0);
        };
    }

    /** {@code lim(under, body)} → "lim" with {@code under} below and the body to the right;
     *  {@code lim(body)} → a bare limit. */
    private static MathBox limOp(List<MathBox> args) {
        MathBox lim = new MathBox.Run("lim", Role.FUNCTION);
        return switch (args.size()) {
            case 2 -> new MathBox.Row(List.of(new MathBox.UnderOver(lim, null, args.get(0)), args.get(1)));
            case 1 -> new MathBox.Row(List.of(lim, args.get(0)));
            default -> throw new MarkupError("lim takes (under, body) or (body)", 0);
        };
    }

    /** {@code root(index, radicand)} → a radical with a degree index. */
    private static MathBox rootOp(List<MathBox> args) {
        if (args.size() != 2) throw new MarkupError("root takes (index, radicand)", 0);
        return new MathBox.Radical(args.get(1), args.get(0));
    }

    // --- lexing helpers --------------------------------------------------------------------------

    private char peekRaw() { return pos < src.length() ? src.charAt(pos) : END_OF_INPUT; }

    /** If {@code token} is next in the source (after whitespace), consume it and return true. */
    private boolean matches(String token) {
        skipWs();
        if (src.regionMatches(pos, token, 0, token.length())) { pos += token.length(); return true; }
        return false;
    }

    private void expect(char c) {
        skipWs();
        if (peekRaw() != c) throw new MarkupError("expected '" + c + "'", pos);
        pos++;
    }

    private void skipWs() { while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++; }

    private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }
    private static boolean isLetter(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'); }
}
