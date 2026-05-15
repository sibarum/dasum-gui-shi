package sibarum.dasum.gui.core.graph;

import java.util.function.BiPredicate;

/**
 * Application-level veto over connections. Consulted by
 * {@link Connections#canConnect} after the framework's direction-rule and
 * type-compatibility checks; if any of the three reject, the connection
 * is disallowed.
 * <p>
 * The default rule is "always allow." Apps override to enforce arbitrary
 * domain rules that the framework can't infer — examples:
 * <ul>
 *   <li>Block intra-node connections (a port can't connect to another port
 *       on the same node):
 *       {@code ConnectionRule.override((a, b) -> a.node() != b.node());}</li>
 *   <li>Reject if it would create a cycle (app walks its own graph state).</li>
 *   <li>Disallow connections that would exceed a per-port fan-in limit.</li>
 *   <li>Permit only output→input on a per-port-pair basis based on
 *       application state (mode flags, locked nodes, etc.).</li>
 * </ul>
 * <p>
 * The rule should be reflexive-friendly and symmetric — the framework
 * doesn't enforce either, but UX gets confusing if the rule disagrees with
 * itself depending on argument order. {@link Connections#canConnect}
 * passes the output-side port first when one is determinable.
 */
public final class ConnectionRule {

    private static BiPredicate<Ports.Port, Ports.Port> rule = (a, b) -> true;

    private ConnectionRule() {}

    /** Returns true if the application allows this connection. */
    public static boolean check(Ports.Port out, Ports.Port in) {
        return rule.test(out, in);
    }

    /** Replace the global rule. {@code null} resets to the default (always allow). */
    public static void override(BiPredicate<Ports.Port, Ports.Port> custom) {
        rule = (custom != null) ? custom : (a, b) -> true;
    }
}
