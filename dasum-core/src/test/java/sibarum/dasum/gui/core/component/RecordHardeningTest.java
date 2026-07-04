package sibarum.dasum.gui.core.component;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.reactive.Property;
import sibarum.dasum.gui.core.render.Color;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fail-fast canonical constructors turn the framework's classic
 * "null now, {@code NullPointerException} in the render pass later" into an
 * immediate, self-describing {@link IllegalArgumentException} at the exact
 * construction site — while leaving the legitimate null sentinels alone.
 */
final class RecordHardeningTest {

    private static final Color C = Color.WHITE;

    @Test
    void flexNullColorThrowsWithHelpfulMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new Component.Flex(null, null, Em.ZERO, null,
                Direction.ROW, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
                List.of(), false, 0));
        assertTrue(ex.getMessage().contains("Flex") && ex.getMessage().contains("color"),
            "message names the type and field: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("TRANSPARENT"), "message suggests the fix");
    }

    @Test
    void flexNullWidthHeightStillAllowed() {
        assertDoesNotThrow(() -> new Component.Flex(null, null, Em.ZERO, Color.TRANSPARENT,
            Direction.ROW, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(), false, 0));
    }

    @Test
    void flexNullChildrenThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            new Component.Flex(null, null, Em.ZERO, Color.TRANSPARENT,
                Direction.ROW, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
                null, false, 0));
    }

    @Test
    void flexNullChildElementThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            new Component.Flex(null, null, Em.ZERO, Color.TRANSPARENT,
                Direction.ROW, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
                Arrays.asList(new Component.Text("x", Em.of(1), C), null), false, 0));
    }

    @Test
    void flexNullEnumThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            new Component.Flex(null, null, Em.ZERO, Color.TRANSPARENT,
                null, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
                List.of(), false, 0));
    }

    @Test
    void boxRequiresConcreteSize() {
        assertThrows(IllegalArgumentException.class, () ->
            new Component.Box(null, Em.of(1), Em.ZERO, Color.TRANSPARENT));
        IllegalArgumentException auto = assertThrows(IllegalArgumentException.class, () ->
            new Component.Box(Em.AUTO, Em.of(1), Em.ZERO, Color.TRANSPARENT));
        assertTrue(auto.getMessage().contains("AUTO"), auto.getMessage());
    }

    @Test
    void textNullContentAndColorThrow() {
        assertThrows(IllegalArgumentException.class, () -> new Component.Text(null, Em.of(1), C));
        assertThrows(IllegalArgumentException.class, () -> new Component.Text("x", Em.of(1), null));
    }

    @Test
    void negativeFlexGrowThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            new Component.Box(Em.of(1), Em.of(1), Em.ZERO, Color.TRANSPARENT, List.of(), false, -1));
    }

    @Test
    void sliderRequiresMinLessThanMax() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new Component.Slider(Direction.ROW, Em.of(1), Em.of(1), Em.of(1),
                C, C, C, new Property<>(0f), 5f, 5f));
        assertTrue(ex.getMessage().contains("min < max"), ex.getMessage());
        assertDoesNotThrow(() -> new Component.Slider(Direction.ROW, Em.of(1), Em.of(1), Em.of(1),
            C, C, C, new Property<>(0f), 0f, 1f));
    }
}
