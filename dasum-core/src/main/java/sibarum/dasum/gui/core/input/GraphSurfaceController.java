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
 * child of the surface in that path becomes the press target. Cursor moves
 * past the drag threshold begin rewriting its {@link GraphSurfacePositions}
 * entry; mouse-up ends the interaction.
 * <p>
 * Click vs drag: a press-and-release without crossing {@link #DRAG_THRESHOLD_PX}
 * resolves as a click and fires {@link Handlers#activate} on the pressed
 * direct child. This lets apps attach {@code Handlers.onClick} to a node and
 * have it work intuitively — short clicks open dialogs / fire actions, sustained
 * presses drag. Threshold matches the {@code PointCloudController} for
 * consistency.
 */
public final class GraphSurfaceController {

    /**
     * Squared cursor-displacement threshold (px²) that distinguishes a
     * node click from a drag. Movement strictly below this stays a press
     * and resolves on mouse-up as a click; movement past it commits to a
     * drag (position updates flowing into
     * {@link GraphSurfacePositions}) and the eventual mouse-up does not
     * fire a click.
     */
    private static final float DRAG_THRESHOLD_PX_SQ = 4f * 4f;

    private static Component.GraphSurface pressedSurface = null;
    private static Component               pressedChild  = null;
    private static double pressX = 0d, pressY = 0d;
    private static float  dragOffsetX = 0f, dragOffsetY = 0f;
    private static boolean dragStarted = false;

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
                pressedSurface = surface;
                pressedChild   = directChild;
                pressX         = cursorX;
                pressY         = cursorY;
                dragOffsetX    = (float) cursorX - childRect.x();
                dragOffsetY    = (float) cursorY - childRect.y();
                dragStarted    = false;
                // Bring to front on any press — matches user expectation that
                // touching a node surfaces it, click or drag either way.
                GraphSurfaceZOrder.bumpToTop(surface, directChild);
                return true;
            }
        }
        return false;
    }

    public static void onCursorMove(double cursorX, double cursorY) {
        if (pressedSurface == null || pressedChild == null) return;

        if (!dragStarted) {
            double dx = cursorX - pressX;
            double dy = cursorY - pressY;
            if (dx * dx + dy * dy < DRAG_THRESHOLD_PX_SQ) return;
            dragStarted = true;
        }

        LayoutResult lr = LatestLayout.result();
        if (lr == null) return;
        PixelRect surfaceRect = lr.rectOf(pressedSurface);
        if (surfaceRect == null) return;
        PixelRect childRect = lr.rectOf(pressedChild);
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

        GraphSurfacePositions.set(pressedSurface, pressedChild, newEmX, newEmY);
    }

    public static void onMouseUp() {
        if (pressedSurface != null && pressedChild != null && !dragStarted) {
            // Press resolved without crossing the drag threshold — fire
            // a click on the pressed child so {@code Handlers.onClick} on
            // graph-surface children works.
            Component dispatchRoot = OverlayStack.activeInputRoot(LatestLayout.root());
            Handlers.activate(pressedChild, dispatchRoot);
        }
        pressedSurface = null;
        pressedChild   = null;
        dragStarted    = false;
    }

    /**
     * True while a press is active on a graph-surface child, whether or
     * not the drag threshold has been crossed. The App-level mouse-up
     * dispatcher uses this to suppress its generic click activation —
     * graph-surface clicks are dispatched from inside this controller
     * instead, so a {@code Handlers.onClick} on a node fires exactly
     * once.
     */
    public static boolean isDragging() {
        return pressedSurface != null;
    }

    public static void cancelDrag() {
        pressedSurface = null;
        pressedChild   = null;
        dragStarted    = false;
    }
}
