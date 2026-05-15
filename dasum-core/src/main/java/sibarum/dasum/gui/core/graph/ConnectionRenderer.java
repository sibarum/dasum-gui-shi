package sibarum.dasum.gui.core.graph;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.layout.LayoutResult;
import sibarum.dasum.gui.core.layout.PixelRect;
import sibarum.dasum.gui.core.render.Batcher;
import sibarum.dasum.gui.core.render.Color;
import sibarum.dasum.gui.core.render.DrawCommand;
import sibarum.dasum.gui.core.render.Vec2;

import java.util.List;

/**
 * Draws {@link Connection}s registered on a {@link Component.GraphSurface}
 * as stroked cubic Bezier curves. The curve flows from {@link Connection#from}
 * to {@link Connection#to} with horizontally-offset control points (standard
 * node-editor look) and a gradient stroke that interpolates from the source
 * port's {@link PortType#color} to the target port's color — so an
 * {@link PortType#ANY} ↔ typed connection visibly mixes.
 * <p>
 * The curve is tessellated on the CPU into a triangle strip and submitted
 * via the existing solid-fill batcher — no new material required.
 * {@link Render} calls {@link #render} after the surface's children so
 * curves sit on top of nodes; the endpoints overlap the port boxes which
 * gives the visual of the curve emerging from the port.
 */
public final class ConnectionRenderer {

    private static final int   SEGMENTS              = 32;
    private static final Em    STROKE_EM             = Em.of(0.16f);
    private static final Em    SELECTION_OUTLINE_EM  = Em.of(0.34f);
    private static final Color SELECTION_OUTLINE     = new Color(1.00f, 1.00f, 1.00f, 0.85f);
    private static final float MIN_HANDLE_PX         = 40f;

    private ConnectionRenderer() {}

    public static void render(Component.GraphSurface surface, LayoutResult layout, Batcher batcher) {
        List<Connection> conns = Connections.on(surface);
        if (conns.isEmpty()) return;
        Connection selected = (ConnectionSelection.surface() == surface)
            ? ConnectionSelection.connection() : null;
        for (Connection c : conns) {
            renderOne(c, layout, batcher, c == selected);
        }
    }

    private static void renderOne(Connection c, LayoutResult layout, Batcher batcher, boolean selected) {
        Ports.Port from = Ports.of(c.from());
        Ports.Port to   = Ports.of(c.to());
        if (from == null || to == null) return;
        PixelRect fromRect = layout.rectOf(c.from());
        PixelRect toRect   = layout.rectOf(c.to());
        if (fromRect == null || toRect == null) return;

        float x0 = fromRect.x() + fromRect.width()  * 0.5f;
        float y0 = fromRect.y() + fromRect.height() * 0.5f;
        float x3 = toRect.x()   + toRect.width()    * 0.5f;
        float y3 = toRect.y()   + toRect.height()   * 0.5f;

        if (selected) {
            // Halo first, then the normal stroke on top — gives a clean
            // white outline around the colored curve.
            stroke(batcher, x0, y0, x3, y3, SELECTION_OUTLINE, SELECTION_OUTLINE, SELECTION_OUTLINE_EM);
        }
        stroke(batcher, x0, y0, x3, y3, from.type().color(), to.type().color());
    }

    /**
     * Draw a stroked Bezier from {@code (x0,y0)} to {@code (x3,y3)} with the
     * standard node-editor horizontal-handle shape and the default stroke
     * thickness {@link #STROKE_EM}. Color is interpolated per-vertex.
     * Convenience overload over {@link #stroke(Batcher, float, float, float, float, Color, Color, Em)}.
     */
    public static void stroke(Batcher batcher, float x0, float y0, float x3, float y3, Color startColor, Color endColor) {
        stroke(batcher, x0, y0, x3, y3, startColor, endColor, STROKE_EM);
    }

    /** Variant that takes an explicit stroke thickness in em. */
    public static void stroke(Batcher batcher, float x0, float y0, float x3, float y3, Color startColor, Color endColor, Em strokeEm) {
        // Horizontal handle — standard node-editor curve shape. Length scales
        // with the gap between endpoints, with a minimum so very close ports
        // still get a visible arc.
        float handle = Math.max(MIN_HANDLE_PX, Math.abs(x3 - x0) * 0.5f);
        float x1 = x0 + handle, y1 = y0;
        float x2 = x3 - handle, y2 = y3;
        float halfStroke = strokeEm.toPixels() * 0.5f;
        emitStroke(batcher, x0, y0, x1, y1, x2, y2, x3, y3, startColor, endColor, halfStroke);
    }

    private static void emitStroke(Batcher batcher,
                                    float x0, float y0, float x1, float y1,
                                    float x2, float y2, float x3, float y3,
                                    Color startColor, Color endColor, float halfStroke) {
        // Sample the curve at SEGMENTS+1 points, accumulate perpendicular
        // offsets to form a triangle strip.
        float prevSx = 0f, prevSy = 0f;
        float prevPx = 0f, prevPy = 0f;
        Color prevColor = startColor;
        for (int i = 0; i <= SEGMENTS; i++) {
            float t  = (float) i / SEGMENTS;
            float mt = 1f - t;
            // Position at t.
            float sx = mt*mt*mt*x0 + 3f*mt*mt*t*x1 + 3f*mt*t*t*x2 + t*t*t*x3;
            float sy = mt*mt*mt*y0 + 3f*mt*mt*t*y1 + 3f*mt*t*t*y2 + t*t*t*y3;
            // Tangent (first derivative) at t.
            float tx = 3f*mt*mt*(x1 - x0) + 6f*mt*t*(x2 - x1) + 3f*t*t*(x3 - x2);
            float ty = 3f*mt*mt*(y1 - y0) + 6f*mt*t*(y2 - y1) + 3f*t*t*(y3 - y2);
            float len = (float) Math.sqrt(tx*tx + ty*ty);
            if (len < 1e-4f) len = 1f;
            // Perpendicular = rotate tangent 90°, scaled by half-stroke.
            float px = (-ty / len) * halfStroke;
            float py = ( tx / len) * halfStroke;
            Color color = mix(startColor, endColor, t);

            if (i > 0) {
                // Two triangles per segment, forming a flat quad strip.
                // a/b are the previous segment's left/right offset points;
                // c/d are this segment's.
                Vec2 a = new Vec2(prevSx + prevPx, prevSy + prevPy);
                Vec2 b = new Vec2(prevSx - prevPx, prevSy - prevPy);
                Vec2 d = new Vec2(sx + px, sy + py);
                Vec2 e = new Vec2(sx - px, sy - py);
                batcher.submit(new DrawCommand.ColoredTriangle(a, b, d, prevColor, prevColor, color));
                batcher.submit(new DrawCommand.ColoredTriangle(b, e, d, prevColor, color,     color));
            }
            prevSx = sx; prevSy = sy;
            prevPx = px; prevPy = py;
            prevColor = color;
        }
    }

    private static Color mix(Color a, Color b, float t) {
        return new Color(
            a.r() + (b.r() - a.r()) * t,
            a.g() + (b.g() - a.g()) * t,
            a.b() + (b.b() - a.b()) * t,
            a.a() + (b.a() - a.a()) * t
        );
    }
}
