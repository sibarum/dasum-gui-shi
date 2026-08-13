package sibarum.dasum.gui.core.text;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.em.EmContext;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.render.Color;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks in the re-wrap invariants the style system depends on: as the wrap
 * width changes, character-offset style ranges must neither disappear nor
 * drift out of alignment with the glyphs. Every geometry consumer
 * ({@link TextGeometry#styleRects}, {@link TextGeometry#caretBounds},
 * {@link TextGeometry#lineOfIndex}) re-derives visual lines from
 * {@link TextMetrics#lines}; these tests pin the exact rects at multiple
 * wrap widths so a future change that lets those consumers disagree about
 * where lines break (e.g. wrap-to-own-width resolved inconsistently) fails
 * loudly.
 *
 * <p>Deterministic geometry trick: a synthetic atlas where every glyph has
 * advance = 1em and lineHeight = 1em. With {@code fontSize = 1em}, one
 * character advance and one line both equal {@link EmContext#pixelsPerEm()},
 * so pixel expectations are a clean multiple of that scale and independent
 * of DPI/zoom — no global mutation needed.
 */
final class TextWrapStyleAlignmentTest {

    private static final String FONT = "wrap-align-test-font";

    /** Content laid out as three whitespace-separated 4-char words. */
    private static final String CONTENT = "aaaa bbbb cccc"; // indices 0..13

    /** Pixels for one glyph advance and one line height (they're equal here). */
    private static float unit() {
        // fontSize = 1em, advance = 1em  ->  advance px = lineH px = pixelsPerEm.
        return EmContext.pixelsPerEm();
    }

    @BeforeAll
    static void registerFont() {
        if (FontGroups.isRegistered(FONT)) return;
        AtlasInfo info = new AtlasInfo("msdf", 4f, 1f, 64, 64, true);
        // lineHeight = 1em keeps one visual line exactly one advance-unit tall.
        FontMetrics metrics = new FontMetrics(1f, 1f, 0.8f, -0.2f, 0f, 0.05f);
        Map<Integer, GlyphData> glyphs = new HashMap<>();
        for (char c : new char[] {'a', 'b', 'c', ' '}) {
            // Only advance matters for line-break / caret / rect geometry;
            // plane/atlas bounds (glyph imagery) are irrelevant here.
            glyphs.put((int) c, new GlyphData(c, 1f, null, null));
        }
        AtlasData atlas = new AtlasData(info, metrics, glyphs);
        FontGroups.register(FontGroup.of(FONT, atlas, null));
    }

    /** A single-line, zero-padding, no-gutter Text at the given wrap width (null = no wrap). */
    private static Component.Text text(Em wrapWidth) {
        return new Component.Text(
            CONTENT, FONT, Em.of(1f), new Color(1f, 1f, 1f, 1f),
            null, null, Em.ZERO,
            wrapWidth, false, false, false,
            true, true, false, false, 0);
    }

    private static PixelRect rectAt(Component.Text t) {
        // Wide enough that nothing clips; wrap is driven by wrapWidth, not the rect.
        return new PixelRect(0f, 0f, 1000f, 1000f);
    }

    // ---- helper: assert a rect equals (x,y,w,h) in advance-units ----
    private static void assertRectUnits(PixelRect r, float xU, float yU, float wU, float hU) {
        float u = unit();
        float eps = u * 1e-3f;
        assertEquals(xU * u, r.x(), eps, "x");
        assertEquals(yU * u, r.y(), eps, "y");
        assertEquals(wU * u, r.width(), eps, "width");
        assertEquals(hU * u, r.height(), eps, "height");
    }

    // ---------------------------------------------------------------
    // Visual-line breakdown at each wrap width (the single lever).
    // ---------------------------------------------------------------

    @Test
    void lineSpansMatchExpectedBreaksAcrossWrapWidths() {
        // No wrap: one line spanning the whole content.
        List<LineBreaker.LineSpan> none = TextMetrics.lines(text(null), CONTENT);
        assertEquals(1, none.size());
        assertEquals(0, none.get(0).start());
        assertEquals(14, none.get(0).end());

        // Wrap at 10 units: "aaaa bbbb " | "cccc".
        List<LineBreaker.LineSpan> w10 = TextMetrics.lines(text(Em.of(10f)), CONTENT);
        assertEquals(2, w10.size());
        assertEquals(0, w10.get(0).start());
        assertEquals(10, w10.get(0).end());
        assertEquals(10, w10.get(1).start());
        assertEquals(14, w10.get(1).end());

        // Wrap at 5 units: "aaaa " | "bbbb " | "cccc".
        List<LineBreaker.LineSpan> w5 = TextMetrics.lines(text(Em.of(5f)), CONTENT);
        assertEquals(3, w5.size());
        assertEquals(0, w5.get(0).start());
        assertEquals(5, w5.get(0).end());
        assertEquals(5, w5.get(1).start());
        assertEquals(10, w5.get(1).end());
        assertEquals(10, w5.get(2).start());
        assertEquals(14, w5.get(2).end());
    }

    // ---------------------------------------------------------------
    // Background/foreground style ranges are char-offset based: the SAME
    // range [2,12) must stay fully covered and correctly positioned as the
    // wrap width changes. "Fully covered" = spans don't disappear;
    // "correctly positioned" = spans stay aligned to the glyphs.
    // ---------------------------------------------------------------

    private static final int RANGE_START = 2;  // 3rd 'a'
    private static final int RANGE_END   = 12; // through 2nd 'c' (exclusive)

    @Test
    void styleRangeFullyCoveredAndAlignedNoWrap() {
        Component.Text t = text(null);
        List<PixelRect> rects = TextGeometry.styleRects(t, CONTENT, rectAt(t), RANGE_START, RANGE_END);
        // One visual line -> one rect, from col 2 to col 12.
        assertEquals(1, rects.size());
        assertRectUnits(rects.get(0), 2f, 0f, 10f, 1f);
    }

    @Test
    void styleRangeFullyCoveredAndAlignedWrap10() {
        Component.Text t = text(Em.of(10f));
        List<PixelRect> rects = TextGeometry.styleRects(t, CONTENT, rectAt(t), RANGE_START, RANGE_END);
        // Line 0 [0,10): cols 2..10.  Line 1 [10,14): cols 0..2.
        assertEquals(2, rects.size());
        assertRectUnits(rects.get(0), 2f, 0f, 8f, 1f);
        assertRectUnits(rects.get(1), 0f, 1f, 2f, 1f);
        assertContiguousCoverage(rects, RANGE_START, RANGE_END);
    }

    @Test
    void styleRangeFullyCoveredAndAlignedWrap5() {
        Component.Text t = text(Em.of(5f));
        List<PixelRect> rects = TextGeometry.styleRects(t, CONTENT, rectAt(t), RANGE_START, RANGE_END);
        // Line 0 [0,5): cols 2..5.  Line 1 [5,10): cols 0..5.  Line 2 [10,14): cols 0..2.
        assertEquals(3, rects.size());
        assertRectUnits(rects.get(0), 2f, 0f, 3f, 1f);
        assertRectUnits(rects.get(1), 0f, 1f, 5f, 1f);
        assertRectUnits(rects.get(2), 0f, 2f, 2f, 1f);
        assertContiguousCoverage(rects, RANGE_START, RANGE_END);
    }

    /**
     * The union of the per-line rect widths must equal the range's total
     * character advance — i.e. no character in [start,end) loses its fill
     * when the range is split across a wrap boundary.
     */
    private static void assertContiguousCoverage(List<PixelRect> rects, int start, int end) {
        float total = 0f;
        for (PixelRect r : rects) total += r.width();
        float expected = (end - start) * unit();
        assertEquals(expected, total, unit() * 1e-3f,
            "summed rect width must equal the full range advance (nothing dropped at the wrap boundary)");
    }

    // ---------------------------------------------------------------
    // Caret geometry must land on the visual line the wrap produces, so it
    // stays aligned with the style rects and glyphs after re-wrap.
    // ---------------------------------------------------------------

    @Test
    void caretTracksVisualLineAcrossWrapWidths() {
        int idx = 7; // 3rd 'b'

        // No wrap: line 0, x = 7 units.
        Component.Text t0 = text(null);
        assertEquals(0, TextGeometry.lineOfIndex(t0, CONTENT, idx));
        PixelRect c0 = TextGeometry.caretBounds(t0, CONTENT, rectAt(t0), idx);
        assertEquals(0f, c0.y(), unit() * 1e-3f);
        assertEquals(7f * unit(), c0.x() + 1f, unit() * 1e-2f); // caret rect is 2px wide, centered

        // Wrap 5: line 1 [5,10), local col 2 -> x = 2 units, y = 1 line down.
        Component.Text t5 = text(Em.of(5f));
        assertEquals(1, TextGeometry.lineOfIndex(t5, CONTENT, idx));
        PixelRect c5 = TextGeometry.caretBounds(t5, CONTENT, rectAt(t5), idx);
        assertEquals(1f * unit(), c5.y(), unit() * 1e-3f);
        assertEquals(2f * unit(), c5.x() + 1f, unit() * 1e-2f);
    }

    @Test
    void hitTestRoundTripsToVisualLineWrap5() {
        Component.Text t = text(Em.of(5f));
        PixelRect rect = rectAt(t);
        float u = unit();
        // Click near the start of visual line 2 (y just inside the 3rd line).
        int idx = TextGeometry.charIndexAt(t, CONTENT, rect, 0.1f * u, 2.5f * u);
        assertEquals(10, idx, "click on the 3rd visual line resolves to that line's first char");
    }

    // ---------------------------------------------------------------
    // Runtime toggle path: TextWrapStates.effectiveWrapPx is the single
    // source of truth. These pin that (a) the toggle overrides the fixed
    // wrapWidth, (b) it wraps to the layout-recorded content width, and (c)
    // every geometry consumer re-breaks at that same width, so style spans
    // and caret stay aligned exactly as on the fixed-width path.
    // ---------------------------------------------------------------

    @Test
    void toggleOffFallsBackToFixedWrapWidth() {
        Component.Text t = text(Em.of(10f));
        // Even with a (wrong) content width recorded, wrap-off keeps the fixed width.
        TextWrapStates.recordContentWidth(t, 5f * unit());
        assertEquals(10f * unit(), TextWrapStates.effectiveWrapPx(t), unit() * 1e-3f);
        assertEquals(2, TextMetrics.lines(t, CONTENT).size());
    }

    @Test
    void toggleOnWrapsToRecordedContentWidthOverridingFixed() {
        // Fixed wrapWidth = 10 units, but toggle on + recorded 5 units -> wrap at 5.
        Component.Text t = text(Em.of(10f));
        TextWrapStates.setWordWrap(t, true);
        TextWrapStates.recordContentWidth(t, 5f * unit());
        try {
            assertEquals(5f * unit(), TextWrapStates.effectiveWrapPx(t), unit() * 1e-3f);
            assertEquals(3, TextMetrics.lines(t, CONTENT).size(),
                "runtime wrap width overrides the record's fixed wrapWidth");
        } finally {
            TextWrapStates.setWordWrap(t, false);
        }
    }

    @Test
    void toggleOnWithNoRecordedWidthFallsBackUntilLayoutRuns() {
        // Toggle on but layout hasn't recorded a width yet -> falls back to the
        // record's fixed wrapWidth (or null). No crash, no accidental wrap-to-zero.
        Component.Text noFixed = text(null);
        TextWrapStates.setWordWrap(noFixed, true);
        try {
            assertEquals(null, TextWrapStates.effectiveWrapPx(noFixed),
                "wrap-on with nothing recorded and no fixed width -> no wrap");
            assertEquals(1, TextMetrics.lines(noFixed, CONTENT).size());
        } finally {
            TextWrapStates.setWordWrap(noFixed, false);
        }
    }

    @Test
    void runtimeWrapProducesIdenticalGeometryToFixedWrap() {
        // The runtime path (toggle on, recorded 5u, no fixed wrapWidth) must
        // yield byte-for-byte the same spans/caret as the fixed 5em path.
        Component.Text fixed = text(Em.of(5f));
        Component.Text runtime = text(null);
        TextWrapStates.setWordWrap(runtime, true);
        TextWrapStates.recordContentWidth(runtime, 5f * unit());
        try {
            PixelRect rf = rectAt(fixed);
            PixelRect rr = rectAt(runtime);

            List<PixelRect> sf = TextGeometry.styleRects(fixed, CONTENT, rf, RANGE_START, RANGE_END);
            List<PixelRect> sr = TextGeometry.styleRects(runtime, CONTENT, rr, RANGE_START, RANGE_END);
            assertEquals(sf.size(), sr.size(), "same number of span rects");
            for (int i = 0; i < sf.size(); i++) {
                assertRectUnits(sr.get(i), sf.get(i).x() / unit(), sf.get(i).y() / unit(),
                    sf.get(i).width() / unit(), sf.get(i).height() / unit());
            }

            // Caret + line index agree on the same visual break.
            int idx = 7;
            assertEquals(TextGeometry.lineOfIndex(fixed, CONTENT, idx),
                         TextGeometry.lineOfIndex(runtime, CONTENT, idx));
            PixelRect cf = TextGeometry.caretBounds(fixed, CONTENT, rf, idx);
            PixelRect cr = TextGeometry.caretBounds(runtime, CONTENT, rr, idx);
            assertEquals(cf.x(), cr.x(), unit() * 1e-3f);
            assertEquals(cf.y(), cr.y(), unit() * 1e-3f);
        } finally {
            TextWrapStates.setWordWrap(runtime, false);
        }
    }

    @Test
    void toggleFlipIsSeamlessForOffsetsAndSpans() {
        // Flipping the toggle only changes where lines break; the char-offset
        // range stays fully covered before and after (spans never disappear).
        Component.Text t = text(null);
        TextWrapStates.recordContentWidth(t, 5f * unit());
        PixelRect rect = rectAt(t);
        try {
            // Off: one line, one rect covering the whole range.
            List<PixelRect> off = TextGeometry.styleRects(t, CONTENT, rect, RANGE_START, RANGE_END);
            assertContiguousCoverage(off, RANGE_START, RANGE_END);
            assertEquals(1, off.size());

            // On: three lines, still fully covered.
            TextWrapStates.setWordWrap(t, true);
            List<PixelRect> on = TextGeometry.styleRects(t, CONTENT, rect, RANGE_START, RANGE_END);
            assertContiguousCoverage(on, RANGE_START, RANGE_END);
            assertEquals(3, on.size());

            // Back off: identical to the first off snapshot.
            TextWrapStates.setWordWrap(t, false);
            List<PixelRect> off2 = TextGeometry.styleRects(t, CONTENT, rect, RANGE_START, RANGE_END);
            assertEquals(off.size(), off2.size());
            assertContiguousCoverage(off2, RANGE_START, RANGE_END);
        } finally {
            TextWrapStates.setWordWrap(t, false);
        }
    }
}
