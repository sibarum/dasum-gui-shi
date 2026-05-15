package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.graph.Connection;
import sibarum.dasum.gui.core.graph.ConnectionSelection;
import sibarum.dasum.gui.core.graph.Connections;
import sibarum.dasum.gui.core.layout.HitTest;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.overlay.OverlayStack;

/**
 * Left-click selection for {@link Connection}s on a {@link Component.GraphSurface}.
 * <p>
 * Only runs when the click's deepest hit is the GraphSurface itself —
 * clicks on nodes or ports fall through to {@code GraphSurfaceController}
 * and {@code ConnectionDragController}. This guarantees the controller
 * doesn't steal a curve hit-test from a node-body drag when the curve
 * happens to pass near the node's interior.
 * <p>
 * Hit-test: same cubic-Bezier sampling as the renderer (24 segments),
 * minimum point-to-segment distance vs. a threshold of half the stroke
 * width plus a small forgiveness margin. Click on a curve within threshold
 * selects it; click elsewhere on the surface clears any existing selection.
 */
public final class ConnectionSelectionController {

    private static final int SAMPLE_SEGMENTS = 24;
    private static final Em  HIT_PADDING_EM  = Em.of(0.25f); // forgiveness on top of the stroke
    private static final float MIN_HANDLE_PX = 40f;

    private ConnectionSelectionController() {}

    public static boolean onMouseDown(double pxX, double pxY) {
        LayoutResult lr = LatestLayout.result();
        Component root = OverlayStack.activeInputRoot(LatestLayout.root());
        if (lr == null || root == null) return false;

        Component hit = HitTest.test(root, lr, (float) pxX, (float) pxY);
        if (!(hit instanceof Component.GraphSurface surface)) {
            // Click landed on a node / port / something inside — don't touch
            // selection here. The press chain's next dispatcher handles it.
            return false;
        }

        Connection nearest = findNearest(surface, lr, (float) pxX, (float) pxY);
        if (nearest != null) {
            ConnectionSelection.set(surface, nearest);
            return true;
        }
        // Click on empty surface area — clear any existing selection on this
        // surface but don't consume; other dispatchers (none, currently)
        // could still react to the empty-surface press.
        if (ConnectionSelection.surface() == surface) {
            ConnectionSelection.clear();
        }
        return false;
    }

    /** Delete the currently-selected connection. Returns true when something was removed. */
    public static boolean onDelete() {
        if (!ConnectionSelection.has()) return false;
        Component.GraphSurface surface = ConnectionSelection.surface();
        Connection c = ConnectionSelection.connection();
        Connections.remove(surface, c);
        ConnectionSelection.clear();
        return true;
    }

    // ---------- hit-testing ----------

    private static Connection findNearest(Component.GraphSurface surface, LayoutResult lr, float px, float py) {
        float threshold = HIT_PADDING_EM.toPixels();
        float thresholdSq = threshold * threshold;
        Connection best = null;
        float bestSq = thresholdSq;
        for (Connection c : Connections.on(surface)) {
            float d2 = distanceSqToCurve(c, lr, px, py);
            if (d2 < bestSq) {
                bestSq = d2;
                best = c;
            }
        }
        return best;
    }

    private static float distanceSqToCurve(Connection c, LayoutResult lr, float px, float py) {
        PixelRect fromRect = lr.rectOf(c.from());
        PixelRect toRect   = lr.rectOf(c.to());
        if (fromRect == null || toRect == null) return Float.POSITIVE_INFINITY;

        float x0 = fromRect.x() + fromRect.width()  * 0.5f;
        float y0 = fromRect.y() + fromRect.height() * 0.5f;
        float x3 = toRect.x()   + toRect.width()    * 0.5f;
        float y3 = toRect.y()   + toRect.height()   * 0.5f;
        float handle = Math.max(MIN_HANDLE_PX, Math.abs(x3 - x0) * 0.5f);
        float x1 = x0 + handle, y1 = y0;
        float x2 = x3 - handle, y2 = y3;

        float prevX = x0, prevY = y0;
        float minSq = Float.POSITIVE_INFINITY;
        for (int i = 1; i <= SAMPLE_SEGMENTS; i++) {
            float t  = (float) i / SAMPLE_SEGMENTS;
            float mt = 1f - t;
            float sx = mt*mt*mt*x0 + 3f*mt*mt*t*x1 + 3f*mt*t*t*x2 + t*t*t*x3;
            float sy = mt*mt*mt*y0 + 3f*mt*mt*t*y1 + 3f*mt*t*t*y2 + t*t*t*y3;
            float d2 = pointToSegmentSq(px, py, prevX, prevY, sx, sy);
            if (d2 < minSq) minSq = d2;
            prevX = sx;
            prevY = sy;
        }
        return minSq;
    }

    private static float pointToSegmentSq(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float lenSq = dx*dx + dy*dy;
        if (lenSq < 1e-6f) {
            float qx = px - ax, qy = py - ay;
            return qx*qx + qy*qy;
        }
        float t = ((px - ax) * dx + (py - ay) * dy) / lenSq;
        if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
        float cx = ax + t * dx;
        float cy = ay + t * dy;
        float qx = px - cx, qy = py - cy;
        return qx*qx + qy*qy;
    }
}
