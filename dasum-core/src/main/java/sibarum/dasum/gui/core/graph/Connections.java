package sibarum.dasum.gui.core.graph;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-{@code GraphSurface} list of {@link Connection}s. Connections are
 * stored data-first; the future bezier-curve render pass reads from here.
 * <p>
 * {@link #canConnect} is the authoritative compatibility check — applies
 * both the direction rule (at least one end can output, at least one end
 * can input) and the type rule ({@link PortTypeCompat#check}).
 * {@link #add} validates the same way and orients the new connection so
 * {@code from} is the output side when one exists; rendering can assume
 * the curve flows from → to.
 */
public final class Connections {

    private static final Map<Component, List<Connection>> BY_SURFACE = new IdentityHashMap<>();

    private Connections() {}

    /** All connections on {@code surface}, in insertion order. */
    public static List<Connection> on(Component surface) {
        List<Connection> list = BY_SURFACE.get(surface);
        return list == null ? List.of() : List.copyOf(list);
    }

    /**
     * Add a connection between two declared ports. Throws
     * {@link IllegalArgumentException} if either endpoint isn't a port or
     * the pair fails {@link #canConnect}. Orientation: {@code from} is set
     * to the OUTPUT side if one exists, else the first argument.
     */
    public static Connection add(Component surface, Component portA, Component portB) {
        Ports.Port a = Ports.of(portA);
        Ports.Port b = Ports.of(portB);
        if (a == null || b == null) {
            throw new IllegalArgumentException("Both endpoints must be declared via Ports.declare");
        }
        if (!canConnect(a, b)) {
            throw new IllegalArgumentException("Ports incompatible: "
                + a.type().displayName() + "/" + a.direction()
                + " <-> " + b.type().displayName() + "/" + b.direction());
        }
        Component from, to;
        if (a.direction() == PortDirection.OUTPUT || b.direction() == PortDirection.INPUT) {
            from = portA; to = portB;
        } else if (b.direction() == PortDirection.OUTPUT || a.direction() == PortDirection.INPUT) {
            from = portB; to = portA;
        } else {
            // BIDIRECTIONAL ↔ BIDIRECTIONAL — order is the call order.
            from = portA; to = portB;
        }
        Connection c = new Connection(from, to);
        BY_SURFACE.computeIfAbsent(surface, k -> new ArrayList<>()).add(c);
        Invalidator.invalidate();
        return c;
    }

    /** Remove a specific connection from the surface. No-op if not present. */
    public static void remove(Component surface, Connection c) {
        List<Connection> list = BY_SURFACE.get(surface);
        if (list != null && list.remove(c)) Invalidator.invalidate();
    }

    /**
     * Can these two ports be connected? Three rules, all must pass:
     * <ol>
     *   <li>Direction: at least one end can output AND at least one can input.</li>
     *   <li>Type: {@link PortTypeCompat#check} on the (oriented) port types.</li>
     *   <li>Application veto: {@link ConnectionRule#check} on the oriented
     *       {@link Ports.Port} pair — app's hook for cycle prevention,
     *       same-node blocking, fan-in limits, etc.</li>
     * </ol>
     * Symmetric in argument order — the controller passes whichever pair the
     * user dragged; the orientation is normalized below so a non-symmetric
     * custom rule sees output-side first when one is determinable.
     */
    public static boolean canConnect(Ports.Port a, Ports.Port b) {
        if (a == null || b == null) return false;
        PortDirection da = a.direction(), db = b.direction();
        boolean directionOk = (da.canOutput() || db.canOutput()) && (da.canInput() || db.canInput());
        if (!directionOk) return false;
        // Orient: output-side first when determinable. Both bidi → input order.
        Ports.Port outSide, inSide;
        if (da.canOutput() && !db.canOutput()) { outSide = a; inSide = b; }
        else if (db.canOutput() && !da.canOutput()) { outSide = b; inSide = a; }
        else { outSide = a; inSide = b; }
        if (!PortTypeCompat.check(outSide.type(), inSide.type())) return false;
        if (!ConnectionRule.check(outSide, inSide)) return false;
        return true;
    }
}
