package sibarum.dasum.gui.vis.scene;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CPU Mandelbulb: distance sign + the escape-iteration complexity channel. */
final class MandelbulbFieldTest {

    @Test
    void exteriorDistanceIsPositiveAndGrowsWithDistance() {
        // The Mandelbulb DE is an EXTERIOR estimator (à la iq): positive
        // and roughly increasing outside the set, ~0/unreliable inside —
        // the marcher only ever approaches the boundary from outside, so
        // it never needs a signed interior. (This matches the shader.)
        MandelbulbField b = new MandelbulbField(8f, 16);
        assertTrue(b.at(5f, 0f, 0f) > 0f, "well outside → positive distance estimate");
        assertTrue(b.at(5f, 0f, 0f) > b.at(2f, 0f, 0f), "farther outside → larger estimate");
    }

    @Test
    void complexityIsEscapeFractionInUnitRange() {
        MandelbulbField b = new MandelbulbField(8f, 16);
        // Far outside escapes on the first iteration → ~0 complexity.
        assertEquals(0f, b.complexityAt(3f, 0f, 0f), 1e-6f);
        // Deep inside never escapes → max complexity (esc == iters).
        assertEquals(1f, b.complexityAt(0f, 0f, 0f), 1e-6f);
        // A near-surface point sits between the extremes.
        float c = b.complexityAt(1.0f, 0f, 0f);
        assertTrue(c > 0f && c <= 1f, "near-surface complexity in (0,1], got " + c);
    }
}
