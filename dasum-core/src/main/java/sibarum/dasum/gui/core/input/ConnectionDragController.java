package sibarum.dasum.gui.core.input;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;
import sibarum.dasum.gui.core.graph.ConnectionRenderer;
import sibarum.dasum.gui.core.graph.Connections;
import sibarum.dasum.gui.core.graph.GraphSurfaceChildren;
import sibarum.dasum.gui.core.graph.Ports;
import sibarum.dasum.gui.core.layout.HitTest;
import sibarum.dasum.gui.core.layout.LatestLayout;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.overlay.OverlayStack;
import sibarum.dasum.gui.core.render.Batcher;
import sibarum.dasum.gui.core.render.Color;

import java.util.List;

/**
 * Drag dispatch for creating connections between ports on a
 * {@link Component.GraphSurface}. Sits ahead of {@link GraphSurfaceController}
 * in the press chain so a press that lands on a port starts a
 * connection-drag instead of a node-body-drag.
 * <p>
 * Flow:
 * <ol>
 *   <li>Press on a port → drag begins. Source port + owning surface captured.</li>
 *   <li>Cursor moves → in-flight curve renders from source port to the cursor.
 *       If the cursor is over another port on the same surface, the target is
 *       latched and {@link Connections#canConnect} decides compatibility.
 *       Visual feedback: gradient (src → target color) when compatible,
 *       fade-to-red when incompatible, fade-to-transparent over nothing.</li>
 *   <li>Mouse up over a compatible port → {@link Connections#add}; drag ends.
 *       Mouse up elsewhere → drag cancels with no side effects.</li>
 *   <li>ESC / window focus loss → cancels the drag.</li>
 * </ol>
 */
public final class ConnectionDragController {

    private static final Color INCOMPAT_TIP   = new Color(0.90f, 0.30f, 0.30f, 1f);
    /** Alpha multiplier applied to the source-port color at the in-flight cursor end. */
    private static final float FREE_TIP_ALPHA = 0.35f;

    private static Component.GraphSurface dragSurface  = null;
    private static Component               sourcePort  = null;
    private static double cursorX = 0d;
    private static double cursorY = 0d;
    private static Component candidateTarget = null;
    private static boolean   candidateValid  = false;

    private ConnectionDragController() {}

    // ---------- input dispatch ----------

    /**
     * Begin a connection drag if the press landed on a port that belongs to a
     * GraphSurface. Returns {@code true} when consumed.
     */
    public static boolean onMouseDown(double pxX, double pxY) {
        LayoutResult lr = LatestLayout.result();
        if (lr == null) return false;
        Component root = OverlayStack.activeInputRoot(LatestLayout.root());
        if (root == null) return false;

        Component hit = HitTest.test(root, lr, (float) pxX, (float) pxY);
        if (hit == null) return false;
        Ports.Port port = Ports.of(hit);
        if (port == null) return false;

        // Find the owning GraphSurface by walking up the hit path.
        Component.GraphSurface owningSurface = surfaceContaining(root, port.node());
        if (owningSurface == null) return false;

        dragSurface = owningSurface;
        sourcePort  = hit;
        cursorX     = pxX;
        cursorY     = pxY;
        candidateTarget = null;
        candidateValid  = false;
        Invalidator.invalidate();
        return true;
    }

    public static void onCursorMove(double pxX, double pxY) {
        if (dragSurface == null) return;
        cursorX = pxX;
        cursorY = pxY;
        // Re-evaluate candidate target.
        LayoutResult lr = LatestLayout.result();
        Component root = OverlayStack.activeInputRoot(LatestLayout.root());
        if (lr != null && root != null) {
            Component hit = HitTest.test(root, lr, (float) pxX, (float) pxY);
            Ports.Port hitPort = (hit != null) ? Ports.of(hit) : null;
            if (hitPort != null && hit != sourcePort && portBelongsToSurface(hitPort)) {
                Ports.Port src = Ports.of(sourcePort);
                candidateTarget = hit;
                candidateValid  = Connections.canConnect(src, hitPort);
            } else {
                candidateTarget = null;
                candidateValid  = false;
            }
        }
        Invalidator.invalidate();
    }

    public static void onMouseUp() {
        if (dragSurface == null) return;
        if (candidateTarget != null && candidateValid) {
            try {
                Connections.add(dragSurface, sourcePort, candidateTarget);
            } catch (IllegalArgumentException ex) {
                // canConnect said yes but add still rejected — defensive no-op.
            }
        }
        cancelDrag();
    }

    public static void cancelDrag() {
        if (dragSurface == null) return;
        dragSurface = null;
        sourcePort  = null;
        candidateTarget = null;
        candidateValid  = false;
        Invalidator.invalidate();
    }

    public static boolean isDragging() {
        return dragSurface != null;
    }

    // ---------- render ----------

    /**
     * Draw the in-flight curve, if a drag is active on {@code surface}.
     * Called by {@code Render.renderGraphSurface} after the finalized
     * connection pass so the in-flight curve sits on top.
     */
    public static void renderInflight(Component.GraphSurface surface, LayoutResult layout, Batcher batcher) {
        if (dragSurface != surface || sourcePort == null) return;
        PixelRect sourceRect = layout.rectOf(sourcePort);
        if (sourceRect == null) return;

        float x0 = sourceRect.x() + sourceRect.width()  * 0.5f;
        float y0 = sourceRect.y() + sourceRect.height() * 0.5f;
        float x3 = (float) cursorX;
        float y3 = (float) cursorY;

        Color startColor = Ports.of(sourcePort).type().color();
        Color endColor;
        if (candidateTarget != null) {
            // Latched onto another port — match the finalized-connection look
            // (full gradient) when compatible; red tip when not.
            endColor = candidateValid
                ? Ports.of(candidateTarget).type().color()
                : INCOMPAT_TIP;
        } else {
            // Free-floating cursor — fade out so the line reads as "in flight".
            endColor = new Color(startColor.r(), startColor.g(), startColor.b(), FREE_TIP_ALPHA);
        }
        ConnectionRenderer.stroke(batcher, x0, y0, x3, y3, startColor, endColor);
    }

    // ---------- internals ----------

    private static Component.GraphSurface surfaceContaining(Component root, Component node) {
        List<Component> path = HitTest.pathTo(root, node);
        for (Component c : path) {
            if (c instanceof Component.GraphSurface s) return s;
        }
        return null;
    }

    private static boolean portBelongsToSurface(Ports.Port port) {
        if (dragSurface == null) return false;
        // Includes dynamically-added nodes so connections from spawned nodes
        // can target ports on the same surface.
        for (Component child : GraphSurfaceChildren.all(dragSurface)) {
            if (child == port.node()) return true;
        }
        return false;
    }
}
