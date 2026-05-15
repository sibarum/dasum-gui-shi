package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.overlay.OverlayStack;
import sibarum.dasum.gui.core.theme.Theme;

import java.util.List;

/**
 * Hit-tests, hover-tracks, and drag-dispatches the scrollbar thumbs drawn
 * by {@code Component.Scroll}. Scrollbars aren't components — they're
 * synthesized at render time — so geometry and hit-testing live here,
 * shared between the renderer (for drawing) and the App's mouse callbacks
 * (for interaction).
 * <p>
 * Hover and drag state are process-global; single-window assumption.
 */
public final class ScrollbarController {

    public enum Axis { VERTICAL, HORIZONTAL }

    public record Hit(Component.Scroll scroll, Axis axis) {}

    private static final float MIN_THUMB_PX = 20f;

    // Hover state
    private static Component.Scroll hoveredScroll = null;
    private static Axis              hoveredAxis  = null;

    // Drag state
    private static Component.Scroll draggingScroll = null;
    private static Axis              draggingAxis  = null;
    private static double dragStartCursor = 0d;
    private static float  dragStartScrollPos = 0f;

    private ScrollbarController() {}

    // ---------- public geometry helpers (also used by Render) ----------

    public static PixelRect verticalThumbRect(Component.Scroll scroll, PixelRect interior) {
        ScrollPosition pos = ScrollStates.of(scroll);
        if (pos.pxMaxY() <= 0f) return null;
        float thickness = Theme.scrollbarThicknessPx();
        float trackH = interior.height();
        float contentH = trackH + pos.pxMaxY();
        float thumbH = Math.max(MIN_THUMB_PX, trackH * (trackH / contentH));
        float trackRange = Math.max(0f, trackH - thumbH);
        float thumbY = interior.y() + (pos.pxMaxY() > 0f ? (pos.pxY() / pos.pxMaxY()) * trackRange : 0f);
        float thumbX = interior.right() - thickness;
        return new PixelRect(thumbX, thumbY, thickness, thumbH);
    }

    public static PixelRect horizontalThumbRect(Component.Scroll scroll, PixelRect interior) {
        ScrollPosition pos = ScrollStates.of(scroll);
        if (pos.pxMaxX() <= 0f) return null;
        float thickness = Theme.scrollbarThicknessPx();
        float trackW = interior.width();
        float contentW = trackW + pos.pxMaxX();
        float thumbW = Math.max(MIN_THUMB_PX, trackW * (trackW / contentW));
        float trackRange = Math.max(0f, trackW - thumbW);
        float thumbX = interior.x() + (pos.pxMaxX() > 0f ? (pos.pxX() / pos.pxMaxX()) * trackRange : 0f);
        float thumbY = interior.bottom() - thickness;
        return new PixelRect(thumbX, thumbY, thumbW, thickness);
    }

    public static boolean isThumbHovered(Component.Scroll scroll, Axis axis) {
        if (draggingScroll == scroll && draggingAxis == axis) return true;
        return hoveredScroll == scroll && hoveredAxis == axis;
    }

    // ---------- input dispatch ----------

    /** Returns {@code true} when the press landed on a thumb and a drag began. */
    public static boolean onMouseDown(double cursorX, double cursorY) {
        Hit hit = hitTest((float) cursorX, (float) cursorY);
        if (hit == null) return false;
        draggingScroll = hit.scroll();
        draggingAxis   = hit.axis();
        ScrollPosition pos = ScrollStates.of(draggingScroll);
        if (draggingAxis == Axis.VERTICAL) {
            dragStartCursor    = cursorY;
            dragStartScrollPos = pos.pxY();
        } else {
            dragStartCursor    = cursorX;
            dragStartScrollPos = pos.pxX();
        }
        return true;
    }

    public static void onCursorMove(double cursorX, double cursorY) {
        if (draggingScroll != null) {
            applyDrag(cursorX, cursorY);
            return;
        }
        updateHover((float) cursorX, (float) cursorY);
    }

    public static void onMouseUp() {
        draggingScroll = null;
        draggingAxis   = null;
    }

