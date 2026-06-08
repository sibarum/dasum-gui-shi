package sibarum.dasum.gui.vis.scene;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.vis.math.Vec3;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The CPU CSG evaluator must match the analytic box SDF (and the shader it mirrors). */
final class CsgFieldTest {

    @Test
    void singleBoxDistances() {
        ScalarField f = CsgField.of(
            List.of(new CsgBox(CsgBox.Op.UNION, Vec3.ZERO, new Vec3(1f, 1f, 1f))), 0f);
        assertEquals(-1f, f.at(0f, 0f, 0f), 1e-5f, "centre: distance to nearest face, inside");
        assertEquals(0f, f.at(1f, 0f, 0f), 1e-5f, "on a face");
        assertEquals(1f, f.at(2f, 0f, 0f), 1e-5f, "one unit outside a face");
        // Outside a corner: Euclidean distance to the corner (1,1,1).
        assertEquals((float) Math.sqrt(3.0), f.at(2f, 2f, 2f), 1e-5f, "corner distance");
    }

    @Test
    void roundingShrinksTheField() {
        var ops = List.of(new CsgBox(CsgBox.Op.UNION, Vec3.ZERO, new Vec3(1f, 1f, 1f)));
        assertEquals(1f - 0.2f, CsgField.of(ops, 0.2f).at(2f, 0f, 0f), 1e-5f,
            "global rounding subtracts from the distance (inflates the solid)");
    }

    @Test
    void subtractCarvesAndNormalsPointOutward() {
        // A box with a smaller box subtracted from its +x face region.
        ScalarField f = CsgField.of(List.of(
            new CsgBox(CsgBox.Op.UNION, Vec3.ZERO, new Vec3(1f, 1f, 1f)),
            new CsgBox(CsgBox.Op.SUBTRACT, new Vec3(1f, 0f, 0f), new Vec3(0.5f, 0.5f, 0.5f))
        ), 0f);
        // A point in the carved notch is now OUTSIDE the solid (positive).
        assertTrue(f.at(0.9f, 0f, 0f) > 0f, "subtracted region reads as outside");

        // Normal on the top face (+y) points up.
        Vec3 n = CsgField.normal(f, 0f, 1f, 0f, 1e-3f);
        assertTrue(n.y() > 0.9f, "top-face normal points +y, got " + n.y());
    }
}
