package sibarum.dasum.gui.core.text;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A printable character absent from the atlas is reported once (deduped),
 * whitespace/control misses are not, and the report fires through the real
 * {@link GlyphLayout} lookup path (both render and measure).
 */
final class MissingGlyphLogTest {

    /** A glyphless atlas — every codepoint misses. */
    private static AtlasData emptyAtlas() {
        return new AtlasData(
            new AtlasInfo("msdf", 4f, 1f, 64, 64, true),
            new FontMetrics(1f, 1.2f, 0.8f, -0.2f, -0.1f, 0.05f),
            Map.of());
    }

    @BeforeEach
    void clean() { MissingGlyphLog.resetForTest(); }

    @Test
    void firstSightingLogsThenDedups() {
        AtlasData atlas = emptyAtlas();
        assertTrue(MissingGlyphLog.report(atlas, 'Z'), "first sighting logs");
        assertFalse(MissingGlyphLog.report(atlas, 'Z'), "repeat is deduped");
        assertTrue(MissingGlyphLog.report(atlas, 'Q'), "a different codepoint is independent");
    }

    @Test
    void dedupIsPerAtlas() {
        AtlasData a = emptyAtlas();
        AtlasData b = emptyAtlas();
        assertTrue(MissingGlyphLog.report(a, 'Z'));
        assertTrue(MissingGlyphLog.report(b, 'Z'), "same char in a different atlas reports separately");
    }

    @Test
    void resetClearsHistory() {
        AtlasData atlas = emptyAtlas();
        assertTrue(MissingGlyphLog.report(atlas, 'Z'));
        MissingGlyphLog.resetForTest();
        assertTrue(MissingGlyphLog.report(atlas, 'Z'), "reset lets it report again");
    }

    @Test
    void glyphLayoutReportsAPrintableMissThroughAdvance() {
        AtlasData atlas = emptyAtlas();
        GlyphLayout layout = new GlyphLayout(atlas);
        // advance() runs the same resolve() path as build(); measuring a missing
        // printable char must have reported it, so a manual re-report dedups.
        layout.advance('Z', 16f);
        assertFalse(MissingGlyphLog.report(atlas, 'Z'), "GlyphLayout already reported the miss");
    }

    @Test
    void whitespaceAndControlMissesAreNotReported() {
        AtlasData atlas = emptyAtlas();
        GlyphLayout layout = new GlyphLayout(atlas);
        layout.advance(' ', 16f);        // space
        layout.advance('\t', 16f);       // tab
        layout.build('\n', 0f, 0f, 16f, null);  // newline via build()
        // None of the above should have reported — so a manual first report returns true.
        assertTrue(MissingGlyphLog.report(atlas, ' '),  "space is not a missing glyph");
        assertTrue(MissingGlyphLog.report(atlas, '\t'), "tab is not a missing glyph");
        assertTrue(MissingGlyphLog.report(atlas, '\n'), "newline is not a missing glyph");
    }
}
