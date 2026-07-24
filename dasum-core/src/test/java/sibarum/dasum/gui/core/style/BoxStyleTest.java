package sibarum.dasum.gui.core.style;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Value-type invariants for the corner-radius / border styling primitives. */
final class BoxStyleTest {

    @Test
    void cornerRadiiHelpers() {
        CornerRadii uniform = CornerRadii.all(Em.of(0.5f));
        assertEquals(Em.of(0.5f), uniform.tl());
        assertEquals(Em.of(0.5f), uniform.br());
        assertTrue(uniform.rounded());

        CornerRadii top = CornerRadii.top(Em.of(0.4f));
        assertEquals(Em.of(0.4f), top.tl());
        assertEquals(Em.of(0.4f), top.tr());
        assertEquals(Em.ZERO, top.br());
        assertEquals(Em.ZERO, top.bl());

        assertFalse(CornerRadii.NONE.rounded());
    }

    @Test
    void cornerRadiiRejectsNullAndAuto() {
        assertThrows(IllegalArgumentException.class,
            () -> new CornerRadii(null, Em.ZERO, Em.ZERO, Em.ZERO));
        assertThrows(IllegalArgumentException.class,
            () -> new CornerRadii(Em.AUTO, Em.ZERO, Em.ZERO, Em.ZERO));
    }

    @Test
    void borderVisibility() {
        assertFalse(Border.NONE.visible());
        assertFalse(Border.of(Em.ZERO, Color.WHITE).visible(), "zero width is invisible");
        assertFalse(Border.of(Em.of(1f), Color.TRANSPARENT).visible(), "transparent color is invisible");
        assertTrue(Border.of(Em.of(0.1f), Color.WHITE).visible());
    }

    @Test
    void borderRejectsNulls() {
        assertThrows(IllegalArgumentException.class, () -> new Border(null, Color.WHITE));
        assertThrows(IllegalArgumentException.class, () -> new Border(Em.of(1f), null));
        assertThrows(IllegalArgumentException.class, () -> new Border(Em.AUTO, Color.WHITE));
    }

    @Test
    void boxStyleNullComponentsNormalizeToNone() {
        BoxStyle s = new BoxStyle(null, null);
        assertSame(CornerRadii.NONE, s.radii());
        assertSame(Border.NONE, s.border());
        assertTrue(s.isPlain(), "no rounding + no border reads as plain");
    }

    @Test
    void boxStylePlainVsVisible() {
        assertTrue(BoxStyle.NONE.isPlain());
        assertFalse(BoxStyle.rounded(Em.of(0.5f)).isPlain(), "rounding makes it non-plain");
        assertFalse(BoxStyle.bordered(Em.of(0.1f), Color.WHITE).isPlain(), "border makes it non-plain");
        // A style whose border is present but invisible and corners square is plain.
        assertTrue(new BoxStyle(CornerRadii.NONE, Border.of(Em.ZERO, Color.WHITE)).isPlain());
    }
}
