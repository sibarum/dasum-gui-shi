package sibarum.dasum.gui.mathtext;

import sibarum.dasum.gui.mathtext.MathBox.Role;

/**
 * Maps a semantic run to the actual glyph string to draw — a shared formatting decision, so both
 * backends and the layout agree on the codepoints. A {@link Role#VARIABLE} latin letter becomes its
 * <b>italic math</b> codepoint (Mathematical Alphanumeric Symbols, U+1D400 block; the reserved
 * italic-h hole maps to U+210E ℎ). Every other role is upright and passes through verbatim — numbers,
 * operators, function names, and symbols the caller already gives as literal glyphs (π, √, ≈).
 */
public final class MathGlyphs {

    private MathGlyphs() {}

    /** The glyph string to draw for {@code text} in {@code role}. */
    public static String resolve(String text, Role role) {
        if (role != Role.VARIABLE) return text;
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> out.appendCodePoint(italic(cp)));
        return out.toString();
    }

    /** The italic-math codepoint for a latin letter, or {@code cp} unchanged if it isn't one. */
    private static int italic(int cp) {
        if (cp == 'h') return 0x210E;                       // italic-h is at the Planck codepoint
        if (cp >= 'a' && cp <= 'z') return 0x1D44E + (cp - 'a');
        if (cp >= 'A' && cp <= 'Z') return 0x1D434 + (cp - 'A');
        return cp;                                          // digits, Greek already italic, etc.
    }
}
