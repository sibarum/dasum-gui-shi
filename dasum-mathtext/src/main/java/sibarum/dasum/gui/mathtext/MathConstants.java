package sibarum.dasum.gui.mathtext;

/**
 * The per-font layout constants for the math typesetter — every offset and "magic number" the box
 * layout needs, all in <b>em</b> (except the two unitless script scales and the font-group name).
 * A real OpenType MATH font ships an analogous table; we don't parse it (yet), so these are the
 * externally-tuned equivalents. They are deliberately data, not code: each math font has its own
 * ideal values, and picking the best face for the platform means swapping this record, not editing
 * the engine. The authoritative, human-editable source is the {@code pontif.mathtext} {@code
 * MathStyle} config (a .ptf); {@link #stixTwoMath()} is the baked fallback and must mirror it.
 *
 * <p>Field groups map onto the box vocabulary (Row · Run · Fraction · Script · Radical · Delimiter):
 *
 * @param scriptScale          sub/superscript size relative to its base (TeX ≈ 0.7)
 * @param scriptScriptScale    size of a script nested inside a script (TeX ≈ 0.55)
 * @param axisHeight           height of the math axis above the baseline — where a fraction bar and
 *                             a binary minus center; fractions are positioned around it
 * @param fractionRuleThickness thickness of the fraction bar
 * @param fractionGapNum       gap between the numerator's bottom and the bar
 * @param fractionGapDen       gap between the bar and the denominator's top
 * @param superscriptShiftUp   how far a superscript's baseline sits above the base baseline
 * @param subscriptShiftDown   how far a subscript's baseline sits below the base baseline
 * @param scriptGapAfter       trailing space after a scripted cluster
 * @param radicalRuleThickness thickness of the radical vinculum (the bar over the radicand)
 * @param radicalGapAbove      gap between the vinculum and the top of the radicand
 * @param radicalKernBefore    space before the surd glyph
 * @param radicalKernAfter     space between the surd and the radicand (the tuck under the hook)
 * @param spaceBinaryOp        space on each side of a binary operator (+ − ·)
 * @param spaceRelation        space on each side of a relation (= ≈ → ≠ …)
 * @param spacePunct           space after punctuation (a comma in an argument list)
 * @param functionGap          space between a function name (sin, log) and its argument
 * @param delimiterPad         padding just inside a delimiter pair ( … )
 * @param fontGroup            the registered dasum FontGroup to draw glyphs from (e.g. "math")
 */
public record MathConstants(
    double scriptScale,
    double scriptScriptScale,
    double axisHeight,
    double fractionRuleThickness,
    double fractionGapNum,
    double fractionGapDen,
    double superscriptShiftUp,
    double subscriptShiftDown,
    double scriptGapAfter,
    double radicalRuleThickness,
    double radicalGapAbove,
    double radicalKernBefore,
    double radicalKernAfter,
    double spaceBinaryOp,
    double spaceRelation,
    double spacePunct,
    double functionGap,
    double delimiterPad,
    String fontGroup
) {

    /** The baked fallback profile for STIX Two Math — mirrors the {@code stixTwoMath()} .ptf profile.
     *  Used when no external {@code MathStyle} config is supplied. */
    public static MathConstants stixTwoMath() {
        return new MathConstants(
            0.70,   // scriptScale
            0.55,   // scriptScriptScale
            0.25,   // axisHeight
            0.04,   // fractionRuleThickness
            0.10,   // fractionGapNum
            0.10,   // fractionGapDen
            0.45,   // superscriptShiftUp
            0.20,   // subscriptShiftDown
            0.05,   // scriptGapAfter
            0.045,  // radicalRuleThickness
            0.06,   // radicalGapAbove
            0.05,   // radicalKernBefore
            0.02,   // radicalKernAfter
            0.22,   // spaceBinaryOp
            0.28,   // spaceRelation
            0.17,   // spacePunct
            0.12,   // functionGap
            0.05,   // delimiterPad
            "math"  // fontGroup
        );
    }
}
