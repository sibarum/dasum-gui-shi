package sibarum.dasum.gui.vis.plot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure geometry for {@link PlotView#displayTile}: the visible-rect tile that
 * a DISPLAY-resolution field map rasterizes. A 6×6 world frame, square
 * viewport.
 */
class DisplayTileTest {

    private static final PlotFrame FRAME =
        new PlotFrame(0f, 0f, 6f, 6f, Axis.linear(-1.6, 1.6), Axis.linear(-1.6, 1.6));

    private static final int CAP = 2048;

    @Test
    void framedExactlyGivesFullFrameAtOneSamplePerPixel() {
        // Camera centered on the frame, ortho half-height 3 → visible rect is
        // exactly the 6×6 frame. A 600px square viewport → ~1 sample/pixel.
        PlotView.Tile t = PlotView.displayTile(FRAME, 3f, 3f, 3f, 600, 600, CAP);
        assertNotNull(t);
        assertEquals(0f, t.x0(), 1e-3f);
        assertEquals(6f, t.x1(), 1e-3f);
        assertEquals(600, t.w(), "full frame fills the viewport → one sample per pixel");
        assertEquals(600, t.h());
    }

    @Test
    void zoomingInClipsToVisibleAndKeepsPixelDensity() {
        // Zoom in 3× (orthoScale 1 → visible half-extent 1 around center 3,3):
        // visible rect [2,4]×[2,4], fully inside the frame. Still ~1 sample/px,
        // but now those samples cover a 2×2 world region — real detail, not
        // magnified texels.
        PlotView.Tile t = PlotView.displayTile(FRAME, 3f, 3f, 1f, 600, 600, CAP);
        assertNotNull(t);
        assertEquals(2f, t.x0(), 1e-3f);
        assertEquals(4f, t.x1(), 1e-3f);
        assertEquals(600, t.w(), "clipped visible region still gets one sample per pixel");
    }

    @Test
    void pannedFullyAwayGivesNoTile() {
        // Center far outside the frame, small visible rect → no overlap.
        assertNull(PlotView.displayTile(FRAME, 100f, 100f, 1f, 600, 600, CAP));
    }

    @Test
    void resolutionIsCapped() {
        // Absurd viewport size is clamped so the buffer stays bounded.
        PlotView.Tile t = PlotView.displayTile(FRAME, 3f, 3f, 3f, 100_000, 100_000, CAP);
        assertNotNull(t);
        assertTrue(t.w() <= CAP && t.h() <= CAP, "samples per axis capped at " + CAP);
    }

    @Test
    void nonSquareViewportWidensVisibleRectByAspect() {
        // 800×400 viewport, orthoScale 3 → visible half-width = 3 * 2 = 6, so
        // the visible rect spans x ∈ [-3, 9]; clipped to the frame [0,6] it's
        // the full width. Height stays the frame's 6.
        PlotView.Tile t = PlotView.displayTile(FRAME, 3f, 3f, 3f, 800, 400, CAP);
        assertNotNull(t);
        assertEquals(0f, t.x0(), 1e-3f);
        assertEquals(6f, t.x1(), 1e-3f);
        // visible width is 12 world units; the 6-wide frame is half of it, so
        // it gets half the 800 horizontal pixels.
        assertEquals(400, t.w(), 1, "frame covers half the wide viewport → half its pixels");
    }
}
