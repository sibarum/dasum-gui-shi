package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.em.EmContext;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.layout.HitTest;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.overlay.Anchor;
import sibarum.dasum.gui.core.overlay.OverlayStack;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.natives.glfw.Glfw;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Dispatcher for right-click context menus. On {@link #onMouseDown},
 * resolves the deepest hit, walks innermost-out along the hit path
 * looking for an explicit {@link ContextMenuProvider} registered via
 * {@link Handlers#onContextMenu}; if none is found and the deepest hit is
 * a selectable {@link Component.Text}, falls back to
 * {@link TextContextMenu#defaultProvider()}.
 * <p>
 * The popup is a flex column pushed onto {@link OverlayStack} anchored at
 * the cursor in em coordinates. Keyboard navigation (arrows / Enter /
 * Escape) and click activation dismiss the menu. Hover over a row moves
 * the keyboard selection to that row. Click outside the popup dismisses
 * via standard modal-overlay mechanics.
 */
public final class ContextMenuController {

    private static final Color MENU_BG     = new Color(0.10f, 0.12f, 0.16f, 1f);
    private static final Color MENU_BORDER = new Color(0.30f, 0.55f, 0.85f, 0.9f);
    private static final Color ROW_FG      = new Color(0.92f, 0.94f, 0.97f, 1f);
    private static final Color ROW_FG_DIM  = new Color(0.50f, 0.52f, 0.57f, 1f);
    private static final Color ROW_BG_SEL  = new Color(0.30f, 0.55f, 0.85f, 1f);
    private static final Color ROW_FG_SEL  = new Color(0.97f, 0.98f, 1.00f, 1f);
    private static final Color SEP_BG      = new Color(0.25f, 0.27f, 0.32f, 1f);
    private static final Color TRANSPARENT = new Color(0f, 0f, 0f, 0f);

    private static boolean menuOpen = false;
    private static List<ContextMenuItem> currentItems = List.of();
    private static final List<Component> currentRows = new ArrayList<>(); // parallel to currentItems (separators included)
    private static int selectedIndex = -1;

    private ContextMenuController() {}

    // ---------- public API ----------

    public static boolean isOpen() { return menuOpen; }

    /**
     * Handle a right-mouse-button press. Returns {@code true} when a menu
     * was opened ({@code false} when no provider matched or the provider
     * returned an empty item list).
     */
    public static boolean onMouseDown(double pxX, double pxY, int modifiers, MemorySegment windowHandle) {
        // Re-opening: close any existing context menu before opening a new one
        // at the new cursor position.
        if (menuOpen) close();

        LayoutResult lr = LatestLayout.result();
        Component root = OverlayStack.activeInputRoot(LatestLayout.root());
        if (lr == null || root == null) return false;

        Component deepest = HitTest.test(root, lr, (float) pxX, (float) pxY);
        if (deepest == null) return false;

        // Innermost-out: walk deepest → root, take the first registered provider.
        List<Component> path = HitTest.pathTo(root, deepest);
        Component owner = null;
        ContextMenuProvider provider = null;
        for (int i = path.size() - 1; i >= 0; i--) {
            ContextMenuProvider p = ContextMenuStates.get(path.get(i));
            if (p != null) { provider = p; owner = path.get(i); break; }
        }
        // Fallback: selectable Text gets the built-in default.
        if (provider == null
            && deepest instanceof Component.Text t && t.selectable()) {
            provider = TextContextMenu.defaultProvider();
            owner = deepest;
        }
        if (provider == null) return false;

        float pxPerEm = EmContext.pixelsPerEm();
        if (pxPerEm <= 0f) return false;
        float emX = (float) pxX / pxPerEm;
        float emY = (float) pxY / pxPerEm;

        ContextEvent event = new ContextEvent(owner, deepest, path, emX, emY, modifiers, windowHandle);
        List<ContextMenuItem> items = provider.itemsFor(event);
        if (items == null || items.isEmpty()) return false;

        currentItems = items;
        selectedIndex = firstNavigableIndex(items);
        menuOpen = true;
        Component popup = buildPopup();
        OverlayStack.push(new OverlayStack.Overlay(
            popup, Anchor.at(Em.of(emX), Em.of(emY)), true,
            ContextMenuController::onDismiss));
        return true;
    }

    /**
     * No-op for now. Hover-follow-selection used to rebuild the popup on
     * every cursor move, which broke clicks: rebuilds change row identity,
     * the cursor-pos listener then HitTests the new tree against the
     * still-stale layout, clobbers HoverState, and the press → release
     * "released-on-same-target" check fails. Clicks work on any visible
     * row regardless of the highlighted-row state; keyboard arrows still
     * move the highlight. Restoring hover indication needs a renderer-
     * level mechanism that doesn't replace component identities.
     */
    public static void onCursorMove(double pxX, double pxY) {
        // intentionally empty
    }

    /**
     * Intercept arrow / Enter / Escape when the menu is open. Returns true
     * when the key was consumed. Callers should invoke this before any
     * other key handler so the keys don't fall through to focused widgets.
     */
    public static boolean handleKey(int key) {
        if (!menuOpen) return false;
        switch (key) {
            case Glfw.GLFW_KEY_UP -> {
                int idx = prevNavigableIndex(selectedIndex);
                if (idx != selectedIndex) {
                    selectedIndex = idx;
                    rebuild();
                }
                return true;
            }
            case Glfw.GLFW_KEY_DOWN -> {
                int idx = nextNavigableIndex(selectedIndex);
                if (idx != selectedIndex) {
                    selectedIndex = idx;
                    rebuild();
                }
                return true;
            }
            case Glfw.GLFW_KEY_ENTER -> { activateSelected(); return true; }
            case Glfw.GLFW_KEY_ESCAPE -> { close(); return true; }
            default -> { return false; }
        }
    }

    public static void close() {
        if (!menuOpen) return;
        OverlayStack.pop();
    }

    // ---------- internals ----------

    private static void onDismiss() {
        menuOpen = false;
        for (Component row : currentRows) Handlers.clearAll(row);
        currentRows.clear();
        currentItems = List.of();
        selectedIndex = -1;
        Invalidator.invalidate();
    }

    private static void activateSelected() {
        if (selectedIndex < 0 || selectedIndex >= currentItems.size()) { close(); return; }
        ContextMenuItem it = currentItems.get(selectedIndex);
        if (!isNavigable(it)) { close(); return; }
        close();
        if (it.action() != null) it.action().run();
    }

    private static void rebuild() {
        if (!menuOpen) return;
        for (Component row : currentRows) Handlers.clearAll(row);
        currentRows.clear();
        Component popup = buildPopup();
        OverlayStack.replaceTopmost(popup);
    }

    private static Component buildPopup() {
        List<Component> rows = new ArrayList<>(currentItems.size());
        for (int i = 0; i < currentItems.size(); i++) {
            ContextMenuItem it = currentItems.get(i);
            if (it.isSeparator()) {
                // Flex (not Box) — Box requires non-null width; Flex with
                // width=null stretches to fill the parent column.
                Component sep = new Component.Flex(
                    null, Em.of(0.08f), Em.ZERO, SEP_BG,
                    Direction.ROW, JustifyContent.START, AlignItems.START, Em.ZERO,
                    List.of(), false, 0
                );
                rows.add(sep);
                currentRows.add(sep);
                continue;
            }
            boolean selected = (i == selectedIndex) && it.enabled();
            Color rowBg = selected ? ROW_BG_SEL : TRANSPARENT;
            Color rowFg = !it.enabled() ? ROW_FG_DIM : (selected ? ROW_FG_SEL : ROW_FG);

            Component.Text label = new Component.Text(
                it.label(), FontGroups.DEFAULT, Em.of(0.95f), rowFg,
                null, null, Em.of(0.25f),
                null, false,
                false, false, false, false, 0
            );
            // AUTO width so each row's intrinsic cross-size = label width
            // (Layout.fitContentCross of the inner column then picks the
            // widest row). AlignItems.STRETCH on the inner makes every row
            // expand to that width so the selected-row background spans
            // the full popup width.
            Component.Flex row = new Component.Flex(
                Em.AUTO, Em.of(1.6f), Em.of(0.3f), rowBg,
                Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.ZERO,
                List.of(label),
                true, 0
            );
            final int idx = i;
            Handlers.onClick(row, () -> {
                selectedIndex = idx;
                activateSelected();
            });
            rows.add(row);
            currentRows.add(row);
        }

        // Em.AUTO on both axes → Layout.fitAxisSize → fitContentCross /
        // fitContentMain. Width = widest row's intrinsic; height = sum of
        // row heights + padding. The popup hugs its content with no
        // fixed-size leaks into the API.
        Component.Flex inner = new Component.Flex(
            Em.AUTO, Em.AUTO, Em.of(0.25f), MENU_BG,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            rows, false, 0
        );
        return new Component.Flex(
            Em.AUTO, Em.AUTO, Em.of(0.08f), MENU_BORDER,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(inner), false, 0
        );
    }

    private static int firstNavigableIndex(List<ContextMenuItem> items) {
        for (int i = 0; i < items.size(); i++) {
            if (isNavigable(items.get(i))) return i;
        }
        return -1;
    }

    private static int nextNavigableIndex(int from) {
        if (currentItems.isEmpty()) return -1;
        int n = currentItems.size();
        int start = (from < 0) ? -1 : from;
        for (int step = 1; step <= n; step++) {
            int i = ((start + step) % n + n) % n;
            if (isNavigable(currentItems.get(i))) return i;
        }
        return from;
    }

    private static int prevNavigableIndex(int from) {
        if (currentItems.isEmpty()) return -1;
        int n = currentItems.size();
        int start = (from < 0) ? n : from;
        for (int step = 1; step <= n; step++) {
            int i = ((start - step) % n + n) % n;
            if (isNavigable(currentItems.get(i))) return i;
        }
        return from;
    }

    private static boolean isNavigable(ContextMenuItem it) {
        return !it.isSeparator() && it.enabled();
    }
}
