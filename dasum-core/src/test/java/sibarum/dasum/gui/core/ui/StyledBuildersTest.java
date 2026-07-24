package sibarum.dasum.gui.core.ui;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.style.BoxStyle;
import sibarum.dasum.gui.core.theme.Theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Builders and themed factories surface the new corner-radius / border style. */
final class StyledBuildersTest {

    @Test
    void themedButtonPicksUpDefaultRadius() {
        Component b = Ui.button("Go").build();
        Component.Flex f = assertInstanceOf(Component.Flex.class, b);
        assertTrue(f.style() != null && f.style().radii().rounded(),
            "themed button is rounded by the theme default");
        assertEquals(Theme.buttonCornerRadius(), f.style().radii().tl());
    }

    @Test
    void buttonBuilderOverridesRadiusAndBorder() {
        Component b = Ui.button("Edit")
            .cornerRadius(Em.of(1f))
            .border(Em.of(0.1f), Color.WHITE)
            .build();
        Component.Flex f = assertInstanceOf(Component.Flex.class, b);
        BoxStyle s = f.style();
        assertEquals(Em.of(1f), s.radii().tl());
        assertTrue(s.border().visible());
        assertEquals(Color.WHITE, s.border().color());
    }

    @Test
    void boxBuilderWithoutStyleStaysFlat() {
        Component.Box box = (Component.Box) Ui.box().size(Em.of(4f), Em.of(2f)).build();
        assertNull(box.style(), "an unstyled box keeps the flat fast-path (null style)");
    }

    @Test
    void flexBuilderPerCornerRadii() {
        Component.Flex f = (Component.Flex) Ui.column()
            .corners(Em.of(0.5f), Em.of(0.5f), Em.ZERO, Em.ZERO)
            .build();
        assertEquals(Em.of(0.5f), f.style().radii().tl());
        assertEquals(Em.ZERO, f.style().radii().br());
        assertFalse(f.style().border().visible());
    }

    @Test
    void roundTripThroughFromPreservesStyle() {
        Component.Flex original = (Component.Flex) Ui.row().cornerRadius(Em.of(0.3f)).build();
        Component.Flex copy = (Component.Flex) Ui.from(original).build();
        assertEquals(original.style().radii().tl(), copy.style().radii().tl());
    }
}
