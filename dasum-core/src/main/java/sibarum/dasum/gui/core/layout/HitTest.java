package sibarum.dasum.gui.core.layout;

import sibarum.dasum.gui.core.component.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Point-in-component hit testing over a {@link LayoutResult}. Walks the
 * tree in topmost-first order — for each container, recurse into children
 * from last to first (later siblings render on top); then test self.
 * Returns the deepest interactive component containing the point, or
 * {@code null} if none.
 * <p>
 * A {@link Component.Scroll} narrows the active clip rect during recursion,
 * so children whose layout rects extend beyond the visible scroll area
 * cannot register as hits.
 */
public final class HitTest {

    private HitTest() {}

    public static Component test(Component root, LayoutResult layout, float px, float py) {
        return testInOrder(root, layout, px, py, null, true);
    }

    /**
     * Like {@link #test} but returns the deepest containing component
     * regardless of its {@code interactive()} flag. Used by passive overlays
     * (tooltips) that may need to surface help text for purely decorative
     * components — labels, banners, icon boxes — that the regular
     * hit-tester filters out.
     * <p>
     * Scroll clipping is still respected: a child whose rect lies outside
     * the visible scroll viewport cannot register as a hit.
     */
    public static Component testAny(Component root, LayoutResult layout, float px, float py) {
        return testInOrder(root, layout, px, py, null, false);
    }

    private static Component testInOrder(Component c, LayoutResult layout, float px, float py,
                                          PixelRect clip, boolean requireInteractive) {
        PixelRect newClip = clip;
        if (c instanceof Component.Scroll) {
            PixelRect r = layout.rectOf(c);
            if (r != null) newClip = (clip == null) ? r : intersect(clip, r);
        }
        List<Component> kids = childrenOf(c);
        for (int i = kids.size() - 1; i >= 0; i--) {
            Component hit = testInOrder(kids.get(i), layout, px, py, newClip, requireInteractive);
            if (hit != null) return hit;
        }
        PixelRect r = layout.rectOf(c);
        if (r != null && (!requireInteractive || c.interactive()) && r.contains(px, py)
            && (clip == null || clip.contains(px, py))) {
            return c;
        }
        return null;
    }

    /**
     * Walk the tree in render order (parent-then-children, left-to-right)
     * and append every interactive component to the returned list. Used by
     * focus cycling (Tab order = render order).
     */
    public static List<Component> collectInteractive(Component root) {
        List<Component> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    /**
     * Walk from {@code root} to {@code target} and return the ancestor
     * path (root first, target last). Empty list if {@code target} isn't
     * reachable from {@code root}. Identity-based, not equals-based.
     */
    public static List<Component> pathTo(Component root, Component target) {
        List<Component> out = new ArrayList<>();
        if (root == null || target == null) return out;
        if (findPath(root, target, out)) return out;
        out.clear();
        return out;
    }

    private static boolean findPath(Component c, Component target, List<Component> path) {
        path.add(c);
        if (c == target) return true;
        for (Component child : childrenOf(c)) {
            if (findPath(child, target, path)) return true;
        }
        path.remove(path.size() - 1);
        return false;
    }

    private static void collect(Component c, List<Component> out) {
        if (c.interactive()) out.add(c);
        for (Component child : childrenOf(c)) collect(child, out);
    }

    /**
     * Topmost {@link Component.Scroll} whose viewport rect contains the
     * given point — used to route mouse wheel events to the right scroll
     * container. Innermost wins (deeply-nested scrollers eat wheel events
     * before their containers).
     */
    public static Component.Scroll findScroll(Component root, LayoutResult layout, float px, float py) {
        return findScrollInOrder(root, layout, px, py);
    }

    private static Component.Scroll findScrollInOrder(Component c, LayoutResult layout, float px, float py) {
        List<Component> kids = childrenOf(c);
        for (int i = kids.size() - 1; i >= 0; i--) {
            Component.Scroll hit = findScrollInOrder(kids.get(i), layout, px, py);
            if (hit != null) return hit;
        }
        if (c instanceof Component.Scroll s) {
            PixelRect r = layout.rectOf(s);
            if (r != null && r.contains(px, py)) return s;
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
            case Component.GraphSurface gs   -> sibarum.dasum.gui.core.graph.GraphSurfaceZOrder.orderedChildren(gs);
            case Component.PointCloud pc -> List.of();
            case Component.DataTable dt -> List.of();
        };
    }

    private static PixelRect intersect(PixelRect a, PixelRect b) {
        float x = Math.max(a.x(), b.x());
        float y = Math.max(a.y(), b.y());
        float right  = Math.min(a.right(),  b.right());
        float bottom = Math.min(a.bottom(), b.bottom());
        return new PixelRect(x, y, Math.max(0f, right - x), Math.max(0f, bottom - y));
    }
}
