package sibarum.dasum.gui.core.overlay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Components;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.input.HoverState;
import sibarum.dasum.gui.core.input.InputState;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.natives.glfw.Glfw;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link TooltipController} — the state machine that decides
 * which component's tooltip is shown each frame.
 * <p>
 * Tests fabricate a minimal {@link LayoutResult} by writing into an
 * {@code IdentityHashMap} — no font / no GLFW / no rendering required.
 */
final class TooltipControllerTest {

    private Component.Box leaf;
    private Component.Box outside;
    private Component.Flex root;
    private LayoutResult layout;

    @BeforeEach
    void setUp() {
        TooltipController.resetForTest();
        TooltipController.setDwellMillis(0);   // most tests want instant show
        HoverState.clear();
        // Pretend the mouse is inside the window — required for the
        // controller to do anything useful.
        InputState.updateMousePos(50, 50);
        InputState.setMods(0);
        InputState.setLeftButtonHeld(false);

        leaf = new Component.Box(Em.of(2f), Em.of(2f), Em.ZERO,
            new Color(0.5f, 0.5f, 0.5f, 1f));
        outside = new Component.Box(Em.of(2f), Em.of(2f), Em.ZERO,
            new Color(0.3f, 0.3f, 0.3f, 1f));
        root = new Component.Flex(
            Em.of(20f), Em.of(20f), Em.ZERO, new Color(0f, 0f, 0f, 0f),
            Direction.ROW, JustifyContent.START, AlignItems.START, Em.ZERO,
            List.of(leaf, outside), false, 0
        );

        // Layout: root covers 0..200, leaf 0..100, outside 100..200 (1D for clarity).
        Map<Component, PixelRect> rects = new IdentityHashMap<>();
        rects.put(root,    new PixelRect(0f,   0f,   200f, 200f));
        rects.put(leaf,    new PixelRect(0f,   0f,   100f, 200f));
        rects.put(outside, new PixelRect(100f, 0f,   100f, 200f));
        layout = new LayoutResult(rects);
    }

    // ---------- baseline: tooltip resolves on hovered component ----------

    @Test
    void showsOnLeafHover_dwellZero_alwaysTrigger() {
        Tooltips.set(leaf, "hi");
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertTrue(TooltipController.isVisible(), "tooltip visible after instant dwell");
        assertEquals("hi", TooltipController.currentText());
    }

    @Test
    void hidesWhenCursorMovesOffComponent() {
        Tooltips.set(leaf, "leaf-text");
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertTrue(TooltipController.isVisible());

        // Move onto the sibling (no tooltip registered on `outside`).
        TooltipController.resolveBeforeRender(layout, root, 150, 10);
        assertFalse(TooltipController.isVisible(), "no tooltip for sibling without registration");
        assertNull(TooltipController.currentText());
    }

    @Test
    void hidesWhenMouseOutsideWindow() {
        Tooltips.set(leaf, "x");
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertTrue(TooltipController.isVisible());

        InputState.markMouseExited();
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertFalse(TooltipController.isVisible());
    }

    @Test
    void hidesDuringDrag_leftButtonHeld() {
        Tooltips.set(leaf, "x");
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertTrue(TooltipController.isVisible());

        InputState.setLeftButtonHeld(true);
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertFalse(TooltipController.isVisible(), "tooltip hides while dragging");
    }

    // ---------- trigger gating ----------

    @Test
    void modAltTrigger_requiresAltHeld() {
        TooltipController.setTrigger(TooltipTrigger.MOD_ALT);
        Tooltips.set(leaf, "alt-only");
        // No mods → invisible.
        InputState.setMods(0);
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertFalse(TooltipController.isVisible());
        // Alt held → visible.
        InputState.setMods(Glfw.GLFW_MOD_ALT);
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertTrue(TooltipController.isVisible());
        // Release alt → hidden again.
        InputState.setMods(0);
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertFalse(TooltipController.isVisible());
    }

    @Test
    void modCtrlAndModShift_independent() {
        TooltipController.setTrigger(TooltipTrigger.MOD_CTRL);
        Tooltips.set(leaf, "x");
        InputState.setMods(Glfw.GLFW_MOD_SHIFT);   // wrong mod
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertFalse(TooltipController.isVisible());

        InputState.setMods(Glfw.GLFW_MOD_CONTROL);
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertTrue(TooltipController.isVisible());
    }

    @Test
    void onModsChanged_hidesImmediatelyOnRelease() {
        TooltipController.setTrigger(TooltipTrigger.MOD_ALT);
        Tooltips.set(leaf, "x");
        InputState.setMods(Glfw.GLFW_MOD_ALT);
        TooltipController.onModsChanged(Glfw.GLFW_MOD_ALT);
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertTrue(TooltipController.isVisible());

        InputState.setMods(0);
        TooltipController.onModsChanged(0);
        // onModsChanged itself should hide on threshold-cross release.
        assertFalse(TooltipController.isVisible(), "release of mod hides immediately");
    }

