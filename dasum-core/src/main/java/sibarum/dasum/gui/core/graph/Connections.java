package sibarum.dasum.gui.core.graph;

import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.event.Invalidator;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

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

    /** Event delivered to listeners registered via {@link #addListener}. */
    public enum EventKind { ADDED, REMOVED }
    public record Event(EventKind kind, Component surface, Connection connection) {}

    private static final Map<Component, List<Connection>> BY_SURFACE = new IdentityHashMap<>();
    private static final List<Consumer<Event>> LISTENERS = new CopyOnWriteArrayList<>();

    private Connections() {}

    /**
     * Subscribe to connection add/remove events across every surface.
     * Listeners are invoked synchronously after the connection list has
     * been updated; the renderer is already invalidated by then. Useful
     * for mirroring connections into an external model (e.g. mirroring
     * dasum visual connections into an mcc {@code ComputationGraph}).
     *
     * <p>Listeners persist for the lifetime of the JVM — there is no
     * unregister API. Register once at startup.
     */
    public static void addListener(Consumer<Event> listener) {
        LISTENERS.add(listener);
    }

    private static void fire(EventKind kind, Component surface, Connection c) {
        if (LISTENERS.isEmpty()) return;
        Event e = new Event(kind, surface, c);
        for (Consumer<Event> l : LISTENERS) l.accept(e);
    }

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
        fire(EventKind.ADDED, surface, c);
        return c;
    }

    /** Remove a specific connection from the surface. No-op if not present. */
    public static void remove(Component surface, Connection c) {
        List<Connection> list = BY_SURFACE.get(surface);
        if (list != null && list.remove(c)) {
            Invalidator.invalidate();
            fire(EventKind.REMOVED, surface, c);
        }
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

    /**
     * Per-component cleanup hook called by
     * {@link sibarum.dasum.gui.core.component.Components#detach}. Handles
     * both surface-keyed entries (if {@code c} is a surface, drop its whole
     * per-surface list) and endpoint-keyed entries (if {@code c} is a port
     * component, remove every connection incident on it across all
     * surfaces).
     */
    public static void clear(Component c) {
        // Drain by-surface entries with c as the key, firing REMOVED for each
        // contained connection.
        List<Connection> dropped = BY_SURFACE.remove(c);
        if (dropped != null) {
            for (Connection conn : dropped) fire(EventKind.REMOVED, c, conn);
            Invalidator.invalidate();
            return;
        }
        // c is not a surface — look for it as an endpoint across every
        // surface's list and fire one REMOVED per incident connection.
        boolean changed = false;
        for (Map.Entry<Component, List<Connection>> e : BY_SURFACE.entrySet()) {
            Component surface = e.getKey();
            List<Connection> list = e.getValue();
            List<Connection> toRemove = new ArrayList<>();
            for (Connection conn : list) {
                if (conn.from() == c || conn.to() == c) toRemove.add(conn);
            }
            if (!toRemove.isEmpty()) {
                list.removeAll(toRemove);
                for (Connection conn : toRemove) fire(EventKind.REMOVED, surface, conn);
                changed = true;
            }
        }
        if (changed) Invalidator.invalidate();
    }
}
