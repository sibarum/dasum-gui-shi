package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.EmContext;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.overlay.OverlayStack;
import sibarum.dasum.gui.core.text.FontGroup;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.natives.glfw.Glfw;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry + input dispatch for {@link Component.Tabs} header strips.
 * Tab cells aren't real components — they're synthesized at render time
 * — so hit-testing, hover, and click dispatch live here, parallel to
 * {@link ScrollbarController}.
 * <p>
 * When the tab cells don't all fit in the strip width, the trailing tabs
 * are dropped from the strip and an <em>overflow marker</em> cell is drawn
 * at the right edge. Clicking it opens a dropdown (via
 * {@link ContextMenuController#openAt}) listing the hidden tabs, so every
 * tab stays reachable however narrow the window gets. {@link #stripLayout}
 * is the single geometry source shared by rendering and hit-testing.
 * <p>
 * Hover state is process-global; single-window assumption.
 */
public final class TabsController {

    private static Component.Tabs hoveredTabs = null;
    /** Index of the hovered tab cell, {@link #HOVER_MARKER} for the overflow
     *  marker, or {@code -1} for nothing. */
    private static int            hoveredTabIndex = -1;

    /** Sentinel {@link #hoveredTabIndex} value meaning the overflow marker. */
    public static final int HOVER_MARKER = -2;

    private TabsController() {}

    // ---------- geometry (shared with Render) ----------

    /** Top strip rect spanning the full Tabs width. */
    public static PixelRect headerStripRect(Component.Tabs tabs, PixelRect outer) {
        return new PixelRect(outer.x(), outer.y(), outer.width(), tabs.headerHeight().toPixels());
    }

    /**
     * Resolved header-strip geometry: which tab cells are shown, the
     * overflow marker (if any), and which tabs are hidden.
     *
     * @param visible    tab indices shown in the strip, a contiguous prefix
     *                   {@code 0..k-1} (may be empty when the strip is tiny)
     * @param cells      cell rects parallel to {@code visible}
     * @param overflow   hidden tab indices {@code k..n-1}, in natural order
     * @param markerRect the overflow marker cell, or {@code null} when
     *                   everything fits
     */
    public record StripLayout(List<Integer> visible, List<PixelRect> cells,
                              List<Integer> overflow, PixelRect markerRect) {}

    /** Width reserved for the overflow marker cell — a square-ish button. */
    public static float markerCellWidth(Component.Tabs tabs) {
        return tabs.tabFontSize().toPixels() + 2f * tabs.tabPadding().toPixels();
    }

    /**
     * Fit the tab cells into {@code outer}, spilling any that don't fit into
     * the overflow list behind a marker cell. When all cells fit, the marker
     * is absent and every tab is visible (identical to the pre-overflow
     * behavior).
     */
    public static StripLayout stripLayout(Component.Tabs tabs, PixelRect outer) {
        int n = tabs.tabs().size();
        float headerH = tabs.headerHeight().toPixels();
        float[] widths = new float[n];
        for (int i = 0; i < n; i++) widths[i] = tabCellWidth(tabs, i);

        int k = fitCount(widths, outer.width(), markerCellWidth(tabs));

        List<Integer> visible = new ArrayList<>(k);
        List<PixelRect> cells = new ArrayList<>(k);
        float x = outer.x();
        for (int i = 0; i < k; i++) {
            visible.add(i);
            cells.add(new PixelRect(x, outer.y(), widths[i], headerH));
            x += widths[i];
        }

        // k == n means everything fit — no marker, no overflow.
        if (k == n) {
            return new StripLayout(List.copyOf(visible), List.copyOf(cells), List.of(), null);
        }
        List<Integer> overflow = new ArrayList<>(n - k);
        for (int i = k; i < n; i++) overflow.add(i);
        float markerW = markerCellWidth(tabs);
        PixelRect markerRect = new PixelRect(
            outer.x() + outer.width() - markerW, outer.y(), markerW, headerH);
        return new StripLayout(List.copyOf(visible), List.copyOf(cells),
            List.copyOf(overflow), markerRect);
    }

    /**
     * Pure fit math shared by {@link #stripLayout} (and unit-testable without
     * a font context): how many leading cells fit into {@code available}.
     * Returns {@code widths.length} when every cell fits with room to spare
     * (no marker needed); otherwise the marker cell is reserved and the count
     * is the leading prefix that fits into {@code available - markerWidth}.
     */
    static int fitCount(float[] widths, float available, float markerWidth) {
        float total = 0f;
        for (float w : widths) total += w;
        if (total <= available) return widths.length;   // everything fits, no marker
        float usable = available - markerWidth;
        float x = 0f;
        int k = 0;
        for (float w : widths) {
            if (x + w > usable) break;
            x += w;
            k++;
        }
        return k;
    }

    public static float tabCellWidth(Component.Tabs tabs, int index) {
        String label = tabs.tabs().get(index).label();
        FontGroup fg = FontGroups.getOrDefault(tabs.fontGroup());
        float fontPx = tabs.tabFontSize().toPixels();
        float labelW = labelWidth(fg, label, fontPx);
        return labelW + 2f * tabs.tabPadding().toPixels();
    }

    public static float labelWidth(FontGroup fg, String label, float fontPx) {
        float w = 0f;
        for (int j = 0; j < label.length(); ) {
            int cp = label.codePointAt(j);
            w += fg.layout().advance(cp, fontPx);
            j += Character.charCount(cp);
        }
        return w;
    }

    public static boolean isTabHovered(Component.Tabs tabs, int index) {
        return hoveredTabs == tabs && hoveredTabIndex == index;
    }

    /** True when the overflow marker of {@code tabs} is hovered. */
    public static boolean isMarkerHovered(Component.Tabs tabs) {
        return hoveredTabs == tabs && hoveredTabIndex == HOVER_MARKER;
    }

    // ---------- input dispatch ----------

    /** Returns true if the press landed on a tab cell or the overflow marker. */
    public static boolean onMouseDown(double cursorX, double cursorY) {
        Hit hit = hitTest((float) cursorX, (float) cursorY);
        if (hit == null) return false;
        if (hit.marker) {
            openOverflowMenu(hit.tabs);
            FocusState.set(hit.tabs);
            return true;
        }
        hit.tabs.activeIndex().set(hit.index);   // selection state: deduped (no-op on re-click)
        if (hit.tabs.onTabPressed() != null) {   // click intent: fires on every press
            hit.tabs.onTabPressed().accept(hit.index);
        }
        FocusState.set(hit.tabs);
        return true;
    }

    private static void openOverflowMenu(Component.Tabs tabs) {
        PixelRect outer = LatestLayout.result() != null ? LatestLayout.result().rectOf(tabs) : null;
        if (outer == null) return;
        StripLayout sl = stripLayout(tabs, outer);
        if (sl.overflow().isEmpty() || sl.markerRect() == null) return;

        List<ContextMenuItem> items = new ArrayList<>(sl.overflow().size());
        for (int idx : sl.overflow()) {
            final int tabIdx = idx;
            String label = tabs.tabs().get(idx).label();
            items.add(new ContextMenuItem(label, () -> {
                tabs.activeIndex().set(tabIdx);
                if (tabs.onTabPressed() != null) tabs.onTabPressed().accept(tabIdx);
                FocusState.set(tabs);
            }));
        }

        float pxPerEm = EmContext.pixelsPerEm();
        if (pxPerEm <= 0f) return;
        PixelRect m = sl.markerRect();
        // Anchor the menu just below the marker's bottom-left corner.
        float emX = m.x() / pxPerEm;
        float emY = (m.y() + m.height()) / pxPerEm;
        ContextMenuController.openAt(items, emX, emY);
    }

    public static void onCursorMove(double cursorX, double cursorY) {
        Hit hit = hitTest((float) cursorX, (float) cursorY);
        Component.Tabs newTabs = hit != null ? hit.tabs : null;
        int newIndex = hit == null ? -1 : (hit.marker ? HOVER_MARKER : hit.index);
        if (newTabs != hoveredTabs || newIndex != hoveredTabIndex) {
            hoveredTabs = newTabs;
            hoveredTabIndex = newIndex;
            Invalidator.invalidate();
        }
    }

    public static void clearHover() {
        if (hoveredTabs != null || hoveredTabIndex >= 0 || hoveredTabIndex == HOVER_MARKER) {
            hoveredTabs = null;
            hoveredTabIndex = -1;
            Invalidator.invalidate();
        }
    }

    /**
     * Left/Right when a Tabs container is focused cycles the active tab.
     * Returns true if the key was consumed.
     */
    public static boolean onKey(int key) {
        if (!(FocusState.focused() instanceof Component.Tabs tabs)) return false;
        if (tabs.tabs().isEmpty()) return false;
        int n = tabs.tabs().size();
        int cur = tabs.activeIndex().get();
        int next;
        switch (key) {
            case Glfw.GLFW_KEY_LEFT  -> next = (cur - 1 + n) % n;
            case Glfw.GLFW_KEY_RIGHT -> next = (cur + 1) % n;
            case Glfw.GLFW_KEY_HOME  -> next = 0;
            case Glfw.GLFW_KEY_END   -> next = n - 1;
            default -> { return false; }
        }
        tabs.activeIndex().set(next);
        return true;
    }

    // ---------- internals ----------

    private record Hit(Component.Tabs tabs, int index, boolean marker) {}

    private static Hit hitTest(float x, float y) {
        Component mainRoot = LatestLayout.root();
        Component root = OverlayStack.activeInputRoot(mainRoot);
        LayoutResult lr = LatestLayout.result();
        if (root == null || lr == null) return null;
        return walkForHit(root, lr, x, y);
    }

    private static Hit walkForHit(Component c, LayoutResult lr, float x, float y) {
        for (Component child : childrenOf(c)) {
            Hit h = walkForHit(child, lr, x, y);
            if (h != null) return h;
        }
        if (c instanceof Component.Tabs tabs) {
            PixelRect outer = lr.rectOf(tabs);
            if (outer != null) {
                PixelRect strip = headerStripRect(tabs, outer);
                if (strip.contains(x, y)) {
                    StripLayout sl = stripLayout(tabs, outer);
                    for (int i = 0; i < sl.visible().size(); i++) {
                        if (sl.cells().get(i).contains(x, y)) {
                            return new Hit(tabs, sl.visible().get(i), false);
                        }
                    }
                    if (sl.markerRect() != null && sl.markerRect().contains(x, y)) {
                        return new Hit(tabs, -1, true);
                    }
                }
            }
        }
        return null;
    }

    private static List<Component> childrenOf(Component c) {
        return switch (c) {
            case Component.Box  b -> sibarum.dasum.gui.core.component.DynamicChildren.effectiveChildren(b);
            case Component.Flex f -> sibarum.dasum.gui.core.component.DynamicChildren.effectiveChildren(f);
            case Component.Scroll s -> s.child() != null ? List.of(s.child()) : List.of();
            case Component.Text t   -> List.of();
            case Component.Checkbox cb -> List.of();
            case Component.Radio<?> r  -> List.of();
            case Component.Slider sl   -> List.of();
            case Component.Tabs t      -> t.activeContent() != null ? List.of(t.activeContent()) : List.of();
            case Component.GraphSurface gs -> sibarum.dasum.gui.core.graph.GraphSurfaceChildren.all(gs);
            case Component.SceneView pc -> List.of();
            case Component.DataTable dt -> List.of();
        };
    }
}
