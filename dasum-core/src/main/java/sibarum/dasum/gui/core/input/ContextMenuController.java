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
import java.util.Locale;

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
    private static final Color INPUT_BG    = new Color(0.13f, 0.16f, 0.21f, 1f);
    private static final Color TRANSPARENT = new Color(0f, 0f, 0f, 0f);

    /** Item count at or below which the popup fits to content with no
     *  filter input or scroll — keeps short menus tight. */
    private static final int FIT_TO_CONTENT_ITEMS = 10;
    /** Width of the popup when it switches to long-menu mode (filter +
     *  scroll). Fixed because mixing AUTO width with a Scroll's bounded
     *  height makes filter typing reflow the popup awkwardly. */
    private static final Em LONG_MODE_WIDTH = Em.of(22f);
    /** Max scroll viewport height for the row list in long-menu mode. */
    private static final Em LONG_MODE_SCROLL_HEIGHT = Em.of(16f);

    private static boolean menuOpen = false;
    private static List<ContextMenuItem> currentItems = List.of();
    /** Currently-rendered rows, parallel to {@link #visibleIndices}. */
    private static final List<Component> currentRows = new ArrayList<>();
    /** Indices into {@link #currentItems} that match the active filter,
     *  in display order. When filter is empty, equals 0..items-1. */
    private static List<Integer> visibleIndices = List.of();
    /** Index into {@link #visibleIndices} pointing at the highlighted row;
     *  {@code -1} if no row is highlightable. */
    private static int selectedVisible = -1;
    /** Stable Text component for the filter input. Reused across rebuilds
     *  to preserve its caret position. {@code null} when the popup is
     *  closed or in fit-to-content (no-filter) mode. */
    private static Component.Text filterInput = null;

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
        if (deepest == null) {
            sibarum.dasum.gui.core.debug.Dbg.log("ctx-menu", () ->
                "right-click at (" + pxX + "," + pxY + ") hit no component");
            return false;
        }
        sibarum.dasum.gui.core.debug.Dbg.log("ctx-menu", () ->
            "right-click at (" + pxX + "," + pxY + ") hit "
                + sibarum.dasum.gui.core.debug.Dbg.id(deepest));

        // Innermost-out: walk deepest → root, take the first registered provider.
        List<Component> path = HitTest.pathTo(root, deepest);
        Component owner = null;
        ContextMenuProvider provider = null;
        for (int i = path.size() - 1; i >= 0; i--) {
            ContextMenuProvider p = ContextMenuStates.get(path.get(i));
            if (p != null) { provider = p; owner = path.get(i); break; }
        }
        if (provider != null) {
            final Component finalOwner = owner;
            sibarum.dasum.gui.core.debug.Dbg.log("ctx-menu", () ->
                "provider resolved on " + sibarum.dasum.gui.core.debug.Dbg.id(finalOwner));
        }
        // Fallback: selectable Text gets the built-in default.
        if (provider == null
            && deepest instanceof Component.Text t && t.selectable()) {
            provider = TextContextMenu.defaultProvider();
            owner = deepest;
        }
        if (provider == null) {
            sibarum.dasum.gui.core.debug.Dbg.log("ctx-menu",
                "no provider on hit path — register via Handlers.onContextMenu on the *final* component identity (post-with*)");
            return false;
        }

        float pxPerEm = EmContext.pixelsPerEm();
        if (pxPerEm <= 0f) return false;
        float emX = (float) pxX / pxPerEm;
        float emY = (float) pxY / pxPerEm;

        ContextEvent event = new ContextEvent(owner, deepest, path, emX, emY, modifiers, windowHandle);
        List<ContextMenuItem> items = provider.itemsFor(event);
        if (items == null || items.isEmpty()) return false;

        return open(items, emX, emY);
    }

    /**
     * Open a menu with a pre-built item list at the given em position
     * (top-left of the popup, viewport-relative). Unlike {@link #onMouseDown}
     * this bypasses right-click hit-testing and provider resolution — used by
     * synthesized chrome that has no component identity of its own, e.g. the
     * {@link TabsController} overflow marker. Returns {@code true} when a menu
     * was opened ({@code false} for a {@code null}/empty item list).
     */
    public static boolean openAt(List<ContextMenuItem> items, float emX, float emY) {
        if (items == null || items.isEmpty()) return false;
        return open(items, emX, emY);
    }

    /** Shared popup-open tail for {@link #onMouseDown} and {@link #openAt}. */
    private static boolean open(List<ContextMenuItem> items, float emX, float emY) {
        if (menuOpen) close();
        currentItems = items;
        menuOpen = true;
        // Long-menu mode: create the filter input lazily so its identity
        // (and TextStates content + caret) persists across rebuilds.
        if (items.size() > FIT_TO_CONTENT_ITEMS) {
            ensureFilterInput();
            sibarum.dasum.gui.core.input.TextStates.setContent(filterInput, "");
        } else {
            filterInput = null;
        }
        recomputeVisible();
        selectedVisible = firstNavigableVisible();
        Component popup = buildPopup();
        OverlayStack.push(new OverlayStack.Overlay(
            popup, Anchor.at(Em.of(emX), Em.of(emY)), true,
            ContextMenuController::onDismiss));
        // Focus the filter input so typing filters immediately.
        if (filterInput != null) {
            FocusState.set(filterInput);
        }
        return true;
    }

    private static void ensureFilterInput() {
        if (filterInput != null) return;
        filterInput = new Component.Text(
            "", FontGroups.DEFAULT, Em.of(0.95f), ROW_FG,
            null, null, Em.of(0.35f),
            null, false,
            true, true, true, false, 0
        );
        Handlers.disableContextMenu(filterInput);
        TextStates.onContentChange(filterInput, q -> {
            if (!menuOpen) return;
            recomputeVisible();
            selectedVisible = firstNavigableVisible();
            rebuild();
        });
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
                int idx = prevNavigableVisible(selectedVisible);
                if (idx != selectedVisible) {
                    selectedVisible = idx;
                    rebuild();
                }
                return true;
            }
            case Glfw.GLFW_KEY_DOWN -> {
                int idx = nextNavigableVisible(selectedVisible);
                if (idx != selectedVisible) {
                    selectedVisible = idx;
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
        visibleIndices = List.of();
        selectedVisible = -1;
        filterInput = null;  // drop reference; new menu opening will recreate
        Invalidator.invalidate();
    }

    private static void activateSelected() {
        if (selectedVisible < 0 || selectedVisible >= visibleIndices.size()) { close(); return; }
        ContextMenuItem it = currentItems.get(visibleIndices.get(selectedVisible));
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
        // Re-focus the filter input across rebuilds so typing continues
        // uninterrupted; the input's identity is stable so caret state
        // is preserved by TextStates.
        if (filterInput != null) FocusState.set(filterInput);
    }

    private static Component buildPopup() {
        boolean longMode = filterInput != null;
        List<Component> rows = buildRows();

        // Inner column of rows. AUTO width in fit-mode (each row sized to
        // its label and the column picks the widest); explicit width in
        // long-mode so the Scroll viewport has a known cross extent.
        Component.Flex listInner = new Component.Flex(
            longMode ? LONG_MODE_WIDTH : Em.AUTO,
            Em.AUTO, Em.of(0.25f), MENU_BG,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            rows, false, 0
        );

        Component listContainer;
        if (longMode) {
            // Cap the scroll viewport height so the popup can't grow past
            // the screen — overflow scrolls inside the popup instead.
            listContainer = new Component.Scroll(
                LONG_MODE_WIDTH, LONG_MODE_SCROLL_HEIGHT, Em.ZERO, MENU_BG,
                listInner, false, 0
            );
        } else {
            listContainer = listInner;
        }

        List<Component> dialogChildren = new ArrayList<>();
        if (longMode) {
            Component filterBox = new Component.Flex(
                LONG_MODE_WIDTH, Em.AUTO, Em.ZERO, INPUT_BG,
                Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
                List.of(filterInput), false, 0
            );
            dialogChildren.add(filterBox);
        }
        dialogChildren.add(listContainer);

        Component dialog = new Component.Flex(
            longMode ? LONG_MODE_WIDTH : Em.AUTO, Em.AUTO, Em.ZERO, MENU_BG,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            dialogChildren, false, 0
        );
        // Thin border halo via outer Flex.
        return new Component.Flex(
            Em.AUTO, Em.AUTO, Em.of(0.08f), MENU_BORDER,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, Em.ZERO,
            List.of(dialog), false, 0
        );
    }

    private static List<Component> buildRows() {
        List<Component> rows = new ArrayList<>(visibleIndices.size());
        for (int visIdx = 0; visIdx < visibleIndices.size(); visIdx++) {
            int realIdx = visibleIndices.get(visIdx);
            ContextMenuItem it = currentItems.get(realIdx);
            if (it.isSeparator()) {
                Component sep = new Component.Flex(
                    null, Em.of(0.08f), Em.ZERO, SEP_BG,
                    Direction.ROW, JustifyContent.START, AlignItems.START, Em.ZERO,
                    List.of(), false, 0
                );
                rows.add(sep);
                currentRows.add(sep);
                continue;
            }
            boolean selected = (visIdx == selectedVisible) && it.enabled();
            Color rowBg = selected ? ROW_BG_SEL : TRANSPARENT;
            Color rowFg = !it.enabled() ? ROW_FG_DIM : (selected ? ROW_FG_SEL : ROW_FG);

            Component.Text label = new Component.Text(
                it.label(), FontGroups.DEFAULT, Em.of(0.95f), rowFg,
                null, null, Em.of(0.25f),
                null, false,
                false, false, false, false, 0
            );
            Component.Flex row = new Component.Flex(
                Em.AUTO, Em.of(1.6f), Em.of(0.3f), rowBg,
                Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.ZERO,
                List.of(label),
                true, 0
            );
            final int visFinal = visIdx;
            Handlers.onClick(row, () -> {
                selectedVisible = visFinal;
                activateSelected();
            });
            rows.add(row);
            currentRows.add(row);
        }
        if (visibleIndices.isEmpty() && filterInput != null) {
            Component.Text empty = new Component.Text(
                "(no matches)", FontGroups.DEFAULT, Em.of(0.9f), ROW_FG_DIM,
                null, null, Em.of(0.35f),
                null, false,
                false, false, false, false, 0
            );
            rows.add(new Component.Flex(
                Em.AUTO, Em.of(1.6f), Em.of(0.3f), TRANSPARENT,
                Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.ZERO,
                List.of(empty), false, 0
            ));
        }
        return rows;
    }

    /** Rebuild {@link #visibleIndices} from the current filter input. */
    private static void recomputeVisible() {
        String filter = filterInput == null ? "" : TextStates.contentOf(filterInput);
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        List<Integer> indices = new ArrayList<>(currentItems.size());
        for (int i = 0; i < currentItems.size(); i++) {
            ContextMenuItem it = currentItems.get(i);
            if (it.isSeparator()) {
                // Drop separators in filter mode — they'd land between
                // non-adjacent items and confuse the visual grouping.
                if (needle.isEmpty()) indices.add(i);
                continue;
            }
            if (needle.isEmpty()
                || it.label().toLowerCase(Locale.ROOT).contains(needle)) {
                indices.add(i);
            }
        }
        visibleIndices = List.copyOf(indices);
    }

    private static int firstNavigableVisible() {
        for (int i = 0; i < visibleIndices.size(); i++) {
            if (isNavigable(currentItems.get(visibleIndices.get(i)))) return i;
        }
        return -1;
    }

    private static int nextNavigableVisible(int from) {
        if (visibleIndices.isEmpty()) return -1;
        int n = visibleIndices.size();
        int start = (from < 0) ? -1 : from;
        for (int step = 1; step <= n; step++) {
            int i = ((start + step) % n + n) % n;
            if (isNavigable(currentItems.get(visibleIndices.get(i)))) return i;
        }
        return from;
    }

    private static int prevNavigableVisible(int from) {
        if (visibleIndices.isEmpty()) return -1;
        int n = visibleIndices.size();
        int start = (from < 0) ? n : from;
        for (int step = 1; step <= n; step++) {
            int i = ((start - step) % n + n) % n;
            if (isNavigable(currentItems.get(visibleIndices.get(i)))) return i;
        }
        return from;
    }

    private static boolean isNavigable(ContextMenuItem it) {
        return !it.isSeparator() && it.enabled();
    }
}
