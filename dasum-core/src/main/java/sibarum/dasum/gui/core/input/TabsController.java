package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.overlay.OverlayStack;
import sibarum.dasum.gui.core.text.FontGroup;
import sibarum.dasum.gui.core.text.FontGroups;
import sibarum.dasum.gui.natives.glfw.Glfw;

import java.util.List;

/**
 * Geometry + input dispatch for {@link Component.Tabs} header strips.
 * Tab cells aren't real components — they're synthesized at render time
 * — so hit-testing, hover, and click dispatch live here, parallel to
 * {@link ScrollbarController}.
 * <p>
 * Hover state is process-global; single-window assumption.
 */
public final class TabsController {

    private static Component.Tabs hoveredTabs = null;
    private static int            hoveredTabIndex = -1;

    private TabsController() {}

    // ---------- geometry (shared with Render) ----------

    /** Top strip rect spanning the full Tabs width. */
    public static PixelRect headerStripRect(Component.Tabs tabs, PixelRect outer) {
        return new PixelRect(outer.x(), outer.y(), outer.width(), tabs.headerHeight().toPixels());
    }

    /** Per-tab header cell rect. Cells are laid out left-to-right, auto-sized to label + padding. */
    public static PixelRect tabCellRect(Component.Tabs tabs, int index, PixelRect outer) {
        float headerH = tabs.headerHeight().toPixels();
        float x = outer.x();
        for (int i = 0; i < index; i++) x += tabCellWidth(tabs, i);
        return new PixelRect(x, outer.y(), tabCellWidth(tabs, index), headerH);
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

    // ---------- input dispatch ----------

    /** Returns true if the press landed on a tab cell and switched the active index. */
    public static boolean onMouseDown(double cursorX, double cursorY) {
        Hit hit = hitTest((float) cursorX, (float) cursorY);
        if (hit == null) return false;
        hit.tabs.activeIndex().set(hit.index);
        FocusState.set(hit.tabs);
        return true;
    }

    public static void onCursorMove(double cursorX, double cursorY) {
        Hit hit = hitTest((float) cursorX, (float) cursorY);
        Component.Tabs newTabs = hit != null ? hit.tabs : null;
        int newIndex = hit != null ? hit.index : -1;
        if (newTabs != hoveredTabs || newIndex != hoveredTabIndex) {
            hoveredTabs = newTabs;
            hoveredTabIndex = newIndex;
            Invalidator.invalidate();
        }
    }

    public static void clearHover() {
        if (hoveredTabs != null || hoveredTabIndex >= 0) {
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

    private record Hit(Component.Tabs tabs, int index) {}

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
                    for (int i = 0; i < tabs.tabs().size(); i++) {
                        PixelRect cell = tabCellRect(tabs, i, outer);
                        if (cell.contains(x, y)) return new Hit(tabs, i);
                    }
                }
            }
        }
        return null;
    }

    private static List<Component> childrenOf(Component c) {
        return switch (c) {
            case Component.Box  b -> b.children();
            case Component.Flex f -> f.children();
            case Component.Scroll s -> s.child() != null ? List.of(s.child()) : List.of();
            case Component.Text t   -> List.of();
            case Component.Checkbox cb -> List.of();
            case Component.Radio<?> r  -> List.of();
            case Component.Slider sl   -> List.of();
            case Component.Tabs t      -> t.activeContent() != null ? List.of(t.activeContent()) : List.of();
            case Component.GraphSurface gs -> sibarum.dasum.gui.core.graph.GraphSurfaceChildren.all(gs);
            case Component.PointCloud pc -> List.of();
        };
    }
}
