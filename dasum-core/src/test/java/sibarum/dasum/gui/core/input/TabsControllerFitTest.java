package sibarum.dasum.gui.core.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the overflow fit math behind {@link Component.Tabs} header
 * strips. Exercises {@link TabsController#fitCount} directly — it's pure
 * (widths in, count out) so it needs no font context or GL, unlike the full
 * {@link TabsController#stripLayout} which measures labels.
 */
final class TabsControllerFitTest {

    private static final float MARKER = 20f;

    @Test
    void everythingFitsReturnsFullCountAndNoMarker() {
        float[] widths = {30f, 30f, 30f};
        // Total 90 <= 100: all fit, marker not even reserved.
        assertEquals(3, TabsController.fitCount(widths, 100f, MARKER));
    }

    @Test
    void exactFitStillCountsAsFits() {
        float[] widths = {40f, 60f};
        // Total 100 == available: boundary counts as fitting (<=), no marker.
        assertEquals(2, TabsController.fitCount(widths, 100f, MARKER));
    }

    @Test
    void overflowReservesMarkerAndFitsLeadingPrefix() {
        float[] widths = {30f, 30f, 30f, 30f};
        // Total 120 > 100 → reserve marker (20), usable = 80 → 2 cells (60) fit,
        // a 3rd (90) would exceed 80.
        assertEquals(2, TabsController.fitCount(widths, 100f, MARKER));
    }

    @Test
    void reservingMarkerCanPushABorderlineTabIntoOverflow() {
        float[] widths = {50f, 50f};
        // Total 100 == available so WITHOUT a marker both fit; but 100 is not
        // strictly less... it's <=, so this returns 2 (no marker). Verify the
        // <= boundary: raise total above available to force the marker path.
        assertEquals(2, TabsController.fitCount(widths, 100f, MARKER));
        // Now 101 > 100 → marker reserved, usable 80 → only the first 50 fits.
        assertEquals(1, TabsController.fitCount(new float[]{50f, 51f}, 100f, MARKER));
    }

    @Test
    void degenerateTinyStripFitsNothingButStaysAccessibleViaMarker() {
        float[] widths = {40f, 40f};
        // Total 80 > 30 → marker reserved, usable = 30 - 20 = 10 → no cell fits.
        // Count 0 means the strip shows only the marker; all tabs live in the menu.
        assertEquals(0, TabsController.fitCount(widths, 30f, MARKER));
    }

    @Test
    void singleTabAlwaysFitsWhenNarrowerThanStrip() {
        assertEquals(1, TabsController.fitCount(new float[]{50f}, 100f, MARKER));
    }
}
