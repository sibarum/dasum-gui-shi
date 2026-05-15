package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.graph.GraphSurfacePositions;
import sibarum.dasum.gui.core.graph.GraphSurfaceZOrder;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.EmContext;
import sibarum.dasum.gui.core.layout.HitTest;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.overlay.OverlayStack;

import java.util.List;

/**
 * Drag dispatch for {@link Component.GraphSurface} direct children. A press
 * anywhere inside an interactive GraphSurface walks the hit path; the direct
 * child of the surface in that path becomes the drag target. Cursor moves
 * rewrite its {@link GraphSurfacePositions} entry; mouse up ends the drag.
 * <p>
 * No drag threshold — drag begins on mouse-down. Clicks on a GraphSurface
 * child without cursor movement still won't fire registered click handlers
 * because the App-level dispatcher suppresses click activation while
 * {@link #isDragging()} is true.
 */
public final class GraphSurfaceController {

    private static Component.GraphSurface draggingSurface = null;
    private static Component               draggingChild  = null;
    private static float dragOffsetX = 0f;
    private static float dragOffsetY = 0f;

    private GraphSurfaceController() {}

    public static boolean onMouseDown(double cursorX, double cursorY) {
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return false;
        Component root = OverlayStack.activeInputRoot(LatestLayout.root());
        if (root == null) return false;

        Component hit = HitTest.test(root, lr, (float) cursorX, (float) cursorY);
        if (hit == null) return false;

        // root → … → surface → directChild → … → hit. Drag works on any
        // GraphSurface regardless of its own {@code interactive} flag; the
        // flag governs only whether the surface itself enters the Tab focus cycle.
        List<Component> path = HitTest.pathTo(root, hit);
        for (int i = 0; i < path.size() - 1; i++) {
            if (path.get(i) instanceof Component.GraphSurface surface) {
                Component directChild = path.get(i + 1);
                PixelRect childRect = lr.rectOf(directChild);
                if (childRect == null) continue;
                draggingSurface = surface;
                draggingChild   = directChild;
                dragOffsetX     = (float) cursorX - childRect.x();
                dragOffsetY     = (float) cursorY - childRect.y();
                GraphSurfaceZOrder.bumpToTop(surface, directChild);
                return true;
            }
        }
        return false;
    }

    public static void onCursorMove(double cursorX, double cursorY) {
        if (draggingSurface == null || draggingChild == null) return;
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return;
        PixelRect surfaceRect = lr.rectOf(draggingSurface);
        if (surfaceRect == null) return;
        PixelRect childRect = lr.rectOf(draggingChild);
        if (childRect == null) return;

        float pxPerEm = EmContext.pixelsPerEm();
        if (pxPerEm <= 0f) return;
        float newPxX = (float) cursorX - dragOffsetX;
        float newPxY = (float) cursorY - dragOffsetY;
        float newEmX = (newPxX - surfaceRect.x()) / pxPerEm;
        float newEmY = (newPxY - surfaceRect.y()) / pxPerEm;

        // Clamp so the child stays fully inside the surface — prevents
        // dragging a node off and losing access to it.
        float childEmW    = childRect.width()    / pxPerEm;
        float childEmH    = childRect.height()   / pxPerEm;
        float surfaceEmW  = surfaceRect.width()  / pxPerEm;
        float surfaceEmH  = surfaceRect.height() / pxPerEm;
        float maxEmX = Math.max(0f, surfaceEmW - childEmW);
        float maxEmY = Math.max(0f, surfaceEmH - childEmH);
        newEmX = Math.max(0f, Math.min(maxEmX, newEmX));
        newEmY = Math.max(0f, Math.min(maxEmY, newEmY));

        GraphSurfacePositions.set(draggingSurface, draggingChild, newEmX, newEmY);
    }

    public static void onMouseUp() {
        draggingSurface = null;
        draggingChild   = null;
    }

    public static boolean isDragging() {
        return draggingSurface != null;
    }

    public static void cancelDrag() {
        draggingSurface = null;
        draggingChild   = null;
    }
}
