package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.layout.HitTest;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;

import java.util.List;

/**
 * Single focused component pointer + Tab/Shift-Tab cycling support.
 * {@link #cycle(Component, boolean)} walks the tree in render order and
 * advances focus to the next interactive component (or the previous one
 * for {@code reverse=true}). Wraps around at the ends.
 * <p>
 * When focus changes, any enclosing {@link Component.Scroll} containers
 * are nudged so the focused component sits inside their viewport. This
 * makes Tab-into-a-list "follow" focus the way every desktop framework
 * has done for decades.
 */
public final class FocusState {

    private static Component focused = null;

    private FocusState() {}

    public static Component focused() { return focused; }

    public static void set(Component c) {
        if (focused == c) return;
        Component previous = focused;
        focused = c;
        // Selection is a focus-scoped concern: when a selectable Text loses
        // focus, its selection collapses (caret position is preserved).
        if (previous instanceof Component.Text pt && pt.selectable()) {
            TextStates.of(previous).collapseToCaret();
        }
        Handlers.fireBlur(previous);
        Handlers.fireFocus(focused);
        Invalidator.invalidate();
        scrollIntoView();
    }

    public static void clear() { set(null); }

    public static void cycle(Component root, boolean reverse) {
        List<Component> all = HitTest.collectInteractive(root);
        if (all.isEmpty()) return;
        int idx = indexOfIdentity(all, focused);
        int next;
        if (idx < 0) {
            next = reverse ? all.size() - 1 : 0;
        } else if (reverse) {
            next = (idx - 1 + all.size()) % all.size();
        } else {
            next = (idx + 1) % all.size();
        }
        set(all.get(next));
    }

    /**
     * Identity-based lookup. {@link List#indexOf} uses {@code Object.equals},
     * but Components are records with value equality — multiple visually
     * identical instances (e.g. four neutral sidebar items) compare equal,
     * which breaks Tab cycling. Always walk by reference identity here.
     */
    private static int indexOfIdentity(List<Component> list, Component target) {
        if (target == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == target) return i;
        }
        return -1;
    }

    /**
     * For each {@link Component.Scroll} between the layout root and the
     * currently-focused component, shift the scroll's position so the
     * focused component's rect is inside the visible viewport. No-op when
     * the focused rect is already visible.
     */
    private static void scrollIntoView() {
        if (focused == null) return;
        Component root = LatestLayout.root();
        LayoutResult lr = LatestLayout.result();
        if (root == null || lr == null) return;
        PixelRect focusedRect = lr.rectOf(focused);
        if (focusedRect == null) return;

        for (Component ancestor : HitTest.pathTo(root, focused)) {
            if (!(ancestor instanceof Component.Scroll scroll)) continue;
            if (ancestor == focused) continue; // don't try to scroll the focused Scroll itself
            PixelRect outer = lr.rectOf(scroll);
            if (outer == null) continue;
            float pad = scroll.padding().toPixels();
            PixelRect interior = new PixelRect(
                outer.x() + pad, outer.y() + pad,
                Math.max(0f, outer.width()  - 2f * pad),
                Math.max(0f, outer.height() - 2f * pad)
            );

            float dx = 0f, dy = 0f;

            // Vertical: always align nearest edge so the focused rect is in view.
            if (focusedRect.bottom() > interior.bottom()) dy = focusedRect.bottom() - interior.bottom();
            else if (focusedRect.y() < interior.y())      dy = focusedRect.y()      - interior.y();

            // Horizontal: only adjust if there's ZERO horizontal overlap with the
            // viewport. Matches browser scrollIntoView's inline:'nearest' default
            // — a wide row that's partially visible is good enough; don't jump.
            boolean horizontallyVisible = focusedRect.right() > interior.x()
                                       && focusedRect.x()    < interior.right();
            if (!horizontallyVisible) {
                if (focusedRect.right() <= interior.x()) dx = focusedRect.right() - interior.x();
                else                                      dx = focusedRect.x()     - interior.right();
            }

            if (dx != 0f || dy != 0f) {
                ScrollStates.of(scroll).scrollBy(dx, dy);
            }
        }
    }
}
