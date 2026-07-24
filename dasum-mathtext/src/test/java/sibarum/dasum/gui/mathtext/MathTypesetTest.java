package sibarum.dasum.gui.mathtext;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.text.AtlasData;
import sibarum.dasum.gui.vis.scene.Layer;
import sibarum.dasum.gui.vis.scene.LineLayer;
import sibarum.dasum.gui.vis.scene.TextLayer;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static sibarum.dasum.gui.mathtext.MathBox.frac;
import static sibarum.dasum.gui.mathtext.MathBox.num;
import static sibarum.dasum.gui.mathtext.MathBox.op;
import static sibarum.dasum.gui.mathtext.MathBox.paren;
import static sibarum.dasum.gui.mathtext.MathBox.pow;
import static sibarum.dasum.gui.mathtext.MathBox.row;
import static sibarum.dasum.gui.mathtext.MathBox.sqrt;
import static sibarum.dasum.gui.mathtext.MathBox.var;

/**
 * POC for the math typesetter: hand-built {@link MathBox} IR laid out once ({@link MathLayout}) and
 * projected to BOTH backends. Proves the reverse-language-design core — one IR, one layout, two dumb
 * tree-walks — end to end against the real STIX Two Math metrics.
 */
class MathTypesetTest {

    private static final AtlasData MATH = AtlasData.loadFromResource("/dasum/atlas/math.json");
    private final MathLayout layout = new MathLayout(MATH, MathConstants.stixTwoMath());

    /** x^2 + 1 */
    private static MathBox quadratic() {
        return row(pow(var("x"), num("2")), op("+"), num("1"));
    }

    /** sqrt((a + b) / 2) — exercises radical, fence, fraction, row, scripts-free runs. */
    private static MathBox nested() {
        return sqrt(paren(frac(row(var("a"), op("+"), var("b")), num("2"))));
    }

    @Test
    void layout_producesPositiveMetrics_andDraws() {
        LaidOut m = layout.layout(nested());
        assertTrue(m.width() > 0 && m.ascent() > 0 && m.descent() >= 0, "sane box metrics: " + m);
        assertTrue(m.draws().size() >= 6, "nested expr lays out several primitives: " + m.draws().size());
    }

    @Test
    void svgBackend_isWellFormed_andPaintsGlyphsAndRules() {
        String svg = MathSvg.write(layout.layout(nested()), 40.0);
        assertDoesNotThrow(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8))),
                () -> "math SVG must be well-formed:\n" + svg);
        assertTrue(svg.contains("<text"), "glyph runs become <text>");
        assertTrue(svg.contains("<rect"), "the fraction bar / vinculum become <rect> rules");
        assertTrue(svg.contains("√"), "the radical surd glyph is emitted");
    }

    @Test
    void oglBackend_emitsTextAndLineLayers_fromTheSameLayout() {
        List<Layer> layers = MathOgl.toLayers(
                layout.layout(nested()), MathConstants.stixTwoMath(), Color.WHITE, 40f, 0f, 0f);
        long texts = layers.stream().filter(l -> l instanceof TextLayer).count();
        long lines = layers.stream().filter(l -> l instanceof LineLayer).count();
        assertTrue(texts >= 5, "one TextLayer per glyph run; got " + texts);
        assertTrue(lines >= 2, "fraction bar + radical vinculum as LineLayers; got " + lines);
    }

    @Test
    void bothBackends_agreeOnRunCount() {
        // Same IR → same draw-list → the SVG <text> count matches the OGL TextLayer count. The single
        // shared layout is what guarantees this parity.
        LaidOut m = layout.layout(quadratic());
        long svgTexts = countOccurrences(MathSvg.write(m, 40.0), "<text");
        long oglTexts = MathOgl.toLayers(m, MathConstants.stixTwoMath(), Color.WHITE, 40f, 0f, 0f)
                .stream().filter(l -> l instanceof TextLayer).count();
        assertTrue(svgTexts == oglTexts && svgTexts >= 3,
                "backends agree on run count (svg=" + svgTexts + ", ogl=" + oglTexts + ")");
    }

    private static long countOccurrences(String s, String sub) {
        long n = 0; int i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }
}