    // ---------- ancestor walk ----------

    @Test
    void bubblesToAncestorWithRegisteredText() {
        // Tooltip on the root flex; leaf has no registration. Hovering
        // over the leaf should surface the root's text (innermost
        // ancestor wins).
        Tooltips.set(root, "ancestor");
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertTrue(TooltipController.isVisible());
        assertEquals("ancestor", TooltipController.currentText());
    }

    @Test
    void innermostAncestorWins() {
        Tooltips.set(root, "outer");
        Tooltips.set(leaf, "inner");
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertEquals("inner", TooltipController.currentText(),
            "innermost (leaf) wins over outer ancestor");
    }

    // ---------- non-interactive participates ----------

    @Test
    void nonInteractiveComponentCanCarryTooltip() {
        // The Box variant defaults to interactive=false. Confirm that
        // doesn't block tooltip display (HitTest.testAny ignores the flag).
        assertFalse(leaf.interactive());
        Tooltips.set(leaf, "passive");
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertTrue(TooltipController.isVisible());
        assertEquals("passive", TooltipController.currentText());
    }

    // ---------- text mutation ----------

    @Test
    void textChange_underStationaryCursor_restartsDwell() {
        TooltipController.setDwellMillis(50);   // measurable dwell
        Tooltips.set(leaf, "v1");
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        // First call: starts dwell, not yet visible.
        assertFalse(TooltipController.isVisible());
        sleep(80);
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertTrue(TooltipController.isVisible());
        assertEquals("v1", TooltipController.currentText());

        // Mutate text — controller should hide and restart dwell.
        Tooltips.set(leaf, "v2");
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertFalse(TooltipController.isVisible(), "text change resets dwell");
        sleep(80);
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertTrue(TooltipController.isVisible());
        assertEquals("v2", TooltipController.currentText());
    }

    // ---------- detach ----------

    @Test
    void detachOfCurrentTarget_hidesImmediately() {
        Tooltips.set(leaf, "doomed");
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertTrue(TooltipController.isVisible());

        // Detach the leaf — Components.detach calls Tooltips.clear which
        // calls TooltipController.onComponentDetached.
        Components.detach(leaf);
        assertFalse(TooltipController.isVisible(), "detach hides live tooltip");
        assertNull(TooltipController.currentText());
    }

    // ---------- hideAll ----------

    @Test
    void hideAll_clearsState() {
        Tooltips.set(leaf, "x");
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertTrue(TooltipController.isVisible());

        TooltipController.hideAll();
        assertFalse(TooltipController.isVisible());
        assertNull(TooltipController.currentText());
    }

    // ---------- silent UI rebuild under stationary cursor ----------

    @Test
    void layoutRebuild_movingComponentsUnderStationaryCursor_isDetected() {
        Tooltips.set(leaf, "leaf");
        Tooltips.set(outside, "outside");
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertEquals("leaf", TooltipController.currentText());

        // Now simulate: the UI was rebuilt and `outside` is now where `leaf`
        // was — but the mouse hasn't moved. Mutate the layout map directly.
        Map<Component, PixelRect> swapped = new IdentityHashMap<>();
        swapped.put(root,    new PixelRect(0f,   0f,   200f, 200f));
        swapped.put(leaf,    new PixelRect(100f, 0f,   100f, 200f));
        swapped.put(outside, new PixelRect(0f,   0f,   100f, 200f));
        LayoutResult swappedLayout = new LayoutResult(swapped);

        TooltipController.resolveBeforeRender(swappedLayout, root, 10, 10);
        // Now `outside` is under the cursor — its tooltip should show,
        // not the stale `leaf` one.
        assertEquals("outside", TooltipController.currentText(),
            "controller re-hit-tested against new layout");
    }

    // ---------- dwell timing ----------

    @Test
    void dwell_notVisibleBeforeThreshold() {
        TooltipController.setDwellMillis(150);
        Tooltips.set(leaf, "x");
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertFalse(TooltipController.isVisible(), "dwell delay enforced");
        sleep(40);
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertFalse(TooltipController.isVisible(), "still within dwell");
        sleep(160);
        TooltipController.resolveBeforeRender(layout, root, 10, 10);
        assertTrue(TooltipController.isVisible(), "dwell elapsed");
    }

    // ---------- migrate copies text ----------

    @Test
    void migrateBetweenComponentInstances_carriesTooltipForward() {
        // App swaps `leaf` for a `withColor()`-modified record; migrateState
        // copies sidecar entries. Tooltip should be reachable on the new
        // instance.
        Tooltips.set(leaf, "carry");
        Component.Box swapped = leaf.withColor(new Color(0.7f, 0.2f, 0.2f, 1f));
        Components.migrateState(leaf, swapped);
        assertEquals("carry", Tooltips.get(swapped));
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
