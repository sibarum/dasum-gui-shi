package sibarum.dasum.gui.core.graph;

import sibarum.dasum.gui.core.component.AlignItems;
import sibarum.dasum.gui.core.component.Component;
import sibarum.dasum.gui.core.component.Direction;
import sibarum.dasum.gui.core.component.JustifyContent;
import sibarum.dasum.gui.core.em.Em;
import sibarum.dasum.gui.core.render.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Default node-layout helper. Produces a fit-to-content {@link Component.Flex}
 * with a title bar at top, input ports stacked on the left, output ports
 * stacked on the right, and bidirectional ports stacked along the bottom.
 * Each port is paired with a label.
 * <p>
 * For non-standard layouts (circular nodes, ports on top edges, port-only
 * nodes with no body, custom port visuals), build the node tree by hand
 * and call {@link Ports#declare} on each port component directly.
 * NodeBuilder is sugar for the common case; the framework primitives are
 * fully usable without it.
 *
 * <p>Example:
 * <pre>{@code
 * Component multiply = NodeBuilder.titled("Multiply")
 *     .input(NUMBER, "a")
 *     .input(NUMBER, "b")
 *     .output(NUMBER, "result")
 *     .background(NODE_BG)
 *     .build();
 *
 * GraphSurfacePositions.set(surface, multiply, 4f, 2f);
 * Ports.attachToSurface(surface, multiply);
 * }</pre>
 */
public final class NodeBuilder {

    private final String title;
    private final List<PortSpec> inputs  = new ArrayList<>();
    private final List<PortSpec> outputs = new ArrayList<>();
    private final List<PortSpec> bidis   = new ArrayList<>();

    private Color background = new Color(0.18f, 0.22f, 0.28f, 1f);
    private Color titleColor = new Color(0.97f, 0.97f, 0.97f, 1f);
    private Color labelColor = new Color(0.88f, 0.90f, 0.92f, 1f);
    private Em padding       = Em.of(0.6f);
    private Em portSize      = Em.of(1.0f);
    private Em titleFontSize = Em.of(1.15f);
    private Em labelFontSize = Em.of(0.95f);
    private Em rowGap        = Em.of(0.4f);
    private Em columnGap     = Em.of(1.2f);

    private record PortSpec(PortType type, String label) {}

    private NodeBuilder(String title) { this.title = title; }

    public static NodeBuilder titled(String title) { return new NodeBuilder(title); }

    public NodeBuilder input(PortType type, String label)         { inputs.add(new PortSpec(type, label));  return this; }
    public NodeBuilder output(PortType type, String label)        { outputs.add(new PortSpec(type, label)); return this; }
    public NodeBuilder bidirectional(PortType type, String label) { bidis.add(new PortSpec(type, label));   return this; }

    public NodeBuilder background(Color c)    { this.background = c;    return this; }
    public NodeBuilder titleColor(Color c)    { this.titleColor = c;    return this; }
    public NodeBuilder labelColor(Color c)    { this.labelColor = c;    return this; }
    public NodeBuilder padding(Em p)          { this.padding = p;       return this; }
    public NodeBuilder portSize(Em s)         { this.portSize = s;      return this; }
    public NodeBuilder titleFontSize(Em s)    { this.titleFontSize = s; return this; }
    public NodeBuilder labelFontSize(Em s)    { this.labelFontSize = s; return this; }
    public NodeBuilder rowGap(Em g)           { this.rowGap = g;        return this; }
    public NodeBuilder columnGap(Em g)        { this.columnGap = g;     return this; }

    /** Build the node component and declare all its ports. */
    public Component build() {
        // Title row.
        Component titleText = new Component.Text(title, titleFontSize, titleColor);
        Component.Flex titleRow = new Component.Flex(
            Em.AUTO, Em.AUTO, Em.ZERO, transparent(),
            Direction.ROW, JustifyContent.CENTER, AlignItems.CENTER, Em.ZERO,
            List.of(titleText), false, 0
        );

        List<PortInstance> inputInstances  = makePortInstances(inputs);
        List<PortInstance> outputInstances = makePortInstances(outputs);
        List<PortInstance> bidiInstances   = makePortInstances(bidis);

        List<Component> nodeChildren = new ArrayList<>();
        nodeChildren.add(titleRow);

        // Main row: inputs left, outputs right (only if either non-empty).
        if (!inputInstances.isEmpty() || !outputInstances.isEmpty()) {
            Component leftCol = new Component.Flex(
                Em.AUTO, Em.AUTO, Em.ZERO, transparent(),
                Direction.COLUMN, JustifyContent.START, AlignItems.START, rowGap,
                rowsFor(inputInstances, /*labelOnLeft*/ false), false, 0
            );
            Component rightCol = new Component.Flex(
                Em.AUTO, Em.AUTO, Em.ZERO, transparent(),
                Direction.COLUMN, JustifyContent.START, AlignItems.END, rowGap,
                rowsFor(outputInstances, /*labelOnLeft*/ true), false, 0
            );
            // width=AUTO (not null) so mainRow's intrinsic cross-axis reflects
            // its actual content (leftCol + gap + rightCol). With null, the
            // intrinsic would be 0 and the node would undersize, then STRETCH
            // would cram mainRow narrower than its content needs — the output
            // column overflowed off the right edge.
            Component mainRow = new Component.Flex(
                Em.AUTO, Em.AUTO, Em.ZERO, transparent(),
                Direction.ROW, JustifyContent.SPACE_BETWEEN, AlignItems.START, columnGap,
                List.of(leftCol, rightCol), false, 0
            );
            nodeChildren.add(mainRow);
        }

        // Bidirectional row at the bottom — port-then-label, centered.
        if (!bidiInstances.isEmpty()) {
            Component bidiRow = new Component.Flex(
                Em.AUTO, Em.AUTO, Em.ZERO, transparent(),
                Direction.ROW, JustifyContent.CENTER, AlignItems.CENTER, columnGap,
                rowsFor(bidiInstances, /*labelOnLeft*/ false), false, 0
            );
            nodeChildren.add(bidiRow);
        }

        Component.Flex node = new Component.Flex(
            Em.AUTO, Em.AUTO, padding, background,
            Direction.COLUMN, JustifyContent.START, AlignItems.STRETCH, rowGap,
            nodeChildren, true, 0
        );

        // Declare ports now that the node component exists.
        for (PortInstance pi : inputInstances)  Ports.declare(node, pi.port, pi.spec.type(), PortDirection.INPUT,         pi.spec.label());
        for (PortInstance pi : outputInstances) Ports.declare(node, pi.port, pi.spec.type(), PortDirection.OUTPUT,        pi.spec.label());
        for (PortInstance pi : bidiInstances)   Ports.declare(node, pi.port, pi.spec.type(), PortDirection.BIDIRECTIONAL, pi.spec.label());
        return node;
    }

    // ---------- internals ----------

    private record PortInstance(Component port, PortSpec spec) {}

    private List<PortInstance> makePortInstances(List<PortSpec> specs) {
        List<PortInstance> out = new ArrayList<>(specs.size());
        for (PortSpec spec : specs) {
            Component port = new Component.Box(portSize, portSize, Em.ZERO, spec.type().color()).withInteractive(true);
            out.add(new PortInstance(port, spec));
        }
        return out;
    }

    /**
     * Wrap each (port, label) pair in a Flex row. {@code labelOnLeft=false}
     * gives [port, label] (used for input + bidi columns); {@code true}
     * gives [label, port] (used for the output column so the port hugs
     * the right edge).
     */
    private List<Component> rowsFor(List<PortInstance> instances, boolean labelOnLeft) {
        List<Component> rows = new ArrayList<>(instances.size());
        for (PortInstance pi : instances) {
            Component label = new Component.Text(pi.spec.label(), labelFontSize, labelColor);
            List<Component> rowChildren = labelOnLeft
                ? List.of(label, pi.port)
                : List.of(pi.port, label);
            Component.Flex row = new Component.Flex(
                Em.AUTO, Em.AUTO, Em.ZERO, transparent(),
                Direction.ROW, JustifyContent.START, AlignItems.CENTER, Em.of(0.35f),
                rowChildren, false, 0
            );
            rows.add(row);
        }
        return rows;
    }

    private static Color transparent() { return new Color(0f, 0f, 0f, 0f); }
}