    public static boolean isDragging() {
        return draggingScroll != null;
    }

    public static void cancelDrag() {
        draggingScroll = null;
        draggingAxis   = null;
    }

    public static void clearHover() {
        if (hoveredScroll != null || hoveredAxis != null) {
            hoveredScroll = null;
            hoveredAxis   = null;
            Invalidator.invalidate();
        }
    }

    // ---------- internals ----------

    private static void applyDrag(double cursorX, double cursorY) {
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return;
        PixelRect outer = lr.rectOf(draggingScroll);
        if (outer == null) return;
        PixelRect interior = interiorOf(outer, draggingScroll);
        ScrollPosition pos = ScrollStates.of(draggingScroll);
        float thickness = Theme.scrollbarThicknessPx();

        if (draggingAxis == Axis.VERTICAL) {
            float trackH = interior.height();
            float contentH = trackH + pos.pxMaxY();
            float thumbH = Math.max(MIN_THUMB_PX, trackH * (trackH / contentH));
            float trackRange = Math.max(1f, trackH - thumbH);
            float deltaCursor = (float) (cursorY - dragStartCursor);
            float newY = dragStartScrollPos + (deltaCursor / trackRange) * pos.pxMaxY();
            pos.scrollByPx(0f, newY - pos.pxY());
        } else {
            float trackW = interior.width();
            float contentW = trackW + pos.pxMaxX();
            float thumbW = Math.max(MIN_THUMB_PX, trackW * (trackW / contentW));
            float trackRange = Math.max(1f, trackW - thumbW);
            float deltaCursor = (float) (cursorX - dragStartCursor);
            float newX = dragStartScrollPos + (deltaCursor / trackRange) * pos.pxMaxX();
            pos.scrollByPx(newX - pos.pxX(), 0f);
        }
        // Silence the unused-locals warning while keeping the math grouped.
        if (thickness < 0f) thickness = 0f;
    }

    private static void updateHover(float cursorX, float cursorY) {
        Hit hit = hitTest(cursorX, cursorY);
        Component.Scroll newScroll = hit != null ? hit.scroll() : null;
        Axis              newAxis  = hit != null ? hit.axis()   : null;
        if (newScroll != hoveredScroll || newAxis != hoveredAxis) {
            hoveredScroll = newScroll;
            hoveredAxis   = newAxis;
            Invalidator.invalidate();
        }
    }

    private static Hit hitTest(float x, float y) {
        Component mainRoot = LatestLayout.root();
        Component root = OverlayStack.activeInputRoot(mainRoot);
        LayoutResult lr = LatestLayout.result();
        if (root == null || lr == null) return null;
        return walkForHit(root, lr, x, y);
    }

    private static Hit walkForHit(Component c, LayoutResult lr, float x, float y) {
        // Innermost wins — recurse deepest first.
        for (Component child : childrenOf(c)) {
            Hit h = walkForHit(child, lr, x, y);
            if (h != null) return h;
        }
        if (c instanceof Component.Scroll scroll) {
            PixelRect outer = lr.rectOf(scroll);
            if (outer != null) {
                PixelRect interior = interiorOf(outer, scroll);
                PixelRect vt = verticalThumbRect(scroll, interior);
                if (vt != null && vt.contains(x, y)) return new Hit(scroll, Axis.VERTICAL);
                PixelRect ht = horizontalThumbRect(scroll, interior);
                if (ht != null && ht.contains(x, y)) return new Hit(scroll, Axis.HORIZONTAL);
            }
        }
        return null;
    }

    private static PixelRect interiorOf(PixelRect outer, Component.Scroll scroll) {
        float pad = scroll.padding().toPixels();
        return new PixelRect(
            outer.x() + pad, outer.y() + pad,
            Math.max(0f, outer.width()  - 2f * pad),
            Math.max(0f, outer.height() - 2f * pad)
        );
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
            case Component.GraphSurface gs   -> sibarum.dasum.gui.core.graph.GraphSurfaceChildren.all(gs);
        };
    }
}
