package sibarum.dasum.gui.core.graph;

import sibarum.dasum.gui.core.component.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Identity-keyed registry marking specific components as ports of a node.
 * The framework doesn't care WHERE in the node's layout the port component
 * sits — apps put it wherever fits their visual design. The registry just
 * answers "is this component a port; if so, what node, what type, what
 * direction" — enough to drive connection-drag and rendering.
 * <p>
 * Most nodes are built via {@link NodeBuilder} which calls
 * {@link #declare} for each port internally. Apps that want fully custom
 * layouts call {@code declare} themselves.
 */
public final class Ports {

    /**
     * @param component   the on-screen port (typically a small {@code Box} —
     *                    any Component works as long as it's interactive)
     * @param type        what flows through this port
     * @param direction   input / output / bidirectional
     * @param node        the node component this port belongs to
     * @param name        optional name (e.g. "a", "result"); may be null
     */
    public record Port(Component component, PortType type, PortDirection direction, Component node, String name) {}

    private static final Map<Component, Port> PORTS = new IdentityHashMap<>();
    private static final Map<Component, List<Port>> BY_NODE = new IdentityHashMap<>();

    private Ports() {}

    /** Declare a port. */
    public static void declare(Component node, Component port, PortType type, PortDirection direction) {
        declare(node, port, type, direction, null);
    }

    public static void declare(Component node, Component port, PortType type, PortDirection direction, String name) {
        Port p = new Port(port, type, direction, node, name);
        PORTS.put(port, p);
        BY_NODE.computeIfAbsent(node, k -> new ArrayList<>()).add(p);
    }

    /** Returns the Port record for {@code component}, or {@code null} if it isn't a port. */
    public static Port of(Component component) {
        return PORTS.get(component);
    }

    /** All ports declared on {@code node}, in declaration order. */
    public static List<Port> onNode(Component node) {
        List<Port> list = BY_NODE.get(node);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** First port on {@code node} with the given name, or {@code null}. */
    public static Port byName(Component node, String name) {
        if (name == null) return null;
        for (Port p : onNode(node)) {
            if (Objects.equals(p.name(), name)) return p;
        }
        return null;
    }

    /**
     * Every port across every direct child of {@code surface} (declared
     * plus dynamically-added via {@link GraphSurfaceChildren#add}) that has
     * ports declared. No separate registration needed beyond {@link #declare}.
     */
    public static List<Port> onSurface(Component.GraphSurface surface) {
        List<Port> out = new ArrayList<>();
        for (Component child : GraphSurfaceChildren.all(surface)) {
            out.addAll(onNode(child));
        }
        return out;
    }

    /**
     * Per-component cleanup hook called by
     * {@link sibarum.dasum.gui.core.component.Components#detach}. Handles
     * three cases:
     * <ul>
     *   <li>{@code c} is a port — remove from {@link #PORTS} and from its
     *       owning node's list in {@link #BY_NODE}.</li>
     *   <li>{@code c} is a node — drop its entire entry in
     *       {@link #BY_NODE} (the port components themselves get cleared
     *       separately as the detach walk visits each one).</li>
     *   <li>{@code c} is neither — no-op.</li>
     * </ul>
     */
    public static void clear(Component c) {
        Port asPort = PORTS.remove(c);
        if (asPort != null) {
            List<Port> siblings = BY_NODE.get(asPort.node());
            if (siblings != null) {
                siblings.removeIf(p -> p.component() == c);
                if (siblings.isEmpty()) BY_NODE.remove(asPort.node());
            }
        }
        BY_NODE.remove(c);
    }
}
