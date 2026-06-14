package sibarum.dasum.gui.core.nav;

import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.command.CommandRegistry;
import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.input.FocusState;
import sibarum.dasum.gui.core.input.ScrollStates;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.Layout;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.reactive.Property;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.theme.Themed;
import sibarum.dasum.gui.core.theme.Variant;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless end-to-end of the navigation engine: build a tree, lay it out into
 * {@link LatestLayout}, then assert {@link Navigator#navigate} switches the
 * right tab and (after the next stored layout) focuses + scrolls the target
 * into view. Layout only lays out the active tab panel, so the
 * one-store-later completion is exercised the same way the event loop drives it.
 */
final class NavigatorTest {

    private static final Color CLEAR = new Color(0f, 0f, 0f, 0f);
    private static final PixelRect VP = new PixelRect(0f, 0f, 420f, 440f);

    private static Component.Flex column(List<Component> children) {
        return new Component.Flex(
            null, null, Em.ZERO, CLEAR,
            Direction.COLUMN, JustifyContent.START, AlignItems.START, Em.ZERO,
            children, false, 0);
    }

    @Test
    void navigatesAcrossTabsThenFocusesAndScrolls() {
        FocusState.clear();

        // Tab 0: a small box. Tab 1: a 10em scroll whose content is a 30em
        // spacer above the target, so the target sits well below the fold.
        Component.Box panel0 = new Component.Box(Em.of(5f), Em.of(5f), Em.ZERO, CLEAR);
        Component target = NavId.tag(
            new Component.Box(Em.of(8f), Em.of(2f), Em.ZERO, CLEAR), "dest.deep");
        Component.Box spacer = new Component.Box(Em.of(8f), Em.of(30f), Em.ZERO, CLEAR);
        Component.Scroll scroll = new Component.Scroll(
            Em.of(10f), Em.of(10f), Em.ZERO, CLEAR, column(List.of(spacer, target)));

        Property<Integer> activeIndex = new Property<>(0);
        Component.Tabs tabs = Themed.tabs(
            Em.of(25f), Em.of(25f), Em.of(2f), Em.of(0.5f), Em.of(0.3f), Em.of(1f),
            List.of(new Component.Tabs.TabPanel("One", panel0),
                    new Component.Tabs.TabPanel("Two", scroll)),
            activeIndex, Variant.PRIMARY);

        LatestLayout.store(tabs, Layout.compute(tabs, VP));
        NavRegistry.register("dest.deep", "Deep destination");

        Navigator.navigate("dest.deep");

        // Tab switch is immediate; focus/scroll is deferred until the now-active
        // panel has a laid-out rect (next stored layout).
        assertEquals(1, activeIndex.get(), "navigate switched to the tab containing the target");
        assertNull(FocusState.focused(), "focus deferred until the destination panel is laid out");

        LatestLayout.store(tabs, Layout.compute(tabs, VP)); // next frame

        assertSame(target, FocusState.focused(), "target focused after relayout");
        assertTrue(ScrollStates.of(scroll).emY() > 0f,
            "ancestor scroll moved to bring the below-the-fold target into view");
    }

    @Test
    void navigateByReferenceFocusesAfterNextStore() {
        FocusState.clear();
        Component.Box target = new Component.Box(Em.of(4f), Em.of(2f), Em.ZERO, CLEAR);
        Component.Flex root = new Component.Flex(
            Em.of(10f), Em.of(10f), Em.ZERO, CLEAR,
            Direction.COLUMN, JustifyContent.START, AlignItems.START, Em.ZERO,
            List.of(target), false, 0);

        LatestLayout.store(root, Layout.compute(root, new PixelRect(0f, 0f, 160f, 160f)));
        Navigator.navigate(target);
        LatestLayout.store(root, Layout.compute(root, new PixelRect(0f, 0f, 160f, 160f)));

        assertSame(target, FocusState.focused(), "reference navigation focuses an already-visible target");
    }

    @Test
    void unknownIdIsNoOp() {
        FocusState.clear();
        Component.Box box = new Component.Box(Em.of(5f), Em.of(5f), Em.ZERO, CLEAR);
        LatestLayout.store(box, Layout.compute(box, new PixelRect(0f, 0f, 160f, 160f)));

        Navigator.navigate("nope.not.registered"); // must not throw
        LatestLayout.store(box, Layout.compute(box, new PixelRect(0f, 0f, 160f, 160f)));

        assertNull(FocusState.focused(), "unknown destination leaves focus untouched");
    }

    @Test
    void registerPublishesGoToCommand() {
        NavRegistry.register("widgets.demo", "Demo widget");
        boolean published = CommandRegistry.filter("Go to: Demo widget").stream()
            .anyMatch(c -> c.id().equals("nav.widgets.demo"));
        assertTrue(published, "registering a destination publishes a Go to: command for the Everything Menu");
    }
}
