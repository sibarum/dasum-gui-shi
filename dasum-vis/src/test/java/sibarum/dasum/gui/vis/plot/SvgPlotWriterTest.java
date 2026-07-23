package sibarum.dasum.gui.vis.plot;

import org.junit.jupiter.api.Test;

import sibarum.dasum.gui.core.render.Color;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless verification of the semantic SVG export — pure string output, no GL or window. */
class SvgPlotWriterTest {

    private static PlotScene2D scene() {
        PlotFrame frame = new PlotFrame(0f, 0f, 10f, 5.5f, Axis.linear(-2, 2), Axis.linear(-3, 3));
        var curves = List.of(
            Series.line(new double[]{-2, -1, 0}, new double[]{-2.5, -1, 0.5}, Color.WHITE),
            Series.line(new double[]{0.5, 1, 2}, new double[]{2, 1, 0.4}, Color.WHITE));
        var asymptotes = List.of(new PlotScene2D.Asymptote(0.385, "x=0.385"));
        var features = List.of(
            new PlotScene2D.Feature(PlotScene2D.FeatureKind.ZERO, 0.281, 0.0, "0.281"),
            new PlotScene2D.Feature(PlotScene2D.FeatureKind.OPTIMUM, -1.0, 2.0, "(-1, 2)"));
        var band = new PlotScene2D.EnclosureBand(
            new double[]{-2, -1, 0}, new double[]{-2.6, -1.1, 0.4}, new double[]{-2.4, -0.9, 0.6});
        return new PlotScene2D(frame, curves, asymptotes, features, band);
    }

    @Test
    void export_producesWellFormedSvg() {
        String svg = SvgPlotWriter.write(scene(), 900, 550);
        assertDoesNotThrow(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8))),
            () -> "exported SVG must be well-formed XML:\n" + svg);
    }

    @Test
    void export_classesEveryElementSemantically() {
        String svg = SvgPlotWriter.write(scene(), 900, 550);
        assertTrue(svg.contains("class=\"pontif-plot\""), "root is classed");
        assertTrue(svg.contains("data-domain=\"-2,2\""), "domain recorded for scripting");
        assertTrue(svg.contains("data-range=\"-3,3\""), "range recorded for scripting");
        assertTrue(svg.contains("class=\"curve\""), "curves are classed");
        assertTrue(svg.contains("class=\"asymptote\"") && svg.contains("data-x=\"0.385\""),
            "asymptote carries its class and data-x");
        assertTrue(svg.contains("feature zero") && svg.contains("data-kind=\"zero\""),
            "a zero feature is classed by kind with a data-kind hook");
        assertTrue(svg.contains("feature optimum"), "an optimum feature is classed by kind");
        assertTrue(svg.contains("tick-label"), "tick labels are classed");
    }

    @Test
    void export_enclosureBandShipsHiddenByDefault() {
        String svg = SvgPlotWriter.write(scene(), 900, 550);
        assertTrue(svg.contains("class=\"enclosure-band\""), "the enclosure band is emitted");
        assertTrue(svg.contains("display:none"), "the band is hidden by default (host opts in)");
    }

    @Test
    void export_omitsOptionalLayersCleanly() {
        // No asymptotes / features / enclosure → those groups are simply absent, still well-formed.
        PlotFrame frame = new PlotFrame(0f, 0f, 10f, 5.5f, Axis.linear(0, 1), Axis.linear(0, 1));
        var scene = new PlotScene2D(frame,
            List.of(Series.line(new double[]{0, 1}, new double[]{0, 1}, Color.WHITE)),
            List.of(), List.of(), null);
        String svg = SvgPlotWriter.write(scene, 400, 300);
        assertDoesNotThrow(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8))));
        assertTrue(svg.contains("class=\"curve\"") && !svg.contains("class=\"enclosure-band\""),
            "curve present, no enclosure band when none supplied");
    }
}
